package com.fan.edgex.hook

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.ConfigProvider
import com.fan.edgex.overlay.DrawerManager
import de.robv.android.xposed.XposedBridge

@SuppressLint("StaticFieldLeak")
object GestureManager {

    private const val TAG = "EdgeX"

    // system_server context (from filterInputEvent hook, for config queries)
    private var systemContext: Context? = null
    // SystemUI context (for overlay windows like DrawerWindow)
    private var systemUIContext: Context? = null
    private var windowManager: WindowManager? = null

    private val CONFIG_URI = ConfigProvider.CONTENT_URI

    // Broadcast action for cross-process communication (system_server -> SystemUI)
    private const val ACTION_PERFORM = "com.fan.edgex.ACTION_PERFORM"
    private const val EXTRA_ACTION = "action"

    // Whether we've registered the screen state broadcast receiver (system_server)
    private var screenStateReceiverRegistered = false
    // Whether we've registered the screen state broadcast receiver (SystemUI)
    private var screenStateReceiverRegisteredUI = false

    private var mHandler: Handler? = null

    // Debug overlay views (in SystemUI process)
    private val debugViews = mutableListOf<DebugOverlayView>()

    private val nativeTouchHandoff = NativeTouchHandoff { message ->
        XposedBridge.log("$TAG: $message")
    }
    private val actionDispatcher by lazy {
        GestureActionDispatcher(
            configUri = CONFIG_URI,
            actionBroadcast = ACTION_PERFORM,
            actionExtra = EXTRA_ACTION,
            resolveConfig = ::getConfig,
            systemContextProvider = { systemContext },
            systemUiContextProvider = { systemUIContext },
            handlerProvider = ::mainHandler,
            log = { message -> XposedBridge.log("$TAG: $message") },
        )
    }
    private val gestureDetector by lazy {
        EdgeGestureDetector(
            handoff = nativeTouchHandoff,
            handlerProvider = ::mainHandler,
            callbacks = object : EdgeGestureDetector.Callbacks {
                override fun isZoneEnabled(zone: String): Boolean =
                    GestureManager.isZoneEnabled(zone)

                override fun resolveAction(zone: String, gestureType: String): String =
                    getConfig(AppConfig.gestureAction(zone, gestureType))

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

                override fun log(message: String) {
                    XposedBridge.log("$TAG: [Gesture] $message")
                }
            },
        )
    }

    private fun mainHandler(): Handler =
        mHandler ?: Handler(Looper.getMainLooper()).also { mHandler = it }

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
                        lastConfigLoad = 0
                        reloadConfigAsync()
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
        // Lazily init system context and register screen state receiver
        if (systemContext == null) {
            systemContext = context
            reloadConfigAsync()
            registerScreenStateReceiver(context)
            // Register config observer so changes are applied immediately
            registerConfigObserver(context)
        }

        if (!isGesturesEnabled()) return false

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
        // Lazily init system context, KeyManager, and screen state receiver
        if (systemContext == null) {
            systemContext = context
            reloadConfigAsync()
            KeyManager.init(context)
            registerScreenStateReceiver(context)
            registerConfigObserver(context)
        }

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
        if (systemUIContext != null) return // Already initialized
        systemUIContext = ctx
        windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Register BroadcastReceiver to receive action commands from system_server
        registerActionReceiver(ctx)

        // Register SCREEN_OFF receiver in SystemUI to auto-dismiss overlays
        registerScreenStateReceiverUI(ctx)

        // Add debug overlay views (left + right edge)
        try {
            addDebugOverlayView(Gravity.LEFT)
            addDebugOverlayView(Gravity.RIGHT)
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to add debug overlay views: ${t.message}")
        }

        // Load initial config
        reloadConfigAsyncForUI()

