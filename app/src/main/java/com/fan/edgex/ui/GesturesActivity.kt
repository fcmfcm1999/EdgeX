package com.fan.edgex.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig

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

        // Initialize Zones with specific icons
        setupZone(findViewById(R.id.zone_left_top), getString(R.string.zone_left_top), "left_top", R.drawable.ic_edge_left_top)
        setupZone(findViewById(R.id.zone_left_mid), getString(R.string.zone_left_mid), "left_mid", R.drawable.ic_edge_left_mid)
        setupZone(findViewById(R.id.zone_left_bottom), getString(R.string.zone_left_bottom), "left_bottom", R.drawable.ic_edge_left_bottom)

        setupZone(findViewById(R.id.zone_right_top), getString(R.string.zone_right_top), "right_top", R.drawable.ic_edge_right_top)
        setupZone(findViewById(R.id.zone_right_mid), getString(R.string.zone_right_mid), "right_mid", R.drawable.ic_edge_right_mid)
        setupZone(findViewById(R.id.zone_right_bottom), getString(R.string.zone_right_bottom), "right_bottom", R.drawable.ic_edge_right_bottom)
    }

    override fun onResume() {
        super.onResume()
        // Refresh UI to show new selections
        setupZone(findViewById(R.id.zone_left_top), getString(R.string.zone_left_top), "left_top", R.drawable.ic_edge_left_top)
        setupZone(findViewById(R.id.zone_left_mid), getString(R.string.zone_left_mid), "left_mid", R.drawable.ic_edge_left_mid)
        setupZone(findViewById(R.id.zone_left_bottom), getString(R.string.zone_left_bottom), "left_bottom", R.drawable.ic_edge_left_bottom)
        setupZone(findViewById(R.id.zone_right_top), getString(R.string.zone_right_top), "right_top", R.drawable.ic_edge_right_top)
        setupZone(findViewById(R.id.zone_right_mid), getString(R.string.zone_right_mid), "right_mid", R.drawable.ic_edge_right_mid)
        setupZone(findViewById(R.id.zone_right_bottom), getString(R.string.zone_right_bottom), "right_bottom", R.drawable.ic_edge_right_bottom)
    }

    private fun setupZone(root: View, title: String, zoneKey: String, iconRes: Int) {
        root.findViewById<TextView>(R.id.title).text = title
        root.findViewById<ImageView>(R.id.zone_icon).setImageResource(iconRes)

        val isLeft = zoneKey.startsWith("left")

        val checkBox = root.findViewById<android.widget.CheckBox>(R.id.checkbox)
        val enabledKey = AppConfig.zoneEnabled(zoneKey)
        checkBox.setOnCheckedChangeListener(null)
        checkBox.isChecked = getConfigBool(enabledKey)

        checkBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) return@setOnCheckedChangeListener
            putConfig(enabledKey, isChecked)
        }

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

        setupAction(root.findViewById(R.id.action_click), getString(R.string.gesture_click), zoneKey, "click", title)
        setupAction(root.findViewById(R.id.action_double_click), getString(R.string.gesture_double_click), zoneKey, "double_click", title)
        setupAction(root.findViewById(R.id.action_long_press), getString(R.string.gesture_long_press), zoneKey, "long_press", title)
        setupAction(root.findViewById(R.id.action_swipe_up), getString(R.string.gesture_swipe_up), zoneKey, "swipe_up", title)
        setupAction(root.findViewById(R.id.action_swipe_down), getString(R.string.gesture_swipe_down), zoneKey, "swipe_down", title)

        if (isLeft) {
            setupAction(root.findViewById(R.id.action_swipe_right), getString(R.string.gesture_swipe_right), zoneKey, "swipe_right", title)
        } else {
            setupAction(root.findViewById(R.id.action_swipe_left), getString(R.string.gesture_swipe_left), zoneKey, "swipe_left", title)
        }
    }

    private fun setupAction(actionView: View, label: String, zoneKey: String, actionKey: String, zoneTitle: String) {
        actionView.findViewById<TextView>(R.id.action_title).text = label

        val fullKey = AppConfig.gestureAction(zoneKey, actionKey)
        val savedLabel = getConfigString("${fullKey}_label", getString(R.string.action_none))
        actionView.findViewById<TextView>(R.id.action_subtitle).text = savedLabel

        actionView.setOnClickListener {
            startActivity(
                android.content.Intent(this, ActionSelectionActivity::class.java)
                    .putExtra("title", "$zoneTitle / $label")
                    .putExtra("pref_key", fullKey)
            )
        }
    }
}
