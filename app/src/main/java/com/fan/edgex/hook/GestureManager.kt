package com.fan.edgex.hook

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.HookConfigSnapshot
import com.fan.edgex.config.ModuleActivationState
import com.fan.edgex.overlay.PanelOverlayManager
import com.fan.edgex.overlay.PieManager
import de.robv.android.xposed.XposedBridge

@SuppressLint("StaticFieldLeak")
object GestureManager {

    private const val TAG = "EdgeX"

    private var systemContext: Context? = null
    private var windowAnchor: View? = null

    private var screenStateReceiverRegistered = false
    private var systemConfigReceiverRegistered = false
    private var keyManagerInitialized = false
    private var fluidEffectPremiumInactiveLogged = false
    private val fluidEffectActionLock = Any()
    private var fluidEffectSequence = 0L
    private var activeFluidEffectId = 0L
    private var fluidEffectActive = false
    private val pendingFluidEffectActions = mutableListOf<PendingFluidEffectAction>()
    private var fluidEffectMovePosted = false
    private var pendingFluidEffectMoveX = 0f
    private var pendingFluidEffectMoveY = 0f
    private var activeEdgeLightingNotificationKey: String? = null

    private var mHandler: Handler? = null

    private data class PendingFluidEffectAction(
        val gestureId: Long,
        val action: Runnable,
    )

