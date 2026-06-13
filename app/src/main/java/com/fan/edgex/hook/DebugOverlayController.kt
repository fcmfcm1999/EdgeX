package com.fan.edgex.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.fan.edgex.config.AppConfig
import com.fan.edgex.overlay.DrawerManager

internal class DebugOverlayController(
    private val config: ConfigAccess,
    private val log: (String) -> Unit,
) {
    private enum class OverlayEdge {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
    }

    interface ConfigAccess {
        fun isGesturesEnabled(): Boolean
        fun isZoneEnabled(zone: String): Boolean
        fun isDebugEnabled(): Boolean
        fun getZoneThicknessDp(zone: String): Int
        fun getEdgeSplits(edge: String): Pair<Int, Int>
    }

    private var initialized = false
    private var receiverRegistered = false
    private var systemUiContext: Context? = null
    private val debugViews = mutableListOf<DebugOverlayView>()

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        systemUiContext = context
        registerScreenStateReceiver(context)
        try {
            addDebugOverlayView(context, OverlayEdge.LEFT)
            addDebugOverlayView(context, OverlayEdge.RIGHT)
            addDebugOverlayView(context, OverlayEdge.TOP)
            addDebugOverlayView(context, OverlayEdge.BOTTOM)
        } catch (t: Throwable) {
            log("Failed to add debug overlay views: ${t.message}")
        }
    }

    fun refresh() {
        val debug = config.isDebugEnabled()
        val color = if (debug) 0x3300FF00.toInt() else 0x00000000
        debugViews.forEach { view ->
            view.updateDebugColor(color)
            view.updateWindowRegion()
            view.invalidate()
        }
    }

    private fun registerScreenStateReceiver(context: Context) {
        if (receiverRegistered) return
        receiverRegistered = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    log("SCREEN_OFF in SystemUI — dismissing overlays")
                    Handler(Looper.getMainLooper()).post {
                        try {
                            TextSelectionOverlay.dismiss()
                        } catch (t: Throwable) {
                            log("Failed to dismiss TextSelectionOverlay: ${t.message}")
                        }
                        try {
                            DrawerManager.dismissDrawer()
                        } catch (t: Throwable) {
                            log("Failed to dismiss DrawerWindow: ${t.message}")
                        }
                    }
                }
            }
        }

        try {
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            log("Screen state receiver registered in SystemUI")
        } catch (e: Exception) {
            receiverRegistered = false
            log("Failed to register screen state receiver in SystemUI: ${e.message}")
        }
    }

    private fun addDebugOverlayView(context: Context, edge: OverlayEdge) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val thicknessPx = (12 * context.resources.displayMetrics.density).toInt()

        val view = DebugOverlayView(context, edge, config)
        val params = WindowManager.LayoutParams(
            if (edge == OverlayEdge.LEFT || edge == OverlayEdge.RIGHT) thicknessPx else WindowManager.LayoutParams.MATCH_PARENT,
            if (edge == OverlayEdge.TOP || edge == OverlayEdge.BOTTOM) thicknessPx else WindowManager.LayoutParams.MATCH_PARENT,
            2027,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = overlayGravity(edge)
        }

        wm.addView(view, params)
        debugViews.add(view)
    }

    private fun overlayGravity(edge: OverlayEdge): Int =
        when (edge) {
            OverlayEdge.LEFT -> Gravity.START or Gravity.TOP
            OverlayEdge.RIGHT -> Gravity.END or Gravity.TOP
            OverlayEdge.TOP -> Gravity.TOP or Gravity.START
            OverlayEdge.BOTTOM -> Gravity.BOTTOM or Gravity.START
        }

    private class DebugOverlayView(
        context: Context,
        private val edge: OverlayEdge,
        private val config: ConfigAccess,
    ) : View(context) {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = android.graphics.Paint()
        private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        private val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                handler.post {
                    updateWindowRegion()
                    invalidate()
                }
            }
        }

        init {
            paint.color = 0x00000000
            paint.style = android.graphics.Paint.Style.FILL
            setWillNotDraw(false)
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            displayManager.registerDisplayListener(displayListener, handler)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            displayManager.unregisterDisplayListener(displayListener)
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

            if (!config.isDebugEnabled()) return
            if (!config.isGesturesEnabled()) return

            val h = height.toFloat()
            val w = width.toFloat()

            when (edge) {
                OverlayEdge.LEFT -> {
                    val splits = config.getEdgeSplits("left")
                    val p1 = h * (splits.first / 100f)
                    val p2 = h * (splits.second / 100f)
                    val density = context.resources.displayMetrics.density

                    if (config.isZoneEnabled("left_top") || config.isZoneEnabled("left")) {
                        val thick = if (config.isZoneEnabled("left_top")) config.getZoneThicknessDp("left_top") else 8
                        canvas.drawRect(0f, 0f, thick * density, p1, paint)
                    }
                    if (config.isZoneEnabled("left_mid") || config.isZoneEnabled("left")) {
                        val thick = if (config.isZoneEnabled("left_mid")) config.getZoneThicknessDp("left_mid") else 8
                        canvas.drawRect(0f, p1, thick * density, p2, paint)
                    }
                    if (config.isZoneEnabled("left_bottom") || config.isZoneEnabled("left")) {
                        val thick = if (config.isZoneEnabled("left_bottom")) config.getZoneThicknessDp("left_bottom") else 8
                        canvas.drawRect(0f, p2, thick * density, h, paint)
                    }
                }
                OverlayEdge.RIGHT -> {
                    val splits = config.getEdgeSplits("right")
                    val p1 = h * (splits.first / 100f)
                    val p2 = h * (splits.second / 100f)
                    val density = context.resources.displayMetrics.density

                    if (config.isZoneEnabled("right_top") || config.isZoneEnabled("right")) {
                        val thick = if (config.isZoneEnabled("right_top")) config.getZoneThicknessDp("right_top") else 8
                        canvas.drawRect(w - thick * density, 0f, w, p1, paint)
                    }
                    if (config.isZoneEnabled("right_mid") || config.isZoneEnabled("right")) {
                        val thick = if (config.isZoneEnabled("right_mid")) config.getZoneThicknessDp("right_mid") else 8
                        canvas.drawRect(w - thick * density, p1, w, p2, paint)
                    }
                    if (config.isZoneEnabled("right_bottom") || config.isZoneEnabled("right")) {
                        val thick = if (config.isZoneEnabled("right_bottom")) config.getZoneThicknessDp("right_bottom") else 8
                        canvas.drawRect(w - thick * density, p2, w, h, paint)
                    }
                }
                OverlayEdge.TOP -> {
                    val splits = config.getEdgeSplits("top")
                    val p1 = w * (splits.first / 100f)
                    val p2 = w * (splits.second / 100f)
                    val density = context.resources.displayMetrics.density

                    if (config.isZoneEnabled("top_left") || config.isZoneEnabled("top")) {
                        val thick = if (config.isZoneEnabled("top_left")) config.getZoneThicknessDp("top_left") else 8
                        canvas.drawRect(0f, 0f, p1, thick * density, paint)
                    }
                    if (config.isZoneEnabled("top_mid") || config.isZoneEnabled("top")) {
                        val thick = if (config.isZoneEnabled("top_mid")) config.getZoneThicknessDp("top_mid") else 8
                        canvas.drawRect(p1, 0f, p2, thick * density, paint)
                    }
                    if (config.isZoneEnabled("top_right") || config.isZoneEnabled("top")) {
                        val thick = if (config.isZoneEnabled("top_right")) config.getZoneThicknessDp("top_right") else 8
                        canvas.drawRect(p2, 0f, w, thick * density, paint)
                    }
                }
                OverlayEdge.BOTTOM -> {
                    val splits = config.getEdgeSplits("bottom")
                    val p1 = w * (splits.first / 100f)
                    val p2 = w * (splits.second / 100f)
                    val density = context.resources.displayMetrics.density

                    if (config.isZoneEnabled("bottom_left") || config.isZoneEnabled("bottom")) {
                        val thick = if (config.isZoneEnabled("bottom_left")) config.getZoneThicknessDp("bottom_left") else 8
                        canvas.drawRect(0f, h - thick * density, p1, h, paint)
                    }
                    if (config.isZoneEnabled("bottom_mid") || config.isZoneEnabled("bottom")) {
                        val thick = if (config.isZoneEnabled("bottom_mid")) config.getZoneThicknessDp("bottom_mid") else 8
                        canvas.drawRect(p1, h - thick * density, p2, h, paint)
                    }
                    if (config.isZoneEnabled("bottom_right") || config.isZoneEnabled("bottom")) {
                        val thick = if (config.isZoneEnabled("bottom_right")) config.getZoneThicknessDp("bottom_right") else 8
                        canvas.drawRect(p2, h - thick * density, w, h, paint)
                    }
                }
            }
        }

        fun updateWindowRegion() {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val bounds = wm.currentWindowMetrics.bounds

            val maxThicknessDp = when (edge) {
                OverlayEdge.LEFT -> listOf("left_top", "left_mid", "left_bottom").maxOfOrNull {
                    val directThick = if (config.isZoneEnabled(it)) config.getZoneThicknessDp(it) else 8
                    val fallbackThick = if (config.isZoneEnabled("left")) 8 else 0
                    maxOf(directThick, fallbackThick)
                } ?: 8
                OverlayEdge.RIGHT -> listOf("right_top", "right_mid", "right_bottom").maxOfOrNull {
                    val directThick = if (config.isZoneEnabled(it)) config.getZoneThicknessDp(it) else 8
                    val fallbackThick = if (config.isZoneEnabled("right")) 8 else 0
                    maxOf(directThick, fallbackThick)
                } ?: 8
                OverlayEdge.TOP -> listOf("top_left", "top_mid", "top_right").maxOfOrNull {
                    val directThick = if (config.isZoneEnabled(it)) config.getZoneThicknessDp(it) else 8
                    val fallbackThick = if (config.isZoneEnabled("top")) 8 else 0
                    maxOf(directThick, fallbackThick)
                } ?: 8
                OverlayEdge.BOTTOM -> listOf("bottom_left", "bottom_mid", "bottom_right").maxOfOrNull {
                    val directThick = if (config.isZoneEnabled(it)) config.getZoneThicknessDp(it) else 8
                    val fallbackThick = if (config.isZoneEnabled("bottom")) 8 else 0
                    maxOf(directThick, fallbackThick)
                } ?: 8
            }
            val thicknessPx = (maxThicknessDp * context.resources.displayMetrics.density).toInt()

            try {
                val params = layoutParams as WindowManager.LayoutParams
                if (config.isDebugEnabled()) {
                    visibility = VISIBLE
                    params.gravity = when (edge) {
                        OverlayEdge.LEFT -> Gravity.START or Gravity.TOP
                        OverlayEdge.RIGHT -> Gravity.END or Gravity.TOP
                        OverlayEdge.TOP -> Gravity.TOP or Gravity.START
                        OverlayEdge.BOTTOM -> Gravity.BOTTOM or Gravity.START
                    }
                    params.x = 0
                    params.y = 0
                    params.width =
                        if (edge == OverlayEdge.LEFT || edge == OverlayEdge.RIGHT) thicknessPx else bounds.width()
                    params.height =
                        if (edge == OverlayEdge.TOP || edge == OverlayEdge.BOTTOM) thicknessPx else bounds.height()
                } else {
                    visibility = GONE
                    params.width = 0
                    params.height = 0
                }
                wm.updateViewLayout(this, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun updateDebugColor(color: Int) {
            paint.color = color
        }
    }
}
