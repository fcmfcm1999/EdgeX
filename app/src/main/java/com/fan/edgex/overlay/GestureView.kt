package com.fan.edgex.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import de.robv.android.xposed.XposedBridge
import kotlin.math.abs
import androidx.core.graphics.toColorInt

class GestureView(context: Context) : View(context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val layoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        
        // Position: Bottom Right
        gravity = Gravity.BOTTOM or Gravity.RIGHT
        width = 40 // Detection width in pixels (adjust as needed)
        height = 400 // Detection height in pixels
        x = 0
        y = 0 // Offset from bottom
    }

    private var startX = 0f
    private var startY = 0f
    private val SWIPE_THRESHOLD = 50 

    init {
        // Visual debug - remove or make transparent later
        setBackgroundColor("#33FF0000".toColorInt())
    }

    fun addToWindow() {
        try {
            windowManager.addView(this, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (Build.VERSION.SDK_INT >= 29) {
            val exclusionRects = listOf(Rect(0, 0, width, height))
            systemGestureExclusionRects = exclusionRects
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                val endX = event.rawX
                val endY = event.rawY
                
                val diffX = startX - endX // Positive = Left
                val diffY = startY - endY // Positive = Up

                if (abs(diffX) > abs(diffY)) {
                    // Horizontal swipe
                    if (diffX > SWIPE_THRESHOLD) {
                        onSwipeLeft()
                    }
                } else {
                    // Vertical swipe
                    if (diffY > SWIPE_THRESHOLD) {
                        onSwipeUp()
                    }
                }
                return true
            }
        }
        return false
    }

    private fun onSwipeLeft() {
        DrawerManager.showDrawer(context)
    }

    private fun onSwipeUp() {
        XposedBridge.log("XposedEdge: Swipe Up Detected")
        try {
            Toast.makeText(context, "Swipe Up!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            XposedBridge.log("XposedEdge: Failed to show toast: " + e.message)
        }
    }
}
