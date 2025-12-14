package com.fan.edgex.overlay

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import de.robv.android.xposed.XposedBridge

class DrawerWindow(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: FrameLayout? = null

    fun show() {
        if (rootView != null) return // Already showing

        rootView = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#99000000")) // Semi-transparent dim background
            
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

        val drawerContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                600, // Width in pixels - adjust or make dp
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.RIGHT
            }
            
            // Prevent clicks on drawer passing through to dismiss
            isClickable = true
            setOnClickListener { } 

            // Header
            addView(TextView(context).apply {
                text = "Quick Apps"
                textSize = 24f
                setPadding(40, 60, 40, 40)
                setTextColor(Color.BLACK)
            })

            // Scrollable App Grid
            val scrollView = ScrollView(context)
            val grid = GridLayout(context).apply {
                columnCount = 3
                rowCount = GridLayout.UNDEFINED
            }
            
            
            // Load apps from ContentProvider
            val debugText = StringBuilder()
            var selectedApps: Set<String> = emptySet()
            
            try {
                val uri = android.net.Uri.parse("content://com.fan.edgex.provider/config")
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                
                if (cursor != null && cursor.moveToFirst()) {
                    val rawConfig = cursor.getString(0)
                    debugText.append("Provider Query Success!\n")
                    debugText.append("Raw: $rawConfig\n")
                    
                    if (rawConfig.isNotEmpty()) {
                        selectedApps = rawConfig.split(",").toSet()
                    }
                    cursor.close()
                } else {
                    debugText.append("Provider Query Returned Null or Empty Cursor\n")
                }
            } catch (e: Exception) {
                debugText.append("Provider Error: ${e.message}\n")
                de.robv.android.xposed.XposedBridge.log("XposedEdge: Provider query failed: " + e.message)
            }
            
            debugText.append("Apps Loaded: ${selectedApps.size}")
            
             // Debug info removed for production

            val pm = context.packageManager
            
            if (selectedApps.isNullOrEmpty()) {
                // Show a message or fallback
                val emptyMsg = TextView(context).apply {
                    text = "No apps selected.\nOpen Xposed Edge app to Configure."
                    textSize = 16f
                    setPadding(40, 40, 40, 40)
                }
                scrollView.addView(emptyMsg)
            } else {
                for (packageName in selectedApps) {
                    try {
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        
                        val appItem = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding(20, 20, 20, 20)
                            
                            val icon = ImageView(context).apply {
                                setImageDrawable(appInfo.loadIcon(pm))
                                layoutParams = LinearLayout.LayoutParams(100, 100)
                            }
                            val label = TextView(context).apply {
                                text = appInfo.loadLabel(pm)
                                textSize = 12f
                                gravity = Gravity.CENTER
                                maxLines = 1
                                setTextColor(Color.DKGRAY)
                            }
                            
                            addView(icon)
                            addView(label)
                            
                            setOnClickListener {
                                val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(launchIntent)
                                    dismiss()
                                }
                            }
                        }
                        grid.addView(appItem)
                    } catch (e: Exception) {
                        // App might be uninstalled
                    }
                }
                scrollView.addView(grid)
            }
            addView(scrollView)
        }

        rootView?.addView(drawerContent)

        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND
            dimAmount = 0.5f
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.LEFT
        }

        try {
            windowManager.addView(rootView, params)
            
            // Simple slide in animation
            drawerContent.translationX = 600f
            ObjectAnimator.ofFloat(drawerContent, "translationX", 0f).start()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
