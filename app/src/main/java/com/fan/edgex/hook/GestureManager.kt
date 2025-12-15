package com.fan.edgex.hook

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import kotlin.math.abs

@SuppressLint("StaticFieldLeak")
object GestureManager {

    private var context: Context? = null
    private var windowManager: WindowManager? = null
    
    // Config URI
    private val CONFIG_URI = Uri.parse("content://com.fan.edgex.provider/config")

    fun init(ctx: Context) {
        if (context != null) return // Already initialized
        context = ctx
        windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Add Left Edge
        addGestureView(Gravity.LEFT)
        // Add Right Edge
        addGestureView(Gravity.RIGHT)
        
        de.robv.android.xposed.XposedBridge.log("EdgeX: GestureManager initialized")
    }

    private val views = mutableListOf<GestureView>()

    private fun addGestureView(gravity: Int) {
        val wm = context?.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = context?.resources?.displayMetrics
        val widthPx = if (metrics != null) (30 * metrics.density).toInt() else 100
        
        val view = GestureView(context!!, gravity)
        val params = WindowManager.LayoutParams(
            widthPx, // Usage of calculated DP width (approx 80-100px) instead of 30px
            WindowManager.LayoutParams.MATCH_PARENT, // Revert to full height for reliability
            2024, 
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = gravity
        
        windowManager?.addView(view, params)
        views.add(view) // Track view
        de.robv.android.xposed.XposedBridge.log("EdgeX: Added GestureView with gravity $gravity width $widthPx")
    }

    // Configuration Cache
    private var configCache = mutableMapOf<String, String>()
    private var lastConfigLoad = 0L
    private val CONFIG_CACHE_TTL = 2000L // 2 seconds

    private fun reloadConfigAsync() {
        if (System.currentTimeMillis() - lastConfigLoad < CONFIG_CACHE_TTL) return
        
        // Simple async update
        val thread = Thread {
            try {
                // Pre-fetch keys
                val keys = listOf("gestures_enabled", 
                                "left_mid_swipe_left", "left_mid_swipe_right",
                                "left_bottom_swipe_left", "left_bottom_swipe_right", 
                                "right_top_swipe_left", "right_top_swipe_right",
                                "right_mid_swipe_left", "right_mid_swipe_right",
                                "right_bottom_swipe_left", "right_bottom_swipe_right",
                                "zone_enabled_left_mid", "zone_enabled_left_bottom",
                                "zone_enabled_right_top", "zone_enabled_right_mid", "zone_enabled_right_bottom")
                
                for (key in keys) {
                     val type = if (key.startsWith("zone_enabled") || key == "gestures_enabled") "boolean" else "string"
                     configCache[key] = queryConfigProvider(key, type)
                }
                lastConfigLoad = System.currentTimeMillis()
                
                // Refresh Views on Main Thread after Cache Update
                Handler(Looper.getMainLooper()).post {
                    views.forEach { it.invalidate() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        thread.start()
    }
    class GestureView(context: Context, val edgeGravity: Int) : View(context) {
        private var startX = 0f
        private var startY = 0f
        private var startTime = 0L
        private val handler = Handler(Looper.getMainLooper())
        private var clickCount = 0
        
        // Double click timeout
        private val resetClick = Runnable {
            if (clickCount == 1) {
                triggerAction("click")
            }
            clickCount = 0
        }

        private val paint = android.graphics.Paint()

        init {
            paint.color = 0x3300FF00.toInt() // Transparent Green for Debug
            paint.style = android.graphics.Paint.Style.FILL
            
            // CRITICAL: specific to generic Views, enables onDraw
            setWillNotDraw(false)
            
            // Register ContentObserver to update visuals ONLY when config changes
            val observer = object : android.database.ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    // Force cache refresh
                    lastConfigLoad = 0 
                    reloadConfigAsync()
                    // postInvalidate handled by reloadConfigAsync completion
                }
            }
            try {
                context.contentResolver.registerContentObserver(CONFIG_URI, true, observer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            // de.robv.android.xposed.XposedBridge.log("EdgeX: onDraw executing") // Debug log
            
            if (!isGesturesEnabled()) return
            
            val w = width.toFloat()
            val h = height.toFloat()
            
            if (edgeGravity == Gravity.LEFT) {
                // Left Mid (0 - 60%)
                if (isZoneEnabled("left_mid")) {
                    canvas.drawRect(0f, 0f, w, h * 0.6f, paint)
                }
                // Left Bottom (60% - 100%)
                if (isZoneEnabled("left_bottom")) {
                    canvas.drawRect(0f, h * 0.6f, w, h, paint)
                }
            } else {
                // Right Top (0 - 33%)
                 if (isZoneEnabled("right_top")) {
                    canvas.drawRect(0f, 0f, w, h * 0.33f, paint)
                }
                // Right Mid (33% - 66%)
                if (isZoneEnabled("right_mid")) {
                    canvas.drawRect(0f, h * 0.33f, w, h * 0.66f, paint)
                }
                // Right Bottom (66% - 100%)
                if (isZoneEnabled("right_bottom")) {
                    canvas.drawRect(0f, h * 0.66f, w, h, paint)
                }
            }
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            val exclusionRects = listOf(Rect(0, 0, width, height))
            systemGestureExclusionRects = exclusionRects
        }
        
        private fun isZoneEnabled(zone: String): Boolean {
             return getConfig("zone_enabled_$zone", "boolean") == "true"
        }

        private val SWIPE_THRESHOLD = 50

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isGesturesEnabled()) return false
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startTime = System.currentTimeMillis()
                    // de.robv.android.xposed.XposedBridge.log("EdgeX: Touch Down at $startX, $startY")
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val endX = event.rawX
                    val endY = event.rawY
                    
                    // Logic from GestureView.kt: diff = start - end
                    val diffX = startX - endX 
                    val diffY = startY - endY
                    val duration = System.currentTimeMillis() - startTime
                    
                    if (abs(diffX) > abs(diffY)) {
                        // Horizontal Swipe
                        if (abs(diffX) > SWIPE_THRESHOLD) {
                            if (diffX > 0) {
                                // Start > End -> Swipe LEFT
                                if (edgeGravity == Gravity.RIGHT) triggerAction("swipe_left")
                            } else {
                                // Start < End -> Swipe RIGHT
                                if (edgeGravity == Gravity.LEFT) triggerAction("swipe_right")
                            }
                        } else {
                            // Tap / Click handling (if movement is small)
                            handleClick(duration)
                        }
                    } else {
                        // Vertical Swipe
                        if (abs(diffY) > SWIPE_THRESHOLD) {
                             if (diffY > 0) {
                                 // Start > End -> Swipe UP
                                 triggerAction("swipe_up")
                             } else {
                                 // Start < End -> Swipe DOWN
                                 triggerAction("swipe_down")
                             }
                        } else {
                            handleClick(duration)
                        }
                    }
                    return true
                }
            }
            return false
        }
        
        private fun handleClick(duration: Long) {
            if (duration > 400) {
                 triggerAction("long_press")
            } else {
                clickCount++
                if (clickCount == 1) {
                    handler.postDelayed(resetClick, 300)
                } else if (clickCount == 2) {
                    handler.removeCallbacks(resetClick)
                    triggerAction("double_click")
                    clickCount = 0
                }
            }
        }
        


        private fun triggerAction(event: String) {
            val zone = getZone(startY)
            val configKey = "${zone}_${event}"
            
            // Query Config
            val action = getConfig(configKey)
            if (action.isEmpty() || action == "default" || action == "none") return
            
            performAction(action)
        }
        
        private fun getZone(y: Float): String {
            val h = context.resources.displayMetrics.heightPixels
            return when {
                y < h * 0.33 -> if (edgeGravity == Gravity.LEFT) "left_mid" else "right_top" // Wait, "left_mid"? User layout says "Left Mid", "Left Bottom". What happened to "Left Top"? Layout only had 2 left zones. 
                // Correction based on USER UI Layout:
                // Left: Left Mid, Left Bottom.
                // Right: Right Top, Right Mid, Right Bottom.
                // Let's refine based on user labels.
                // Left: < 50% -> Left Mid? > 50% -> Left Bottom? 
                // Right: < 33% Top, 33-66 Mid, > 66 Bottom.
                
                // REVISED LOGIC:
                else -> {
                    if (edgeGravity == Gravity.LEFT) {
                        if (y < h * 0.6) "left_mid" else "left_bottom" // Assuming top part is just "Mid" or User didn't ask for Left Top.
                    } else {
                         if (y < h * 0.33) "right_top"
                         else if (y < h * 0.66) "right_mid"
                         else "right_bottom"
                    }
                }
            }
        }
    }

    private fun isGesturesEnabled(): Boolean {
        // Default to TRUE so it works on first install
        // Logic: if cache returns "false" explicitly, then false. Else true (default or missing).
        val value = getConfig("gestures_enabled", "boolean", "true")
        return value != "false"
    }


    
    private fun getConfig(key: String, type: String = "string", defValue: String = ""): String {
         // Trigger reload if stale, but return cached immediately
         reloadConfigAsync()
         
         if (configCache.containsKey(key)) {
             return configCache[key] ?: defValue
         }
         
         // Fallback to sync query if completely missing (first run)
         // Store result in cache
         val value = queryConfigProvider(key, type)
         if (value.isNotEmpty()) configCache[key] = value
         return if (value.isNotEmpty()) value else defValue
    }

    private fun queryConfigProvider(key: String, type: String): String {
        try {
            val cursor: Cursor? = context?.contentResolver?.query(
                CONFIG_URI,
                null,
                key,
                arrayOf(""),
                type
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    private fun performAction(action: String) {
        val ctx = context ?: return
        
        // Show Toast for the action
        // Toast.makeText(ctx, "Gesture: $action", Toast.LENGTH_SHORT).show() // Generic debug
        
        when (action) {
            "back" -> {
                Toast.makeText(ctx, "执行: 返回", Toast.LENGTH_SHORT).show()
            }
            
            "home" -> {
                Toast.makeText(ctx, "执行: 返回桌面", Toast.LENGTH_SHORT).show()
                 // Placeholder for Home
            }
            
            "freezer_drawer" -> {
                Toast.makeText(ctx, "打开冰箱抽屉", Toast.LENGTH_SHORT).show()
                // Launch Freezer Activity
//                val intent = Intent().setClassName("com.fan.edgex", "com.fan.edgex.ui.FreezerActivity")
//                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                ctx.startActivity(intent)
            }
            
            "screenshot" -> {
                Toast.makeText(ctx, "正在截屏...", Toast.LENGTH_SHORT).show()
//                try { Runtime.getRuntime().exec("input keyevent 120") } catch(e:Exception){}
            }
            
            "refreeze" -> {
                Toast.makeText(ctx, "重新冻结应用", Toast.LENGTH_SHORT).show()
                // Refreeze logic placeholder
            }
            
            else -> Toast.makeText(ctx, "未知动作: $action", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Placeholder for actual global action
    object AccessibilityService { const val GLOBAL_ACTION_BACK = 1 } 
    private fun performGlobalAction(action: Int) {
         try { Runtime.getRuntime().exec("input keyevent 4") } catch(e:Exception){}
    }
}
