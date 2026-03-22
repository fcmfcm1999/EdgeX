package com.fan.edgex.hook

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import com.fan.edgex.overlay.DrawerManager
import de.robv.android.xposed.XposedBridge
import kotlin.math.abs
import kotlin.math.sqrt

@SuppressLint("StaticFieldLeak")
object GestureManager {

    private const val TAG = "EdgeX"

    // system_server context (from filterInputEvent hook, for config queries)
    private var systemContext: Context? = null
    // SystemUI context (for overlay windows like DrawerWindow)
    private var systemUIContext: Context? = null
    private var windowManager: WindowManager? = null

    // Config URI
    private val CONFIG_URI = Uri.parse("content://com.fan.edgex.provider/config")

    // Broadcast action for cross-process communication (system_server -> SystemUI)
    private const val ACTION_PERFORM = "com.fan.edgex.ACTION_PERFORM"
    private const val EXTRA_ACTION = "action"

    // Gesture detection variables (used in system_server process)
    private var mDownTime = 0L
    private var mDownX = 0f
    private var mDownY = 0f
    private var mIsLongPress = false
    private var mIsSwiping = false
    private var mIsInSection = false
    private var mActiveZone: String? = null

    private val TOUCH_SLOP = 24
    private val SWIPE_THRESHOLD = 50
    private val LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout()
    private val TAP_TIMEOUT = ViewConfiguration.getTapTimeout()

    private var mHandler: Handler? = null

    // Debug overlay views (in SystemUI process)
    private val debugViews = mutableListOf<DebugOverlayView>()

    /**
     * Called from system_server (filterInputEvent hook).
     * Handles MotionEvent at the input pipeline level.
     * Returns true if the event should be consumed (blocked from reaching apps).
     */
    fun handleMotionEvent(event: MotionEvent, context: Context): Boolean {
        // Lazily init system context
        if (systemContext == null) {
            systemContext = context
            // Initial config load
            reloadConfigAsync()
        }

        if (!isGesturesEnabled()) return false

        val action = event.actionMasked
        val x = event.rawX
        val y = event.rawY

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val dm = context.resources.displayMetrics
                val screenWidth = dm.widthPixels
                val screenHeight = dm.heightPixels

                // Edge detection width: ~5% of screen width
                val edgeThreshold = screenWidth * 0.05f
                val isInLeftEdge = x < edgeThreshold
                val isInRightEdge = x > (screenWidth - edgeThreshold)

                if (!isInLeftEdge && !isInRightEdge) {
                    mIsInSection = false
                    return false
                }

                // Determine which zone this touch is in
                val side = if (isInLeftEdge) "left" else "right"
                val verticalZone = when {
                    y < screenHeight * 0.33f -> "top"
                    y < screenHeight * 0.66f -> "mid"
                    else -> "bottom"
                }
                val zone = "${side}_${verticalZone}"

                // Check if this zone is enabled
                if (!isZoneEnabled(zone)) {
                    mIsInSection = false
                    return false
                }

                mIsInSection = true
                mActiveZone = zone
                mDownTime = event.downTime
                mDownX = x
                mDownY = y
                mIsLongPress = false
                mIsSwiping = false
                XposedBridge.log("$TAG: Touch intercepted in zone [$zone] at ($x, $y)")
                return true // Consume DOWN event
            }

            MotionEvent.ACTION_MOVE -> {
                if (!mIsInSection) return false

                if (!mIsSwiping) {
                    val dx = x - mDownX
                    val dy = y - mDownY
                    if (sqrt(dx * dx + dy * dy) > TOUCH_SLOP) {
                        mIsSwiping = true
                    }
                }
                if (!mIsLongPress && (event.eventTime - mDownTime) > LONG_PRESS_TIMEOUT) {
                    mIsLongPress = true
                }
                return true // Consume MOVE event
            }

            MotionEvent.ACTION_UP -> {
                if (!mIsInSection) return false

                val zone = mActiveZone
                var gestureType: String? = null

                if (mIsSwiping) {
                    val dx = x - mDownX
                    val dy = y - mDownY
                    if (abs(dx) > abs(dy)) {
                        // Horizontal swipe
                        if (abs(dx) > SWIPE_THRESHOLD) {
                            gestureType = if (dx < 0) "swipe_left" else "swipe_right"
                        }
                    } else {
                        // Vertical swipe
                        if (abs(dy) > SWIPE_THRESHOLD) {
                            gestureType = if (dy < 0) "swipe_up" else "swipe_down"
                        }
                    }
                } else if (mIsLongPress) {
                    gestureType = "long_press"
                } else {
                    // Tap - not consumed, let it pass through
                    XposedBridge.log("$TAG: Tap detected in zone [$zone], passing through")
                    mIsInSection = false
                    mActiveZone = null
                    return false
                }

                if (gestureType != null && zone != null) {
                    XposedBridge.log("$TAG: Gesture [$gestureType] in zone [$zone]")
                    triggerAction(zone, gestureType, context)
                } else {
                    XposedBridge.log("$TAG: Swipe detected but below threshold. dx=${x - mDownX}, dy=${y - mDownY}")
                }

                mIsInSection = false
                mActiveZone = null
                return true // Consume UP event
            }

