package com.fan.edgex.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R

class KeysActivity : AppCompatActivity() {

    companion object {
        // Only Volume Up, Volume Down and Power keys
        val SUPPORTED_KEYS = listOf(
            Triple(KeyEvent.KEYCODE_VOLUME_UP, R.string.key_volume_up, R.drawable.ic_keyboard),
            Triple(KeyEvent.KEYCODE_VOLUME_DOWN, R.string.key_volume_down, R.drawable.ic_keyboard),
            Triple(KeyEvent.KEYCODE_POWER, R.string.key_power, R.drawable.ic_keyboard)
        )
    }

    private val prefs by lazy { getSharedPreferences("config", MODE_PRIVATE) }
    private val keyViews = mutableMapOf<Int, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keys)

        // Immersive Header Fix
        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.systemWindowInsetTop, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Add Key Items
        val container = findViewById<LinearLayout>(R.id.keys_container)
        for ((keyCode, nameRes, iconRes) in SUPPORTED_KEYS) {
            val view = createKeyItem(keyCode, nameRes, iconRes)
            keyViews[keyCode] = view
            container.addView(view)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh UI to show new selections
        refreshAllKeyItems()
    }

    private fun createKeyItem(keyCode: Int, nameRes: Int, iconRes: Int): View {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.item_key, null)

        val title = view.findViewById<TextView>(R.id.title)
        val subtitle = view.findViewById<TextView>(R.id.subtitle)
        val icon = view.findViewById<ImageView>(R.id.key_icon)
        val checkbox = view.findViewById<CheckBox>(R.id.checkbox)
        val header = view.findViewById<View>(R.id.header)
        val body = view.findViewById<View>(R.id.body)
        val arrow = view.findViewById<ImageView>(R.id.arrow)

        // Set Title and Icon
        title.text = getString(nameRes)
        icon.setImageResource(iconRes)

        // Load State
        val keyEnabled = prefs.getBoolean("key_enabled_$keyCode", false)
        checkbox.setOnCheckedChangeListener(null)
        checkbox.isChecked = keyEnabled

        // Update Subtitle
        updateKeySubtitle(keyCode, subtitle)

        // Checkbox Click
        checkbox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) return@setOnCheckedChangeListener
            prefs.edit().putBoolean("key_enabled_$keyCode", isChecked).commit()
            notifyConfigChange()
        }

        // Expand/Collapse
        header.setOnClickListener {
            if (body.visibility == View.VISIBLE) {
                body.visibility = View.GONE
                arrow.animate().rotation(0f).start()
            } else {
                body.visibility = View.VISIBLE
                arrow.animate().rotation(180f).start()
            }
        }

        // Setup Actions
        val keyName = getString(nameRes)
        setupAction(view.findViewById(R.id.action_click), getString(R.string.key_mode_click), keyCode, "click", keyName)
        setupAction(view.findViewById(R.id.action_double_click), getString(R.string.key_mode_double_click), keyCode, "double_click", keyName)
        setupAction(view.findViewById(R.id.action_long_press), getString(R.string.key_mode_long_press), keyCode, "long_press", keyName)

        return view
    }

    private fun setupAction(actionView: View, label: String, keyCode: Int, mode: String, keyName: String) {
        val titleView = actionView.findViewById<TextView>(R.id.action_title)
        val subtitleView = actionView.findViewById<TextView>(R.id.action_subtitle)

        titleView.text = label

        val prefKey = "key_${keyCode}_$mode"

        // Load Saved Action Label
        val savedLabel = prefs.getString("${prefKey}_label", getString(R.string.label_default_action))
        subtitleView.text = savedLabel

        // Set Click Listener
        actionView.setOnClickListener {
            val intent = Intent(this, ActionSelectionActivity::class.java)
            intent.putExtra("title", "$keyName / $label")
            intent.putExtra("pref_key", prefKey)
            startActivity(intent)
        }
    }

    private fun updateKeySubtitle(keyCode: Int, subtitleView: TextView) {
        val actions = mutableListOf<String>()
        
        val clickAction = prefs.getString("key_${keyCode}_click_label", null)
        val doubleClickAction = prefs.getString("key_${keyCode}_double_click_label", null)
        val longPressAction = prefs.getString("key_${keyCode}_long_press_label", null)

        if (!clickAction.isNullOrEmpty() && clickAction != getString(R.string.label_default_action)) {
            actions.add("${getString(R.string.key_mode_click)}: $clickAction")
        }
        if (!doubleClickAction.isNullOrEmpty() && doubleClickAction != getString(R.string.label_default_action)) {
            actions.add("${getString(R.string.key_mode_double_click)}: $doubleClickAction")
        }
        if (!longPressAction.isNullOrEmpty() && longPressAction != getString(R.string.label_default_action)) {
            actions.add("${getString(R.string.key_mode_long_press)}: $longPressAction")
        }

        subtitleView.text = if (actions.isEmpty()) {
            getString(R.string.key_not_configured)
        } else {
            actions.joinToString(", ")
        }
    }

    private fun refreshAllKeyItems() {
        for ((keyCode, _, _) in SUPPORTED_KEYS) {
            keyViews[keyCode]?.let { view ->
                val subtitle = view.findViewById<TextView>(R.id.subtitle)
                val checkbox = view.findViewById<CheckBox>(R.id.checkbox)
                
                // Refresh checkbox state
                val keyEnabled = prefs.getBoolean("key_enabled_$keyCode", false)
                checkbox.setOnCheckedChangeListener(null)
                checkbox.isChecked = keyEnabled
                checkbox.setOnCheckedChangeListener { buttonView, isChecked ->
                    if (!buttonView.isPressed) return@setOnCheckedChangeListener
                    prefs.edit().putBoolean("key_enabled_$keyCode", isChecked).commit()
                    notifyConfigChange()
                }

                // Refresh subtitle
                updateKeySubtitle(keyCode, subtitle)

                // Refresh action subtitles
                val keyName = getString(SUPPORTED_KEYS.find { it.first == keyCode }?.second ?: R.string.key_volume_up)
                refreshAction(view.findViewById(R.id.action_click), keyCode, "click")
                refreshAction(view.findViewById(R.id.action_double_click), keyCode, "double_click")
                refreshAction(view.findViewById(R.id.action_long_press), keyCode, "long_press")
            }
        }
    }

    private fun refreshAction(actionView: View, keyCode: Int, mode: String) {
        val subtitleView = actionView.findViewById<TextView>(R.id.action_subtitle)
        val prefKey = "key_${keyCode}_$mode"
        val savedLabel = prefs.getString("${prefKey}_label", getString(R.string.label_default_action))
        subtitleView.text = savedLabel
    }

    private fun notifyConfigChange() {
        contentResolver.notifyChange(Uri.parse("content://com.fan.edgex.provider/config"), null)
    }
}
