package com.fan.edgex.overlay

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import android.view.View
import com.fan.edgex.config.AppConfig
import kotlin.math.roundToInt

class EdgeLightingView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
) : View(context, attrs) {

    var glowColor: Int = Color.CYAN
        set(value) {
            field = value
            invalidate()
        }

    var glowWidthPx: Float = 5f * resources.displayMetrics.density
        set(value) {
            field = value.coerceAtLeast(1f)
            paint.strokeWidth = field * 2f
            paint.maskFilter = BlurMaskFilter(field, BlurMaskFilter.Blur.NORMAL)
            invalidate()
        }

    var glowAlpha: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var effect: String = AppConfig.EDGE_LIGHTING_EFFECT_BASIC
        set(value) {
            field = value
            invalidate()
        }

    var flowProgress: Float = 0f
        set(value) {
            field = value - value.toInt()
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = glowWidthPx * 2f
        maskFilter = BlurMaskFilter(glowWidthPx, BlurMaskFilter.Blur.NORMAL)
    }

    private val rect = RectF()
    private val shaderMatrix = Matrix()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (glowAlpha <= 0f || width <= 0 || height <= 0) return

        val alphaColor = Color.argb(
            (Color.alpha(glowColor) * glowAlpha).toInt().coerceIn(0, 255),
            Color.red(glowColor),
            Color.green(glowColor),
            Color.blue(glowColor),
        )

        when (effect) {
            AppConfig.EDGE_LIGHTING_EFFECT_FLOW -> {
                drawFlow(canvas, alphaColor, widthFactor = 0.55f)
                return
            }
            AppConfig.EDGE_LIGHTING_EFFECT_SPOTLIGHT -> {
                drawSpotlight(canvas, alphaColor)
                return
            }
            AppConfig.EDGE_LIGHTING_EFFECT_MULTICOLOR -> {
                drawMulticolor(canvas)
                return
            }
            AppConfig.EDGE_LIGHTING_EFFECT_ECLIPSE -> {
                drawEclipse(canvas, alphaColor)
                return
            }
            AppConfig.EDGE_LIGHTING_EFFECT_ECHO -> {
                drawEcho(canvas, alphaColor)
                return
            }
            AppConfig.EDGE_LIGHTING_EFFECT_COMET -> {
                drawComet(canvas, alphaColor)
                return
            }
            AppConfig.EDGE_LIGHTING_EFFECT_RIPPLE -> {
                drawRipple(canvas, alphaColor)
                return
            }
        }

        paint.shader = null
        paint.color = alphaColor
        val inset = glowWidthPx * 0.5f
        rect.set(-inset, -inset, width + inset, height + inset)
        canvas.drawRect(rect, paint)
    }

    private fun drawFlow(canvas: Canvas, alphaColor: Int, widthFactor: Float) {
        val transparent = Color.argb(0, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
        val colors = intArrayOf(transparent, alphaColor, transparent)
        val positions = floatArrayOf(0f, 0.5f, 1f)

        paint.color = Color.WHITE

        paint.shader = movingShader(width.toFloat().coerceAtLeast(1f), horizontal = true, colors, positions, widthFactor)
        canvas.drawLine(0f, 0f, width.toFloat(), 0f, paint)
        canvas.drawLine(width.toFloat(), height.toFloat(), 0f, height.toFloat(), paint)

        paint.shader = movingShader(height.toFloat().coerceAtLeast(1f), horizontal = false, colors, positions, widthFactor)
        canvas.drawLine(width.toFloat(), 0f, width.toFloat(), height.toFloat(), paint)
        canvas.drawLine(0f, height.toFloat(), 0f, 0f, paint)
        paint.shader = null
    }

    private fun drawSpotlight(canvas: Canvas, alphaColor: Int) {
        paint.shader = null
        paint.color = ColorUtils.setAlphaComponent(alphaColor, (Color.alpha(alphaColor) * 0.28f).toInt().coerceIn(0, 255))
        val inset = glowWidthPx * 0.5f
        rect.set(-inset, -inset, width + inset, height + inset)
        canvas.drawRect(rect, paint)
        drawFlow(canvas, alphaColor, widthFactor = 0.22f)
    }

    private fun drawMulticolor(canvas: Canvas) {
        val alpha = (255 * glowAlpha).toInt().coerceIn(0, 255)
        val colors = intArrayOf(
            Color.argb(alpha, 0, 255, 255),
            Color.argb(alpha, 120, 96, 255),
            Color.argb(alpha, 255, 64, 180),
            Color.argb(alpha, 255, 210, 64),
            Color.argb(alpha, 0, 255, 140),
            Color.argb(alpha, 0, 255, 255),
        )
        paint.color = Color.WHITE

        paint.shader = multicolorShader(width.toFloat().coerceAtLeast(1f), horizontal = true, colors, phase = flowProgress)
        canvas.drawLine(0f, 0f, width.toFloat(), 0f, paint)

        paint.shader = multicolorShader(height.toFloat().coerceAtLeast(1f), horizontal = false, colors, phase = flowProgress + 0.25f)
        canvas.drawLine(width.toFloat(), 0f, width.toFloat(), height.toFloat(), paint)

        paint.shader = multicolorShader(width.toFloat().coerceAtLeast(1f), horizontal = true, colors, phase = 1f - flowProgress)
        canvas.drawLine(width.toFloat(), height.toFloat(), 0f, height.toFloat(), paint)

        paint.shader = multicolorShader(height.toFloat().coerceAtLeast(1f), horizontal = false, colors, phase = 0.75f - flowProgress)
        canvas.drawLine(0f, height.toFloat(), 0f, 0f, paint)
        paint.shader = null
    }

    private fun drawEclipse(canvas: Canvas, alphaColor: Int) {
        val alpha = Color.alpha(alphaColor)
        val transparent = Color.argb(0, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
        val shadow = Color.argb((alpha * 0.12f).toInt().coerceIn(0, 255), 0, 0, 0)
        val highlight = ColorUtils.blendARGB(alphaColor, Color.WHITE, 0.35f)
        val colors = intArrayOf(shadow, transparent, highlight, transparent, shadow)
        val positions = floatArrayOf(0f, 0.26f, 0.5f, 0.74f, 1f)
        paint.color = Color.WHITE
        paint.shader = movingShader(width.toFloat().coerceAtLeast(1f), horizontal = true, colors, positions, 0.8f)
        canvas.drawLine(0f, 0f, width.toFloat(), 0f, paint)
        canvas.drawLine(width.toFloat(), height.toFloat(), 0f, height.toFloat(), paint)
        paint.shader = movingShader(height.toFloat().coerceAtLeast(1f), horizontal = false, colors, positions, 0.8f)
        canvas.drawLine(width.toFloat(), 0f, width.toFloat(), height.toFloat(), paint)
        canvas.drawLine(0f, height.toFloat(), 0f, 0f, paint)
        paint.shader = null
    }

    private fun drawEcho(canvas: Canvas, alphaColor: Int) {
        paint.shader = null
        val originalStrokeWidth = paint.strokeWidth
        val originalMaskFilter = paint.maskFilter
        val echoStrokeWidth = originalStrokeWidth * 0.52f
        val echoBlur = glowWidthPx * 0.56f
        val insetStep = glowWidthPx * 0.42f
        paint.maskFilter = BlurMaskFilter(echoBlur, BlurMaskFilter.Blur.NORMAL)
        for (index in 0 until 3) {
            val echoAlpha = (Color.alpha(alphaColor) * (0.95f - index * 0.24f)).toInt().coerceIn(0, 255)
            paint.color = ColorUtils.setAlphaComponent(alphaColor, echoAlpha)
            paint.strokeWidth = echoStrokeWidth * (1f - index * 0.18f)
            val inset = index * insetStep
            rect.set(inset, inset, width - inset, height - inset)
            canvas.drawRect(rect, paint)
        }
        paint.strokeWidth = originalStrokeWidth
        paint.maskFilter = originalMaskFilter
    }

    private fun movingShader(
        length: Float,
        horizontal: Boolean,
        colors: IntArray,
        positions: FloatArray,
        widthFactor: Float,
    ): LinearGradient {
        val gradientLength = length * widthFactor.coerceIn(0.1f, 1f)
        val shader = if (horizontal) {
            LinearGradient(0f, 0f, gradientLength, 0f, colors, positions, Shader.TileMode.MIRROR)
        } else {
            LinearGradient(0f, 0f, 0f, gradientLength, colors, positions, Shader.TileMode.MIRROR)
        }
        shaderMatrix.reset()
        val periodCount = (1f / widthFactor.coerceIn(0.1f, 1f)).roundToInt().coerceAtLeast(1)
        val offset = gradientLength * 2f * periodCount * flowProgress
        if (horizontal) {
            shaderMatrix.setTranslate(offset, 0f)
        } else {
            shaderMatrix.setTranslate(0f, offset)
        }
        shader.setLocalMatrix(shaderMatrix)
        return shader
    }

    private fun drawComet(canvas: Canvas, alphaColor: Int) {
        val wf = width.toFloat().coerceAtLeast(1f)
        val hf = height.toFloat().coerceAtLeast(1f)
        val totalPerim = 2f * (wf + hf)

        // Perimeter boundary fractions (clockwise: top→right→bottom→left)
        val f1 = wf / totalPerim
        val f2 = (wf + hf) / totalPerim
        val f3 = (2f * wf + hf) / totalPerim

        val tailFrac = 0.15f
        val head = flowProgress
        val tailF0 = (head - tailFrac + 1f) % 1f

        fun perimDist(f: Float): Float { var d = f - tailF0; if (d < 0f) d += 1f; return d }

        fun colorAt(f: Float): Int {
            val t = (perimDist(f) / tailFrac).coerceIn(0f, 1f)
            val a = (Color.alpha(alphaColor) * t * t).toInt().coerceIn(0, 255)
            return Color.argb(a, Color.red(alphaColor), Color.green(alphaColor), Color.blue(alphaColor))
        }

        fun drawSideSegment(
            sideF0: Float, sideF1: Float,
            x0: Float, y0: Float, x1: Float, y1: Float,
            arcStart: Float, arcEnd: Float,
        ) {
            val sideLen = sideF1 - sideF0
            if (sideLen <= 0f) return
            val oStart = maxOf(arcStart, sideF0)
            val oEnd = minOf(arcEnd, sideF1)
            if (oStart >= oEnd) return
            val ls = (oStart - sideF0) / sideLen
            val le = (oEnd - sideF0) / sideLen
            val px0 = x0 + (x1 - x0) * ls; val py0 = y0 + (y1 - y0) * ls
            val px1 = x0 + (x1 - x0) * le; val py1 = y0 + (y1 - y0) * le
            paint.shader = LinearGradient(px0, py0, px1, py1, colorAt(oStart), colorAt(oEnd), Shader.TileMode.CLAMP)
            canvas.drawLine(px0, py0, px1, py1, paint)
        }

        fun drawAllSides(arcStart: Float, arcEnd: Float) {
            drawSideSegment(0f, f1, 0f, 0f, wf, 0f, arcStart, arcEnd)
            drawSideSegment(f1, f2, wf, 0f, wf, hf, arcStart, arcEnd)
            drawSideSegment(f2, f3, wf, hf, 0f, hf, arcStart, arcEnd)
            drawSideSegment(f3, 1f, 0f, hf, 0f, 0f, arcStart, arcEnd)
        }

        paint.color = Color.WHITE
        if (tailF0 <= head) {
            drawAllSides(tailF0, head)
        } else {
            drawAllSides(tailF0, 1f)
            drawAllSides(0f, head)
        }
        paint.shader = null
    }

    private fun drawRipple(canvas: Canvas, alphaColor: Int) {
        paint.shader = null
        val origStrokeWidth = paint.strokeWidth
        val origMaskFilter = paint.maskFilter

        val maxInset = glowWidthPx * 3.5f
        paint.maskFilter = BlurMaskFilter(glowWidthPx * 0.6f, BlurMaskFilter.Blur.NORMAL)
        paint.strokeWidth = glowWidthPx * 0.65f

        for (i in 0 until 3) {
            val phase = (flowProgress + i / 3f) % 1f
            val inset = phase * maxInset
            val alpha = (Color.alpha(alphaColor) * (1f - phase)).toInt().coerceIn(0, 255)
            paint.color = ColorUtils.setAlphaComponent(alphaColor, alpha)
            rect.set(inset, inset, width - inset, height - inset)
            if (rect.width() > 0 && rect.height() > 0) canvas.drawRect(rect, paint)
        }

        paint.strokeWidth = origStrokeWidth
        paint.maskFilter = origMaskFilter
    }

    private fun multicolorShader(
        length: Float,
        horizontal: Boolean,
        colors: IntArray,
        phase: Float,
    ): LinearGradient {
        val gradientLength = length * 0.72f
        val shader = if (horizontal) {
            LinearGradient(0f, 0f, gradientLength, 0f, colors, null, Shader.TileMode.REPEAT)
        } else {
            LinearGradient(0f, 0f, 0f, gradientLength, colors, null, Shader.TileMode.REPEAT)
        }
        val normalizedPhase = ((phase % 1f) + 1f) % 1f
        shaderMatrix.reset()
        val offset = gradientLength * 3f * normalizedPhase
        if (horizontal) {
            shaderMatrix.setTranslate(offset, 0f)
        } else {
            shaderMatrix.setTranslate(0f, offset)
        }
        shader.setLocalMatrix(shaderMatrix)
        return shader
    }
}
