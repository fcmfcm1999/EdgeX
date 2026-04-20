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
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig

class KeysActivity : AppCompatActivity() {

    data class KeyConfig(
        val keyCode: Int,
        val nameRes: Int,
        val iconRes: Int
    )

    companion object {
        // Only Volume Up, Volume Down and Power keys
        val SUPPORTED_KEYS = listOf(
            KeyConfig(KeyEvent.KEYCODE_VOLUME_UP, R.string.key_volume_up, R.drawable.ic_volume_up),
            KeyConfig(KeyEvent.KEYCODE_VOLUME_DOWN, R.string.key_volume_down, R.drawable.ic_volume_down),
            KeyConfig(KeyEvent.KEYCODE_POWER, R.string.key_power, R.drawable.ic_power)
        )
    }

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
        for (keyConfig in SUPPORTED_KEYS) {
            val view = createKeyItem(keyConfig)
            keyViews[keyConfig.keyCode] = view
            container.addView(view)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh UI to show new selections
        refreshAllKeyItems()
    }

    private fun createKeyItem(config: KeyConfig): View {
        val keyCode = config.keyCode
        val nameRes = config.nameRes
        val iconRes = config.iconRes

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
        checkbox.setOnCheckedChangeListener(null)
        checkbox.isChecked = getConfigBool(AppConfig.keyEnabled(keyCode))

        // Update Subtitle
        updateKeySubtitle(keyCode, subtitle)

        // Checkbox Click
        checkbox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) return@setOnCheckedChangeListener
            putConfig(AppConfig.keyEnabled(keyCode), isChecked)
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

        val prefKey = AppConfig.keyAction(keyCode, mode)

        // Load Saved Action Label
        subtitleView.text = getConfigString("${prefKey}_label", getString(R.string.label_default_action))

        // Set Click Listener
        actionView.setOnClickListener {
            val intent = Intent(this, ActionSelectionActivity::class.java)
            intent.putExtra("title", "$keyName / $label")
            intent.putExtra("pref_key", prefKey)
            startActivity(intent)
        }
    }

    private fun updateKeySubtitle(keyCode: Int, subtitleView: TextView) {
        val default = getString(R.string.label_default_action)
        val labels = AppConfig.KEY_TRIGGERS.zip(
            listOf(getString(R.string.key_mode_click), getString(R.string.key_mode_double_click), getString(R.string.key_mode_long_press))
        ).mapNotNull { (trigger, name) ->
            val label = getConfigString("${AppConfig.keyAction(keyCode, trigger)}_label")
            label.takeIf { it.isNotEmpty() && it != default }?.let { "$name: $it" }
        }
        subtitleView.text = labels.joinToString(", ").ifEmpty { getString(R.string.key_not_configured) }
    }

    private fun refreshAllKeyItems() {
        for (config in SUPPORTED_KEYS) {
            val keyCode = config.keyCode
            keyViews[keyCode]?.let { view ->
                val subtitle = view.findViewById<TextView>(R.id.subtitle)
                val checkbox = view.findViewById<CheckBox>(R.id.checkbox)
                
                // Refresh checkbox state
                checkbox.setOnCheckedChangeListener(null)
                checkbox.isChecked = getConfigBool(AppConfig.keyEnabled(keyCode))
                checkbox.setOnCheckedChangeListener { buttonView, isChecked ->
                    if (!buttonView.isPressed) return@setOnCheckedChangeListener
                    putConfig(AppConfig.keyEnabled(keyCode), isChecked)
                }

                // Refresh subtitle
                updateKeySubtitle(keyCode, subtitle)

                // Refresh action subtitles
                refreshAction(view.findViewById(R.id.action_click), keyCode, "click")
                refreshAction(view.findViewById(R.id.action_double_click), keyCode, "double_click")
                refreshAction(view.findViewById(R.id.action_long_press), keyCode, "long_press")
            }
        }
    }

    private fun refreshAction(actionView: View, keyCode: Int, mode: String) {
        val prefKey = AppConfig.keyAction(keyCode, mode)
        actionView.findViewById<TextView>(R.id.action_subtitle).text =
            getConfigString("${prefKey}_label", getString(R.string.label_default_action))
    }
}
