package com.fan.edgex.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.BuildConfig
import com.fan.edgex.R
import com.fan.edgex.utils.UpdateChecker

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

        // Keys Checkbox
        val cbKeys = findViewById<android.widget.CheckBox>(R.id.checkbox_keys)
        cbKeys.isChecked = prefs.getBoolean("keys_enabled", false)
        cbKeys.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("keys_enabled", isChecked).apply()
            contentResolver.notifyChange(android.net.Uri.parse("content://com.fan.edgex.provider/config"), null)
        }

        findViewById<View>(R.id.item_gestures).setOnClickListener {
            // Only allow entering if allowed? Or always allow? 
            // User: "Only when enabled ... gestures take effect". This usually means background logic.
            // But just in case, I'll allow configuration access always (standard UX).
            startActivity(android.content.Intent(this, GesturesActivity::class.java))
        }

        findViewById<View>(R.id.item_keys).setOnClickListener {
            startActivity(android.content.Intent(this, KeysActivity::class.java))
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

        // 1.5 Arc Drawer Toggle
        val cbArcDrawer = findViewById<android.widget.CheckBox>(R.id.checkbox_arc_drawer)
        cbArcDrawer.isChecked = prefs.getBoolean("freezer_arc_drawer_enabled", false)

        val onArcDrawerToggle = { isChecked: Boolean ->
            prefs.edit().putBoolean("freezer_arc_drawer_enabled", isChecked).apply()
            contentResolver.notifyChange(android.net.Uri.parse("content://com.fan.edgex.provider/config"), null)
            cbArcDrawer.isChecked = isChecked
        }

        cbArcDrawer.setOnCheckedChangeListener { _, isChecked -> onArcDrawerToggle(isChecked) }

        findViewById<View>(R.id.item_arc_drawer).setOnClickListener {
            cbArcDrawer.performClick()
        }

        // 2. Restart SystemUI
        findViewById<View>(R.id.item_restart_sysui).setOnClickListener {
            val result = com.fan.edgex.utils.findProcessAndKill("com.android.systemui")
            result.onSuccess {
                android.widget.Toast.makeText(this, getString(R.string.toast_restarting_sysui), android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure {
                android.widget.Toast.makeText(this, getString(R.string.toast_restart_failed, it.message), android.widget.Toast.LENGTH_LONG).show()
            }
        }

        // About Section (Collapsible)
        val aboutContent = findViewById<View>(R.id.about_content)
        val aboutArrow = findViewById<android.widget.ImageView>(R.id.arrow_about)
        var isAboutExpanded = false
        
        findViewById<View>(R.id.item_about).setOnClickListener {
            isAboutExpanded = !isAboutExpanded
            aboutContent.visibility = if (isAboutExpanded) View.VISIBLE else View.GONE
            aboutArrow.rotation = if (isAboutExpanded) 180f else 0f
        }

        // Project URL Click Handler
        findViewById<View>(R.id.item_project_url).setOnClickListener {
            android.widget.Toast.makeText(this, getString(R.string.toast_star_reminder), android.widget.Toast.LENGTH_LONG).apply {
                setGravity(android.view.Gravity.CENTER, 0, 0)
                show()
            }
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/fcmfcm1999/EdgeX"))
                startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, getString(R.string.toast_cannot_open_browser), android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Version display (from BuildConfig)
        findViewById<TextView>(R.id.text_version_value).text = BuildConfig.VERSION_NAME

        // Manual update check on version row click
        findViewById<View>(R.id.item_version).setOnClickListener {
            UpdateChecker.checkNow(this)
        }

        // Donate
        findViewById<View>(R.id.item_donate).setOnClickListener {
            com.fan.edgex.utils.DonateDialog.show(this)
        }

        // Auto update check on launch
        UpdateChecker.checkOnLaunch(this)
    }
}
