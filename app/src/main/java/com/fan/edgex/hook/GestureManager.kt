package com.fan.edgex.hook

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.ConfigProvider
import com.fan.edgex.config.HookConfigSnapshot
import de.robv.android.xposed.XposedBridge

@SuppressLint("StaticFieldLeak")
object GestureManager {

    private const val TAG = "EdgeX"

    // system_server context (from filterInputEvent hook, for config queries)
    private var systemContext: Context? = null
    // Legacy SystemUI context. Runtime overlays now run from system_server.
    private var systemUIContext: Context? = null

    private val CONFIG_URI = ConfigProvider.CONTENT_URI

    // Whether we've registered the screen state broadcast receiver (system_server)
    private var screenStateReceiverRegistered = false
    private var systemConfigReceiverRegistered = false
    private var systemUiConfigReceiverRegistered = false
    private var keyManagerInitialized = false

    private var mHandler: Handler? = null

    private val nativeTouchHandoff = NativeTouchHandoff { message ->
        log(message)
    }
    private val configRepository = HookConfigRepository(
        contentUri = CONFIG_URI,
        supportedKeysProvider = { KeyManager.SUPPORTED_KEYS.keys },
        keyTriggersProvider = { AppConfig.KEY_TRIGGERS },
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
                    actionDispatcher.triggerGestureAction(zone, gestureType, context, touchX, touchY)
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

    private fun ensureSystemServerInitialized(context: Context, initializeKeys: Boolean) {
        if (systemContext == null) {
            systemContext = context
            configRepository.attachSystemContext(context)
            configRepository.reloadAsync()
            registerScreenStateReceiver(context)
            registerConfigChangeReceiver(context, systemUi = false)
            configRepository.ensureObserverRegistered(context) {
                configRepository.reloadAsync(::refreshDebugOverlay)
            }
            debugOverlayController.initialize(context)
            configRepository.reloadAsync(::refreshDebugOverlay)
        }
        if (initializeKeys && !keyManagerInitialized) {
            KeyManager.init(context)
            keyManagerInitialized = true
        }
    }

    private fun ensureSystemUiInitialized(context: Context) {
        if (systemUIContext != null) return

        systemUIContext = context
        configRepository.attachSystemUiContext(context)
        registerConfigChangeReceiver(context, systemUi = true)
        configRepository.ensureObserverRegistered(context) {
            configRepository.reloadAsync(::refreshDebugOverlay)
        }
        debugOverlayController.initialize(context)
        configRepository.reloadAsync(::refreshDebugOverlay)
        log("SystemUI overlay initialized with broadcast receiver")
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
                        XposedBridge.log("$TAG: SCREEN_OFF — resetting gesture and key state")
                        gestureDetector.reset()
                        KeyManager.reset()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        XposedBridge.log("$TAG: SCREEN_ON — state is clean")
                    }
                    Intent.ACTION_USER_UNLOCKED -> {
                        XposedBridge.log("$TAG: USER_UNLOCKED — reloading config post-FBE")
                        configRepository.invalidate()
                        configRepository.reloadAsync()
                    }
                }
            }
        }

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_UNLOCKED)
            }
            context.registerReceiver(receiver, filter)
            XposedBridge.log("$TAG: Screen state receiver registered in system_server")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Failed to register screen state receiver: ${e.message}")
            screenStateReceiverRegistered = false
        }
    }

    private fun registerConfigChangeReceiver(context: Context, systemUi: Boolean) {
        if (systemUi) {
            if (systemUiConfigReceiverRegistered) return
            systemUiConfigReceiverRegistered = true
        } else {
            if (systemConfigReceiverRegistered) return
            systemConfigReceiverRegistered = true
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != HookConfigSnapshot.ACTION_CONFIG_CHANGED) return

                val keys = intent.getStringArrayExtra(HookConfigSnapshot.EXTRA_KEYS)
                val values = intent.getStringArrayExtra(HookConfigSnapshot.EXTRA_VALUES)
                if (keys != null && values != null) {
                    configRepository.updateFromBroadcast(keys, values)
                    refreshDebugOverlay()
                } else if (intent.getBooleanExtra(HookConfigSnapshot.EXTRA_FULL_SNAPSHOT, false)) {
                    configRepository.invalidate()
                    configRepository.reloadAsync(::refreshDebugOverlay)
                }
            }
        }

        try {
            val filter = IntentFilter(HookConfigSnapshot.ACTION_CONFIG_CHANGED)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            log("Config broadcast receiver registered in ${if (systemUi) "SystemUI" else "system_server"}")
        } catch (e: Exception) {
            if (systemUi) {
                systemUiConfigReceiverRegistered = false
            } else {
                systemConfigReceiverRegistered = false
            }
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

        // Skip gestures when keyguard (lockscreen) is showing to avoid intercepting unlock swipes
        try {
            val km = context.getSystemService(android.app.KeyguardManager::class.java)
            if (km?.isKeyguardLocked == true) return false
        } catch (_: Exception) {}

        return gestureDetector.handle(event, context)
    }

    /**
     * Called from system_server (filterInputEvent hook) for KeyEvents.
     * Delegates to KeyManager for state machine processing.
     */
    fun handleKeyEvent(event: KeyEvent, context: Context, hookParam: de.robv.android.xposed.XC_MethodHook.MethodHookParam, policyFlags: Int = 0): Boolean {
        ensureSystemServerInitialized(context, initializeKeys = true)

        return KeyManager.handleKeyEvent(event, context, hookParam, policyFlags)
    }

    /**
     * Execute an action triggered by a key press (called from KeyManager).
     */
    fun executeKeyAction(action: String, context: Context) {
        actionDispatcher.executeKeyAction(action, context)
    }

    /**
     * Legacy entry point kept for older scoped installs. New runtime overlays
     * initialize from system_server.
     */
    fun initSystemUI(ctx: Context) {
        ensureSystemUiInitialized(ctx)
    }

}
