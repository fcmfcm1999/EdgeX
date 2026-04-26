package com.fan.edgex.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.net.toUri
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.BuildConfig
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.FreezerBootstrap
import com.fan.edgex.config.broadcastFullConfigSnapshot
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.putConfig
import com.fan.edgex.utils.UpdateChecker

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ThemeManager.applyToActivity(this)
        broadcastFullConfigSnapshot()
        
        // Fix for "Header too tall": fitsSystemWindows="true" defaults to applying ALL system insets (Top + Bottom).
        // Since this view is at the top, we only want the Top Inset (Status Bar).
        // The Bottom Inset (Nav Bar) was erroneously adding huge padding to the bottom of the header.
        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                view.paddingLeft,
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top, // Only apply status bar height
                view.paddingRight,
                view.paddingBottom // Keep original bottom padding (0 or defined)
            )
            // Return insets to let them propagate if needed, but we consumed what we needed
            insets
        }
        
        FreezerBootstrap.ensureMigrated(this)

        val cbGestures = findViewById<android.widget.CheckBox>(R.id.checkbox_gestures)
        cbGestures.isChecked = getConfigBool(AppConfig.GESTURES_ENABLED)
        cbGestures.setOnCheckedChangeListener { _, isChecked -> putConfig(AppConfig.GESTURES_ENABLED, isChecked) }

        val cbKeys = findViewById<android.widget.CheckBox>(R.id.checkbox_keys)
        cbKeys.isChecked = getConfigBool(AppConfig.KEYS_ENABLED)
        cbKeys.setOnCheckedChangeListener { _, isChecked -> putConfig(AppConfig.KEYS_ENABLED, isChecked) }

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

        findViewById<View>(R.id.item_theme).setOnClickListener {
            startActivity(android.content.Intent(this, ThemeActivity::class.java))
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
        cbDebug.isChecked = getConfigBool(AppConfig.DEBUG_MATRIX)
        cbDebug.setOnCheckedChangeListener { _, isChecked -> putConfig(AppConfig.DEBUG_MATRIX, isChecked) }
        findViewById<View>(R.id.item_debug_matrix).setOnClickListener { cbDebug.performClick() }

        // 1.5 Arc Drawer Toggle
        val cbArcDrawer = findViewById<android.widget.CheckBox>(R.id.checkbox_arc_drawer)
        cbArcDrawer.isChecked = getConfigBool(AppConfig.FREEZER_ARC_DRAWER)
        cbArcDrawer.setOnCheckedChangeListener { _, isChecked -> putConfig(AppConfig.FREEZER_ARC_DRAWER, isChecked) }
        findViewById<View>(R.id.item_arc_drawer).setOnClickListener { cbArcDrawer.performClick() }

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
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, "https://github.com/fcmfcm1999/EdgeX".toUri())
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

    override fun onResume() {
        super.onResume()
        ThemeManager.applyToActivity(this)
    }
}
