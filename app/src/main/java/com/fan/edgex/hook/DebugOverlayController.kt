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
            view.destroyDrawingCache()
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
                    if (config.isZoneEnabled("left")) {
                        canvas.drawRect(0f, 0f, w, h, paint)
                    } else {
                        if (config.isZoneEnabled("left_top")) canvas.drawRect(0f, 0f, w, h * 0.33f, paint)
                        if (config.isZoneEnabled("left_mid")) canvas.drawRect(0f, h * 0.33f, w, h * 0.66f, paint)
                        if (config.isZoneEnabled("left_bottom")) canvas.drawRect(0f, h * 0.66f, w, h, paint)
                    }
                }
                OverlayEdge.RIGHT -> {
                    if (config.isZoneEnabled("right")) {
                        canvas.drawRect(0f, 0f, w, h, paint)
                    } else {
                        if (config.isZoneEnabled("right_top")) canvas.drawRect(0f, 0f, w, h * 0.33f, paint)
                        if (config.isZoneEnabled("right_mid")) canvas.drawRect(0f, h * 0.33f, w, h * 0.66f, paint)
                        if (config.isZoneEnabled("right_bottom")) canvas.drawRect(0f, h * 0.66f, w, h, paint)
                    }
                }
                OverlayEdge.TOP -> {
                    if (config.isZoneEnabled("top")) {
                        canvas.drawRect(0f, 0f, w, h, paint)
                    } else {
                        if (config.isZoneEnabled("top_left")) canvas.drawRect(0f, 0f, w * 0.33f, h, paint)
                        if (config.isZoneEnabled("top_mid")) canvas.drawRect(w * 0.33f, 0f, w * 0.66f, h, paint)
                        if (config.isZoneEnabled("top_right")) canvas.drawRect(w * 0.66f, 0f, w, h, paint)
                    }
                }
                OverlayEdge.BOTTOM -> {
                    if (config.isZoneEnabled("bottom")) {
                        canvas.drawRect(0f, 0f, w, h, paint)
                    } else {
                        if (config.isZoneEnabled("bottom_left")) canvas.drawRect(0f, 0f, w * 0.33f, h, paint)
                        if (config.isZoneEnabled("bottom_mid")) canvas.drawRect(w * 0.33f, 0f, w * 0.66f, h, paint)
                        if (config.isZoneEnabled("bottom_right")) canvas.drawRect(w * 0.66f, 0f, w, h, paint)
                    }
                }
            }
        }

        fun updateWindowRegion() {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val realSize = android.graphics.Point()
            wm.defaultDisplay.getRealSize(realSize)
            val thicknessPx = (12 * context.resources.displayMetrics.density).toInt()

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
                        if (edge == OverlayEdge.LEFT || edge == OverlayEdge.RIGHT) thicknessPx else realSize.x
                    params.height =
                        if (edge == OverlayEdge.TOP || edge == OverlayEdge.BOTTOM) thicknessPx else realSize.y
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
