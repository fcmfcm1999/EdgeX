package com.fan.edgex.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PieView(context: Context) : View(context) {

    data class Slot(val label: String, val action: String, val icon: Drawable? = null)
    data class Ring(val slots: List<Slot>)

    private companion object {
        const val INNER_DEAD_ZONE_DP = 60f
        const val RING_BOUNDARY_DP   = 110f   // hit-test boundary between ring 0 and ring 1
        const val RING0_DRAW_OUTER   = 103f   // ring 0 drawn outer edge
        const val RING1_DRAW_INNER   = 117f   // ring 1 drawn inner edge (14dp gap)
        const val OUTER_LIMIT_DP     = 166f
        const val FAN_ARC_DEG        = 320f
        const val SECTOR_GAP_DEG     = 2.5f
        const val ICON_SIZE_DP       = 28f
        const val LABEL_TEXT_SIZE_SP  = 12f

        const val ANGLE_START_RIGHT  = 100f
        const val ANGLE_START_LEFT   = -80f
        const val ANGLE_START_BOTTOM = 190f
        const val ANGLE_START_TOP    = 10f

        val COLOR_RING_INNER       = Color.rgb(2, 134, 180)
        val COLOR_RING_OUTER       = Color.rgb(34, 163, 208)
        val COLOR_HIGHLIGHT        = Color.rgb(0, 102, 146)
        val COLOR_HIGHLIGHT_STROKE = Color.argb(210, 255, 255, 255)
        val COLOR_DIVIDER          = Color.argb(235, 255, 245, 250)
        val COLOR_SHADOW           = Color.argb(80, 0, 24, 48)
        val COLOR_DOT              = Color.argb(230, 255, 255, 255)
        val COLOR_DOT_HALO         = Color.argb(70, 255, 255, 255)
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
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_SHADOW
        setShadowLayer(8f, 0f, 3f, COLOR_SHADOW)
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = COLOR_DIVIDER
    }
    private val highlightStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = COLOR_HIGHLIGHT_STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_DOT
    }
    private val dotHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_DOT_HALO
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val path = Path()
    private val outerRect = RectF()
    private val innerRect = RectF()

    private var animFraction = 0f
    private var animator: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

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

    private fun sp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private fun fanStartAngle() = when (edge) {
        "right"  -> ANGLE_START_RIGHT
        "left"   -> ANGLE_START_LEFT
        "bottom" -> ANGLE_START_BOTTOM
        "top"    -> ANGLE_START_TOP
        else     -> ANGLE_START_RIGHT
    }

    // Drawing radii (with gap between rings)
    private fun ringDrawInnerR(ringIndex: Int) =
        if (ringIndex == 0) dp(INNER_DEAD_ZONE_DP) else dp(RING1_DRAW_INNER)
    private fun ringDrawOuterR(ringIndex: Int) =
        if (ringIndex == 0) dp(RING0_DRAW_OUTER) else dp(OUTER_LIMIT_DP)

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
            if (isAngleInArc(fingerAngle, normalize(sectorStartAngle(i, n)), sectorSweep(n))) {
                return Pair(ringIndex, i)
            }
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

        dividerPaint.strokeWidth = dp(2f)
        dividerPaint.alpha = alpha
        highlightStrokePaint.strokeWidth = dp(1.5f)
        highlightStrokePaint.alpha = alpha
        shadowPaint.alpha = (95 * scale).toInt().coerceIn(0, 95)
        labelPaint.textSize = sp(LABEL_TEXT_SIZE_SP) * scale
        labelPaint.alpha = alpha

        val iconHalf = (dp(ICON_SIZE_DP) / 2f * scale).toInt()

        rings.forEachIndexed { ringIndex, ring ->
            val n = ring.slots.size
            if (n == 0) return@forEachIndexed

            val innerR = ringDrawInnerR(ringIndex) * scale
            val outerR = ringDrawOuterR(ringIndex) * scale

            ring.slots.forEachIndexed { slotIndex, slot ->
                val startAngle = sectorStartAngle(slotIndex, n)
                val sweep = sectorSweep(n)
                val isHighlighted = (ringIndex == highlightedRing && slotIndex == highlightedSlot)

                // Sector path
                path.reset()
                outerRect.set(anchorX - outerR, anchorY - outerR, anchorX + outerR, anchorY + outerR)
                innerRect.set(anchorX - innerR, anchorY - innerR, anchorX + innerR, anchorY + innerR)
                path.arcTo(outerRect, startAngle, sweep)
                path.arcTo(innerRect, startAngle + sweep, -sweep)
                path.close()

                canvas.drawPath(path, shadowPaint)

                sectorPaint.color = when {
                    isHighlighted -> COLOR_HIGHLIGHT
                    ringIndex == 0 -> COLOR_RING_INNER
                    else -> COLOR_RING_OUTER
                }
                sectorPaint.alpha = alpha
                canvas.drawPath(path, sectorPaint)

                // Icon or label centered in sector
                val midRad = Math.toRadians((startAngle + sweep / 2f).toDouble())
                val midR = (innerR + outerR) / 2f
                val cx = (anchorX + midR * cos(midRad)).toInt()
                val cy = (anchorY + midR * sin(midRad)).toInt()

                val icon = slot.icon
                if (icon != null) {
                    icon.alpha = alpha
                    icon.setBounds(cx - iconHalf, cy - iconHalf, cx + iconHalf, cy + iconHalf)
                    icon.draw(canvas)
                } else if (slot.label.isNotBlank()) {
                    val baseline = cy - (labelPaint.descent() + labelPaint.ascent()) / 2f
                    canvas.drawText(slot.label.take(4), cx.toFloat(), baseline, labelPaint)
                }

                if (isHighlighted) {
                    canvas.drawPath(path, highlightStrokePaint)
                }
            }

        }

        // Center anchor dot
        dotHaloPaint.alpha = (95 * scale).toInt().coerceIn(0, 95)
        canvas.drawCircle(anchorX, anchorY, dp(15f) * scale, dotHaloPaint)
        dotPaint.alpha = alpha
        canvas.drawCircle(anchorX, anchorY, dp(4.5f) * scale, dotPaint)
    }
}
