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
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.ConfigProvider
import com.fan.edgex.overlay.DrawerManager
import com.topjohnwu.superuser.Shell
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import kotlin.math.abs

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

    private enum class EdgeSide { LEFT, RIGHT }

    private data class EdgeZoneMatch(
        val zone: String,
        val side: EdgeSide,
    )

    private data class GestureSession(
        val zone: String,
        val side: EdgeSide,
        val downX: Float,
        val downY: Float,
        var targetX: Float,
        var targetY: Float,
        val startedAtMs: Long,
        val savedDownEvent: MotionEvent,
        var isSwiping: Boolean = false,
        var consumeStream: Boolean = true,
        var nativeStreamCancelled: Boolean = false,
        var nativeDownInjected: Boolean = false,
        var continuousAction: String? = null,
        var lastAdjustY: Float = 0f,
    )

    // Gesture detection state (used in system_server process)
    private var mGestureSession: GestureSession? = null
    private var mInjectMethod: java.lang.reflect.Method? = null

    // Continuous adjustment state
    private const val CONTINUOUS_STEP_PX = 30
    private const val GESTURE_TIMEOUT_MS = 5000L

    // Double-tap and Long-press detection state
    private var mLastTapUpTime = 0L
    private var mLastTapZone: String? = null
    private var mPendingClickRunnable: Runnable? = null
    private var mPendingLongPressRunnable: Runnable? = null
    private const val DOUBLE_TAP_TIMEOUT_MS = 300L
    private const val LONG_PRESS_TIMEOUT_MS = 500L

    // Whether we've registered the screen state broadcast receiver (system_server)
    private var screenStateReceiverRegistered = false
    // Whether we've registered the screen state broadcast receiver (SystemUI)
    private var screenStateReceiverRegisteredUI = false

    private const val EDGE_THRESHOLD_DP = 8f
    private const val TOUCH_SLOP_PX = 24f
    private const val TOUCH_SLOP_SQ = TOUCH_SLOP_PX * TOUCH_SLOP_PX

    private var mHandler: Handler? = null

    // Debug overlay views (in SystemUI process)
    private val debugViews = mutableListOf<DebugOverlayView>()

    /**
     * Reset all gesture detection state.
     * Called on SCREEN_OFF to prevent stale state from blocking touches after unlock.
     */
    private fun resetGestureState() {
        val previousSession = mGestureSession
        mGestureSession = null

        mPendingLongPressRunnable?.let { mHandler?.removeCallbacks(it) }
        mPendingLongPressRunnable = null

        previousSession?.savedDownEvent?.recycle()

        // Note: pending click / tap state are intentionally kept here so the
        // click half of a click-vs-double-click decision can complete after
        // the gesture session itself has ended.
        if (previousSession != null) {
            XposedBridge.log("$TAG: [Gesture] resetGestureState — cleared stale gesture session")
        }
    }

    private fun startLongPressTimer(context: Context, session: GestureSession) {
        mPendingLongPressRunnable?.let { mHandler?.removeCallbacks(it) }
        val r = Runnable {
            mPendingLongPressRunnable = null
            if (mGestureSession !== session) return@Runnable
            if (session.isSwiping) return@Runnable

            val zone = session.zone
            val lpAction = getConfig(AppConfig.gestureAction(zone, "long_press"))
            if (lpAction.isNotEmpty() && lpAction != "none") {
                cancelNativeStream(session, context)
                triggerAction(zone, "long_press", context, session.targetX, session.targetY)
            } else {
                // No long-press action: Release the DOWN event to the app!
                XposedBridge.log("$TAG: [Gesture] Long press timeout — no EdgeX action, releasing DOWN")
                dispatchSavedDownIfNeeded(context, session)
            }
        }
        mPendingLongPressRunnable = r
        (mHandler ?: Handler(Looper.getMainLooper()).also { mHandler = it }).postDelayed(r, LONG_PRESS_TIMEOUT_MS)
    }

    private fun cancelNativeStream(session: GestureSession, context: Context) {
        if (!session.nativeStreamCancelled) {
            injectEventInternal(context, session.savedDownEvent, MotionEvent.ACTION_CANCEL)
            session.nativeStreamCancelled = true
        }
        mPendingLongPressRunnable?.let { mHandler?.removeCallbacks(it) }
        mPendingLongPressRunnable = null
    }

    private fun dispatchSavedDownIfNeeded(context: Context, session: GestureSession) {
        if (!session.nativeDownInjected && !session.nativeStreamCancelled) {
            injectEventInternal(context, session.savedDownEvent)
            session.nativeDownInjected = true
        }
    }

    private fun resumeNativeStream(session: GestureSession, context: Context, currentEvent: MotionEvent) {
        if (!session.nativeStreamCancelled) {
            dispatchSavedDownIfNeeded(context, session)
            injectEventInternal(context, currentEvent)
        }
        mPendingLongPressRunnable?.let { mHandler?.removeCallbacks(it) }
        mPendingLongPressRunnable = null
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

        // Safety guard: if the session has been active for too long without
        // receiving UP/CANCEL (e.g. screen was locked mid-gesture),
        // auto-reset to prevent blocking touch events.
        mGestureSession?.let { session ->
            val elapsed = System.currentTimeMillis() - session.startedAtMs
            if (elapsed > GESTURE_TIMEOUT_MS) {
                XposedBridge.log("$TAG: [Gesture] Safety timeout: session stuck for ${elapsed}ms — resetting")
                resetGestureState()
            }
        }

        if (!isGesturesEnabled()) return false

        // Skip gestures when keyguard (lockscreen) is showing to avoid intercepting unlock swipes
        try {
            val km = context.getSystemService(android.app.KeyguardManager::class.java)
            if (km?.isKeyguardLocked == true) return false
        } catch (_: Exception) {}

        val action = event.actionMasked
        val pointerCount = event.pointerCount

        when (action) {
            MotionEvent.ACTION_DOWN -> return handleGestureDown(event, context)

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Extra finger added: reset state to avoid direction confusion.
                resetGestureState()
                return false
            }

            MotionEvent.ACTION_MOVE -> return handleGestureMove(event, context)

            MotionEvent.ACTION_UP -> return handleGestureUp(event, context)

            MotionEvent.ACTION_CANCEL -> return handleGestureCancel(event, context)

            else -> {
                if (mGestureSession != null) {
                    XposedBridge.log("$TAG: [Gesture] unhandled action=$action pointers=$pointerCount sessionActive=true")
                }
            }
        }

        return false
    }

    private fun handleGestureDown(event: MotionEvent, context: Context): Boolean {
        val zoneMatch = resolveEdgeZone(context, event.rawX, event.rawY) ?: run {
            if (mGestureSession != null) {
                XposedBridge.log("$TAG: [Gesture] DOWN outside edge while sessionActive=true — resetting state")
            }
            resetGestureState()
            return false
        }

        clearPendingSingleClick()

        val session = GestureSession(
            zone = zoneMatch.zone,
            side = zoneMatch.side,
            downX = event.rawX,
            downY = event.rawY,
            targetX = event.rawX,
            targetY = event.rawY,
            startedAtMs = System.currentTimeMillis(),
            savedDownEvent = MotionEvent.obtain(event),
        )
        mGestureSession = session
        startLongPressTimer(context, session)
        return session.consumeStream
    }

    private fun handleGestureMove(event: MotionEvent, context: Context): Boolean {
        val session = mGestureSession ?: return false
        updateTargetPoint(session, event.rawX, event.rawY)

        if (!session.isSwiping) {
            val dx = event.rawX - session.downX
            val dy = event.rawY - session.downY
            if ((dx * dx) + (dy * dy) > TOUCH_SLOP_SQ) {
                session.isSwiping = true
                val gestureType = resolveSwipeGesture(dx, dy)
                val action = getConfig(AppConfig.gestureAction(session.zone, gestureType))

                if (hasConfiguredAction(action)) {
                    cancelNativeStream(session, context)
                    if (isContinuousAdjustmentAction(action)) {
                        session.continuousAction = action
                        session.lastAdjustY = event.rawY
                    } else {
                        triggerAction(session.zone, gestureType, context, session.targetX, session.targetY)
                    }
                } else {
                    resumeNativeStream(session, context, event)
                }
            }
        } else {
            val continuousAction = session.continuousAction
            when {
                continuousAction != null -> handleContinuousAdjustment(session, continuousAction, context, event.rawY)
                shouldProxyToNative(session) -> injectEventInternal(context, event)
            }
        }

        return session.consumeStream
    }

    private fun handleGestureUp(event: MotionEvent, context: Context): Boolean {
        val session = mGestureSession ?: return false

        if (session.isSwiping) {
            return finishGestureSession()
        }

        val clickAction = getConfig(AppConfig.gestureAction(session.zone, "click"))
        val hasClickAction = hasConfiguredAction(clickAction)
        val hasDoubleClickAction = hasConfiguredAction(
            getConfig(AppConfig.gestureAction(session.zone, "double_click"))
        )

        if (hasClickAction || hasDoubleClickAction) {
            cancelNativeStream(session, context)

            if (!hasDoubleClickAction) {
                triggerAction(session.zone, "click", context, session.targetX, session.targetY)
            } else {
                resolveTapAction(session, context, event.eventTime)
            }
        } else {
            resumeNativeStream(session, context, event)
        }

        return finishGestureSession()
    }

    private fun handleGestureCancel(event: MotionEvent, context: Context): Boolean {
        val session = mGestureSession ?: return false
        if (!session.nativeStreamCancelled) {
            resumeNativeStream(session, context, event)
        }
        resetGestureState()
        return true
    }

    private fun finishGestureSession(): Boolean {
        val consumed = mGestureSession?.consumeStream ?: false
        resetGestureState()
        return consumed
    }

    private fun clearPendingSingleClick() {
        mPendingClickRunnable?.let { mHandler?.removeCallbacks(it) }
        mPendingClickRunnable = null
    }

    private fun resolveEdgeZone(context: Context, x: Float, y: Float): EdgeZoneMatch? {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val realSize = android.graphics.Point()
        wm.defaultDisplay.getRealSize(realSize)

        val edgeThreshold = EDGE_THRESHOLD_DP * context.resources.displayMetrics.density
        val side = when {
            x < edgeThreshold -> EdgeSide.LEFT
            x > realSize.x - edgeThreshold -> EdgeSide.RIGHT
            else -> return null
        }

        val verticalZone = when {
            y < realSize.y * 0.33f -> "top"
            y < realSize.y * 0.66f -> "mid"
            else -> "bottom"
        }
        val zone = "${side.name.lowercase()}_$verticalZone"
        return EdgeZoneMatch(zone, side).takeIf { isZoneEnabled(zone) }
    }

    private fun updateTargetPoint(session: GestureSession, x: Float, y: Float) {
        when (session.side) {
            EdgeSide.LEFT -> {
                if (x > session.targetX) {
                    session.targetX = x
                    session.targetY = y
                }
            }
            EdgeSide.RIGHT -> {
                if (x < session.targetX) {
                    session.targetX = x
                    session.targetY = y
                }
            }
        }
    }

    private fun resolveSwipeGesture(dx: Float, dy: Float): String =
        when {
            abs(dx) > abs(dy) -> if (dx < 0) "swipe_left" else "swipe_right"
            else -> if (dy < 0) "swipe_up" else "swipe_down"
        }

    private fun hasConfiguredAction(action: String): Boolean =
        action.isNotEmpty() && action != "none"

    private fun isContinuousAdjustmentAction(action: String): Boolean =
        action == "brightness_up" || action == "brightness_down" ||
                action == "volume_up" || action == "volume_down"

    private fun shouldProxyToNative(session: GestureSession): Boolean =
        session.consumeStream && session.nativeDownInjected && !session.nativeStreamCancelled

    private fun handleContinuousAdjustment(
        session: GestureSession,
        action: String,
        context: Context,
        currentY: Float,
    ) {
        val delta = session.lastAdjustY - currentY
        if (abs(delta) < CONTINUOUS_STEP_PX) return

        val steps = (abs(delta) / CONTINUOUS_STEP_PX).toInt()
        val up = delta > 0
        val handler = mHandler ?: Handler(Looper.getMainLooper()).also { mHandler = it }
        repeat(steps) {
            handler.post {
                when {
                    action == "brightness_up" || action == "brightness_down" -> adjustBrightness(context, up)
                    action == "volume_up" || action == "volume_down" -> adjustVolume(context, up)
                }
            }
        }
        session.lastAdjustY -= steps * CONTINUOUS_STEP_PX * (if (delta > 0) 1 else -1)
    }

    private fun resolveTapAction(session: GestureSession, context: Context, eventTime: Long) {
        val zone = session.zone
        val capturedX = session.targetX
        val capturedY = session.targetY
        val timeSinceLast = eventTime - mLastTapUpTime

        if (timeSinceLast < DOUBLE_TAP_TIMEOUT_MS && mLastTapZone == zone) {
            clearPendingSingleClick()
            mLastTapUpTime = 0L
            mLastTapZone = null
            triggerAction(zone, "double_click", context, capturedX, capturedY)
            return
        }

        mLastTapUpTime = eventTime
        mLastTapZone = zone
        val runnable = Runnable {
            mPendingClickRunnable = null
            mLastTapUpTime = 0L
            mLastTapZone = null
            triggerAction(zone, "click", context, capturedX, capturedY)
        }
        mPendingClickRunnable = runnable
        (mHandler ?: Handler(Looper.getMainLooper()).also { mHandler = it })
            .postDelayed(runnable, DOUBLE_TAP_TIMEOUT_MS)
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

    // ======== Action Handling ========

    private fun triggerAction(zone: String, gestureType: String, context: Context, touchX: Float, touchY: Float) {
        val configKey = AppConfig.gestureAction(zone, gestureType)
        val action = getConfig(configKey)

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
            action == "kill_app" -> {
                killForegroundApp(context)
            }
            action == "brightness_up" || action == "brightness_down" -> {
                adjustBrightness(context, action == "brightness_up")
            }
            action == "volume_up" || action == "volume_down" -> {
                adjustVolume(context, action == "volume_up")
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
                            showToast(context, ModuleRes.getString(R.string.toast_no_text_found))
                        }
                        UniversalCopyManager.CollectStatus.UNAVAILABLE -> {
                            showToast(context, ModuleRes.getString(R.string.toast_copy_unavailable))
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

    private fun killForegroundApp(context: Context) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            val tasks = am.getRunningTasks(1)
            if (tasks.isNullOrEmpty()) return
            val pkg = tasks[0].topActivity?.packageName ?: return
            if (pkg == context.packageName) return
            XposedHelpers.callMethod(am, "forceStopPackage", pkg)
            XposedBridge.log("$TAG: Killed foreground app: $pkg")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: killForegroundApp failed: ${e.message}")
        }
    }

    private fun adjustBrightness(context: Context, up: Boolean) {
        try {
            val displayManager = context.getSystemService("display") as android.hardware.display.DisplayManager
            val getBrightness = android.hardware.display.DisplayManager::class.java
                .getMethod("getBrightness", Int::class.java)
            val setBrightness = android.hardware.display.DisplayManager::class.java
                .getMethod("setBrightness", Int::class.java, Float::class.java)
            val current = getBrightness.invoke(displayManager, 0) as Float
            val step = 1.0f / 16f
            val newVal = if (up) minOf(1.0f, current + step) else maxOf(0.0f, current - step)
            setBrightness.invoke(displayManager, 0, newVal)
            XposedBridge.log("$TAG: Brightness $current -> $newVal")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: adjustBrightness failed: ${e.message}")
        }
    }

    private fun adjustVolume(context: Context, up: Boolean) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val direction = if (up) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
            am.adjustVolume(direction, android.media.AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
            XposedBridge.log("$TAG: adjustVolume failed: ${e.message}")
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
                    showToast(context, ModuleRes.getString(R.string.toast_shell_invalid_format))
                    return@Thread
                }

                val runAsRoot = parts[0] == "true"
                val command = parts[1]

                if (command.isBlank()) {
                    showToast(context, ModuleRes.getString(R.string.toast_empty_command))
                    return@Thread
                }

                XposedBridge.log("$TAG: Executing shell command (root=$runAsRoot): $command")

                if (runAsRoot) {
                    val result = Shell.cmd(command).exec()
                    if (!result.isSuccess) {
                        val errorMsg = result.err.joinToString("\n")
                        XposedBridge.log("$TAG: Shell command failed: $errorMsg")
                        showToast(context, ModuleRes.getString(R.string.toast_command_failed, errorMsg))
                    } else {
                        val output = result.out.joinToString("\n")
                        XposedBridge.log("$TAG: Shell command output: $output")
                        if (output.isNotBlank()) showToast(context, output.take(100))
                    }
                } else {
                    val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                    p.outputStream.close()
                    val exitCode = p.waitFor()
                    if (exitCode != 0) {
                        val errorMsg = p.errorStream.bufferedReader().readText()
                        XposedBridge.log("$TAG: Shell command failed (exit=$exitCode): $errorMsg")
                        showToast(context, ModuleRes.getString(R.string.toast_command_failed, errorMsg))
                    } else {
                        val output = p.inputStream.bufferedReader().readText()
                        XposedBridge.log("$TAG: Shell command output: $output")
                        if (output.isNotBlank()) showToast(context, output.take(100))
                    }
                }
            } catch (e: Exception) {
                XposedBridge.log("$TAG: Shell command exception: ${e.message}")
                e.printStackTrace()
                showToast(context, ModuleRes.getString(R.string.toast_command_error, e.message))
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
                    showToast(ctx, ModuleRes.getString(R.string.toast_unknown_action, action))
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
                showToast(context, ModuleRes.getString(R.string.toast_shortcut_format_error))
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
                            showToast(context, ModuleRes.getString(R.string.toast_cannot_launch_shortcut))
                        }
                    } catch (e2: Exception) {
                        showToast(context, ModuleRes.getString(R.string.toast_launch_failed))
                    }
                }
            } else {
                showToast(context, ModuleRes.getString(R.string.toast_requires_android_71))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(context, ModuleRes.getString(R.string.toast_shortcut_launch_failed, e.message))
        }
    }

    private fun performRefreeze(context: Context) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val packageList = mutableListOf<String>()
                val cursor: Cursor? = systemContext?.contentResolver?.query(
                    Uri.withAppendedPath(CONFIG_URI, AppConfig.FREEZER_APP_LIST),
                    null, null, null, null
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
                                ModuleRes.getString(R.string.toast_freezer_list_empty),
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

                if (count > 0) {
                    handler.post {
                        Toast.makeText(
                            context,
                            ModuleRes.getString(R.string.toast_refrozen_apps, count),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    handler.post {
                        Toast.makeText(
                            context,
                            ModuleRes.getString(R.string.toast_no_apps_to_freeze),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                handler.post { 
                    Toast.makeText(
                        context, 
                        ModuleRes.getString(R.string.toast_freeze_error, e.message),
                        Toast.LENGTH_SHORT
                    ).show() 
                }
            }
        }.start()
    }

    private fun injectEventInternal(context: Context, event: MotionEvent, action: Int? = null) {
        try {
            val im = context.getSystemService(Context.INPUT_SERVICE) as android.hardware.input.InputManager
            if (mInjectMethod == null) {
                mInjectMethod = im.javaClass.getMethod("injectInputEvent", android.view.InputEvent::class.java, Int::class.javaPrimitiveType)
            }
            
            val injected = if (action != null) {
                MotionEvent.obtain(event.downTime, SystemClock.uptimeMillis(), action, event.rawX, event.rawY, event.metaState)
            } else {
                MotionEvent.obtain(event)
            }
            
            mInjectMethod?.invoke(im, injected, 0) // ASYNC
            injected.recycle()
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Proxy injection failed: ${e.message}")
        }
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
