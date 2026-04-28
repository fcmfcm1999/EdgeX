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

class PieView(context: Context) : View(context) {

    data class Slot(val label: String, val action: String, val icon: Drawable? = null) {
        val hasAction: Boolean
            get() = action.isNotEmpty() && action != "none"
    }
    data class Ring(val slots: List<Slot>)

    private companion object {
        const val INNER_DEAD_ZONE_DP = 90f
        const val RING_BOUNDARY_DP   = 159f   // hit-test boundary between ring 0 and ring 1
        const val RING0_DRAW_OUTER   = 154f   // ring 0 drawn outer edge
        const val RING1_DRAW_INNER   = 164f   // ring 1 drawn inner edge (10dp gap)
        const val OUTER_LIMIT_DP     = 250f
        const val FAN_ARC_DEG        = 160f
        const val SECTOR_GAP_DEG     = 1.5f
        const val ICON_SIZE_DP       = 40f
        const val LABEL_TEXT_SIZE_SP  = 12f

        const val ANGLE_START_RIGHT  = 100f
        const val ANGLE_START_LEFT   = -80f
        const val ANGLE_START_BOTTOM = 190f
        const val ANGLE_START_TOP    = 10f

        val COLOR_HIGHLIGHT_STROKE = Color.argb(210, 255, 255, 255)
        val COLOR_DIVIDER          = Color.argb(235, 255, 245, 250)
        val COLOR_SHADOW           = Color.argb(80, 0, 24, 48)
        val COLOR_DOT              = Color.argb(230, 255, 255, 255)
        val COLOR_DOT_HALO         = Color.argb(70, 255, 255, 255)
    }

    var accentColor: Int = Color.rgb(2, 134, 180)
        set(value) { field = value; invalidate() }

    private fun colorNormal(ringIndex: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(accentColor, hsv)
        if (ringIndex == 1) hsv[2] = (hsv[2] + 0.12f).coerceAtMost(1f)
        return Color.HSVToColor(Color.alpha(accentColor), hsv)
    }

    private fun colorHighlight(): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(accentColor, hsv)
        hsv[2] = (hsv[2] * 0.65f).coerceAtLeast(0f)
        return Color.HSVToColor(Color.alpha(accentColor), hsv)
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

    private fun activeSlotCount(): Int {
        var maxSlot = -1
        rings.forEach { ring ->
            ring.slots.forEachIndexed { index, slot ->
                if (slot.hasAction) maxSlot = maxOf(maxSlot, index)
            }
        }
        return (maxSlot + 1).coerceAtLeast(1)
    }

    private fun sectorStartAngle(slotIndex: Int, count: Int): Float =
        fanStartAngle() + slotIndex * (FAN_ARC_DEG / count) + SECTOR_GAP_DEG / 2f

    private fun sectorSweep(count: Int): Float =
        (FAN_ARC_DEG / count) - SECTOR_GAP_DEG

    fun hitTest(x: Float, y: Float): Pair<Int, Int>? {
        val dx = x - anchorX
        val dy = y - anchorY
        val distSq = dx * dx + dy * dy
        val innerLimit = dp(INNER_DEAD_ZONE_DP)
        val outerLimit = dp(OUTER_LIMIT_DP)

        if (distSq < innerLimit * innerLimit || distSq > outerLimit * outerLimit) return null

        val boundary = dp(RING_BOUNDARY_DP)
        val ringIndex = if (distSq <= boundary * boundary) 0 else 1
        val ring = rings.getOrNull(ringIndex) ?: return null
        val n = activeSlotCount()
        if (n == 0) return null

        var fingerAngle = normalize(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat())
        if (edge == "left" && fingerAngle >= 270f) {
            fingerAngle -= 360f
        }

        for (i in 0 until n) {
            val start = fanStartAngle() + i * (FAN_ARC_DEG / n)
            val end = start + (FAN_ARC_DEG / n)
            if (fingerAngle > start && fingerAngle < end) {
                return if (ring.slots.getOrNull(i)?.hasAction == true) Pair(ringIndex, i) else null
            }
        }

        return null
    }

    private fun normalize(a: Float): Float = ((a % 360f) + 360f) % 360f

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
            val n = activeSlotCount()
            if (n == 0) return@forEachIndexed

            val innerR = ringDrawInnerR(ringIndex) * scale
            val outerR = ringDrawOuterR(ringIndex) * scale

            for (slotIndex in 0 until n) {
                val slot = ring.slots.getOrNull(slotIndex) ?: continue
                if (!slot.hasAction) continue

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

                sectorPaint.color = if (isHighlighted) colorHighlight() else colorNormal(ringIndex)
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
