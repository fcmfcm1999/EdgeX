package com.fan.edgex.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var onColorChanged: ((Int) -> Unit)? = null

    var showAlphaBar: Boolean = true
        set(value) {
            field = value
            requestLayout()
        }

    private val density = resources.displayMetrics.density
    private val barHeight = 24f * density
    private val gap = 10f * density

    private var hue = 0f
    private var sat = 1f
    private var bri = 1f
    private var alpha = 255

    private val svRect = RectF()
    private val hueRect = RectF()
    private val alphaRect = RectF()

    private var svBitmap: Bitmap? = null
    private var hueBitmap: Bitmap? = null
    private var alphaBitmap: Bitmap? = null

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = 3f * density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private enum class ActiveRegion { NONE, SV, HUE, ALPHA }
    private var activeRegion = ActiveRegion.NONE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val barCount = if (showAlphaBar) 2 else 1
        val height = (width + barCount * (barHeight + gap)).roundToInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val svSize = w.toFloat()
        svRect.set(0f, 0f, svSize, svSize)
        hueRect.set(0f, svSize + gap, svSize, svSize + gap + barHeight)
        alphaRect.set(
            0f,
            svSize + gap + barHeight + gap,
            svSize,
            svSize + gap + barHeight + gap + barHeight,
        )
        invalidateBitmaps()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (svRect.isEmpty) return

        if (svBitmap == null) svBitmap = buildSvBitmap()
        svBitmap?.let { canvas.drawBitmap(it, null, svRect, bitmapPaint) }

        if (hueBitmap == null) hueBitmap = buildHueBitmap()
        hueBitmap?.let { canvas.drawBitmap(it, null, hueRect, bitmapPaint) }

        if (showAlphaBar) {
            if (alphaBitmap == null) alphaBitmap = buildAlphaBitmap()
            alphaBitmap?.let { canvas.drawBitmap(it, null, alphaRect, bitmapPaint) }
        }

        val svX = svRect.left + sat * svRect.width()
        val svY = svRect.top + (1f - bri) * svRect.height()
        val circleRadius = 8f * density
        shadowPaint.strokeWidth = 3f * density
        canvas.drawCircle(svX, svY, circleRadius, shadowPaint)
        selectorPaint.color = Color.WHITE
        selectorPaint.strokeWidth = 2f * density
        canvas.drawCircle(svX, svY, circleRadius, selectorPaint)

        drawBarSelector(canvas, hueRect.left + (hue / 360f) * hueRect.width(), hueRect)
        if (showAlphaBar) {
            drawBarSelector(canvas, alphaRect.left + (alpha / 255f) * alphaRect.width(), alphaRect)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeRegion = when {
                    svRect.contains(x, y) -> ActiveRegion.SV
                    hueRect.contains(x, y) -> ActiveRegion.HUE
                    showAlphaBar && alphaRect.contains(x, y) -> ActiveRegion.ALPHA
                    else -> ActiveRegion.NONE
                }
                updateFromTouch(x, y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateFromTouch(x, y)
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> {
                activeRegion = ActiveRegion.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun setColor(color: Int) {
        alpha = Color.alpha(color)
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0]
        sat = hsv[1]
        bri = hsv[2]
        invalidateBitmaps()
    }

    fun getColor(): Int =
        Color.HSVToColor(if (showAlphaBar) alpha else 255, floatArrayOf(hue, sat, bri))

    private fun updateFromTouch(x: Float, y: Float) {
        when (activeRegion) {
            ActiveRegion.SV -> {
                sat = ((x - svRect.left) / svRect.width()).coerceIn(0f, 1f)
                bri = 1f - ((y - svRect.top) / svRect.height()).coerceIn(0f, 1f)
                alphaBitmap?.recycle()
                alphaBitmap = null
                invalidate()
                onColorChanged?.invoke(getColor())
            }
            ActiveRegion.HUE -> {
                hue = ((x - hueRect.left) / hueRect.width()).coerceIn(0f, 1f) * 360f
                svBitmap?.recycle()
                svBitmap = null
                alphaBitmap?.recycle()
                alphaBitmap = null
                invalidate()
                onColorChanged?.invoke(getColor())
            }
            ActiveRegion.ALPHA -> {
                alpha = (((x - alphaRect.left) / alphaRect.width()).coerceIn(0f, 1f) * 255f).roundToInt()
                invalidate()
                onColorChanged?.invoke(getColor())
            }
            ActiveRegion.NONE -> Unit
        }
    }

    private fun buildSvBitmap(): Bitmap {
        val width = svRect.width().roundToInt().coerceAtLeast(1)
        val height = svRect.height().roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))

        val hPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), 0f, Color.WHITE, hueColor, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), hPaint)

        val vPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vPaint)
        return bitmap
    }

    private fun buildHueBitmap(): Bitmap {
        val width = hueRect.width().roundToInt().coerceAtLeast(1)
        val height = hueRect.height().roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val hueStops = floatArrayOf(0f, 60f, 120f, 180f, 240f, 300f, 360f)
        val colors = IntArray(hueStops.size) { index ->
            Color.HSVToColor(floatArrayOf(hueStops[index], 1f, 1f))
        }
        val positions = FloatArray(hueStops.size) { index -> index / (hueStops.size - 1).toFloat() }
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), 0f, colors, positions, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    private fun buildAlphaBitmap(): Bitmap {
        val width = alphaRect.width().roundToInt().coerceAtLeast(1)
        val height = alphaRect.height().roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val checkSize = 12f * density
        val checkPaint = Paint()
        var x = 0f
        var col = 0
        while (x < width) {
            var y = 0f
            var row = 0
            while (y < height) {
                checkPaint.color = if ((col + row) % 2 == 0) 0xFFCCCCCC.toInt() else Color.WHITE
                canvas.drawRect(
                    x,
                    y,
                    (x + checkSize).coerceAtMost(width.toFloat()),
                    (y + checkSize).coerceAtMost(height.toFloat()),
                    checkPaint,
                )
                y += checkSize
                row++
            }
            x += checkSize
            col++
        }

        val opaqueColor = Color.HSVToColor(floatArrayOf(hue, sat, bri))
        val gradientPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), 0f, Color.TRANSPARENT, opaqueColor, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)
        return bitmap
    }

    private fun drawBarSelector(canvas: Canvas, x: Float, rect: RectF) {
        val halfWidth = 3f * density
        shadowPaint.strokeWidth = 3f * density
        shadowPaint.style = Paint.Style.STROKE
        canvas.drawRect(x - halfWidth - 1, rect.top - 1, x + halfWidth + 1, rect.bottom + 1, shadowPaint)
        fillPaint.color = Color.WHITE
        canvas.drawRect(x - halfWidth, rect.top, x + halfWidth, rect.bottom, fillPaint)
        selectorPaint.color = Color.BLACK
        selectorPaint.strokeWidth = 1f * density
        canvas.drawRect(x - halfWidth, rect.top, x + halfWidth, rect.bottom, selectorPaint)
    }

    private fun invalidateBitmaps() {
        svBitmap?.recycle()
        hueBitmap?.recycle()
        alphaBitmap?.recycle()
        svBitmap = null
        hueBitmap = null
        alphaBitmap = null
        invalidate()
    }
}
