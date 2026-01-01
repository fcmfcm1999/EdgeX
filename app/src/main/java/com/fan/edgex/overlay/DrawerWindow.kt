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
import java.io.DataOutputStream

class DrawerWindow(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: FrameLayout? = null

    fun show() {
        if (rootView != null) return // Already showing

        var useArcDrawer = false
        try {
            val cursor = context.contentResolver.query(
                android.net.Uri.parse("content://com.fan.edgex.provider/config"),
                null,
                "freezer_arc_drawer_enabled",
                arrayOf("false"),
                "boolean"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    useArcDrawer = it.getString(0).toBoolean()
                }
            }
        } catch (e: Exception) {
            de.robv.android.xposed.XposedBridge.log("EdgeX: Failed to read arc drawer config: ${e.message}")
        }

        val displayMetrics = context.resources.displayMetrics
        
        rootView = FrameLayout(context).apply {
            if (useArcDrawer) {
                setBackgroundColor(Color.TRANSPARENT) // Fully transparent background for Arc
            } else {
                setBackgroundColor(Color.parseColor("#99000000")) // Semi-transparent black for Legacy
            }
            
            // Use OnTouchListener for immediate response, fixing "double tap" issues
            setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    dismiss()
                    true
                } else {
                    false
                }
            }
            
            // Handle Back key to close
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismiss()
                    true
                } else {
                    false
                }
            }
            
            // Ensure window takes focus immediately
            post { requestFocus() }
        }

        // --- Content Loading ---
        // Load Apps (Common)
        val pm = context.packageManager
        val displayApps = mutableListOf<android.content.pm.ResolveInfo>()

        try {
            // 0. Load Persistent Freezer List from ConfigProvider
            try {
                val cursor = context.contentResolver.query(
                    android.net.Uri.parse("content://com.fan.edgex.provider/config"), 
                    null, 
                    "freezer_app_list", 
                    null, 
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val listStr = it.getString(0)
                        if (!listStr.isNullOrEmpty()) {
                            // Add to session history
                            val packages = listStr.split(",")
                            DrawerManager.frozenAppsHistory.addAll(packages)
                        }
                    }
                }
            } catch (e: Exception) {
                de.robv.android.xposed.XposedBridge.log("EdgeX: Failed to read config: ${e.message}")
            }

            // Find all launcher activities
            val mainIntent = Intent(Intent.ACTION_MAIN, null)
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            
            // Get everything allowed
            val allActivities = pm.queryIntentActivities(mainIntent, android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)
            
            // 1. Update History with currently frozen apps (Auto-discovery)
            for (resolveInfo in allActivities) {
                val appInfo = resolveInfo.activityInfo.applicationInfo
                 if (!appInfo.enabled) {
                     DrawerManager.frozenAppsHistory.add(appInfo.packageName)
                 }
            }

            // 2. Display Apps from History (Union of Persistent + Auto-detected)
            for (resolveInfo in allActivities) {
                 val appInfo = resolveInfo.activityInfo.applicationInfo
                 // Only show if it is in our Freezer History
                 if (DrawerManager.frozenAppsHistory.contains(appInfo.packageName)) {
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

        // --- Window Layout Params ---
        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            
            if (useArcDrawer) {
                // Arc Drawer: Full width, Transparent BG handles dim
                width = WindowManager.LayoutParams.MATCH_PARENT
                flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND // Dim the entire screen behind the transparent rootView
                dimAmount = 0.5f // Dim amount for the background
            } else {
                // Legacy Drawer: Half width, System Dim
                width = displayMetrics.widthPixels / 2 + (7 * displayMetrics.density).toInt() // Half screen width + 7dp
                flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND
                dimAmount = 0.5f
            }
            
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.RIGHT // Position on right side
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
                    text = "空空如也"
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
                text = "空空如也"
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
                        setTextColor(if (isFrozen) Color.GRAY else Color.BLACK) // Black for legacy
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
        rootView?.addView(scrollView)
    }
    
    // Extract unfreeze logic to avoid duplication
    private fun threadUnfreeze(packageName: String, label: String, pm: android.content.pm.PackageManager) {
        Thread {
            var success = false
            try {
                pm.setApplicationEnabledSetting(
                    packageName, 
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 
                    0
                )
                success = true
            } catch (e: Exception) {
                de.robv.android.xposed.XposedBridge.log("EdgeX: PM API Failed: ${e.message}, trying Root fallback...")
            }

            if (!success) {
                // 2. Fallback to Root Shell
                try {
                    val process = Runtime.getRuntime().exec("su")
                    val os = DataOutputStream(process.outputStream)
                    os.writeBytes("pm enable $packageName\n")
                    os.writeBytes("exit\n")
                    os.flush()
                     if (process.waitFor() == 0) success = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            if (success) {
                handler.post {
                    android.widget.Toast.makeText(context, "正在启动 $label...", android.widget.Toast.LENGTH_SHORT).show()
                    launchApp(context, pm, packageName)
                }
            } else {
               handler.post {
                    android.widget.Toast.makeText(context, "解冻失败", android.widget.Toast.LENGTH_SHORT).show()
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
                        android.widget.Toast.makeText(context, "启动超时，请手动打开", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                 android.widget.Toast.makeText(context, "启动出错: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        task.run()
    }

    private fun dismiss() {
        if (rootView != null) {
            try {
                windowManager.removeView(rootView)
            } catch (e: Exception) {
                // Ignore if already removed
            }
            rootView = null
        }
    }
}
