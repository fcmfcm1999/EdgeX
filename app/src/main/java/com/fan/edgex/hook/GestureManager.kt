package com.fan.edgex.hook

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

import android.view.WindowManager
import android.widget.Toast
import com.fan.edgex.overlay.DrawerManager
import de.robv.android.xposed.XposedBridge
import kotlin.math.abs
import kotlin.math.sqrt

@SuppressLint("StaticFieldLeak")
object GestureManager {

    private const val TAG = "EdgeX"

    // system_server context (from filterInputEvent hook, for config queries)
    private var systemContext: Context? = null
    // SystemUI context (for overlay windows like DrawerWindow)
    private var systemUIContext: Context? = null
    private var windowManager: WindowManager? = null

    // Config URI
    private val CONFIG_URI = Uri.parse("content://com.fan.edgex.provider/config")

    // Broadcast action for cross-process communication (system_server -> SystemUI)
    private const val ACTION_PERFORM = "com.fan.edgex.ACTION_PERFORM"
    private const val EXTRA_ACTION = "action"

    // Gesture detection variables (used in system_server process)
    private var mDownX = 0f
    private var mDownY = 0f
    private var mTargetX = 0f
    private var mTargetY = 0f
    private var mIsSwiping = false
    private var mIsInSection = false
    private var mActiveZone: String? = null
    private var mGestureDownTime = 0L  // Timestamp when mIsInSection was set true
    private const val GESTURE_TIMEOUT_MS = 5000L  // Safety timeout to auto-reset stale gesture state

    // Whether we've registered the screen state broadcast receiver (system_server)
    private var screenStateReceiverRegistered = false
    // Whether we've registered the screen state broadcast receiver (SystemUI)
    private var screenStateReceiverRegisteredUI = false

    private val TOUCH_SLOP = 24
    
    // Simple bilingual support for SystemUI process (can't access app resources)
    private fun isChinese(context: Context): Boolean {
        return try {
            val locales = context.resources.configuration.locales
            locales.size() > 0 && locales[0].language == "zh"
        } catch (t: Throwable) {
            false
        }
    }
    
    private fun getLocalizedString(context: Context, en: String, zh: String): String {
        return if (isChinese(context)) zh else en
    }
    private val SWIPE_THRESHOLD = 50

    private var mHandler: Handler? = null

    // Debug overlay views (in SystemUI process)
    private val debugViews = mutableListOf<DebugOverlayView>()