        XposedBridge.log("$TAG: SystemUI overlay initialized with broadcast receiver")
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
            XposedBridge.log("$TAG: Action BroadcastReceiver registered in SystemUI")
        } catch (e: Exception) {
            try {
                val filter = IntentFilter(ACTION_PERFORM)
                ctx.registerReceiver(receiver, filter)
                XposedBridge.log("$TAG: Action BroadcastReceiver registered (legacy) in SystemUI")
            } catch (e2: Exception) {
                XposedBridge.log("$TAG: Failed to register BroadcastReceiver: ${e2.message}")
            }
        }
    }

    /**
     * Register broadcast receiver for SCREEN_OFF in SystemUI process.
     * Auto-dismisses overlay windows (DrawerWindow, TextSelectionOverlay)
     * when the screen turns off, preventing them from blocking touch after unlock.
     */
    private fun registerScreenStateReceiverUI(ctx: Context) {
        if (screenStateReceiverRegisteredUI) return
        screenStateReceiverRegisteredUI = true

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    XposedBridge.log("$TAG: SCREEN_OFF in SystemUI — dismissing overlays")
                    Handler(Looper.getMainLooper()).post {
                        try {
                            TextSelectionOverlay.dismiss()
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: Failed to dismiss TextSelectionOverlay: ${t.message}")
                        }
                        try {
                            DrawerManager.dismissDrawer()
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: Failed to dismiss DrawerWindow: ${t.message}")
                        }
                    }
                }
            }
        }

        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            ctx.registerReceiver(receiver, filter)
            XposedBridge.log("$TAG: Screen state receiver registered in SystemUI")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Failed to register screen state receiver in SystemUI: ${e.message}")
            screenStateReceiverRegisteredUI = false
        }
    }

    // ======== Debug Overlay View (SystemUI process, visual only) ========

    private fun addDebugOverlayView(gravity: Int) {
        val ctx = systemUIContext ?: return
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = ctx.resources.displayMetrics
        val widthPx = (12 * metrics.density).toInt()

        val view = DebugOverlayView(ctx, gravity)
        val params = WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.MATCH_PARENT,
            2027, // TYPE_MAGNIFICATION_OVERLAY
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = gravity

        wm.addView(view, params)
        debugViews.add(view)
    }

    /**
     * Debug-only overlay view. Not touchable - purely for visual feedback.
     */
    class DebugOverlayView(context: Context, val edgeGravity: Int) : View(context) {
        private var windowOffsetY = 0
        private var lastScreenHeight = 0
        private val handler = Handler(Looper.getMainLooper())
        private val paint = android.graphics.Paint()
        private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        private val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                handler.post {
                    updateWindowRegion()
                    invalidate()
                }
            }
        }

        init {
            paint.color = 0x00000000
            paint.style = android.graphics.Paint.Style.FILL

            setWillNotDraw(false)
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            val observer = object : android.database.ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    lastConfigLoad = 0
                    reloadConfigAsyncForUI()
                }
            }
            try {
                context.contentResolver.registerContentObserver(CONFIG_URI, true, observer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            displayManager.registerDisplayListener(displayListener, handler)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            displayManager.unregisterDisplayListener(displayListener)
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

            if (getConfig(AppConfig.DEBUG_MATRIX) != "true") return
            if (!isGesturesEnabled()) return

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val realSize = android.graphics.Point()
            wm.defaultDisplay.getRealSize(realSize)
            
            // Detect screen rotation and update overlay region
            if (realSize.y != lastScreenHeight) {
                lastScreenHeight = realSize.y
                handler.post {
                    updateWindowRegion()
                }
            }
            
            // realSize.y is always the height in current orientation
            val h = realSize.y.toFloat()
            val w = width.toFloat()

            val offsetY = -windowOffsetY.toFloat()

            if (edgeGravity == Gravity.LEFT) {
                if (isZoneEnabled("left_top"))
                    canvas.drawRect(0f, 0f + offsetY, w, h * 0.33f + offsetY, paint)
                if (isZoneEnabled("left_mid"))
                    canvas.drawRect(0f, h * 0.33f + offsetY, w, h * 0.66f + offsetY, paint)
                if (isZoneEnabled("left_bottom"))
                    canvas.drawRect(0f, h * 0.66f + offsetY, w, h + offsetY, paint)
            } else {
                if (isZoneEnabled("right_top"))
                    canvas.drawRect(0f, 0f + offsetY, w, h * 0.33f + offsetY, paint)
                if (isZoneEnabled("right_mid"))
                    canvas.drawRect(0f, h * 0.33f + offsetY, w, h * 0.66f + offsetY, paint)
                if (isZoneEnabled("right_bottom"))
                    canvas.drawRect(0f, h * 0.66f + offsetY, w, h + offsetY, paint)
            }
        }

        fun updateWindowRegion() {
            val ctx = context ?: return
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // Get real screen size (rotated)
            val realSize = android.graphics.Point()
            wm.defaultDisplay.getRealSize(realSize)
            // realSize.y is always the height in current orientation
            val h = realSize.y

            var minTop = h
            var maxBottom = 0
            var hasEnabledZone = false

            if (edgeGravity == Gravity.LEFT) {
                if (isZoneEnabled("left_top")) {
                    minTop = kotlin.math.min(minTop, 0)
                    maxBottom = kotlin.math.max(maxBottom, (h * 0.33f).toInt())
                    hasEnabledZone = true
                }
                if (isZoneEnabled("left_mid")) {
                    minTop = kotlin.math.min(minTop, (h * 0.33f).toInt())
                    maxBottom = kotlin.math.max(maxBottom, (h * 0.66f).toInt())
                    hasEnabledZone = true
                }
                if (isZoneEnabled("left_bottom")) {
                    minTop = kotlin.math.min(minTop, (h * 0.66f).toInt())
                    maxBottom = kotlin.math.max(maxBottom, h)
                    hasEnabledZone = true
                }
            } else {
                if (isZoneEnabled("right_top")) {
                    minTop = kotlin.math.min(minTop, 0)
                    maxBottom = kotlin.math.max(maxBottom, (h * 0.33f).toInt())
                    hasEnabledZone = true
                }
                if (isZoneEnabled("right_mid")) {
                    minTop = kotlin.math.min(minTop, (h * 0.33f).toInt())
                    maxBottom = kotlin.math.max(maxBottom, (h * 0.66f).toInt())
                    hasEnabledZone = true
                }
                if (isZoneEnabled("right_bottom")) {
                    minTop = kotlin.math.min(minTop, (h * 0.66f).toInt())
                    maxBottom = kotlin.math.max(maxBottom, h)
                    hasEnabledZone = true
                }
            }
            try {
                val params = layoutParams as WindowManager.LayoutParams
                val isDebug = getConfig(AppConfig.DEBUG_MATRIX) == "true"
                when {
                    isDebug -> {
                        visibility = VISIBLE
                        params.gravity = Gravity.TOP or edgeGravity
                        params.y = 0
                        params.height = h
                        windowOffsetY = 0
                    }
                    !hasEnabledZone -> {
                        params.height = 0
                        visibility = GONE
                        windowOffsetY = 0
                    }
                    else -> {
                        visibility = VISIBLE
                        params.gravity = Gravity.TOP or edgeGravity
                        params.y = minTop
                        params.height = maxBottom - minTop
                        windowOffsetY = minTop
                    }
                }
                wm.updateViewLayout(this, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun updateDebugColor(color: Int) {
            paint.color = color
        }
    }

    // ======== Configuration ========

    // Config observer (system_server) to receive immediate updates from ContentProvider
    private var configObserverRegistered = false
    private var configContentObserver: android.database.ContentObserver? = null

    private fun registerConfigObserver(ctx: Context) {
        if (configObserverRegistered) return
        configObserverRegistered = true
        val handler = Handler(Looper.getMainLooper())
        val observer = object : android.database.ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                XposedBridge.log("$TAG: Config change observed: $uri")
                // Force reload
                lastConfigLoad = 0
                reloadConfigAsync()
            }
        }
        try {
            ctx.contentResolver.registerContentObserver(CONFIG_URI, true, observer)
            configContentObserver = observer
            XposedBridge.log("$TAG: Config ContentObserver registered in process")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Failed to register config observer: ${e.message}")
        }
    }

    private val configCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var lastConfigLoad = 0L
    private val CONFIG_CACHE_TTL = 2000L
    private val configExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "EdgeX-Config").apply { isDaemon = true }
    }

    private fun reloadConfigAsync() {
        if (System.currentTimeMillis() - lastConfigLoad < CONFIG_CACHE_TTL) return

        configExecutor.execute {
            try {
                loadConfigKeys()
                lastConfigLoad = System.currentTimeMillis()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun reloadConfigAsyncForUI() {
        if (System.currentTimeMillis() - lastConfigLoad < CONFIG_CACHE_TTL) return

        configExecutor.execute {
            try {
                loadConfigKeys()
                lastConfigLoad = System.currentTimeMillis()

                Handler(Looper.getMainLooper()).post {
                    val debug = getConfig(AppConfig.DEBUG_MATRIX) == "true"
                    val color = if (debug) 0x3300FF00.toInt() else 0x00000000

                    debugViews.forEach {
                        it.updateDebugColor(color)
                        it.updateWindowRegion()
                        it.destroyDrawingCache()
                        it.invalidate()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadConfigKeys() {
        fun query(key: String) { configCache[key] = queryConfigProvider(key) }

        query(AppConfig.GESTURES_ENABLED)
        query(AppConfig.DEBUG_MATRIX)
        query(AppConfig.KEYS_ENABLED)

        for (zone in AppConfig.ZONES) {
            query(AppConfig.zoneEnabled(zone))
            for (gesture in AppConfig.GESTURES) {
                query(AppConfig.gestureAction(zone, gesture))
            }
        }

        for (keyCode in KeyManager.SUPPORTED_KEYS.keys) {
            query(AppConfig.keyEnabled(keyCode))
            for (trigger in AppConfig.KEY_TRIGGERS) {
                query(AppConfig.keyAction(keyCode, trigger))
            }
        }

        KeyManager.updateConfig(configCache)
    }

    private fun isGesturesEnabled(): Boolean =
        getConfig(AppConfig.GESTURES_ENABLED) == "true"

    private fun isZoneEnabled(zone: String): Boolean =
        getConfig(AppConfig.zoneEnabled(zone)) == "true"

    private fun getConfig(key: String, defValue: String = ""): String =
        configCache[key] ?: defValue

    private fun queryConfigProvider(key: String): String {
        val ctx = systemContext ?: systemUIContext ?: return ""
        try {
            ctx.contentResolver.query(
                Uri.withAppendedPath(CONFIG_URI, key),
                null, null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        } catch (_: Exception) {
            // Expected on Binder-restricted threads; async reload will handle it
        }
        return ""
    }

}
