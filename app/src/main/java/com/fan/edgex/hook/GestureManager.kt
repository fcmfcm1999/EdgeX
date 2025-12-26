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
import com.fan.edgex.overlay.DrawerManager
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
        val widthPx = if (metrics != null) (40 * metrics.density).toInt() else 120
        
        val view = GestureView(context!!, gravity)
        val params = WindowManager.LayoutParams(
            widthPx, // Usage of calculated DP width (approx 80-100px) instead of 30px
            WindowManager.LayoutParams.MATCH_PARENT, // Revert to full height for reliability
            2027, // TYPE_MAGNIFICATION_OVERLAY: Often allows full gesture exclusion and is visible.
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
                val keys = listOf(
                    "gestures_enabled",
                    "zone_enabled_left_mid", "zone_enabled_left_bottom",
                    "zone_enabled_right_top", "zone_enabled_right_mid", "zone_enabled_right_bottom",
                    "left_mid_swipe_left", "left_mid_swipe_right", "left_mid_swipe_up", "left_mid_swipe_down",
                    "left_bottom_swipe_left", "left_bottom_swipe_right", "left_bottom_swipe_up", "left_bottom_swipe_down",
                    "right_top_swipe_left", "right_top_swipe_right", "right_top_swipe_up", "right_top_swipe_down",
                    "right_mid_swipe_left", "right_mid_swipe_right", "right_mid_swipe_up", "right_mid_swipe_down",
                    "right_bottom_swipe_left", "right_bottom_swipe_right", "right_bottom_swipe_up", "right_bottom_swipe_down"
                )
                
                for (key in keys) {
                     val type = if (key.startsWith("zone_enabled") || key == "gestures_enabled") "boolean" else "string"
                     configCache[key] = queryConfigProvider(key, type)
                }
                lastConfigLoad = System.currentTimeMillis()
                
                // Refresh Views on Main Thread after Cache Update
                Handler(Looper.getMainLooper()).post {
                    views.forEach { 
                        it.updateWindowRegion()
                        it.invalidate() 
                    }
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
            // No-op here, layout update handled via ContentObserver -> reloadConfig -> main thread handler
        }

        fun updateWindowRegion() {
            val h = context.resources.displayMetrics.heightPixels
            var minTop = h // Start from bottom
            var maxBottom = 0 // Start from top
            
            var hasEnabledZone = false

            if (edgeGravity == Gravity.LEFT) {
                if (isZoneEnabled("left_mid")) {
                    minTop = kotlin.math.min(minTop, 0)
                    maxBottom = kotlin.math.max(maxBottom, (h * 0.6f).toInt())
                    hasEnabledZone = true
                }
                if (isZoneEnabled("left_bottom")) {
                    minTop = kotlin.math.min(minTop, (h * 0.6f).toInt())
                    maxBottom = kotlin.math.max(maxBottom, h)
                    hasEnabledZone = true
                }
            } else {
                 if (isZoneEnabled("right_top")) {
                    minTop = kotlin.math.min(minTop, 0)
                    maxBottom = kotlin.math.max(maxBottom, (h * 0.33f).toInt())
                    hasEnabledZone = true
                }
                if (isZoneEnabled("right_mid")) {
                    minTop = kotlin.math.min(minTop, (h * 0.33f).toInt())
                    maxBottom = kotlin.math.max(maxBottom, (h * 0.66f).toInt())
                    hasEnabledZone = true
                }
                if (isZoneEnabled("right_bottom")) {
                    minTop = kotlin.math.min(minTop, (h * 0.66f).toInt())
                    maxBottom = kotlin.math.max(maxBottom, h)
                    hasEnabledZone = true
                }
            }
            
            // Update Window Layout Params
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val params = layoutParams as WindowManager.LayoutParams
                
                if (!hasEnabledZone) {
                    // Hide completely
                    params.height = 0
                } else {
                    // Set precise coverage
                    params.gravity = Gravity.TOP or edgeGravity
                    params.y = minTop
                    params.height = maxBottom - minTop
                }
                
                wm.updateViewLayout(this, params)
                
                // Also update exclusion rects based on the NEW local coordinates
                // Note: exclusion rects are usually relative to the window content
                val exclusionRects = mutableListOf<Rect>()
                exclusionRects.add(Rect(0, 0, width, params.height)) 
                systemGestureExclusionRects = exclusionRects
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                            // User requested to remove Click features to fix obstruction.
                            // So we trigger Pass-Through immediately.
                            injectInputEvent(endX, endY)
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
                            injectInputEvent(endX, endY)
                        }
                    }
                    return true
                }
            }
            return false
        }
        
        private fun injectInputEvent(x: Float, y: Float) {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            
            // 1. Temporarily disable touch to let event pass through THIS window
            handler.post { setTouchable(false) }
            
            // 2. Wait for WindowManager to update InputWindows (approx 16-32ms frames, safe 50ms)
            handler.postDelayed({
                // 3. Inject Event (Async)
                Thread {
                    var success = false
                    try {
                        val method = Class.forName("android.hardware.input.InputManager")
                            .getMethod("getInstance")
                        val instance = method.invoke(null)
                        val injectMethod = instance.javaClass.getMethod("injectInputEvent", android.view.InputEvent::class.java, Int::class.javaPrimitiveType)

                        val downTime = android.os.SystemClock.uptimeMillis()
                        
                        // DOWN
                        val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
                        downEvent.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                        injectMethod.invoke(instance, downEvent, 0)
                        downEvent.recycle()
                        
                        // Delay for valid tap duration (10-20ms)
                        Thread.sleep(15)

                        // UP
                        val eventTime = android.os.SystemClock.uptimeMillis()
                        val upEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, x, y, 0)
                        upEvent.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                        injectMethod.invoke(instance, upEvent, 0)
                        upEvent.recycle()
                        
                        success = true
                    } catch (e: Exception) {
                        de.robv.android.xposed.XposedBridge.log("EdgeX: Reflection Injection Failed: ${e.message}")
                    }
                    
                    if (!success) {
                        // Fallback to Shell
                        try {
                            Runtime.getRuntime().exec("input tap ${x.toInt()} ${y.toInt()}")
                        } catch (e: Exception) {
                             de.robv.android.xposed.XposedBridge.log("EdgeX: Shell Injection Failed: ${e.message}")
                        }
                    }

                    // 4. Restore Touch (Main Thread)
                    handler.post { setTouchable(true) }
                }.start()
            }, 100) // Increased delay to 100ms for WM stability
        }
        
        private fun setTouchable(touchable: Boolean) {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val params = layoutParams as WindowManager.LayoutParams
                if (touchable) {
                    // remove FLAG_NOT_TOUCHABLE -> Become Touchable
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                } else {
                    // add FLAG_NOT_TOUCHABLE -> Become Untouchable (Transparent to events)
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                }
                wm.updateViewLayout(this, params)
            } catch (e: Exception) {
                e.printStackTrace()
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
                y < h * 0.33 -> if (edgeGravity == Gravity.LEFT) "left_mid" else "right_top" 
                else -> {
                    if (edgeGravity == Gravity.LEFT) {
                        if (y < h * 0.6) "left_mid" else "left_bottom"
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
        return getConfig("gestures_enabled", "boolean", "true") != "false"
    }

    
    private fun getConfig(key: String, type: String = "string", defValue: String = ""): String {
         reloadConfigAsync()
         
         if (configCache.containsKey(key)) {
             return configCache[key] ?: defValue
         }
         
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
        
        when (action) {
            "back" -> {
                try { Runtime.getRuntime().exec("input keyevent 4") } catch(e:Exception){}
            }
            "home" -> {
                 try { Runtime.getRuntime().exec("input keyevent 3") } catch(e:Exception){}
            }
            "freezer_drawer" -> {
                DrawerManager.showDrawer(context!!)
            }
            "screenshot" -> {
                try { Runtime.getRuntime().exec("input keyevent 120") } catch(e:Exception){}
            }
            "refreeze" -> {
                performRefreeze(ctx)
            }
            else -> Toast.makeText(ctx, "未知动作: $action", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performRefreeze(context: Context) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                // 1. Get List from Config (Persistent)
                var listStr = queryConfigProvider("freezer_app_list", "string")
                de.robv.android.xposed.XposedBridge.log("EdgeX: Refreeze - freezer_app_list query returned: '$listStr'")
                
                // 2. Fallback to Runtime History if persistent list is empty
                if (listStr.isEmpty()) {
                    val historySet = DrawerManager.frozenAppsHistory
                    if (historySet.isEmpty()) {
                        handler.post { Toast.makeText(context, "冰箱列表为空", Toast.LENGTH_SHORT).show() }
                        return@Thread
                    }
                    listStr = historySet.joinToString(",")
                    de.robv.android.xposed.XposedBridge.log("EdgeX: Refreeze - using runtime history: '$listStr'")
                }
                
                val packages = listStr.split(",")
                val pm = context.packageManager
                var count = 0
                
                for (pkg in packages) {
                    if (pkg.isBlank()) continue
                    try {
                        // Check if installed and enabled
                        val info = pm.getApplicationInfo(pkg, 0) 
                        if (info.enabled) {
                             // Freeze it
                             var success = false
                             // 1. Try API first (SystemUI context)
                             try {
                                 pm.setApplicationEnabledSetting(pkg, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)
                                 success = true
                             } catch (e: Exception) {
                                  // Ignore, try Root
                             }
                             
                             // 2. Fallback to Root
                             if (!success) {
                                 try {
                                    val p = Runtime.getRuntime().exec("su")
                                    val os = java.io.DataOutputStream(p.outputStream)
                                    os.writeBytes("pm disable $pkg\n")
                                    os.writeBytes("exit\n")
                                    os.flush()
                                    if (p.waitFor() == 0) success = true
                                 } catch(e: Exception){
                                     e.printStackTrace()
                                 }
                             }
                             
                             if (success) count++
                        }
                    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                        // App not found, maybe uninstalled
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                if (count > 0) {
                    handler.post { Toast.makeText(context, "已重新冻结 $count 个应用", Toast.LENGTH_SHORT).show() }
                } else {
                    handler.post { Toast.makeText(context, "没有需要冻结的应用", Toast.LENGTH_SHORT).show() }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post { Toast.makeText(context, "冻结出错: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }
}
