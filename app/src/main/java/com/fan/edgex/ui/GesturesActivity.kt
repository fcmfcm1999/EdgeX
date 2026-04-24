package com.fan.edgex.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig

class GesturesActivity : AppCompatActivity() {
    private data class ActionSpec(
        val viewId: Int,
        @StringRes val labelRes: Int,
        val actionKey: String,
    )

    private data class ZoneSpec(
        val viewId: Int,
        @StringRes val titleRes: Int,
        val zoneKey: String,
        @DrawableRes val iconRes: Int,
        val actions: List<ActionSpec>,
    )

    private val defaultActions = listOf(
        ActionSpec(R.id.action_click, R.string.gesture_click, "click"),
        ActionSpec(R.id.action_double_click, R.string.gesture_double_click, "double_click"),
        ActionSpec(R.id.action_long_press, R.string.gesture_long_press, "long_press"),
    )

    private val sideLeftActions = defaultActions + listOf(
        ActionSpec(R.id.action_swipe_right, R.string.gesture_swipe_right, "swipe_right"),
        ActionSpec(R.id.action_swipe_up, R.string.gesture_swipe_up, "swipe_up"),
        ActionSpec(R.id.action_swipe_down, R.string.gesture_swipe_down, "swipe_down"),
    )

    private val sideRightActions = defaultActions + listOf(
        ActionSpec(R.id.action_swipe_left, R.string.gesture_swipe_left, "swipe_left"),
        ActionSpec(R.id.action_swipe_up, R.string.gesture_swipe_up, "swipe_up"),
        ActionSpec(R.id.action_swipe_down, R.string.gesture_swipe_down, "swipe_down"),
    )

    private val topActions = defaultActions + listOf(
        ActionSpec(R.id.action_swipe_down, R.string.gesture_swipe_down, "swipe_down"),
        ActionSpec(R.id.action_swipe_left, R.string.gesture_swipe_left, "swipe_left"),
        ActionSpec(R.id.action_swipe_right, R.string.gesture_swipe_right, "swipe_right"),
    )

    private val bottomActions = defaultActions + listOf(
        ActionSpec(R.id.action_swipe_up, R.string.gesture_swipe_up, "swipe_up"),
        ActionSpec(R.id.action_swipe_left, R.string.gesture_swipe_left, "swipe_left"),
        ActionSpec(R.id.action_swipe_right, R.string.gesture_swipe_right, "swipe_right"),
    )

    private val zoneSpecs = listOf(
        ZoneSpec(R.id.zone_left_top, R.string.zone_left_top, "left_top", R.drawable.ic_edge_left_top, sideLeftActions),
        ZoneSpec(R.id.zone_left_mid, R.string.zone_left_mid, "left_mid", R.drawable.ic_edge_left_mid, sideLeftActions),
        ZoneSpec(R.id.zone_left_bottom, R.string.zone_left_bottom, "left_bottom", R.drawable.ic_edge_left_bottom, sideLeftActions),
        ZoneSpec(R.id.zone_right_top, R.string.zone_right_top, "right_top", R.drawable.ic_edge_right_top, sideRightActions),
        ZoneSpec(R.id.zone_right_mid, R.string.zone_right_mid, "right_mid", R.drawable.ic_edge_right_mid, sideRightActions),
        ZoneSpec(R.id.zone_right_bottom, R.string.zone_right_bottom, "right_bottom", R.drawable.ic_edge_right_bottom, sideRightActions),
        ZoneSpec(R.id.zone_top_left, R.string.zone_top_left, "top_left", R.drawable.ic_edge_top_left, topActions),
        ZoneSpec(R.id.zone_top_mid, R.string.zone_top_mid, "top_mid", R.drawable.ic_edge_top_mid, topActions),
        ZoneSpec(R.id.zone_top_right, R.string.zone_top_right, "top_right", R.drawable.ic_edge_top_right, topActions),
        ZoneSpec(R.id.zone_bottom_left, R.string.zone_bottom_left, "bottom_left", R.drawable.ic_edge_bottom_left, bottomActions),
        ZoneSpec(R.id.zone_bottom_mid, R.string.zone_bottom_mid, "bottom_mid", R.drawable.ic_edge_bottom_mid, bottomActions),
        ZoneSpec(R.id.zone_bottom_right, R.string.zone_bottom_right, "bottom_right", R.drawable.ic_edge_bottom_right, bottomActions),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gestures)
        ThemeManager.applyToActivity(this)

        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.systemWindowInsetTop, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        zoneSpecs.forEach(::setupZone)
    }

    override fun onResume() {
        super.onResume()
        zoneSpecs.forEach(::setupZone)
    }

    private fun setupZone(spec: ZoneSpec) {
        val root = findViewById<View>(spec.viewId)
        val title = getString(spec.titleRes)
        val zoneKey = spec.zoneKey

        root.findViewById<TextView>(R.id.title).text = title
        root.findViewById<ImageView>(R.id.zone_icon).setImageResource(spec.iconRes)

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

        spec.actions.forEach { action ->
            setupAction(root.findViewById(action.viewId), getString(action.labelRes), zoneKey, action.actionKey, title)
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
