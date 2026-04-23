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
import de.robv.android.xposed.XposedBridge

@SuppressLint("StaticFieldLeak")
object GestureManager {

    private const val TAG = "EdgeX"

    // system_server context (from filterInputEvent hook, for config queries)
    private var systemContext: Context? = null
    // SystemUI context (for overlay windows like DrawerWindow)
    private var systemUIContext: Context? = null

    private val CONFIG_URI = ConfigProvider.CONTENT_URI

    // Broadcast action for cross-process communication (system_server -> SystemUI)
    private const val ACTION_PERFORM = "com.fan.edgex.ACTION_PERFORM"
    private const val EXTRA_ACTION = "action"

    // Whether we've registered the screen state broadcast receiver (system_server)
    private var screenStateReceiverRegistered = false
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
            configUri = CONFIG_URI,
            actionBroadcast = ACTION_PERFORM,
            actionExtra = EXTRA_ACTION,
            resolveConfig = configRepository::get,
            systemContextProvider = { systemContext },
            systemUiContextProvider = { systemUIContext },
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

                override fun resolveAction(zone: String, gestureType: String): String =
                    configRepository.get(AppConfig.gestureAction(zone, gestureType))

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
            configRepository.ensureObserverRegistered(context) {
                configRepository.reloadAsync()
            }
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
        registerActionReceiver(context)
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
     * Called from SystemUI process to initialize overlay windows and broadcast receiver.
     * Used for debug visualization and DrawerWindow.
     */
    fun initSystemUI(ctx: Context) {
        ensureSystemUiInitialized(ctx)
    }

    /**
     * Register a BroadcastReceiver in SystemUI to handle action commands
     * sent from system_server via filterInputEvent hook.
     */
    private fun registerActionReceiver(ctx: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.getStringExtra(EXTRA_ACTION) ?: return
                actionDispatcher.handleSystemUiAction(action, context)
            }
        }

        try {
            val filter = IntentFilter(ACTION_PERFORM)
            // Must use RECEIVER_EXPORTED so system_server (different UID) can reach us
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            log("Action BroadcastReceiver registered in SystemUI")
        } catch (e: Exception) {
            try {
                val filter = IntentFilter(ACTION_PERFORM)
                ctx.registerReceiver(receiver, filter)
                log("Action BroadcastReceiver registered (legacy) in SystemUI")
            } catch (e2: Exception) {
                log("Failed to register BroadcastReceiver: ${e2.message}")
            }
        }
    }

}