            MotionEvent.ACTION_CANCEL -> {
                mIsInSection = false
                mActiveZone = null
                return false
            }
        }

        return mIsInSection
    }

    /**
     * Called from SystemUI process to initialize overlay windows and broadcast receiver.
     * Used for debug visualization and DrawerWindow.
     */
    fun initSystemUI(ctx: Context) {
        if (systemUIContext != null) return // Already initialized
        systemUIContext = ctx
        windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Register BroadcastReceiver to receive action commands from system_server
        registerActionReceiver(ctx)

        // Add debug overlay views (left + right edge)
        addDebugOverlayView(Gravity.LEFT)
        addDebugOverlayView(Gravity.RIGHT)

        // Load initial config
        reloadConfigAsyncForUI()

        XposedBridge.log("$TAG: SystemUI overlay initialized with broadcast receiver")
    }

    /**
     * Register a BroadcastReceiver in SystemUI to handle action commands
     * sent from system_server via filterInputEvent hook.
     */
    private fun registerActionReceiver(ctx: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.getStringExtra(EXTRA_ACTION) ?: return
                XposedBridge.log("$TAG: SystemUI received action broadcast: $action")
                performActionInSystemUI(action, context)
            }
        }

        try {
            val filter = IntentFilter(ACTION_PERFORM)
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            XposedBridge.log("$TAG: Action BroadcastReceiver registered in SystemUI")
        } catch (e: Exception) {
            // Fallback for older Android versions without RECEIVER_NOT_EXPORTED
            try {
                val filter = IntentFilter(ACTION_PERFORM)
                ctx.registerReceiver(receiver, filter)
                XposedBridge.log("$TAG: Action BroadcastReceiver registered (legacy) in SystemUI")
            } catch (e2: Exception) {
                XposedBridge.log("$TAG: Failed to register BroadcastReceiver: ${e2.message}")
            }
        }
    }

    // ======== Debug Overlay View (SystemUI process, visual only) ========

    private fun addDebugOverlayView(gravity: Int) {
        val ctx = systemUIContext ?: return
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = ctx.resources.displayMetrics
        val widthPx = (12 * metrics.density).toInt()

        val view = DebugOverlayView(ctx, gravity)
        val params = WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.MATCH_PARENT,
            2027, // TYPE_MAGNIFICATION_OVERLAY
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or  // NOT touchable
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = gravity

        wm.addView(view, params)
        debugViews.add(view)
        XposedBridge.log("$TAG: Added DebugOverlayView with gravity $gravity width $widthPx")
    }

    /**
     * Debug-only overlay view. Not touchable - purely for visual feedback.
     */
    class DebugOverlayView(context: Context, val edgeGravity: Int) : View(context) {
        private var windowOffsetY = 0
        private val handler = Handler(Looper.getMainLooper())
        private val paint = android.graphics.Paint()

        init {
            paint.color = 0x00000000
            paint.style = android.graphics.Paint.Style.FILL

            setWillNotDraw(false)
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            val observer = object : android.database.ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    lastConfigLoad = 0
                    reloadConfigAsyncForUI()
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
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

            if (!isGesturesEnabled()) return

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = android.util.DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(metrics)
            val h = metrics.heightPixels.toFloat()
            val w = width.toFloat()

            val offsetY = -windowOffsetY.toFloat()

            if (edgeGravity == Gravity.LEFT) {
                if (isZoneEnabled("left_top"))
                    canvas.drawRect(0f, 0f + offsetY, w, h * 0.33f + offsetY, paint)
                if (isZoneEnabled("left_mid"))
                    canvas.drawRect(0f, h * 0.33f + offsetY, w, h * 0.66f + offsetY, paint)
                if (isZoneEnabled("left_bottom"))
                    canvas.drawRect(0f, h * 0.66f + offsetY, w, h + offsetY, paint)
            } else {
                if (isZoneEnabled("right_top"))
                    canvas.drawRect(0f, 0f + offsetY, w, h * 0.33f + offsetY, paint)
                if (isZoneEnabled("right_mid"))
                    canvas.drawRect(0f, h * 0.33f + offsetY, w, h * 0.66f + offsetY, paint)
                if (isZoneEnabled("right_bottom"))
                    canvas.drawRect(0f, h * 0.66f + offsetY, w, h + offsetY, paint)
            }
        }

        fun updateWindowRegion() {
            val ctx = context ?: return
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = android.util.DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(metrics)
            val h = metrics.heightPixels

            var minTop = h
            var maxBottom = 0
            var hasEnabledZone = false

            if (edgeGravity == Gravity.LEFT) {
                if (isZoneEnabled("left_top")) {
                    minTop = kotlin.math.min(minTop, 0)
                    maxBottom = kotlin.math.max(maxBottom, (h * 0.33f).toInt())
                    hasEnabledZone = true
                }
                if (isZoneEnabled("left_mid")) {
                    minTop = kotlin.math.min(minTop, (h * 0.33f).toInt())
                    maxBottom = kotlin.math.max(maxBottom, (h * 0.66f).toInt())
                    hasEnabledZone = true
                }
                if (isZoneEnabled("left_bottom")) {
                    minTop = kotlin.math.min(minTop, (h * 0.66f).toInt())
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

            try {
                val params = layoutParams as WindowManager.LayoutParams
                if (!hasEnabledZone) {
                    params.height = 0
                    visibility = GONE
                } else {
                    visibility = VISIBLE
                    params.gravity = Gravity.TOP or edgeGravity
                    params.y = minTop
                    params.height = maxBottom - minTop
                }

                windowOffsetY = if (hasEnabledZone) minTop else 0
                wm.updateViewLayout(this, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun updateDebugColor(color: Int) {
            paint.color = color
        }
    }

    // ======== Configuration ========

    private var configCache = mutableMapOf<String, String>()
    private var lastConfigLoad = 0L
    private val CONFIG_CACHE_TTL = 2000L

    private fun reloadConfigAsync() {
        if (System.currentTimeMillis() - lastConfigLoad < CONFIG_CACHE_TTL) return

        Thread {
            try {
                loadConfigKeys()
                lastConfigLoad = System.currentTimeMillis()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun reloadConfigAsyncForUI() {
        if (System.currentTimeMillis() - lastConfigLoad < CONFIG_CACHE_TTL) return

        Thread {
            try {
                loadConfigKeys()
                lastConfigLoad = System.currentTimeMillis()

                Handler(Looper.getMainLooper()).post {
                    val debug = getConfig("debug_matrix_enabled", "boolean") == "true"
                    val color = if (debug) 0x3300FF00.toInt() else 0x00000000

                    debugViews.forEach {
                        it.updateDebugColor(color)
                        it.updateWindowRegion()
                        it.destroyDrawingCache()
                        it.invalidate()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun loadConfigKeys() {
        val keys = listOf(
            "gestures_enabled", "debug_matrix_enabled",
            "zone_enabled_left_top", "zone_enabled_left_mid", "zone_enabled_left_bottom",
            "zone_enabled_right_top", "zone_enabled_right_mid", "zone_enabled_right_bottom",
            "left_top_swipe_left", "left_top_swipe_right", "left_top_swipe_up", "left_top_swipe_down",
            "left_mid_swipe_left", "left_mid_swipe_right", "left_mid_swipe_up", "left_mid_swipe_down",
            "left_bottom_swipe_left", "left_bottom_swipe_right", "left_bottom_swipe_up", "left_bottom_swipe_down",
            "right_top_swipe_left", "right_top_swipe_right", "right_top_swipe_up", "right_top_swipe_down",
            "right_mid_swipe_left", "right_mid_swipe_right", "right_mid_swipe_up", "right_mid_swipe_down",
            "right_bottom_swipe_left", "right_bottom_swipe_right", "right_bottom_swipe_up", "right_bottom_swipe_down"
        )

        for (key in keys) {
            val type = if (key.startsWith("zone_enabled") || key == "gestures_enabled" || key == "debug_matrix_enabled") "boolean" else "string"
            configCache[key] = queryConfigProvider(key, type)
        }
    }

    private fun isGesturesEnabled(): Boolean {
        return getConfig("gestures_enabled", "boolean", "true") != "false"
    }

    private fun isZoneEnabled(zone: String): Boolean {
        val value = configCache["zone_enabled_$zone"]
        return value == "true"
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
        val ctx = systemContext ?: systemUIContext
        try {
            val cursor: Cursor? = ctx?.contentResolver?.query(
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

    // ======== Action Handling ========

    private fun triggerAction(zone: String, gestureType: String, context: Context) {
        val configKey = "${zone}_${gestureType}"
        val action = getConfig(configKey)

        XposedBridge.log("$TAG: triggerAction configKey=$configKey -> action='$action'")

        if (action.isEmpty() || action == "default" || action == "none") return

        performAction(action, context)
    }

    /**
     * Called from system_server process. For shell-based actions, execute directly.
     * For UI actions (drawer, toast, etc.), send broadcast to SystemUI.
     */
    private fun performAction(action: String, context: Context) {
        XposedBridge.log("$TAG: performAction: $action")

        when {
            // Shell-based actions can run directly from system_server
            action == "back" -> {
                try { Runtime.getRuntime().exec("input keyevent 4") } catch (e: Exception) {}
            }
            action == "home" -> {
                try { Runtime.getRuntime().exec("input keyevent 3") } catch (e: Exception) {}
            }
            action == "screenshot" -> {
                try { Runtime.getRuntime().exec("input keyevent 120") } catch (e: Exception) {}
            }
            else -> {
                // UI actions need SystemUI context -> send broadcast
                sendActionToSystemUI(action, context)
            }
        }
    }

    /**
     * Send action command to SystemUI process via broadcast.
     */
    private fun sendActionToSystemUI(action: String, context: Context) {
        try {
            val intent = Intent(ACTION_PERFORM)
            intent.setPackage("com.android.systemui")
            intent.putExtra(EXTRA_ACTION, action)
            context.sendBroadcast(intent)
            XposedBridge.log("$TAG: Sent action broadcast to SystemUI: $action")
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Failed to send broadcast: ${e.message}")
        }
    }

    /**
     * Execute action within SystemUI process (called by BroadcastReceiver).
     */
    private fun performActionInSystemUI(action: String, context: Context) {
        val ctx = systemUIContext ?: context

        when {
            action.startsWith("app_shortcut:") -> {
                Handler(Looper.getMainLooper()).post {
                    launchShortcut(ctx, action)
                }
            }
            action == "freezer_drawer" -> {
                Handler(Looper.getMainLooper()).post {
                    DrawerManager.showDrawer(ctx)
                }
            }
            action == "refreeze" -> {
                performRefreeze(ctx)
            }
            else -> {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(ctx, "未知动作: $action", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showToast(context: Context, text: String) {
        if (mHandler == null) mHandler = Handler(Looper.getMainLooper())
        mHandler?.post {
            try {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {}
        }
    }

    private fun launchShortcut(context: Context, action: String) {
        try {
            val parts = action.split(":")
            if (parts.size != 3) {
                showToast(context, "快捷方式格式错误")
                return
            }

            val packageName = parts[1]
            val shortcutId = parts[2]

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE)
                        as android.content.pm.LauncherApps

                try {
                    launcherApps.startShortcut(
                        packageName, shortcutId, null, null,
                        android.os.Process.myUserHandle()
                    )
                } catch (e: Exception) {
                    XposedBridge.log("$TAG: Failed to launch shortcut: ${e.message}")
                    try {
                        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                        if (intent != null) {
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } else {
                            showToast(context, "无法启动快捷方式")
                        }
                    } catch (e2: Exception) {
                        showToast(context, "启动失败")
                    }
                }
            } else {
                showToast(context, "需要 Android 7.1 及以上版本")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(context, "快捷方式启动失败: ${e.message}")
        }
    }

    private fun performRefreeze(context: Context) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                var listStr = queryConfigProvider("freezer_app_list", "string")
                XposedBridge.log("$TAG: Refreeze - freezer_app_list query returned: '$listStr'")

                if (listStr.isEmpty()) {
                    val historySet = DrawerManager.frozenAppsHistory
                    if (historySet.isEmpty()) {
                        handler.post { Toast.makeText(context, "冰箱列表为空", Toast.LENGTH_SHORT).show() }
                        return@Thread
                    }
                    listStr = historySet.joinToString(",")
                    XposedBridge.log("$TAG: Refreeze - using runtime history: '$listStr'")
                }

                val packages = listStr.split(",")
                val pm = context.packageManager
                var count = 0

                for (pkg in packages) {
                    if (pkg.isBlank()) continue
                    try {
                        val info = pm.getApplicationInfo(pkg, 0)
                        if (info.enabled) {
                            var success = false
                            try {
                                pm.setApplicationEnabledSetting(
                                    pkg,
                                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                    0
                                )
                                success = true
                            } catch (e: Exception) {}

                            if (!success) {
                                try {
                                    val p = Runtime.getRuntime().exec("su")
                                    val os = java.io.DataOutputStream(p.outputStream)
                                    os.writeBytes("pm disable $pkg\n")
                                    os.writeBytes("exit\n")
                                    os.flush()
                                    if (p.waitFor() == 0) success = true
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            if (success) count++
                        }
                    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                        // App not found
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
