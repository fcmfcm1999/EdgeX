package com.fan.edgex.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.hook.ModuleRes

class DrawerWindow(
    private val context: Context,
    private val resolveConfig: (String) -> String,
    private val onDismiss: (() -> Unit)? = null,
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: FrameLayout? = null
    // Left boundary of drawer content area.
    // Taps to the left of this X coordinate dismiss the drawer.
    private var contentLeftBound = 0

    fun show() {
        if (rootView != null) return // Already showing

        val useArcDrawer = resolveConfig(AppConfig.FREEZER_ARC_DRAWER).toBoolean()

        val displayMetrics = context.resources.displayMetrics
        
        rootView = object : FrameLayout(context) {
            override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
                if (ev.action == android.view.MotionEvent.ACTION_DOWN) {
                    // If tap is LEFT of the drawer content, dismiss immediately
                    if (ev.x < contentLeftBound) {
                        dismiss()
                        return true
                    }
                }
                return super.dispatchTouchEvent(ev)
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismiss()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            // Always transparent - drawer content has its own background
            setBackgroundColor(Color.TRANSPARENT)

            // Focusable for key events (back key).
            // We set isFocusableInTouchMode = true to prevent Android from 
            // swallowing the first touch event during touch mode transition.
            isFocusable = true
            isFocusableInTouchMode = true
        }

        // --- Content Loading ---
        // Load Apps (Common)
        val pm = context.packageManager
        val displayApps = mutableListOf<android.content.pm.ResolveInfo>()

        try {
            val configuredPackages = linkedSetOf<String>()

            // 0. Load persistent freezer list from hook-side memory cache.
            val listStr = resolveConfig(AppConfig.FREEZER_APP_LIST)
            if (listStr.isNotEmpty()) {
                configuredPackages.addAll(
                    listStr.split(",")
                        .map { pkg -> pkg.trim() }
                        .filter { pkg -> pkg.isNotEmpty() },
                )
            }

            // Find all launcher activities
            val mainIntent = Intent(Intent.ACTION_MAIN, null)
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)

            val allActivities = pm.queryIntentActivities(mainIntent, android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)

            for (resolveInfo in allActivities) {
                val appInfo = resolveInfo.activityInfo.applicationInfo
                if (configuredPackages.contains(appInfo.packageName)) {
                    displayApps.add(resolveInfo)
                }
            }

            // Sort by Name (Stable)
            displayApps.sortWith(Comparator { o1, o2 ->
                val label1 = o1.loadLabel(pm).toString()
                val label2 = o2.loadLabel(pm).toString()
                label1.compareTo(label2, ignoreCase = true)
            })
            
        } catch (e: Exception) {
            de.robv.android.xposed.XposedBridge.log("EdgeX: Failed to load apps: " + e.message)
        }

        // --- View Generation ---
        if (useArcDrawer) {
            setupArcLayout(displayApps, pm, displayMetrics)
        } else {
            setupLegacyLayout(displayApps, pm)
        }

        // Set the content left boundary for dismiss-on-tap detection
        val drawerWidth = if (useArcDrawer) {
            // Arc items occupy roughly the right 200dp
            (200 * displayMetrics.density).toInt()
        } else {
            displayMetrics.widthPixels / 2 + (7 * displayMetrics.density).toInt()
        }
        contentLeftBound = displayMetrics.widthPixels - drawerWidth

        // --- Window Layout Params ---
        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
            format = PixelFormat.TRANSLUCENT
            // Always full-width so taps on the left dim area are within
            // window bounds and properly trigger dismiss.
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND
            dimAmount = 0.5f
        }
        
        try {
            windowManager.addView(rootView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupArcLayout(displayApps: List<android.content.pm.ResolveInfo>, pm: android.content.pm.PackageManager, displayMetrics: android.util.DisplayMetrics) {
        val drawerContent = FrameLayout(context).apply {
            // Transparent background - no white panel
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, // Take half width from parent
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.RIGHT // Align to right side
            }
            
            // Prevent clicks on drawer passing through to dismiss
            isClickable = false // Allow clicks to pass through empty areas

            // Arc Layout for Apps
            val arcLayout = ArcLayoutView(context).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
                onEmptySpaceClick = {
                    dismiss()
                }
            }
            
            if (displayApps.isEmpty()) {
                val emptyMsg = TextView(context).apply {
                    text = ModuleRes.getString(R.string.label_empty_drawer)
                    textSize = 16f
                    setPadding(40, 40, 40, 40)
                    setTextColor(Color.DKGRAY)
                    gravity = Gravity.CENTER
                }
                addView(emptyMsg)
            } else {
                val shownPackages = mutableSetOf<String>()
                
                // Reusable Grayscale Filter
                val matrix = android.graphics.ColorMatrix()
                matrix.setSaturation(0f)
                val grayscaleFilter = android.graphics.ColorMatrixColorFilter(matrix)

                for (resolveInfo in displayApps) {
                    val appInfo = resolveInfo.activityInfo.applicationInfo
                    val packageName = appInfo.packageName
                    val isFrozen = !appInfo.enabled
                    
                    if (shownPackages.contains(packageName)) continue
                    shownPackages.add(packageName)

                    try {
                        val appItem = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding(12, 12, 12, 12) // Smaller padding
                            
                            // Add opaque rounded background for better visibility
                            background = android.graphics.drawable.GradientDrawable().apply {
                                setColor(Color.parseColor("#F0F0F0")) // Opaque light gray-white
                                cornerRadius = 24f
                            }
                            
                            val icon = ImageView(context).apply {
                                setImageDrawable(resolveInfo.loadIcon(pm)) 
                                layoutParams = LinearLayout.LayoutParams(55, 55) // Smaller icons
                                
                                if (isFrozen) {
                                    colorFilter = grayscaleFilter
                                    alpha = 0.5f
                                } else {
                                    colorFilter = null
                                    alpha = 1.0f
                                }
                            }
                            val label = TextView(context).apply {
                                text = resolveInfo.loadLabel(pm)
                                textSize = 9f // Smaller text
                                gravity = Gravity.CENTER
                                maxLines = 1
                                setTextColor(if (isFrozen) Color.GRAY else Color.DKGRAY)
                            }
                            
                            addView(icon)
                            addView(label)
                            
                            setOnClickListener {
                                if (isFrozen) {
                                    threadUnfreeze(packageName, resolveInfo.loadLabel(pm).toString(), pm)
                                } else {
                                    // === STANDARD LAUNCH ===
                                    launchApp(context, pm, packageName)
                                }
                            }
                        }
                        arcLayout.addItem(appItem)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            addView(arcLayout)
        }

        rootView?.addView(drawerContent)
    }

    private fun setupLegacyLayout(displayApps: List<android.content.pm.ResolveInfo>, pm: android.content.pm.PackageManager) {
        val displayMetrics = context.resources.displayMetrics
        val drawerWidth = displayMetrics.widthPixels / 2 + (7 * displayMetrics.density).toInt()

        // Right-aligned container to hold the legacy drawer content
        val drawerContainer = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                drawerWidth,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.RIGHT
            }
            setBackgroundColor(Color.parseColor("#99000000"))
            // Prevent taps on drawer content from propagating to rootView's dismiss
            isClickable = true
        }

        val scrollView = ScrollView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        val grid = GridLayout(context).apply {
            columnCount = 4
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        if (displayApps.isEmpty()) {
            val emptyMsg = TextView(context).apply {
                text = ModuleRes.getString(R.string.label_empty_drawer)
                textSize = 16f
                setPadding(40, 40, 40, 40)
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
            }
            scrollView.addView(emptyMsg)
        } else {
            val shownPackages = mutableSetOf<String>()
            val matrix = android.graphics.ColorMatrix()
            matrix.setSaturation(0f)
            val grayscaleFilter = android.graphics.ColorMatrixColorFilter(matrix)

            for (resolveInfo in displayApps) {
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val packageName = appInfo.packageName
                if (shownPackages.contains(packageName)) continue
                shownPackages.add(packageName)

                val isFrozen = !appInfo.enabled

                val appItem = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(20, 20, 20, 20)
                    
                    val icon = ImageView(context).apply {
                        setImageDrawable(resolveInfo.loadIcon(pm))
                        layoutParams = LinearLayout.LayoutParams(100, 100)
                         if (isFrozen) {
                            colorFilter = grayscaleFilter
                            alpha = 0.5f
                        } else {
                            colorFilter = null
                            alpha = 1.0f
                        }
                    }
                    val label = TextView(context).apply {
                        text = resolveInfo.loadLabel(pm)
                        textSize = 12f
                        gravity = Gravity.CENTER
                        maxLines = 1
                        setTextColor(if (isFrozen) Color.GRAY else Color.BLACK)
                    }
                    
                    addView(icon)
                    addView(label)
                    
                    setOnClickListener {
                        if (isFrozen) {
                             threadUnfreeze(packageName, resolveInfo.loadLabel(pm).toString(), pm)
                        } else {
                            launchApp(context, pm, packageName)
                        }
                    }
                }
                grid.addView(appItem)
            }
        }
        
        scrollView.addView(grid)
        drawerContainer.addView(scrollView)
        rootView?.addView(drawerContainer)
    }
    
    // Extract unfreeze logic to avoid duplication
    private fun threadUnfreeze(packageName: String, label: String, pm: android.content.pm.PackageManager) {
        de.robv.android.xposed.XposedBridge.log("EdgeX: DrawerWindow.threadUnfreeze - packageName: $packageName")
        Thread {
            var success = false
            try {
                pm.setApplicationEnabledSetting(
                    packageName, 
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 
                    0
                )
                success = true
                de.robv.android.xposed.XposedBridge.log("EdgeX: PM API unfreeze SUCCESS for $packageName")
            } catch (e: Exception) {
                de.robv.android.xposed.XposedBridge.log("EdgeX: PM API unfreeze FAILED for $packageName: ${e.message}")
                // DO NOT fallback to su in SystemUI process - it causes system crashes
            }

            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            if (success) {
                handler.post {
                    // Silent on success - user will see the app launch
                    launchApp(context, pm, packageName)
                }
            } else {
               handler.post {
                    android.widget.Toast.makeText(
                        context, 
                        ModuleRes.getString(R.string.toast_unfreeze_failed_drawer),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
               }
            }
        }.start() 
    }

    private fun launchApp(context: Context, pm: android.content.pm.PackageManager, packageName: String) {
         // Retry launching a few times to allow system to update state
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var task: Runnable? = null
        var retries = 0
        
        task = Runnable {
            try {
                val intent = pm.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    context.startActivity(intent)
                    dismiss()
                } else {
                    retries++
                    if (retries < 10) { 
                        handler.postDelayed(task!!, 200)
                    } else {
                        android.widget.Toast.makeText(
                            context, 
                            ModuleRes.getString(R.string.toast_launch_timeout),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                 android.widget.Toast.makeText(
                     context, 
                     ModuleRes.getString(R.string.toast_launch_error, e.message),
                     android.widget.Toast.LENGTH_SHORT
                 ).show()
            }
        }
        task.run()
    }

    fun isShowing() = rootView != null

    /**
     * Force-dismiss the drawer from external callers (e.g. SCREEN_OFF handler).
     */
    fun forceDismiss() {
        dismiss()
    }

    private fun dismiss() {
        if (rootView != null) {
            try {
                windowManager.removeView(rootView)
            } catch (e: Exception) {
                // Ignore if already removed
            }
            rootView = null
            onDismiss?.invoke()
        }
    }
}
