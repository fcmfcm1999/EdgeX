package com.fan.edgex.hook

import android.content.Context
import android.os.Handler
import android.view.MotionEvent
import android.view.WindowManager
import kotlin.math.abs

internal class EdgeGestureDetector(
    private val handoff: NativeTouchHandoff,
    private val callbacks: Callbacks,
    private val handlerProvider: () -> Handler,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    interface Callbacks {
        fun isZoneEnabled(zone: String): Boolean
        fun resolveAction(zone: String, gestureType: String): String
        fun dispatchAction(zone: String, gestureType: String, context: Context, touchX: Float, touchY: Float)
        fun performContinuousAdjustment(action: String, context: Context, up: Boolean)
        fun log(message: String)
    }

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
        val handoff: NativeTouchHandoff.Session,
        var isSwiping: Boolean = false,
        var continuousAction: String? = null,
        var lastAdjustY: Float = 0f,
    )

    private var activeSession: GestureSession? = null
    private var lastTapUpTime = 0L
    private var lastTapZone: String? = null
    private var pendingClickRunnable: Runnable? = null
    private var pendingLongPressRunnable: Runnable? = null

    fun handle(event: MotionEvent, context: Context): Boolean {
        activeSession?.let { session ->
            val elapsed = nowMillis() - session.startedAtMs
            if (elapsed > GESTURE_TIMEOUT_MS) {
                callbacks.log("Safety timeout: session stuck for ${elapsed}ms — resetting")
                reset()
            }
        }

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event, context)
            MotionEvent.ACTION_POINTER_DOWN -> {
                reset()
                false
            }
            MotionEvent.ACTION_MOVE -> handleMove(event, context)
            MotionEvent.ACTION_UP -> handleUp(event, context)
            MotionEvent.ACTION_CANCEL -> handleCancel(event, context)
            else -> {
                if (activeSession != null) {
                    callbacks.log(
                        "unhandled action=${event.actionMasked} pointers=${event.pointerCount} sessionActive=true"
                    )
                }
                false
            }
        }
    }

    fun reset() {
        val previousSession = activeSession
        activeSession = null

        pendingLongPressRunnable?.let { handlerProvider().removeCallbacks(it) }
        pendingLongPressRunnable = null

        previousSession?.let { handoff.dispose(it.handoff) }
        if (previousSession != null) {
            callbacks.log("resetGestureState — cleared stale gesture session")
        }
    }

    private fun handleDown(event: MotionEvent, context: Context): Boolean {
        val zoneMatch = resolveEdgeZone(context, event.rawX, event.rawY) ?: run {
            if (activeSession != null) {
                callbacks.log("DOWN outside edge while sessionActive=true — resetting state")
            }
            reset()
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
            startedAtMs = nowMillis(),
            handoff = handoff.begin(event),
        )
        activeSession = session
        startLongPressTimer(context, session)
        return session.handoff.consumeStream
    }

    private fun handleMove(event: MotionEvent, context: Context): Boolean {
        val session = activeSession ?: return false
        updateTargetPoint(session, event.rawX, event.rawY)

        if (!session.isSwiping) {
            val dx = event.rawX - session.downX
            val dy = event.rawY - session.downY
            if ((dx * dx) + (dy * dy) > TOUCH_SLOP_SQ) {
                session.isSwiping = true
                val gestureType = resolveSwipeGesture(dx, dy)
                val action = callbacks.resolveAction(session.zone, gestureType)

                if (hasConfiguredAction(action)) {
                    handoff.cancel(session.handoff, context)
                    cancelLongPressTimer()
                    if (isContinuousAdjustmentAction(action)) {
                        session.continuousAction = action
                        session.lastAdjustY = event.rawY
                    } else {
                        callbacks.dispatchAction(session.zone, gestureType, context, session.targetX, session.targetY)
                    }
                } else {
                    handoff.resume(session.handoff, context, event)
                    cancelLongPressTimer()
                }
            }
        } else {
            val continuousAction = session.continuousAction
            when {
                continuousAction != null -> handleContinuousAdjustment(session, continuousAction, context, event.rawY)
                handoff.shouldProxyToNative(session.handoff) -> handoff.forwardToNative(session.handoff, context, event)
            }
        }

        return session.handoff.consumeStream
    }

    private fun handleUp(event: MotionEvent, context: Context): Boolean {
        val session = activeSession ?: return false

        if (session.isSwiping) {
            handoff.forwardToNative(session.handoff, context, event)
            return finishSession()
        }

        val clickAction = callbacks.resolveAction(session.zone, "click")
        val hasClickAction = hasConfiguredAction(clickAction)
        val hasDoubleClickAction = hasConfiguredAction(callbacks.resolveAction(session.zone, "double_click"))

        if (hasClickAction || hasDoubleClickAction) {
            handoff.cancel(session.handoff, context)
            cancelLongPressTimer()

            if (!hasDoubleClickAction) {
                callbacks.dispatchAction(session.zone, "click", context, session.targetX, session.targetY)
            } else {
                resolveTapAction(session, context, event.eventTime)
            }
        } else {
            handoff.resume(session.handoff, context, event)
            cancelLongPressTimer()
        }

        return finishSession()
    }

    private fun handleCancel(event: MotionEvent, context: Context): Boolean {
        val session = activeSession ?: return false
        if (!session.handoff.nativeStreamCancelled) {
            handoff.resume(session.handoff, context, event)
        }
        reset()
        return true
    }

    private fun finishSession(): Boolean {
        val consumed = activeSession?.handoff?.consumeStream ?: false
        reset()
        return consumed
    }

    private fun startLongPressTimer(context: Context, session: GestureSession) {
        cancelLongPressTimer()
        val runnable = Runnable {
            pendingLongPressRunnable = null
            if (activeSession !== session) return@Runnable
            if (session.isSwiping) return@Runnable

            val action = callbacks.resolveAction(session.zone, "long_press")
            if (hasConfiguredAction(action)) {
                handoff.cancel(session.handoff, context)
                callbacks.dispatchAction(session.zone, "long_press", context, session.targetX, session.targetY)
            } else {
                callbacks.log("Long press timeout — no EdgeX action, releasing DOWN")
                handoff.dispatchSavedDownIfNeeded(session.handoff, context)
            }
        }
        pendingLongPressRunnable = runnable
        handlerProvider().postDelayed(runnable, LONG_PRESS_TIMEOUT_MS)
    }

    private fun cancelLongPressTimer() {
        pendingLongPressRunnable?.let { handlerProvider().removeCallbacks(it) }
        pendingLongPressRunnable = null
    }

    private fun clearPendingSingleClick() {
        pendingClickRunnable?.let { handlerProvider().removeCallbacks(it) }
        pendingClickRunnable = null
    }

    private fun resolveTapAction(session: GestureSession, context: Context, eventTime: Long) {
        val zone = session.zone
        val capturedX = session.targetX
        val capturedY = session.targetY
        val timeSinceLast = eventTime - lastTapUpTime

        if (timeSinceLast < DOUBLE_TAP_TIMEOUT_MS && lastTapZone == zone) {
            clearPendingSingleClick()
            lastTapUpTime = 0L
            lastTapZone = null
            callbacks.dispatchAction(zone, "double_click", context, capturedX, capturedY)
            return
        }

        lastTapUpTime = eventTime
        lastTapZone = zone
        val runnable = Runnable {
            pendingClickRunnable = null
            lastTapUpTime = 0L
            lastTapZone = null
            callbacks.dispatchAction(zone, "click", context, capturedX, capturedY)
        }
        pendingClickRunnable = runnable
        handlerProvider().postDelayed(runnable, DOUBLE_TAP_TIMEOUT_MS)
    }

    private fun resolveEdgeZone(context: Context, x: Float, y: Float): EdgeZoneMatch? {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val realSize = android.graphics.Point()
        windowManager.defaultDisplay.getRealSize(realSize)

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
        return EdgeZoneMatch(zone, side).takeIf { callbacks.isZoneEnabled(zone) }
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
        repeat(steps) {
            handlerProvider().post {
                callbacks.performContinuousAdjustment(action, context, up)
            }
        }
        session.lastAdjustY -= steps * CONTINUOUS_STEP_PX * (if (delta > 0) 1 else -1)
    }

    private companion object {
        const val CONTINUOUS_STEP_PX = 30
        const val GESTURE_TIMEOUT_MS = 5000L
        const val DOUBLE_TAP_TIMEOUT_MS = 300L
        const val LONG_PRESS_TIMEOUT_MS = 500L
        const val EDGE_THRESHOLD_DP = 8f
        const val TOUCH_SLOP_PX = 24f
        const val TOUCH_SLOP_SQ = TOUCH_SLOP_PX * TOUCH_SLOP_PX
    }
}
