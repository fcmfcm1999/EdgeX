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
    data class Ring(val slots: List<Slot>)

    private companion object {
        const val RING1_RADIUS_DP = 110f
        const val RING2_RADIUS_DP = 185f
        const val INNER_DEAD_ZONE_DP = 35f
        const val RING_BOUNDARY_DP = 150f
        const val OUTER_LIMIT_DP = 230f
        const val ITEM_RADIUS_DP = 38f
        const val LABEL_SIZE_SP = 11f
        const val FAN_ARC_DEG = 160f

        const val ANGLE_START_RIGHT  = 100f
        const val ANGLE_START_LEFT   = -80f
        const val ANGLE_START_BOTTOM = 190f
        const val ANGLE_START_TOP    = 10f
    }

    var rings: List<Ring> = emptyList()
        set(value) { field = value; invalidate() }
    var anchorX: Float = 0f
    var anchorY: Float = 0f
    var edge: String = "right"
    var highlightedRing: Int = -1
        set(value) {
            if (field != value) { field = value; invalidate() }
        }
    var highlightedSlot: Int = -1
        set(value) {
            if (field != value) { field = value; invalidate() }
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

    fun isAnimationComplete() = animFraction >= 1.0f

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

    fun hitTest(x: Float, y: Float): Pair<Int, Int>? {
        val dx = x - anchorX
        val dy = y - anchorY
        val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        val innerR = dp(INNER_DEAD_ZONE_DP)
        val boundaryR = dp(RING_BOUNDARY_DP)
        val outerR = dp(OUTER_LIMIT_DP)

        if (dist < innerR || dist > outerR) return null

        val ringIndex = if (dist <= boundaryR) 0 else 1
        val ring = rings.getOrNull(ringIndex) ?: return null
        val n = ring.slots.size
        if (n == 0) return null
        if (n == 1) return Pair(ringIndex, 0)

        val fingerAngle = normalize(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat())
        var minDiff = Float.MAX_VALUE
        var bestSlot = -1
        for (i in 0 until n) {
            val itemAngle = normalize(itemAngleDeg(i, n))
            val diff = angleDiff(fingerAngle, itemAngle)
            if (diff < minDiff) {
                minDiff = diff
                bestSlot = i
            }
        }
        return if (bestSlot >= 0) Pair(ringIndex, bestSlot) else null
    }

    private fun normalize(a: Float): Float = ((a % 360f) + 360f) % 360f

    private fun angleDiff(a: Float, b: Float): Float {
        val d = ((a - b + 360f) % 360f)
        return if (d > 180f) 360f - d else d
    }

    override fun onDraw(canvas: Canvas) {
        if (animFraction == 0f || rings.isEmpty()) return

        val scale = animFraction
        val itemR = dp(ITEM_RADIUS_DP) * scale
        textPaint.textSize = sp(LABEL_SIZE_SP) * scale
        strokePaint.strokeWidth = dp(1.5f)

        val ringRadii = listOf(dp(RING1_RADIUS_DP) * scale, dp(RING2_RADIUS_DP) * scale)

        rings.forEachIndexed { ringIndex, ring ->
            val ringR = ringRadii[ringIndex]
            val n = ring.slots.size
            ring.slots.forEachIndexed { slotIndex, slot ->
                val angleDeg = itemAngleDeg(slotIndex, n)
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val cx = anchorX + ringR * cos(angleRad).toFloat()
                val cy = anchorY + ringR * sin(angleRad).toFloat()

                val isHighlighted = (ringIndex == highlightedRing && slotIndex == highlightedSlot)
                val alpha = (255 * scale).toInt().coerceIn(0, 255)

                circlePaint.color = Color.argb((40 * scale).toInt().coerceIn(0, 255), 0, 0, 0)
                canvas.drawCircle(cx + dp(2f), cy + dp(3f), itemR, circlePaint)

                circlePaint.color = if (isHighlighted)
                    Color.argb(alpha, 50, 130, 240)
                else
                    Color.argb((190 * scale).toInt().coerceIn(0, 255), 20, 20, 20)
                canvas.drawCircle(cx, cy, itemR, circlePaint)

                strokePaint.color = if (isHighlighted)
                    Color.argb((200 * scale).toInt().coerceIn(0, 255), 100, 170, 255)
                else
                    Color.argb((80 * scale).toInt().coerceIn(0, 255), 255, 255, 255)
                canvas.drawCircle(cx, cy, itemR, strokePaint)

                val label = slot.label.take(7)
                textPaint.color = Color.argb(alpha, 255, 255, 255)
                canvas.drawText(label, cx, cy + textPaint.textSize * 0.38f, textPaint)
            }
        }

        dotPaint.color = Color.argb((120 * scale).toInt().coerceIn(0, 255), 255, 255, 255)
        canvas.drawCircle(anchorX, anchorY, dp(5f) * scale, dotPaint)
    }
}
