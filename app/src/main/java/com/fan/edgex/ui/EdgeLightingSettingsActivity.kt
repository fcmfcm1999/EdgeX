package com.fan.edgex.ui

import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.widget.addTextChangedListener
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.license.PremiumActivator
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import com.fan.edgex.overlay.EdgeLightingView
import com.fan.edgex.service.NotificationEdgeService
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.sin

class EdgeLightingSettingsActivity : AppCompatActivity() {

    private data class EffectOption(val value: String, val labelRes: Int)

    private lateinit var previewView: EdgeLightingView
    private lateinit var previewLabel: TextView
    private var previewAnimator: ValueAnimator? = null

    private val effects = listOf(
        EffectOption(AppConfig.EDGE_LIGHTING_EFFECT_BASIC, R.string.edge_lighting_effect_basic),
        EffectOption(AppConfig.EDGE_LIGHTING_EFFECT_BREATHING, R.string.edge_lighting_effect_breathing),
        EffectOption(AppConfig.EDGE_LIGHTING_EFFECT_COMET, R.string.edge_lighting_effect_comet),
        EffectOption(AppConfig.EDGE_LIGHTING_EFFECT_FLOW, R.string.edge_lighting_effect_flow),
        EffectOption(AppConfig.EDGE_LIGHTING_EFFECT_MULTICOLOR, R.string.edge_lighting_effect_multicolor),
        EffectOption(AppConfig.EDGE_LIGHTING_EFFECT_SPOTLIGHT, R.string.edge_lighting_effect_spotlight),
        EffectOption(AppConfig.EDGE_LIGHTING_EFFECT_ECLIPSE, R.string.edge_lighting_effect_eclipse),
        EffectOption(AppConfig.EDGE_LIGHTING_EFFECT_ECHO, R.string.edge_lighting_effect_echo),
        EffectOption(AppConfig.EDGE_LIGHTING_EFFECT_RIPPLE, R.string.edge_lighting_effect_ripple),
    )
    private var selectedEffect = AppConfig.EDGE_LIGHTING_EFFECT_BASIC
    private val chipViews = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edge_lighting_settings)
        ThemeManager.applyToActivity(this)

        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                view.paddingLeft,
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top,
                view.paddingRight,
                view.paddingBottom,
            )
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        previewView = findViewById(R.id.preview_lighting_view)
        previewLabel = findViewById(R.id.preview_effect_label)

        setupToggles()
        setupEffectChips()
        setupColorControls()
        setupSeekBars()
        setupNotificationAccessRow()
        setupAppFilterEntry()
        syncPreviewFromConfig()
        applyPremiumGating()
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.applyToActivity(this)
        refreshNotificationAccessStatus()
        refreshAppFilterSummary()
        startPreviewAnimation()
        applyPremiumGating()
    }

    override fun onPause() {
        super.onPause()
        stopPreviewAnimation()
    }

    // --- Preview ---

    private fun syncPreviewFromConfig() {
        val color = parseColor(getConfigString(AppConfig.EDGE_LIGHTING_COLOR, DEFAULT_COLOR))
        val widthDp = getConfigString(AppConfig.EDGE_LIGHTING_WIDTH_DP, "5").toIntOrNull() ?: 5
        val alpha = getConfigString(AppConfig.EDGE_LIGHTING_ALPHA, "1.0").toFloatOrNull() ?: 1f

        previewView.glowColor = color
        previewView.glowWidthPx = widthDp.coerceIn(1, 20) * resources.displayMetrics.density
        previewView.glowAlpha = alpha.coerceIn(0f, 1f)
        previewView.effect = selectedEffect
    }

    private fun startPreviewAnimation() {
        stopPreviewAnimation()
        previewAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                previewView.flowProgress = progress
                val baseAlpha = getConfigString(AppConfig.EDGE_LIGHTING_ALPHA, "1.0")
                    .toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
                previewView.glowAlpha = if (previewView.effect == AppConfig.EDGE_LIGHTING_EFFECT_BREATHING) {
                    val pulse = 0.4f + 0.6f * ((sin(progress * PI * 4.0) + 1.0) / 2.0).toFloat()
                    baseAlpha * pulse
                } else {
                    baseAlpha
                }
            }
            start()
        }
    }

    private fun stopPreviewAnimation() {
        previewAnimator?.cancel()
        previewAnimator = null
    }

    private fun updatePreviewEffect(effect: String) {
        selectedEffect = effect
        previewView.effect = effect
        previewLabel.text = getString(effects.first { it.value == effect }.labelRes)
    }

    private fun updatePreviewColor(color: Int) {
        previewView.glowColor = color
    }

    private fun updatePreviewWidth(widthDp: Int) {
        previewView.glowWidthPx = widthDp.coerceIn(1, 20) * resources.displayMetrics.density
    }

    // --- Toggles ---

    private fun setupToggles() {
        val enabledSwitch = bindSwitch(R.id.switch_edge_lighting_enabled, AppConfig.EDGE_LIGHTING_ENABLED, false)
        val autoColorSwitch = bindSwitch(R.id.switch_auto_color, AppConfig.EDGE_LIGHTING_AUTO_COLOR, true)

        fun applyEnabledState(isEnabled: Boolean) {
            autoColorSwitch.isEnabled = isEnabled
            autoColorSwitch.alpha = if (isEnabled) 1f else DISABLED_ALPHA
        }

        applyEnabledState(enabledSwitch.isChecked)
        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            putConfig(AppConfig.EDGE_LIGHTING_ENABLED, isChecked)
            applyEnabledState(isChecked)
        }
    }

    private fun bindSwitch(id: Int, key: String, default: Boolean): Switch {
        val switch = findViewById<Switch>(id)
        switch.isChecked = getConfigBool(key, default)
        switch.setOnCheckedChangeListener { _, isChecked -> putConfig(key, isChecked) }
        return switch
    }

    // --- Effect Chips ---

    private fun setupEffectChips() {
        selectedEffect = getConfigString(AppConfig.EDGE_LIGHTING_EFFECT, AppConfig.EDGE_LIGHTING_EFFECT_BASIC)
        val container = findViewById<LinearLayout>(R.id.effect_chips_container)
        val dp = resources.displayMetrics.density
        val accent = ThemeManager.currentAccent(this)

        effects.forEachIndexed { index, option ->
            val chip = createChip(getString(option.labelRes), option.value == selectedEffect, accent)
            chip.setOnClickListener {
                selectChip(index, accent)
                putConfig(AppConfig.EDGE_LIGHTING_EFFECT, option.value)
                updatePreviewEffect(option.value)
            }
            if (index > 0) {
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                params.marginStart = (8 * dp).toInt()
                chip.layoutParams = params
            }
            chipViews.add(chip)
            container.addView(chip)
        }

        previewLabel.text = getString(effects.first { it.value == selectedEffect }.labelRes)
    }

    private fun createChip(label: String, selected: Boolean, accent: Int): TextView {
        val dp = resources.displayMetrics.density
        val chip = TextView(this)
        chip.text = label
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        chip.setPadding((16 * dp).toInt(), (9 * dp).toInt(), (16 * dp).toInt(), (9 * dp).toInt())
        applyChipStyle(chip, selected, accent)
        return chip
    }

    private fun applyChipStyle(chip: TextView, selected: Boolean, accent: Int) {
        val dp = resources.displayMetrics.density
        val shape = GradientDrawable()
        shape.cornerRadius = 20 * dp
        if (selected) {
            shape.setColor(accent)
            chip.setTextColor(ThemeManager.onAccentColor(accent))
        } else {
            shape.setColor(Color.TRANSPARENT)
            shape.setStroke((1 * dp).toInt(), resources.getColor(R.color.ui_edit_stroke, null))
            chip.setTextColor(resources.getColor(R.color.ui_text_primary, null))
        }
        chip.background = shape
    }

    private fun selectChip(selectedIndex: Int, accent: Int) {
        chipViews.forEachIndexed { index, chip ->
            applyChipStyle(chip, index == selectedIndex, accent)
        }
    }

    // --- Color Controls ---

    private fun setupColorControls() {
        val red = findViewById<EditText>(R.id.edit_edge_lighting_red)
        val green = findViewById<EditText>(R.id.edit_edge_lighting_green)
        val blue = findViewById<EditText>(R.id.edit_edge_lighting_blue)
        val color = parseColor(getConfigString(AppConfig.EDGE_LIGHTING_COLOR, DEFAULT_COLOR))

        red.setText(Color.red(color).toString())
        green.setText(Color.green(color).toString())
        blue.setText(Color.blue(color).toString())
        refreshColorPreview()

        red.addTextChangedListener { refreshColorPreview() }
        green.addTextChangedListener { refreshColorPreview() }
        blue.addTextChangedListener { refreshColorPreview() }

        findViewById<View>(R.id.btn_edge_lighting_apply_color).setOnClickListener {
            val customColor = readColorFromInputs()
            if (customColor == null) {
                Toast.makeText(this, R.string.toast_theme_invalid_rgb, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            putConfig(AppConfig.EDGE_LIGHTING_COLOR, displayColor(customColor))
            updatePreviewColor(customColor)
            Toast.makeText(this, R.string.edge_lighting_color_saved, Toast.LENGTH_SHORT).show()
        }
    }

    // --- SeekBars ---

    private fun setupSeekBars() {
        bindSeekBar(
            seekBarId = R.id.seek_edge_lighting_width,
            valueId = R.id.text_edge_lighting_width_value,
            key = AppConfig.EDGE_LIGHTING_WIDTH_DP,
            defaultValue = 5,
            min = 1,
            max = 20,
            formatter = { getString(R.string.edge_lighting_width_dp, it) },
            onChanged = { updatePreviewWidth(it) },
        )
        bindSeekBar(
            seekBarId = R.id.seek_edge_lighting_duration,
            valueId = R.id.text_edge_lighting_duration_value,
            key = AppConfig.EDGE_LIGHTING_DURATION_MS,
            defaultValue = 3000,
            min = 500,
            max = 10000,
            step = 100,
            formatter = { getString(R.string.edge_lighting_duration_ms, it) },
        )
        bindSeekBar(
            seekBarId = R.id.seek_edge_lighting_alpha,
            valueId = R.id.text_edge_lighting_alpha_value,
            key = AppConfig.EDGE_LIGHTING_ALPHA,
            defaultValue = 100,
            min = 0,
            max = 100,
            formatter = { getString(R.string.edge_lighting_alpha_pct, it) },
            saveValue = { putConfig(AppConfig.EDGE_LIGHTING_ALPHA, (it / 100f).toString()) },
            readValue = { ((getConfigString(AppConfig.EDGE_LIGHTING_ALPHA, "1.0").toFloatOrNull() ?: 1f) * 100).toInt() },
        )
    }

    private fun bindSeekBar(
        seekBarId: Int,
        valueId: Int,
        key: String,
        defaultValue: Int,
        min: Int,
        max: Int,
        step: Int = 1,
        formatter: (Int) -> String,
        saveValue: (Int) -> Unit = { putConfig(key, it.toString()) },
        readValue: () -> Int = { getConfigString(key, defaultValue.toString()).toIntOrNull() ?: defaultValue },
        onChanged: ((Int) -> Unit)? = null,
    ) {
        val seekBar = findViewById<SeekBar>(seekBarId)
        val label = findViewById<TextView>(valueId)
        seekBar.max = ((max - min) / step).coerceAtLeast(1)

        fun progressToValue(progress: Int) = min + progress * step
        fun valueToProgress(value: Int) = (value.coerceIn(min, max) - min) / step

        val initial = readValue().coerceIn(min, max)
        seekBar.progress = valueToProgress(initial)
        label.text = formatter(initial)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progressToValue(progress)
                label.text = formatter(value.coerceIn(min, max))
                if (fromUser) {
                    saveValue(value)
                    onChanged?.invoke(value)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = progressToValue(seekBar?.progress ?: 0)
                saveValue(value)
                onChanged?.invoke(value)
            }
        })
    }

    // --- Notification Access ---

    private fun setupNotificationAccessRow() {
        findViewById<View>(R.id.item_notification_access).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        refreshNotificationAccessStatus()
    }

    private fun refreshNotificationAccessStatus() {
        val granted = isNotificationAccessGranted()
        val statusText = findViewById<TextView>(R.id.text_notification_access_status)
        statusText.setText(
            if (granted) R.string.edge_lighting_notif_status_granted
            else R.string.edge_lighting_notif_status_required,
        )
        val accent = ThemeManager.currentAccent(this)
        statusText.setTextColor(
            if (granted) accent
            else resources.getColor(R.color.ui_attention_red, theme),
        )
    }

    private fun isNotificationAccessGranted(): Boolean {
        val componentName = ComponentName(this, NotificationEdgeService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        return enabled.split(':').any { it.equals(componentName, ignoreCase = true) }
    }

    // --- App Filter ---

    private fun setupAppFilterEntry() {
        findViewById<View>(R.id.item_edge_lighting_apps).setOnClickListener {
            startActivity(Intent(this, EdgeLightingAppFilterActivity::class.java))
        }
        refreshAppFilterSummary()
    }

    private fun refreshAppFilterSummary() {
        val selectedCount = parsePackageList(getConfigString(AppConfig.EDGE_LIGHTING_APP_LIST)).size
        findViewById<TextView>(R.id.text_edge_lighting_app_summary).text =
            if (selectedCount == 0) getString(R.string.edge_lighting_app_filter_all)
            else getString(R.string.edge_lighting_app_filter_selected, selectedCount)
    }

    // --- Color helpers ---

    private fun refreshColorPreview() {
        val color = readColorFromInputs() ?: parseColor(DEFAULT_COLOR)
        ThemeManager.tintSwatch(findViewById(R.id.preview_edge_lighting_color), color)
        findViewById<TextView>(R.id.text_edge_lighting_color_hex).text = displayColor(color)
    }

    private fun readColorFromInputs(): Int? {
        val r = findViewById<EditText>(R.id.edit_edge_lighting_red).text.toString().toIntOrNull()
        val g = findViewById<EditText>(R.id.edit_edge_lighting_green).text.toString().toIntOrNull()
        val b = findViewById<EditText>(R.id.edit_edge_lighting_blue).text.toString().toIntOrNull()
        if (r == null || g == null || b == null) return null
        if (r !in 0..255 || g !in 0..255 || b !in 0..255) return null
        return Color.rgb(r, g, b)
    }

    private fun parseColor(value: String): Int =
        runCatching { value.toColorInt() }.getOrElse { DEFAULT_COLOR.toColorInt() }

    private fun displayColor(color: Int): String =
        String.format("#%06X", 0xFFFFFF and color)

    private fun parsePackageList(value: String): Set<String> {
        if (value.isBlank()) return emptySet()
        return runCatching {
            val array = JSONArray(value)
            buildSet {
                for (i in 0 until array.length()) {
                    val pkg = array.optString(i).trim()
                    if (pkg.isNotEmpty()) add(pkg)
                }
            }
        }.getOrElse {
            value.split(",").mapNotNullTo(mutableSetOf()) { it.trim().takeIf(String::isNotEmpty) }
        }
    }

    // --- Premium gating ---

    private fun applyPremiumGating() {
        val notActivated = PremiumActivator.status(this) == PremiumActivator.Status.NotActivated
        val card = findViewById<LinearLayout>(R.id.card_general)
        card.alpha = if (notActivated) DISABLED_ALPHA else 1f
        setGroupEnabled(card, !notActivated)
    }

    private fun setGroupEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) setGroupEnabled(view.getChildAt(i), enabled)
        }
    }

    private companion object {
        const val DEFAULT_COLOR = "#00FFFF"
        const val DISABLED_ALPHA = 0.45f
    }
}