    private val nativeTouchHandoff = NativeTouchHandoff { message ->
        log(message)
    }
    private val configRepository = HookConfigRepository(
        updateKeyConfig = KeyManager::updateConfig,
        log = ::log,
    )
    private val actionDispatcher by lazy {
        GestureActionDispatcher(
            resolveConfig = configRepository::get,
            handlerProvider = ::mainHandler,
            log = ::log,
        )
    }
    private val debugOverlayController = DebugOverlayController(
        config = object : DebugOverlayController.ConfigAccess {
            override fun isGesturesEnabled(): Boolean = configRepository.isGesturesEnabled()
            override fun isZoneEnabled(zone: String): Boolean = configRepository.isZoneEnabled(zone)
            override fun isDebugEnabled(): Boolean = configRepository.get(AppConfig.DEBUG_MATRIX) == "true"
        },
        log = ::log,
    )
    private val gestureDetector by lazy {
        EdgeGestureDetector(
            handoff = nativeTouchHandoff,
            handlerProvider = ::mainHandler,
            callbacks = object : EdgeGestureDetector.Callbacks {
                override fun isZoneEnabled(zone: String): Boolean =
                    configRepository.isZoneEnabled(zone)

                override fun resolveAction(zone: String, gestureType: String): String {
                    val direct = configRepository.get(AppConfig.gestureAction(zone, gestureType))
                    if (direct.isNotEmpty() && direct != "none") return direct

                    val fallbackZone = AppConfig.fallbackEdgeZone(zone) ?: return direct
                    return configRepository.get(AppConfig.gestureAction(fallbackZone, gestureType), direct)
                }

                override fun dispatchAction(
                    zone: String,
                    gestureType: String,
                    context: Context,
                    touchX: Float,
                    touchY: Float,
                ) {
                    val action = Runnable {
                        actionDispatcher.triggerGestureAction(zone, gestureType, context, touchX, touchY)
                    }
                    if (!enqueueUntilFluidEffectComplete(action)) {
                        action.run()
                    }
                }

                override fun performContinuousAdjustment(action: String, context: Context, up: Boolean) {
                    when {
                        action == "brightness_up" || action == "brightness_down" ->
                            actionDispatcher.adjustBrightness(context, up)
                        action == "volume_up" || action == "volume_down" ->
                            actionDispatcher.adjustVolume(context, up)
                    }
                }

                override fun isGlobalCopyModeActive(): Boolean =
                    TextSelectionOverlay.isShowing()

                override fun log(message: String) {
                    gestureLog(message)
                }

                override fun showPie(context: Context, anchorX: Float, anchorY: Float, edge: String) {
                    mainHandler().post { actionDispatcher.showPie(context, anchorX, anchorY, edge) }
                }

                override fun updatePie(x: Float, y: Float) {
                    mainHandler().post { PieManager.update(x, y) }
                }

                override fun commitPie(context: Context) {
                    mainHandler().post { actionDispatcher.commitPieAction(context) }
                }

                override fun cancelPie() {
                    mainHandler().post { PieManager.dismiss() }
                }

                override fun onFluidEffectDown(
                    zone: String,
                    touchX: Float,
                    touchY: Float,
                    screenW: Float,
                    screenH: Float,
                ) {
                    if (configRepository.get(AppConfig.FLUID_EFFECT_ENABLED, "true") != "true") return
                    if (!PremiumRuntime.isActive()) {
                        if (!fluidEffectPremiumInactiveLogged) {
                            fluidEffectPremiumInactiveLogged = true
                            log("Fluid Effect skipped: Supporter Extras premium DEX is not active")
                        }
                        return
                    }

                    val edge = zone.substringBefore("_")
                    val edgeColorKey = when (edge) {
                        "left" -> AppConfig.FLUID_EFFECT_COLOR_LEFT
                        "right" -> AppConfig.FLUID_EFFECT_COLOR_RIGHT
                        "top" -> AppConfig.FLUID_EFFECT_COLOR_TOP
                        "bottom" -> AppConfig.FLUID_EFFECT_COLOR_BOTTOM
                        else -> null
                    }
                    val colorValue = edgeColorKey
                        ?.let { configRepository.get(it).takeIf(String::isNotEmpty) }
                        ?: configRepository.get(AppConfig.FLUID_EFFECT_COLOR, DEFAULT_FLUID_EFFECT_COLOR)
                    val color = parseFluidEffectColor(colorValue)
                    val sizeProgress = configRepository
                        .get(AppConfig.FLUID_EFFECT_SIZE, AppConfig.FLUID_EFFECT_SIZE_DEFAULT.toString())
                        .toIntOrNull()
                        ?.coerceIn(0, 100)
                        ?: AppConfig.FLUID_EFFECT_SIZE_DEFAULT
                    val alpha = configRepository
                        .get(AppConfig.FLUID_EFFECT_ALPHA, "0.8")
                        .toFloatOrNull()
                        ?.coerceIn(0f, 1f)
                        ?: 0.8f

                    val sysCtx = systemContext ?: return
                    val gestureId = beginFluidEffectGate()
                    mainHandler().post {
                        val shown = PremiumRuntime.onFluidEffectDown(
                            sysCtx,
                            edge,
                            touchX,
                            touchY,
                            screenW,
                            screenH,
                            color,
                            sizeProgress,
                            alpha,
                        )
                        if (!shown) {
                            log("Fluid Effect skipped: premium runtime returned false")
                            completeFluidEffectGate(gestureId)
                        }
                    }
                }

                override fun onFluidEffectMove(touchX: Float, touchY: Float) {
                    postFluidEffectMove(touchX, touchY)
                }

                override fun onFluidEffectUp() {
                    val gestureId = currentFluidEffectId()
                    mainHandler().post {
                        if (gestureId == 0L) {
                            PremiumRuntime.onFluidEffectUp()
                            return@post
                        }
                        val accepted = PremiumRuntime.onFluidEffectUp(
                            Runnable { completeFluidEffectGate(gestureId) },
                        )
                        if (!accepted) {
                            completeFluidEffectGate(gestureId)
                        }
                    }
                }
            },
        )
    }

    private fun mainHandler(): Handler =
        mHandler ?: Handler(Looper.getMainLooper()).also { mHandler = it }

    private fun log(message: String) {
        XposedBridge.log("$TAG: $message")
    }

    private fun gestureLog(message: String) {
        XposedBridge.log("$TAG: [Gesture] $message")
    }

    private fun beginFluidEffectGate(): Long =
        synchronized(fluidEffectActionLock) {
            fluidEffectSequence += 1
            activeFluidEffectId = fluidEffectSequence
            fluidEffectActive = true
            activeFluidEffectId
        }

