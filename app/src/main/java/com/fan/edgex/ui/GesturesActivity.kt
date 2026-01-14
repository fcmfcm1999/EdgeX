package com.fan.edgex.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R

class GesturesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gestures)

        // Immersive Header Fix
        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.systemWindowInsetTop, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Initialize Zones
        setupZone(findViewById(R.id.zone_left_top), "左上", "left_top", isLeft = true)
        setupZone(findViewById(R.id.zone_left_mid), "左中", "left_mid", isLeft = true)
        setupZone(findViewById(R.id.zone_left_bottom), "左下", "left_bottom", isLeft = true)
        
        setupZone(findViewById(R.id.zone_right_top), "右上", "right_top", isLeft = false)
        setupZone(findViewById(R.id.zone_right_mid), "右中", "right_mid", isLeft = false)
        setupZone(findViewById(R.id.zone_right_bottom), "右下", "right_bottom", isLeft = false)
    }

    override fun onResume() {
        super.onResume()
        // Refresh UI to show new selections
        // Re-run setup to refresh text. 
        // NOTE: This is slightly inefficient but safe/easy for this scale.
        setupZone(findViewById(R.id.zone_left_top), "左上", "left_top", isLeft = true)
        setupZone(findViewById(R.id.zone_left_mid), "左中", "left_mid", isLeft = true)
        setupZone(findViewById(R.id.zone_left_bottom), "左下", "left_bottom", isLeft = true)
        setupZone(findViewById(R.id.zone_right_top), "右上", "right_top", isLeft = false)
        setupZone(findViewById(R.id.zone_right_mid), "右中", "right_mid", isLeft = false)
        setupZone(findViewById(R.id.zone_right_bottom), "右下", "right_bottom", isLeft = false)
    }

    private fun setupZone(root: View, title: String, zoneKey: String, isLeft: Boolean) {
        // Set Title
        root.findViewById<TextView>(R.id.title).text = title
        
        // Zone Enabled Checkbox
        val checkBox = root.findViewById<android.widget.CheckBox>(R.id.checkbox)
        val prefs = getSharedPreferences("config", android.content.Context.MODE_PRIVATE)
        
        // Load State (Default false)
        val isZoneEnabled = prefs.getBoolean("zone_enabled_$zoneKey", false)
        checkBox.isChecked = isZoneEnabled
        
        // Save State
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("zone_enabled_$zoneKey", isChecked).apply()
            // Notify Hook to refresh
            contentResolver.notifyChange(android.net.Uri.parse("content://com.fan.edgex.provider/config"), null)
        }

        // Expand/Collapse Logic (Only set once ideally, but resetting listener is harmless)
        val header = root.findViewById<View>(R.id.header)
        val body = root.findViewById<View>(R.id.body)
        val arrow = root.findViewById<ImageView>(R.id.arrow)

        if (!header.hasOnClickListeners()) {
             header.setOnClickListener {
                if (body.visibility == View.VISIBLE) {
                    body.visibility = View.GONE
                    arrow.animate().rotation(0f).start()
                } else {
                    body.visibility = View.VISIBLE
                    arrow.animate().rotation(180f).start()
                }
            }
        }

        // Setup Action Labels & Clicks
        setupAction(root.findViewById(R.id.action_click), "单击", zoneKey, "click", title)
        setupAction(root.findViewById(R.id.action_double_click), "双击", zoneKey, "double_click", title)
        setupAction(root.findViewById(R.id.action_long_press), "长按", zoneKey, "long_press", title)
        setupAction(root.findViewById(R.id.action_swipe_up), "上划", zoneKey, "swipe_up", title)
        setupAction(root.findViewById(R.id.action_swipe_down), "下划", zoneKey, "swipe_down", title)

        // Conditional Swipe
        if (isLeft) {
            setupAction(root.findViewById(R.id.action_swipe_right), "右划", zoneKey, "swipe_right", title)
        } else {
             setupAction(root.findViewById(R.id.action_swipe_left), "左划", zoneKey, "swipe_left", title)
        }
    }

    private fun setupAction(actionView: View, label: String, zoneKey: String, actionKey: String, zoneTitle: String) {
        val titleView = actionView.findViewById<TextView>(R.id.action_title)
        val subtitleView = actionView.findViewById<TextView>(R.id.action_subtitle)
        
        titleView.text = label
        
        val fullKey = "${zoneKey}_${actionKey}"
        
        // Load Saved Action Label
        val prefs = getSharedPreferences("config", android.content.Context.MODE_PRIVATE)
        val savedLabel = prefs.getString(fullKey + "_label", "默认")
        subtitleView.text = savedLabel
        
        // Set Click Listener
        actionView.setOnClickListener {
            val intent = android.content.Intent(this, ActionSelectionActivity::class.java)
            intent.putExtra("title", "$zoneTitle / $label")
            intent.putExtra("pref_key", fullKey)
            startActivity(intent)
        }
    }
}