    /**
     * Reset all gesture detection state.
     * Called on SCREEN_OFF to prevent stale state from blocking touches after unlock.
     */
    private fun resetGestureState() {
        val wasInSection = mIsInSection
        mIsInSection = false
        mActiveZone = null
        mIsSwiping = false
        mDownX = 0f
        mDownY = 0f
        mTargetX = 0f
        mTargetY = 0f
        mGestureDownTime = 0L
        if (wasInSection) {
            XposedBridge.log("$TAG: [Gesture] resetGestureState — cleared stale mIsInSection")
        }
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
                        resetGestureState()
                        KeyManager.reset()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        XposedBridge.log("$TAG: SCREEN_ON — state is clean")
                    }
                }
            }
        }

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
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
        }

        // Safety guard: if mIsInSection has been true for too long without
        // receiving UP/CANCEL (e.g. screen was locked mid-gesture),
        // auto-reset to prevent blocking touch events.
        if (mIsInSection && mGestureDownTime > 0) {
            val elapsed = System.currentTimeMillis() - mGestureDownTime
            if (elapsed > GESTURE_TIMEOUT_MS) {
                XposedBridge.log("$TAG: [Gesture] Safety timeout: mIsInSection stuck for ${elapsed}ms — resetting")
                resetGestureState()
            }
        }

        if (!isGesturesEnabled()) return false

        val action = event.actionMasked
        val x = event.rawX
        val y = event.rawY
        val pointerCount = event.pointerCount

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val realSize = android.graphics.Point()
                wm.defaultDisplay.getRealSize(realSize)
                val screenWidth = realSize.x
                val screenHeight = realSize.y
                
                val density = context.resources.displayMetrics.density
                
                val edgeThreshold = 12 * density
                val isInLeftEdge = x < edgeThreshold
                val isInRightEdge = x > (screenWidth - edgeThreshold)

                if (!isInLeftEdge && !isInRightEdge) {
                    if (mIsInSection) {
                        XposedBridge.log("$TAG: [Gesture] DOWN outside edge while mIsInSection=true — resetting state")
                    }
                    mIsInSection = false
                    return false
                }

                val side = if (isInLeftEdge) "left" else "right"
                val verticalZone = when {
                    y < screenHeight * 0.33f -> "top"
                    y < screenHeight * 0.66f -> "mid"
                    else -> "bottom"
                }
                val zone = "${side}_${verticalZone}"
                if (!isZoneEnabled(zone)) {
                    mIsInSection = false
                    return false
                }

                XposedBridge.log("$TAG: [Gesture] DOWN zone=$zone x=${"%.1f".format(x)} y=${"%.1f".format(y)} pointers=$pointerCount")
                mIsInSection = true
                mGestureDownTime = System.currentTimeMillis()
                mActiveZone = zone
                mDownX = x
                mDownY = y
                mTargetX = x
                mTargetY = y
                mIsSwiping = false
                return true // consume: prevent app from seeing edge touch
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                XposedBridge.log("$TAG: [Gesture] POINTER_DOWN pointers=$pointerCount mIsInSection=$mIsInSection — cancelling gesture")
                // Extra finger added: cancel the gesture to avoid direction confusion
                mIsInSection = false
                mActiveZone = null
                mIsSwiping = false
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!mIsInSection) return false
                updateTargetPoint(x, y)

                if (!mIsSwiping) {
                    val dx = x - mDownX
                    val dy = y - mDownY
                    if (sqrt(dx * dx + dy * dy) > TOUCH_SLOP) {
                        mIsSwiping = true
                        XposedBridge.log("$TAG: [Gesture] SWIPING started zone=$mActiveZone dx=${"%.1f".format(dx)} dy=${"%.1f".format(dy)}")
                    }
                }
                return true // consume while tracking
            }

            MotionEvent.ACTION_UP -> {
                if (!mIsInSection) {
                    mIsSwiping = false
                    return false
                }

                val zone = mActiveZone

                if (mIsSwiping && zone != null) {
                    updateTargetPoint(x, y)
                    val dx = x - mDownX
                    val dy = y - mDownY
                    var gestureType: String? = null

                    if (abs(dx) > abs(dy)) {
                        if (abs(dx) > SWIPE_THRESHOLD) {
                            gestureType = if (dx < 0) "swipe_left" else "swipe_right"
                        }
                    } else {
                        if (abs(dy) > SWIPE_THRESHOLD) {
                            gestureType = if (dy < 0) "swipe_up" else "swipe_down"
                        }
                    }

                    XposedBridge.log("$TAG: [Gesture] UP zone=$zone dx=${"%.1f".format(dx)} dy=${"%.1f".format(dy)} => gestureType=$gestureType pointers=$pointerCount")

                    if (gestureType != null) {
                        triggerAction(zone, gestureType, context, mTargetX, mTargetY)
                    }
                } else {
                    XposedBridge.log("$TAG: [Gesture] UP zone=$zone mIsSwiping=$mIsSwiping — no gesture triggered")
                }

                mIsInSection = false
                mActiveZone = null
                mIsSwiping = false
                mGestureDownTime = 0L
                return true // consume
            }

            MotionEvent.ACTION_CANCEL -> {
                XposedBridge.log("$TAG: [Gesture] CANCEL zone=$mActiveZone")
                mIsInSection = false
                mActiveZone = null
                mIsSwiping = false
                mGestureDownTime = 0L
                return true // consume
            }

            else -> {
                if (mIsInSection) {
                    XposedBridge.log("$TAG: [Gesture] unhandled action=$action pointers=$pointerCount mIsInSection=$mIsInSection")
                }
            }
        }

        return false
    }

    private fun updateTargetPoint(x: Float, y: Float) {
        when {
            mActiveZone?.startsWith("left_") == true -> {
                if (x > mTargetX) {
                    mTargetX = x
                    mTargetY = y
                }
            }
            mActiveZone?.startsWith("right_") == true -> {
                if (x < mTargetX) {
                    mTargetX = x
                    mTargetY = y
                }
            }
            else -> {
                mTargetX = x
                mTargetY = y
            }
        }
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
        }

        return KeyManager.handleKeyEvent(event, context, hookParam, policyFlags)
    }

    /**
     * Execute an action triggered by a key press (called from KeyManager).
     */
    fun executeKeyAction(action: String, context: Context) {
        val handler = mHandler ?: Handler(Looper.getMainLooper()).also { mHandler = it }
        handler.post {
            performAction(action, context, 0f, 0f)
        }
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
                performActionInSystemUI(action, context)
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

            if (getConfig("debug_matrix_enabled", "boolean", "false") != "true") return
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
                if (!hasEnabledZone) {
                    params.height = 0
                    visibility = GONE
                } else {
                    visibility = VISIBLE
                    params.gravity = Gravity.TOP or edgeGravity
                    params.y = minTop
                    params.height = maxBottom - minTop
                }

                windowOffsetY = if (hasEnabledZone) minTop else 0
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

    private val configCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var lastConfigLoad = 0L
    private val CONFIG_CACHE_TTL = 2000L

    private fun reloadConfigAsync() {
        if (System.currentTimeMillis() - lastConfigLoad < CONFIG_CACHE_TTL) return

        Thread {
            try {
                loadConfigKeys()
                lastConfigLoad = System.currentTimeMillis()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun reloadConfigAsyncForUI() {
        if (System.currentTimeMillis() - lastConfigLoad < CONFIG_CACHE_TTL) return

        Thread {
            try {
                loadConfigKeys()
                lastConfigLoad = System.currentTimeMillis()

                Handler(Looper.getMainLooper()).post {
                    val debug = getConfig("debug_matrix_enabled", "boolean") == "true"
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
        }.start()
    }

    private fun loadConfigKeys() {
        val keys = mutableListOf(
            "gestures_enabled", "debug_matrix_enabled",
            "zone_enabled_left_top", "zone_enabled_left_mid", "zone_enabled_left_bottom",
            "zone_enabled_right_top", "zone_enabled_right_mid", "zone_enabled_right_bottom",
            "left_top_swipe_left", "left_top_swipe_right", "left_top_swipe_up", "left_top_swipe_down",
            "left_mid_swipe_left", "left_mid_swipe_right", "left_mid_swipe_up", "left_mid_swipe_down",
            "left_bottom_swipe_left", "left_bottom_swipe_right", "left_bottom_swipe_up", "left_bottom_swipe_down",
            "right_top_swipe_left", "right_top_swipe_right", "right_top_swipe_up", "right_top_swipe_down",
            "right_mid_swipe_left", "right_mid_swipe_right", "right_mid_swipe_up", "right_mid_swipe_down",
            "right_bottom_swipe_left", "right_bottom_swipe_right", "right_bottom_swipe_up", "right_bottom_swipe_down",
            // Keys config
            "keys_enabled"
        )
        
        // Add key-specific configs for each supported key
        for (keyCode in KeyManager.SUPPORTED_KEYS.keys) {
            keys.add("key_enabled_$keyCode")
            keys.add("key_${keyCode}_click")
            keys.add("key_${keyCode}_double_click")
            keys.add("key_${keyCode}_long_press")
        }

        for (key in keys) {
            val type = if (key.startsWith("zone_enabled") || key.startsWith("key_enabled") || 
                          key == "gestures_enabled" || key == "debug_matrix_enabled" || 
                          key == "keys_enabled") "boolean" else "string"
            configCache[key] = queryConfigProvider(key, type)
        }
        
        // Update KeyManager with new config
        KeyManager.updateConfig(configCache)
    }

    private fun isGesturesEnabled(): Boolean {
        return getConfig("gestures_enabled", "boolean", "true") != "false"
    }

    private fun isZoneEnabled(zone: String): Boolean {
        return getConfig("zone_enabled_$zone", "boolean", "false") == "true"
    }

    private fun getConfig(key: String, type: String = "string", defValue: String = ""): String {
        reloadConfigAsync()
        // NEVER do sync IPC here — this runs on the InputDispatcher thread.
        // Return cached value or default; async reload will populate cache.
        return configCache[key] ?: defValue
    }

    private fun queryConfigProvider(key: String, type: String): String {
        val ctx = systemContext ?: systemUIContext ?: return ""
        try {
            val cursor: Cursor? = ctx.contentResolver.query(
                CONFIG_URI,
                null,
                key,
                arrayOf(""),
                type
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(0)
                }
            }
        } catch (e: Exception) {
            // Expected on Binder-restricted threads; async reload will handle it
        }
        return ""
    }

    // ======== Action Handling ========

    private fun triggerAction(zone: String, gestureType: String, context: Context, touchX: Float, touchY: Float) {
        val configKey = "${zone}_${gestureType}"
        val action = getConfig(configKey, "string")

        XposedBridge.log("$TAG: [Gesture] triggerAction key=$configKey action='$action'")

        if (action.isNotEmpty() && action != "none") {
            // Post to main handler — NEVER block the InputDispatcher thread with action execution
            val handler = mHandler ?: Handler(Looper.getMainLooper()).also { mHandler = it }
            handler.post {
                performAction(action, context, touchX, touchY)
            }
        }
    }
    /**
     * Called from system_server process. For shell-based actions, execute directly.
     * For UI actions (drawer, toast, etc.), send broadcast to SystemUI.
     */
    private fun performAction(action: String, context: Context, touchX: Float, touchY: Float) {
        XposedBridge.log("$TAG: performAction called with action='$action'")
        when {
            action == "back" -> {
                XposedBridge.log("$TAG: Performing GLOBAL_ACTION_BACK")
                val result = GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_BACK)
                XposedBridge.log("$TAG: GLOBAL_ACTION_BACK result=$result")
            }
            action == "home" -> {
                XposedBridge.log("$TAG: Performing GLOBAL_ACTION_HOME")
                val result = GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_HOME)
                XposedBridge.log("$TAG: GLOBAL_ACTION_HOME result=$result")
            }
            action == "recent" || action == "recents" -> {
                XposedBridge.log("$TAG: Performing GLOBAL_ACTION_RECENTS")
                val result = GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_RECENTS)
                XposedBridge.log("$TAG: GLOBAL_ACTION_RECENTS result=$result")
            }
            action == "notifications" -> {
                GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_NOTIFICATIONS)
            }
            action == "quick_settings" -> {
                GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_QUICK_SETTINGS)
            }
            action == "power_dialog" -> {
                GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_POWER_DIALOG)
            }
            action == "lock_screen" -> {
                GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_LOCK_SCREEN)
            }
            action == "screenshot" -> {
                performScreenshot(context)
            }
            action == "universal_copy" -> {
                UniversalCopyManager.collectAllTexts(context) { result ->
                    when (result.status) {
                        UniversalCopyManager.CollectStatus.FOUND -> {
                            TextSelectionOverlay.show(context, result.blocks)
                        }
                        UniversalCopyManager.CollectStatus.NO_TEXT -> {
                            showToast(context, getLocalizedString(context, "No text found", "未找到可复制文本"))
                        }
                        UniversalCopyManager.CollectStatus.UNAVAILABLE -> {
                            showToast(context, getLocalizedString(context, "Global copy unavailable", "全局复制不可用"))
                        }
                    }
                }
            }
            action.startsWith("shell:") -> {
                executeShellCommand(action, context)
            }
            else -> {
                // UI actions need SystemUI context -> send broadcast
                sendActionToSystemUI(action, context)
            }
        }
    }

    /**
     * Execute a shell command action.
     * Since system_server cannot directly execute shell commands due to SELinux,
     * we send it to SystemUI process for execution.
     * Format: shell:{runAsRoot}:{command}
     */
    private fun executeShellCommand(action: String, context: Context) {
        // Send to SystemUI for execution (system_server has SELinux restrictions)
        sendActionToSystemUI(action, context)
    }

    /**
     * Actually execute shell command (called in SystemUI process).
     * Format: shell:{runAsRoot}:{command}
     */
    private fun doExecuteShellCommand(action: String, context: Context) {
        Thread {
            try {
                val content = action.removePrefix("shell:")
                val parts = content.split(":", limit = 2)
                if (parts.size != 2) {
                    showToast(context, getLocalizedString(context, "Invalid shell command format", "无效的命令格式"))
                    return@Thread
                }

                val runAsRoot = parts[0] == "true"
                val command = parts[1]

                if (command.isBlank()) {
                    showToast(context, getLocalizedString(context, "Empty command", "空命令"))
                    return@Thread
                }

                XposedBridge.log("$TAG: Executing shell command (root=$runAsRoot): $command")

                val shell = if (runAsRoot) "su" else "sh"
                val process = Runtime.getRuntime().exec(shell)
                val outputStream = process.outputStream

                // Write command lines
                val lines = command.split("\n")
                for (line in lines) {
                    outputStream.write((line + "\n").toByteArray())
                    outputStream.flush()
                }
                outputStream.write("exit\n".toByteArray())
                outputStream.flush()

                val exitCode = process.waitFor()

                if (exitCode != 0) {
                    val errorStream = process.errorStream.bufferedReader().readText()
                    XposedBridge.log("$TAG: Shell command failed (exit=$exitCode): $errorStream")
                    showToast(context, getLocalizedString(context, 
                        "Command failed: $errorStream", 
                        "命令执行失败：$errorStream"))
                } else {
                    val output = process.inputStream.bufferedReader().readText()
                    XposedBridge.log("$TAG: Shell command output: $output")
                    if (output.isNotBlank()) {
                        showToast(context, output.take(100))
                    }
                }
            } catch (e: Exception) {
                XposedBridge.log("$TAG: Shell command exception: ${e.message}")
                e.printStackTrace()
                showToast(context, getLocalizedString(context,
                    "Command error: ${e.message}",
                    "命令出错：${e.message}"))
            }
        }.start()
    }

    /**
     * Send action command to SystemUI process via broadcast.
     * Uses sendBroadcastAsUser to handle system_server context properly.
     */
    private fun sendActionToSystemUI(action: String, context: Context) {
        try {
            val intent = Intent(ACTION_PERFORM).apply {
                putExtra(EXTRA_ACTION, action)
                setPackage("com.android.systemui")
            }
            val userHandle = android.os.UserHandle::class.java
                .getField("CURRENT").get(null) as android.os.UserHandle
            context.sendBroadcastAsUser(intent, userHandle)
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Failed to send broadcast: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Execute action within SystemUI process (called by BroadcastReceiver).
     */
    private fun performActionInSystemUI(action: String, context: Context) {
        val ctx = systemUIContext ?: context

        when {
            action.startsWith("app_shortcut:") -> {
                Handler(Looper.getMainLooper()).post {
                    launchShortcut(ctx, action)
                }
            }
            action.startsWith("shell:") -> {
                doExecuteShellCommand(action, ctx)
            }
            action == "freezer_drawer" -> {
                Handler(Looper.getMainLooper()).post {
                    DrawerManager.showDrawer(ctx)
                }
            }
            action == "refreeze" -> {
                performRefreeze(ctx)
            }
            else -> {
                Handler(Looper.getMainLooper()).post {
                    showToast(ctx, getLocalizedString(ctx, "Unknown action: $action", "未知操作：$action"))
                }
            }
        }
    }

    private fun showToast(context: Context, text: String) {
        if (mHandler == null) mHandler = Handler(Looper.getMainLooper())
        mHandler?.post {
            try {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {}
        }
    }

    private fun launchShortcut(context: Context, action: String) {
        try {
            val parts = action.split(":")
            if (parts.size != 3) {
                showToast(context, getLocalizedString(context, "Shortcut format error", "快捷方式格式错误"))
                return
            }

            val packageName = parts[1]
            val shortcutId = parts[2]

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE)
                        as android.content.pm.LauncherApps

                try {
                    launcherApps.startShortcut(
                        packageName, shortcutId, null, null,
                        android.os.Process.myUserHandle()
                    )
                } catch (e: Exception) {
                    XposedBridge.log("$TAG: Failed to launch shortcut: ${e.message}")
                    try {
                        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                        if (intent != null) {
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } else {
                            showToast(context, getLocalizedString(context, "Cannot launch shortcut", "无法启动快捷方式"))
                        }
                    } catch (e2: Exception) {
                        showToast(context, getLocalizedString(context, "Launch failed", "启动失败"))
                    }
                }
            } else {
                showToast(context, getLocalizedString(context, "Requires Android 7.1 or higher", "需要 Android 7.1 或更高版本"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(context, getLocalizedString(context, "Shortcut launch failed: ${e.message}", "快捷方式启动失败：${e.message}"))
        }
    }

    private fun performRefreeze(context: Context) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val packageList = mutableListOf<String>()
                val cursor: Cursor? = systemContext?.contentResolver?.query(
                    CONFIG_URI,
                    null,
                    "freezer_app_list",
                    arrayOf(""),
                    "string"
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val listStr = it.getString(0)
                        if (!listStr.isNullOrEmpty()) {
                            val packages = listStr.split(",")
                            packageList.addAll(packages)
                        }
                    }
                }

                if (packageList.isEmpty()) {
                    val historySet = DrawerManager.frozenAppsHistory
                    if (historySet.isEmpty()) {
                        handler.post { 
                            Toast.makeText(
                                context, 
                                getLocalizedString(context, "Freezer list is empty", "冰箱列表为空"), 
                                Toast.LENGTH_SHORT
                            ).show() 
                        }
                        return@Thread
                    }
                    packageList.addAll(historySet)
                }

                val pm = context.packageManager
                var count = 0

                for (pkg in packageList) {
                    if (pkg.isBlank()) continue
                    try {
                        val info = pm.getApplicationInfo(pkg, 0)
                        if (info.enabled) {
                            var success = false
                            try {
                                pm.setApplicationEnabledSetting(
                                    pkg,
                                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                    0
                                )
                                success = true
                                de.robv.android.xposed.XposedBridge.log("EdgeX: PM API freeze SUCCESS for $pkg")
                            } catch (e: Exception) {
                                de.robv.android.xposed.XposedBridge.log("EdgeX: PM API freeze FAILED for $pkg: ${e.message}")
                                // DO NOT fallback to su in SystemUI process - it causes system crashes
                            }

                            if (success) count++
                        }
                    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                        // App not found
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Silent on success - no toast needed
                if (count == 0) {
                    handler.post { 
                        Toast.makeText(
                            context, 
                            getLocalizedString(context, "No apps to freeze", "没有应用需要冷冻"), 
                            Toast.LENGTH_SHORT
                        ).show() 
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                handler.post { 
                    Toast.makeText(
                        context, 
                        getLocalizedString(context, "Freeze error: ${e.message}", "冷冻错误：${e.message}"), 
                        Toast.LENGTH_SHORT
                    ).show() 
                }
            }
        }.start()
    }

    private fun performScreenshot(context: Context) {
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SYSRQ, 0)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SYSRQ, 0)
        val errors = mutableListOf<String>()

        // Path A: use context system service instance (works on newer Android).
        try {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE)
            if (inputManager != null) {
                val injectMethod = inputManager.javaClass.getMethod(
                    "injectInputEvent",
                    Class.forName("android.view.InputEvent"),
                    Int::class.javaPrimitiveType
                )
                injectMethod.invoke(inputManager, down, 0) // ASYNC
                injectMethod.invoke(inputManager, up, 0)
                return
            }
        } catch (t: Throwable) {
            errors.add("INPUT_SERVICE: ${t.message}")
        }

        // Path B: InputManagerGlobal singleton (works on some builds).
        try {
            val globalCls = Class.forName("android.hardware.input.InputManagerGlobal")
            val getInstance = globalCls.getMethod("getInstance")
            val global = getInstance.invoke(null)
            val injectMethod = globalCls.getMethod(
                "injectInputEvent",
                Class.forName("android.view.InputEvent"),
                Int::class.javaPrimitiveType
            )
            injectMethod.invoke(global, down, 0)
            injectMethod.invoke(global, up, 0)
            return
        } catch (t: Throwable) {
            errors.add("InputManagerGlobal: ${t.message}")
        }

        // Path C fallback: shell command (may be denied in system_server).
        try {
            Runtime.getRuntime().exec("input keyevent 120")
        } catch (e: Exception) {
            errors.add("shell: ${e.message}")
            XposedBridge.log("$TAG: screenshot failed -> ${errors.joinToString(" | ")}")
        }
    }
}