    private fun currentFluidEffectId(): Long =
        synchronized(fluidEffectActionLock) {
            if (fluidEffectActive) activeFluidEffectId else 0L
        }

    private fun enqueueUntilFluidEffectComplete(action: Runnable): Boolean =
        synchronized(fluidEffectActionLock) {
            if (!fluidEffectActive) return false
            pendingFluidEffectActions += PendingFluidEffectAction(activeFluidEffectId, action)
            true
        }

    private fun postFluidEffectMove(touchX: Float, touchY: Float) {
        synchronized(fluidEffectActionLock) {
            if (!fluidEffectActive) return
            pendingFluidEffectMoveX = touchX
            pendingFluidEffectMoveY = touchY
            if (fluidEffectMovePosted) return
            fluidEffectMovePosted = true
        }
        mainHandler().post {
            val move = synchronized(fluidEffectActionLock) {
                fluidEffectMovePosted = false
                if (!fluidEffectActive) return@post
                pendingFluidEffectMoveX to pendingFluidEffectMoveY
            }
            PremiumRuntime.onFluidEffectMove(move.first, move.second)
        }
    }

    private fun completeFluidEffectGate(gestureId: Long) {
        val actions = synchronized(fluidEffectActionLock) {
            if (activeFluidEffectId == gestureId) {
                fluidEffectActive = false
            }
            fluidEffectMovePosted = false
            val ready = pendingFluidEffectActions.filter { it.gestureId == gestureId }
            pendingFluidEffectActions.removeAll { it.gestureId == gestureId }
            ready
        }
        actions.forEach { pending ->
            mainHandler().post(pending.action)
        }
    }

    private fun cancelFluidEffectGate() {
        synchronized(fluidEffectActionLock) {
            fluidEffectActive = false
            fluidEffectMovePosted = false
            pendingFluidEffectActions.clear()
        }
    }

    private fun ensureSystemServerInitialized(context: Context, initializeKeys: Boolean) {
        if (systemContext == null) {
            systemContext = context
            configRepository.attachSystemContext(context)
            configRepository.reloadAsync()
            registerScreenStateReceiver(context)
            registerConfigChangeReceiver(context)
            mainHandler().post {
                debugOverlayController.initialize(context)
                addWindowAnchor(context)
                FlashlightManager.initialize(context, mainHandler())
                configRepository.reloadAsync(::refreshDebugOverlay)
            }
            actionDispatcher.bindShellService(context)
        }
        if (initializeKeys && !keyManagerInitialized) {
            KeyManager.init(context)
            keyManagerInitialized = true
        }
    }

    fun initSystemServer(context: Context) {
        ensureSystemServerInitialized(context, initializeKeys = false)
    }

    private fun refreshDebugOverlay() {
        debugOverlayController.refresh()
    }

