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
            addDebugOverlayView(context, Gravity.LEFT)
            addDebugOverlayView(context, Gravity.RIGHT)
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

    private fun addDebugOverlayView(context: Context, gravity: Int) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val widthPx = (12 * context.resources.displayMetrics.density).toInt()

        val view = DebugOverlayView(context, gravity, config)
        val params = WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.MATCH_PARENT,
            2027,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
        }

        wm.addView(view, params)
        debugViews.add(view)
    }

    private class DebugOverlayView(
        context: Context,
        private val edgeGravity: Int,
        private val config: ConfigAccess,
    ) : View(context) {
        private var windowOffsetY = 0
        private var lastScreenHeight = 0
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

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val realSize = android.graphics.Point()
            wm.defaultDisplay.getRealSize(realSize)

            if (realSize.y != lastScreenHeight) {
                lastScreenHeight = realSize.y
                handler.post { updateWindowRegion() }
            }

            val h = realSize.y.toFloat()
            val w = width.toFloat()
            val offsetY = -windowOffsetY.toFloat()

            if (edgeGravity == Gravity.LEFT) {
                if (config.isZoneEnabled("left_top")) canvas.drawRect(0f, 0f + offsetY, w, h * 0.33f + offsetY, paint)
                if (config.isZoneEnabled("left_mid")) canvas.drawRect(0f, h * 0.33f + offsetY, w, h * 0.66f + offsetY, paint)
                if (config.isZoneEnabled("left_bottom")) canvas.drawRect(0f, h * 0.66f + offsetY, w, h + offsetY, paint)
            } else {
                if (config.isZoneEnabled("right_top")) canvas.drawRect(0f, 0f + offsetY, w, h * 0.33f + offsetY, paint)
                if (config.isZoneEnabled("right_mid")) canvas.drawRect(0f, h * 0.33f + offsetY, w, h * 0.66f + offsetY, paint)
                if (config.isZoneEnabled("right_bottom")) canvas.drawRect(0f, h * 0.66f + offsetY, w, h + offsetY, paint)
            }
        }

        fun updateWindowRegion() {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val realSize = android.graphics.Point()
            wm.defaultDisplay.getRealSize(realSize)
            val h = realSize.y

            var minTop = h
            var maxBottom = 0
            var hasEnabledZone = false

            if (edgeGravity == Gravity.LEFT) {
                if (config.isZoneEnabled("left_top")) {
                    minTop = kotlin.math.min(minTop, 0)
                    maxBottom = kotlin.math.max(maxBottom, (h * 0.33f).toInt())
                    hasEnabledZone = true
                }
                if (config.isZoneEnabled("left_mid")) {
                    minTop = kotlin.math.min(minTop, (h * 0.33f).toInt())
                    maxBottom = kotlin.math.max(maxBottom, (h * 0.66f).toInt())
                    hasEnabledZone = true
                }
                if (config.isZoneEnabled("left_bottom")) {
                    minTop = kotlin.math.min(minTop, (h * 0.66f).toInt())
                    maxBottom = kotlin.math.max(maxBottom, h)
                    hasEnabledZone = true
                }
            } else {
                if (config.isZoneEnabled("right_top")) {
                    minTop = kotlin.math.min(minTop, 0)
                    maxBottom = kotlin.math.max(maxBottom, (h * 0.33f).toInt())
                    hasEnabledZone = true
                }
                if (config.isZoneEnabled("right_mid")) {
                    minTop = kotlin.math.min(minTop, (h * 0.33f).toInt())
                    maxBottom = kotlin.math.max(maxBottom, (h * 0.66f).toInt())
                    hasEnabledZone = true
                }
                if (config.isZoneEnabled("right_bottom")) {
                    minTop = kotlin.math.min(minTop, (h * 0.66f).toInt())
                    maxBottom = kotlin.math.max(maxBottom, h)
                    hasEnabledZone = true
                }
            }

            try {
                val params = layoutParams as WindowManager.LayoutParams
                when {
                    config.isDebugEnabled() -> {
                        visibility = VISIBLE
                        params.gravity = Gravity.TOP or edgeGravity
                        params.y = 0
                        params.height = h
                        windowOffsetY = 0
                    }
                    !hasEnabledZone -> {
                        params.height = 0
                        visibility = GONE
                        windowOffsetY = 0
                    }
                    else -> {
                        visibility = VISIBLE
                        params.gravity = Gravity.TOP or edgeGravity
                        params.y = minTop
                        params.height = maxBottom - minTop
                        windowOffsetY = minTop
                    }
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
