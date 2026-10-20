package com.fan.edgex.hook

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

/** Draws the touch target while an edge gesture is being used as a pointer. */
internal class GesturePointerController(
    private val handoff: NativeTouchHandoff,
    private val log: (String) -> Unit,
    private val resolveThemeColor: () -> Int,
) {
    private var pointerView: PointerView? = null
    private var windowManager: WindowManager? = null

    fun start(context: Context, x: Float, y: Float) {
        finish()
        val manager = context.getSystemService(WindowManager::class.java)
        val bounds = manager.currentWindowMetrics.bounds
        val view = PointerView(
            context = context,
            screenWidth = bounds.width().toFloat(),
            screenHeight = bounds.height().toFloat(),
            accentColor = resolveThemeColor(),
        ).also {
            it.startAt(x, y)
        }
        try {
            manager.addView(view, WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                2027, // TYPE_ACCESSIBILITY_MAGNIFICATION_OVERLAY – usable by system_server
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START })
            pointerView = view
            windowManager = manager
        } catch (t: Throwable) {
            log("Gesture pointer overlay unavailable: ${t.message}")
        }
    }

    fun moveTo(x: Float, y: Float) {
        pointerView?.movePointer(x, y)
    }

    fun tapAndFinish(context: Context, x: Float, y: Float) {
        val target = pointerView?.movePointer(x, y) ?: return
        finish()
        handoff.injectTap(context, target.first, target.second)
    }

    fun finish() {
        val view = pointerView ?: return
        try {
            windowManager?.removeViewImmediate(view)
        } catch (_: Throwable) {
        } finally {
            pointerView = null
            windowManager = null
        }
    }

    private class PointerView(
        context: Context,
        private val screenWidth: Float,
        private val screenHeight: Float,
        accentColor: Int,
    ) : View(context) {
        private val fingerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = context.resources.displayMetrics.density * 1.5f
        }
        private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(accentColor, 0xAA)
        }
        private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(accentColor, 0xCC)
            strokeWidth = context.resources.displayMetrics.density
        }
        private val radius = context.resources.displayMetrics.density * 10f
        private var fingerX = 0f
        private var fingerY = 0f
        private var targetX = 0f
        private var targetY = 0f
        private val screenLocation = IntArray(2)

        fun startAt(x: Float, y: Float) {
            fingerX = x
            fingerY = y
            targetX = x
            targetY = y
            invalidate()
        }

        /**
         * Turns the small edge-control area into a relative touchpad. The actual finger is
         * shown as a hollow circle, while the solid circle is the amplified tap target.
         */
        fun movePointer(x: Float, y: Float): Pair<Float, Float> {
            targetX = clamp(targetX + (x - fingerX) * POINTER_GAIN, 0f, screenWidth)
            targetY = clamp(targetY + (y - fingerY) * POINTER_GAIN, 0f, screenHeight)
            fingerX = x
            fingerY = y
            invalidate()
            return targetX to targetY
        }

        override fun onDraw(canvas: Canvas) {
            // Raw touch coordinates are display-relative. The system overlay can begin below
            // an inset on some ROMs, so convert them to this View's local coordinate space.
            getLocationOnScreen(screenLocation)
            val fingerLocalX = fingerX - screenLocation[0]
            val fingerLocalY = fingerY - screenLocation[1]
            val targetLocalX = targetX - screenLocation[0]
            val targetLocalY = targetY - screenLocation[1]
            canvas.drawLine(fingerLocalX, fingerLocalY, targetLocalX, targetLocalY, guidePaint)
            canvas.drawCircle(targetLocalX, targetLocalY, radius, targetPaint)
            canvas.drawCircle(fingerLocalX, fingerLocalY, radius, fingerPaint)
        }

        private fun clamp(value: Float, lower: Float, upper: Float): Float =
            max(lower, min(value, upper))

        private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha shl 24)

        private companion object {
            // A ~300 px thumb movement covers a typical phone display dimension.
            const val POINTER_GAIN = 4f
        }
    }
}