    /**
     * Register broadcast receiver for SCREEN_OFF/ON in system_server process.
     * Resets gesture and key state when the screen turns off to prevent
     * stale state from blocking touch after unlock.
     */
    private fun registerScreenStateReceiver(context: Context) {
        if (screenStateReceiverRegistered) return
        screenStateReceiverRegistered = true

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        cancelFluidEffectGate()
                        gestureDetector.reset()
                        KeyManager.reset()
                        mainHandler().post {
                            PieManager.dismiss()
                            PanelOverlayManager.dismiss()
                            PremiumRuntime.onScreenOff()
                        }
                    }
                    Intent.ACTION_USER_UNLOCKED -> {
                        configRepository.invalidate()
                        configRepository.reloadAsync()
                        actionDispatcher.onUserUnlocked(ctx)
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        actionDispatcher.onUserUnlocked(ctx)
                    }
                }
            }
        }

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_UNLOCKED)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            context.registerReceiver(receiver, filter)
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Failed to register screen state receiver: ${e.message}")
            screenStateReceiverRegistered = false
        }
    }

    private fun registerConfigChangeReceiver(context: Context) {
        if (systemConfigReceiverRegistered) return
        systemConfigReceiverRegistered = true

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    HookConfigSnapshot.ACTION_CONFIG_CHANGED -> {
                        val keys = intent.getStringArrayExtra(HookConfigSnapshot.EXTRA_KEYS)
                        val values = intent.getStringArrayExtra(HookConfigSnapshot.EXTRA_VALUES)
                        if (keys != null && values != null) {
                            configRepository.updateFromBroadcast(
                                keys,
                                values,
                                intent.getBooleanExtra(HookConfigSnapshot.EXTRA_FULL_SNAPSHOT, false),
                            )
                            refreshDebugOverlay()
                        } else if (intent.getBooleanExtra(HookConfigSnapshot.EXTRA_FULL_SNAPSHOT, false)) {
                            configRepository.invalidate()
                            configRepository.reloadAsync(::refreshDebugOverlay)
                        }
                    }
                    HookConfigSnapshot.ACTION_EXECUTE_ACTION -> {
                        val action = intent.getStringExtra(HookConfigSnapshot.EXTRA_ACTION_CODE).orEmpty()
                        if (action.isBlank() || action == "none") return

                        log("Execute action request from UI: $action")
                        actionDispatcher.executeKeyAction(action, ctx)
                    }
                    HookConfigSnapshot.ACTION_HOOK_STATUS_REQUEST -> {
                        ctx.sendBroadcast(ModuleActivationState.responseIntent(System.currentTimeMillis()))
                    }
                    HookConfigSnapshot.ACTION_EDGE_LIGHTING -> {
                        if (configRepository.get(AppConfig.EDGE_LIGHTING_ENABLED) != "true") return

                        val sysCtx = systemContext ?: ctx
                        val color = intent.getIntExtra(
                            HookConfigSnapshot.EXTRA_EDGE_LIGHTING_COLOR,
                            parseColor(configRepository.get(AppConfig.EDGE_LIGHTING_COLOR), 0xFF00FFFF.toInt()),
                        )
                        val durationMs = intent.getIntExtra(
                            HookConfigSnapshot.EXTRA_EDGE_LIGHTING_DURATION_MS,
                            configRepository.get(AppConfig.EDGE_LIGHTING_DURATION_MS).toIntOrNull() ?: 3000,
                        ).coerceIn(500, 60000)
                        val widthDp = (configRepository.get(AppConfig.EDGE_LIGHTING_WIDTH_DP).toIntOrNull() ?: 5)
                            .coerceIn(1, 20)
                        val alpha = (configRepository.get(AppConfig.EDGE_LIGHTING_ALPHA).toFloatOrNull() ?: 1f)
                            .coerceIn(0f, 1f)
                        val effect = configRepository.get(
                            AppConfig.EDGE_LIGHTING_EFFECT,
                            AppConfig.EDGE_LIGHTING_EFFECT_BASIC,
                        )
                        val notificationKey = intent.getStringExtra(
                            HookConfigSnapshot.EXTRA_EDGE_LIGHTING_NOTIFICATION_KEY,
                        ) ?: return
                        mainHandler().post {
                            if (PremiumRuntime.showEdgeLighting(sysCtx, effect, color, durationMs, widthDp, alpha)) {
                                activeEdgeLightingNotificationKey = notificationKey
                            }
                        }
                    }
                    HookConfigSnapshot.ACTION_EDGE_LIGHTING_DISMISS -> {
                        val notificationKey = intent.getStringExtra(
                            HookConfigSnapshot.EXTRA_EDGE_LIGHTING_NOTIFICATION_KEY,
                        ) ?: return
                        mainHandler().post {
                            if (notificationKey == activeEdgeLightingNotificationKey) {
                                activeEdgeLightingNotificationKey = null
                                PremiumRuntime.dismissEdgeLighting()
                            }
                        }
                    }
                    GameModeManager.ACTION_DISABLE -> {
                        val sysCtx = systemContext ?: ctx
                        val h = mainHandler()
                        h.post { GameModeManager.disable(sysCtx, h) }
                    }
                    FlashlightManager.ACTION_TURN_OFF -> {
                        val sysCtx = systemContext ?: ctx
                        val h = mainHandler()
                        h.post { FlashlightManager.turnOff(sysCtx, h) }
                    }
                }
            }
        }

        try {
            val filter = IntentFilter().apply {
                addAction(HookConfigSnapshot.ACTION_CONFIG_CHANGED)
                addAction(HookConfigSnapshot.ACTION_EXECUTE_ACTION)
                addAction(HookConfigSnapshot.ACTION_HOOK_STATUS_REQUEST)
                addAction(HookConfigSnapshot.ACTION_EDGE_LIGHTING)
                addAction(HookConfigSnapshot.ACTION_EDGE_LIGHTING_DISMISS)
                addAction(GameModeManager.ACTION_DISABLE)
                addAction(FlashlightManager.ACTION_TURN_OFF)
            }
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } catch (e: Exception) {
            systemConfigReceiverRegistered = false
            log("Failed to register config broadcast receiver: ${e.message}")
        }
    }

    /**
     * Called from system_server (filterInputEvent hook).
     * Handles MotionEvent at the input pipeline level and consumes touches
     * once a gesture starts from an enabled edge zone.
     */
    fun handleMotionEvent(event: MotionEvent, context: Context): Boolean {
        ensureSystemServerInitialized(context, initializeKeys = false)

        if (!configRepository.isGesturesEnabled()) return false
        if (GameModeManager.isActive) return false

        return gestureDetector.handle(event, context)
    }

    /**
     * Called from system_server (filterInputEvent hook) for KeyEvents.
     * Delegates to KeyManager for state machine processing.
     */
    fun handleKeyEvent(event: KeyEvent, context: Context, hookParam: de.robv.android.xposed.XC_MethodHook.MethodHookParam, policyFlags: Int = 0): Boolean {
        ensureSystemServerInitialized(context, initializeKeys = true)

        if (GameModeManager.isActive) return false
        return KeyManager.handleKeyEvent(event, context, hookParam, policyFlags)
    }

    fun executeKeyAction(action: String, context: Context) {
        actionDispatcher.executeKeyAction(action, context)
    }

    /**
     * Adds a 1×1 transparent system window so that system_server always has a visible
     * window in WMS. Android 15 home-screen protection blocks startActivity() from any
     * process that has no visible WMS window when the launcher task is in the foreground,
     * even for SYSTEM_UID callers. This anchor satisfies that check without any visual
     * effect (1 px, fully transparent, FLAG_NOT_TOUCHABLE).
     */
    private fun addWindowAnchor(context: Context) {
        if (windowAnchor != null) return
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val anchor = View(context)
            val params = WindowManager.LayoutParams(
                1, 1,
                2027, // TYPE_ACCESSIBILITY_MAGNIFICATION_OVERLAY – usable by system processes
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            wm.addView(anchor, params)
            windowAnchor = anchor
            log("Window anchor added")
        } catch (t: Throwable) {
            log("Failed to add window anchor: ${t.message}")
        }
    }

    private fun parseColor(value: String, fallback: Int): Int =
        try {
            android.graphics.Color.parseColor(value)
        } catch (_: Exception) {
            fallback
        }

    private fun parseFluidEffectColor(value: String): Int {
        val normalized = value.removePrefix("#")
        return runCatching {
            when (normalized.length) {
                6 -> (0xFF000000.toInt() or normalized.toInt(16))
                8 -> java.lang.Long.parseLong(normalized, 16).toInt()
                else -> java.lang.Long.parseLong(DEFAULT_FLUID_EFFECT_COLOR, 16).toInt()
            }
        }.getOrDefault(0xCCFFFFFF.toInt())
    }

    private const val DEFAULT_FLUID_EFFECT_COLOR = "CCFFFFFF"

}
