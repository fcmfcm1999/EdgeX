package com.fan.edgex.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Fix for "Header too tall": fitsSystemWindows="true" defaults to applying ALL system insets (Top + Bottom).
        // Since this view is at the top, we only want the Top Inset (Status Bar).
        // The Bottom Inset (Nav Bar) was erroneously adding huge padding to the bottom of the header.
        findViewById<android.view.View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                view.paddingLeft,
                insets.systemWindowInsetTop, // Only apply status bar height
                view.paddingRight,
                view.paddingBottom // Keep original bottom padding (0 or defined)
            )
            // Return insets to let them propagate if needed, but we consumed what we needed
            insets
        }
        
        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        val cbGestures = findViewById<android.widget.CheckBox>(R.id.checkbox_gestures)
        cbGestures.isChecked = prefs.getBoolean("gestures_enabled", true)
        cbGestures.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("gestures_enabled", isChecked).apply()
            // Notify Hook to refresh
            contentResolver.notifyChange(android.net.Uri.parse("content://com.fan.edgex.provider/config"), null)
        }

        findViewById<View>(R.id.item_gestures).setOnClickListener {
            // Only allow entering if allowed? Or always allow? 
            // User: "Only when enabled ... gestures take effect". This usually means background logic.
            // But just in case, I'll allow configuration access always (standard UX).
            startActivity(android.content.Intent(this, GesturesActivity::class.java))
        }

        findViewById<View>(R.id.item_freezer).setOnClickListener {
            startActivity(android.content.Intent(this, FreezerActivity::class.java))
        }

        // Advanced Options (Collapsible)
        val advancedContent = findViewById<View>(R.id.advanced_options_content)
        val advancedArrow = findViewById<android.widget.ImageView>(R.id.arrow_advanced)
        var isAdvancedExpanded = false
        
        findViewById<View>(R.id.item_advanced).setOnClickListener {
            isAdvancedExpanded = !isAdvancedExpanded
            advancedContent.visibility = if (isAdvancedExpanded) View.VISIBLE else View.GONE
            advancedArrow.rotation = if (isAdvancedExpanded) 180f else 0f
        }

        // Advanced Options
        
        // 1. Debug Matrix
        val cbDebug = findViewById<android.widget.CheckBox>(R.id.checkbox_debug_matrix)
        cbDebug.isChecked = prefs.getBoolean("debug_matrix_enabled", false)
        
        val onDebugToggle = { isChecked: Boolean ->
            prefs.edit().putBoolean("debug_matrix_enabled", isChecked).apply()
            contentResolver.notifyChange(android.net.Uri.parse("content://com.fan.edgex.provider/config"), null)
            cbDebug.isChecked = isChecked
        }
        
        cbDebug.setOnCheckedChangeListener { _, isChecked -> onDebugToggle(isChecked) }
        
        findViewById<View>(R.id.item_debug_matrix).setOnClickListener {
            cbDebug.performClick()
        }

        // 2. Restart SystemUI
        findViewById<View>(R.id.item_restart_sysui).setOnClickListener {
            val result = com.fan.edgex.utils.findProcessAndKill("com.android.systemui")
            result.onSuccess {
                android.widget.Toast.makeText(this, "正在重启 SystemUI...", android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure {
                android.widget.Toast.makeText(this, "重启失败: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}
