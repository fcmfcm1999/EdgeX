package com.fan.edgex.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class PieView(context: Context) : View(context) {

    data class Slot(val label: String, val action: String)

    private companion object {
        // Distance from anchor to item centers
        const val RING_RADIUS_DP = 120f
        // Min distance from anchor before selection activates
        const val INNER_DEAD_ZONE_DP = 35f
        // Max selection distance
        const val OUTER_DEAD_ZONE_DP = 220f
        // Radius of each item circle
        const val ITEM_RADIUS_DP = 38f
        const val LABEL_SIZE_SP = 11f
        // Fan arc: same 160° as XPE
        const val FAN_ARC_DEG = 160f

        // Start angle for each edge so the fan opens toward the center of the screen
        const val ANGLE_START_RIGHT  = 100f   // right edge → fan opens leftward
        const val ANGLE_START_LEFT   = -80f   // left edge  → fan opens rightward
        const val ANGLE_START_BOTTOM = 190f   // bottom edge → fan opens upward
        const val ANGLE_START_TOP    = 10f    // top edge   → fan opens downward
    }

    var slots: List<Slot> = emptyList()
        set(value) { field = value; invalidate() }
    var anchorX: Float = 0f
    var anchorY: Float = 0f
    var edge: String = "right"
    var highlightedIndex: Int = -1
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(80, 255, 255, 255)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        isFakeBoldText = false
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(160, 255, 255, 255)
    }

    private var animFraction = 0f
    private var animator: ValueAnimator? = null

    fun animateIn() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 120
            addUpdateListener {
                animFraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    @Suppress("DEPRECATION")
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity

    private fun fanStartAngle() = when (edge) {
        "right"  -> ANGLE_START_RIGHT
        "left"   -> ANGLE_START_LEFT
        "bottom" -> ANGLE_START_BOTTOM
        "top"    -> ANGLE_START_TOP
        else     -> ANGLE_START_RIGHT
    }

    private fun itemAngleDeg(index: Int, count: Int): Float {
        val start = fanStartAngle()
        return if (count == 1) start + FAN_ARC_DEG / 2f
        else start + index * (FAN_ARC_DEG / (count - 1))
    }

    fun hitTest(x: Float, y: Float): Int {
        val n = slots.size
        if (n == 0) return -1

        val dx = x - anchorX
        val dy = y - anchorY
        val distSq = dx * dx + dy * dy
        val innerSq = dp(INNER_DEAD_ZONE_DP).pow(2)
        val outerSq = dp(OUTER_DEAD_ZONE_DP).pow(2)
        if (distSq < innerSq || distSq > outerSq) return -1

        if (n == 1) return 0

        val fingerAngle = normalize(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat())
        var minDiff = Float.MAX_VALUE
        var bestIndex = -1
        for (i in 0 until n) {
            val itemAngle = normalize(itemAngleDeg(i, n))
            val diff = angleDiff(fingerAngle, itemAngle)
            if (diff < minDiff) {
                minDiff = diff
                bestIndex = i
            }
        }
        // Only accept if within half the step angle (no dead zones between items)
        return bestIndex
    }

    private fun normalize(a: Float): Float = ((a % 360f) + 360f) % 360f

    private fun angleDiff(a: Float, b: Float): Float {
        val d = ((a - b + 360f) % 360f)
        return if (d > 180f) 360f - d else d
    }

    override fun onDraw(canvas: Canvas) {
        if (animFraction == 0f || slots.isEmpty()) return

        val scale = animFraction
        val n = slots.size
        val ringR = dp(RING_RADIUS_DP) * scale
        val itemR = dp(ITEM_RADIUS_DP) * scale
        textPaint.textSize = sp(LABEL_SIZE_SP) * scale
        strokePaint.strokeWidth = dp(1.5f)

        slots.forEachIndexed { i, slot ->
            val angleDeg = itemAngleDeg(i, n)
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val cx = anchorX + ringR * cos(angleRad).toFloat()
            val cy = anchorY + ringR * sin(angleRad).toFloat()

            val isHighlighted = (i == highlightedIndex)
            val alpha = (255 * scale).toInt().coerceIn(0, 255)

            // Shadow
            circlePaint.color = Color.argb((40 * scale).toInt().coerceIn(0, 255), 0, 0, 0)
            canvas.drawCircle(cx + dp(2f), cy + dp(3f), itemR, circlePaint)

            // Background
            circlePaint.color = if (isHighlighted)
                Color.argb(alpha, 50, 130, 240)
            else
                Color.argb((190 * scale).toInt().coerceIn(0, 255), 20, 20, 20)
            canvas.drawCircle(cx, cy, itemR, circlePaint)

            // Border
            strokePaint.color = if (isHighlighted)
                Color.argb((200 * scale).toInt().coerceIn(0, 255), 100, 170, 255)
            else
                Color.argb((80 * scale).toInt().coerceIn(0, 255), 255, 255, 255)
            canvas.drawCircle(cx, cy, itemR, strokePaint)

            // Label (truncate long labels)
            val label = slot.label.take(7)
            textPaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawText(label, cx, cy + textPaint.textSize * 0.38f, textPaint)
        }

        // Anchor dot
        dotPaint.color = Color.argb((120 * scale).toInt().coerceIn(0, 255), 255, 255, 255)
        canvas.drawCircle(anchorX, anchorY, dp(5f) * scale, dotPaint)
    }
}
