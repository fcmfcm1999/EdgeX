package com.fan.edgex.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PieView(context: Context) : View(context) {

    data class Slot(val label: String, val action: String)
    data class Ring(val slots: List<Slot>)

    private companion object {
        const val INNER_DEAD_ZONE_DP = 40f
        const val RING_BOUNDARY_DP   = 140f
        const val OUTER_LIMIT_DP     = 250f
        const val FAN_ARC_DEG        = 160f
        const val SECTOR_GAP_DEG     = 2.5f
        const val LABEL_SIZE_SP      = 11f

        const val ANGLE_START_RIGHT  = 100f
        const val ANGLE_START_LEFT   = -80f
        const val ANGLE_START_BOTTOM = 190f
        const val ANGLE_START_TOP    = 10f

        val COLOR_NORMAL    = Color.argb(220, 13, 71, 161)
        val COLOR_HIGHLIGHT = Color.argb(240, 6, 40, 100)
        val COLOR_DIVIDER   = Color.WHITE
        val COLOR_DOT       = Color.argb(200, 255, 255, 255)
    }

    var rings: List<Ring> = emptyList()
        set(value) { field = value; invalidate() }
    var anchorX: Float = 0f
    var anchorY: Float = 0f
    var edge: String = "right"
    var highlightedRing: Int = -1
        set(value) { if (field != value) { field = value; invalidate() } }
    var highlightedSlot: Int = -1
        set(value) { if (field != value) { field = value; invalidate() } }

    private val sectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = COLOR_DIVIDER
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        isFakeBoldText = true
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_DOT
    }
    private val path = Path()
    private val outerRect = RectF()
    private val innerRect = RectF()

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

    private fun ringInnerR(ringIndex: Int) = if (ringIndex == 0) dp(INNER_DEAD_ZONE_DP) else dp(RING_BOUNDARY_DP)
    private fun ringOuterR(ringIndex: Int) = if (ringIndex == 0) dp(RING_BOUNDARY_DP) else dp(OUTER_LIMIT_DP)

    private fun sectorStartAngle(slotIndex: Int, count: Int): Float =
        fanStartAngle() + slotIndex * (FAN_ARC_DEG / count) + SECTOR_GAP_DEG / 2f

    private fun sectorSweep(count: Int): Float =
        (FAN_ARC_DEG / count) - SECTOR_GAP_DEG

    fun hitTest(x: Float, y: Float): Pair<Int, Int>? {
        val dx = x - anchorX
        val dy = y - anchorY
        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        if (dist < dp(INNER_DEAD_ZONE_DP) || dist > dp(OUTER_LIMIT_DP)) return null

        val ringIndex = if (dist < dp(RING_BOUNDARY_DP)) 0 else 1
        val ring = rings.getOrNull(ringIndex) ?: return null
        val n = ring.slots.size
        if (n == 0) return null
        if (n == 1) return Pair(ringIndex, 0)

        val fingerAngle = normalize(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat())

        for (i in 0 until n) {
            val start = normalize(sectorStartAngle(i, n))
            val end = normalize(start + sectorSweep(n))
            if (isAngleInArc(fingerAngle, start, sectorSweep(n))) return Pair(ringIndex, i)
        }

        // fallback: nearest mid-angle
        var minDiff = Float.MAX_VALUE
        var best = -1
        for (i in 0 until n) {
            val mid = normalize(sectorStartAngle(i, n) + sectorSweep(n) / 2f)
            val d = angleDiff(fingerAngle, mid)
            if (d < minDiff) { minDiff = d; best = i }
        }
        return if (best >= 0) Pair(ringIndex, best) else null
    }

    private fun isAngleInArc(angle: Float, start: Float, sweep: Float): Boolean {
        val end = normalize(start + sweep)
        return if (start <= end) angle in start..end
        else angle >= start || angle <= end
    }

    private fun normalize(a: Float): Float = ((a % 360f) + 360f) % 360f

    private fun angleDiff(a: Float, b: Float): Float {
        val d = ((a - b + 360f) % 360f)
        return if (d > 180f) 360f - d else d
    }

    override fun onDraw(canvas: Canvas) {
        if (animFraction == 0f || rings.isEmpty()) return

        val scale = animFraction
        val alpha = (255 * scale).toInt().coerceIn(0, 255)

        textPaint.textSize = sp(LABEL_SIZE_SP) * scale
        dividerPaint.strokeWidth = dp(2f)
        dividerPaint.alpha = alpha

        rings.forEachIndexed { ringIndex, ring ->
            val n = ring.slots.size
            if (n == 0) return@forEachIndexed

            val innerR = ringInnerR(ringIndex) * scale
            val outerR = ringOuterR(ringIndex) * scale

            ring.slots.forEachIndexed { slotIndex, slot ->
                val startAngle = sectorStartAngle(slotIndex, n)
                val sweep = sectorSweep(n)
                val isHighlighted = (ringIndex == highlightedRing && slotIndex == highlightedSlot)

                // Build sector path
                path.reset()
                outerRect.set(anchorX - outerR, anchorY - outerR, anchorX + outerR, anchorY + outerR)
                innerRect.set(anchorX - innerR, anchorY - innerR, anchorX + innerR, anchorY + innerR)
                path.arcTo(outerRect, startAngle, sweep)
                path.arcTo(innerRect, startAngle + sweep, -sweep)
                path.close()

                val baseColor = if (isHighlighted) COLOR_HIGHLIGHT else COLOR_NORMAL
                sectorPaint.color = baseColor
                sectorPaint.alpha = alpha
                canvas.drawPath(path, sectorPaint)

                // Divider lines at sector start boundary
                val startRad = Math.toRadians(startAngle.toDouble())
                canvas.drawLine(
                    anchorX + innerR * cos(startRad).toFloat(),
                    anchorY + innerR * sin(startRad).toFloat(),
                    anchorX + outerR * cos(startRad).toFloat(),
                    anchorY + outerR * sin(startRad).toFloat(),
                    dividerPaint,
                )

                // Label in sector centroid
                val midAngleRad = Math.toRadians((startAngle + sweep / 2f).toDouble())
                val midR = (innerR + outerR) / 2f
                val tx = anchorX + midR * cos(midAngleRad).toFloat()
                val ty = anchorY + midR * sin(midAngleRad).toFloat()
                textPaint.color = Color.argb(alpha, 255, 255, 255)
                canvas.drawText(slot.label.take(6), tx, ty + textPaint.textSize * 0.38f, textPaint)
            }

            // Final divider line after last sector
            val endAngle = sectorStartAngle(n - 1, n) + sectorSweep(n)
            val endRad = Math.toRadians(endAngle.toDouble())
            canvas.drawLine(
                anchorX + innerR * cos(endRad).toFloat(),
                anchorY + innerR * sin(endRad).toFloat(),
                anchorX + outerR * cos(endRad).toFloat(),
                anchorY + outerR * sin(endRad).toFloat(),
                dividerPaint,
            )
        }

        // Center dot at anchor
        dotPaint.alpha = alpha
        canvas.drawCircle(anchorX, anchorY, dp(5f) * scale, dotPaint)
    }
}
