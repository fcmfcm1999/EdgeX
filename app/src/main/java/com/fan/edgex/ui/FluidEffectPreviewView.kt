package com.fan.edgex.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

class FluidEffectPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var globalColor: Int = DEFAULT_COLOR
        set(value) {
            field = value
            invalidate()
        }

    var leftColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    var rightColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    var topColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    var bottomColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    var sizeProgress: Int = 63
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    var maxAlpha: Float = 0.8f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var progress: Float = 0f
        set(value) {
            field = value - value.toInt()
            invalidate()
        }

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val phase = (progress * 4f).toInt().coerceIn(0, 3)
        val local = (progress * 4f) - phase
        val eased = 0.5f - 0.5f * kotlin.math.cos(local * PI.toFloat() * 2f)
        val alphaPulse = 0.55f + 0.45f * ((sin(local * PI.toFloat() * 2f) + 1f) * 0.5f)
        val distance = (12f + 54f * eased) * resources.displayMetrics.density

        when (phase) {
            0 -> drawBlob(canvas, Edge.LEFT, width.toFloat(), height.toFloat(), distance, height * 0.5f, leftColor ?: globalColor, alphaPulse)
            1 -> drawBlob(canvas, Edge.TOP, width.toFloat(), height.toFloat(), distance, width * 0.5f, topColor ?: globalColor, alphaPulse)
            2 -> drawBlob(canvas, Edge.RIGHT, width.toFloat(), height.toFloat(), distance, height * 0.5f, rightColor ?: globalColor, alphaPulse)
            else -> drawBlob(canvas, Edge.BOTTOM, width.toFloat(), height.toFloat(), distance, width * 0.5f, bottomColor ?: globalColor, alphaPulse)
        }
    }

    private fun drawBlob(
        canvas: Canvas,
        edge: Edge,
        screenW: Float,
        screenH: Float,
        distFromEdge: Float,
        fingerAlong: Float,
        color: Int,
        alphaPulse: Float,
    ) {
        val density = resources.displayMetrics.density
        val maxDist = 62f * density
        val elasticity = 0.3f + sizeProgress.coerceIn(0, 100) * 0.027f
        val protrusion = log10((distFromEdge / maxDist + 1.0)).toFloat() * maxDist * elasticity
        val spreadDim = if (edge == Edge.LEFT || edge == Edge.RIGHT) screenH else screenW
        val halfSpread = spreadDim * 0.13f
        val cp = halfSpread * 0.55f

        path.reset()
        when (edge) {
            Edge.LEFT -> {
                path.moveTo(0f, fingerAlong - halfSpread)
                path.cubicTo(0f, fingerAlong - cp, protrusion, fingerAlong - cp, protrusion, fingerAlong)
                path.cubicTo(protrusion, fingerAlong + cp, 0f, fingerAlong + cp, 0f, fingerAlong + halfSpread)
            }
            Edge.RIGHT -> {
                val tx = screenW - protrusion
                path.moveTo(screenW, fingerAlong - halfSpread)
                path.cubicTo(screenW, fingerAlong - cp, tx, fingerAlong - cp, tx, fingerAlong)
                path.cubicTo(tx, fingerAlong + cp, screenW, fingerAlong + cp, screenW, fingerAlong + halfSpread)
            }
            Edge.TOP -> {
                path.moveTo(fingerAlong - halfSpread, 0f)
                path.cubicTo(fingerAlong - cp, 0f, fingerAlong - cp, protrusion, fingerAlong, protrusion)
                path.cubicTo(fingerAlong + cp, protrusion, fingerAlong + cp, 0f, fingerAlong + halfSpread, 0f)
            }
            Edge.BOTTOM -> {
                val ty = screenH - protrusion
                path.moveTo(fingerAlong - halfSpread, screenH)
                path.cubicTo(fingerAlong - cp, screenH, fingerAlong - cp, ty, fingerAlong, ty)
                path.cubicTo(fingerAlong + cp, ty, fingerAlong + cp, screenH, fingerAlong + halfSpread, screenH)
            }
        }
        path.close()
        paint.color = Color.argb(
            (Color.alpha(color) * maxAlpha * alphaPulse).toInt().coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
        canvas.drawPath(path, paint)
    }

    private enum class Edge { LEFT, RIGHT, TOP, BOTTOM }

    private companion object {
        const val DEFAULT_COLOR = 0xCCFFFFFF.toInt()
    }
}
