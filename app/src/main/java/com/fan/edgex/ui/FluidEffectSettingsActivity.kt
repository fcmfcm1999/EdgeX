package com.fan.edgex.ui

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import com.fan.edgex.license.PremiumActivator

class FluidEffectSettingsActivity : AppCompatActivity() {

    private data class ColorRow(
        val configKey: String,
        val rowId: Int,
        val swatchId: Int,
        val valueId: Int,
        val labelRes: Int,
        val allowReset: Boolean,
    )

    private lateinit var previewView: FluidEffectPreviewView
    private var previewAnimator: ValueAnimator? = null

    private val colorRows = listOf(
        ColorRow(
            AppConfig.FLUID_EFFECT_COLOR,
            R.id.item_fluid_effect_color_global,
            R.id.swatch_fluid_global,
            R.id.text_fluid_color_global_value,
            R.string.fluid_effect_color_global,
            allowReset = false,
        ),
        ColorRow(
            AppConfig.FLUID_EFFECT_COLOR_LEFT,
            R.id.item_fluid_effect_color_left,
            R.id.swatch_fluid_left,
            R.id.text_fluid_color_left_value,
            R.string.fluid_effect_color_left,
            allowReset = true,
        ),
        ColorRow(
            AppConfig.FLUID_EFFECT_COLOR_RIGHT,
            R.id.item_fluid_effect_color_right,
            R.id.swatch_fluid_right,
            R.id.text_fluid_color_right_value,
            R.string.fluid_effect_color_right,
            allowReset = true,
        ),
        ColorRow(
            AppConfig.FLUID_EFFECT_COLOR_TOP,
            R.id.item_fluid_effect_color_top,
            R.id.swatch_fluid_top,
            R.id.text_fluid_color_top_value,
            R.string.fluid_effect_color_top,
            allowReset = true,
        ),
        ColorRow(
            AppConfig.FLUID_EFFECT_COLOR_BOTTOM,
            R.id.item_fluid_effect_color_bottom,
            R.id.swatch_fluid_bottom,
            R.id.text_fluid_color_bottom_value,
            R.string.fluid_effect_color_bottom,
            allowReset = true,
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fluid_effect_settings)
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

        previewView = findViewById(R.id.preview_fluid_effect)

        setupEnabledSwitch()
        setupColorRows()
        setupSeekBars()
        syncPreviewFromConfig()
        applyPremiumGating()
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.applyToActivity(this)
        startPreviewAnimation()
        applyPremiumGating()
    }

    override fun onPause() {
        super.onPause()
        stopPreviewAnimation()
    }

    private fun setupEnabledSwitch() {
        val enabledSwitch = findViewById<Switch>(R.id.switch_fluid_effect_enabled)
        enabledSwitch.isChecked = getConfigBool(AppConfig.FLUID_EFFECT_ENABLED, true)
        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            putConfig(AppConfig.FLUID_EFFECT_ENABLED, isChecked)
        }
    }

    private fun setupColorRows() {
        colorRows.forEach { row ->
            findViewById<View>(row.rowId).setOnClickListener {
                showColorPickerDialog(
                    title = getString(row.labelRes),
                    configKey = row.configKey,
                    allowReset = row.allowReset,
                ) {
                    refreshColorRows()
                    syncPreviewFromConfig()
                }
            }
        }
        refreshColorRows()
    }

    private fun setupSeekBars() {
        bindSeekBar(
            seekBarId = R.id.seek_fluid_size,
            valueId = R.id.text_fluid_size_value,
            key = AppConfig.FLUID_EFFECT_SIZE,
            defaultValue = AppConfig.FLUID_EFFECT_SIZE_DEFAULT,
            min = 0,
            max = 100,
            formatter = { getString(R.string.fluid_effect_size_value, elasticityForProgress(it)) },
            onChanged = {
                previewView.sizeProgress = it
            },
        )
        bindSeekBar(
            seekBarId = R.id.seek_fluid_alpha,
            valueId = R.id.text_fluid_alpha_value,
            key = AppConfig.FLUID_EFFECT_ALPHA,
            defaultValue = 80,
            min = 10,
            max = 100,
            formatter = { getString(R.string.fluid_effect_alpha_value, it) },
            readValue = {
                ((getConfigString(AppConfig.FLUID_EFFECT_ALPHA, "0.8").toFloatOrNull() ?: 0.8f) * 100).toInt()
            },
            saveValue = {
                putConfig(AppConfig.FLUID_EFFECT_ALPHA, (it / 100f).toString())
            },
            onChanged = {
                previewView.maxAlpha = it / 100f
            },
        )
    }

    private fun bindSeekBar(
        seekBarId: Int,
        valueId: Int,
        key: String,
        defaultValue: Int,
        min: Int,
        max: Int,
        formatter: (Int) -> String,
        readValue: () -> Int = { getConfigString(key, defaultValue.toString()).toIntOrNull() ?: defaultValue },
        saveValue: (Int) -> Unit = { putConfig(key, it.toString()) },
        onChanged: (Int) -> Unit,
    ) {
        val seekBar = findViewById<SeekBar>(seekBarId)
        val value = findViewById<TextView>(valueId)
        seekBar.max = max - min

        val initial = readValue().coerceIn(min, max)
        seekBar.progress = initial - min
        value.text = formatter(initial)
        onChanged(initial)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val current = (progress + min).coerceIn(min, max)
                value.text = formatter(current)
                if (fromUser) {
                    saveValue(current)
                    onChanged(current)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val current = ((seekBar?.progress ?: 0) + min).coerceIn(min, max)
                saveValue(current)
                onChanged(current)
            }
        })
    }

    private fun refreshColorRows() {
        val globalColor = parseColor(getConfigString(AppConfig.FLUID_EFFECT_COLOR, DEFAULT_COLOR))
        colorRows.forEach { row ->
            val stored = getConfigString(row.configKey)
            val usesDefault = row.allowReset && stored.isBlank()
            val color = if (usesDefault) globalColor else parseColor(stored.ifBlank { DEFAULT_COLOR })
            setSwatchColor(findViewById(row.swatchId), color)
            findViewById<View>(row.swatchId).alpha = if (usesDefault) 0.45f else 1f
            findViewById<TextView>(row.valueId).text =
                if (usesDefault) getString(R.string.fluid_effect_color_default) else "#${displayColor(color)}"
        }
    }

    private fun syncPreviewFromConfig() {
        val globalColor = parseColor(getConfigString(AppConfig.FLUID_EFFECT_COLOR, DEFAULT_COLOR))
        previewView.globalColor = globalColor
        previewView.leftColor = edgeOverride(AppConfig.FLUID_EFFECT_COLOR_LEFT)
        previewView.rightColor = edgeOverride(AppConfig.FLUID_EFFECT_COLOR_RIGHT)
        previewView.topColor = edgeOverride(AppConfig.FLUID_EFFECT_COLOR_TOP)
        previewView.bottomColor = edgeOverride(AppConfig.FLUID_EFFECT_COLOR_BOTTOM)
        previewView.sizeProgress = getConfigString(
            AppConfig.FLUID_EFFECT_SIZE,
            AppConfig.FLUID_EFFECT_SIZE_DEFAULT.toString(),
        ).toIntOrNull()?.coerceIn(0, 100) ?: AppConfig.FLUID_EFFECT_SIZE_DEFAULT
        previewView.maxAlpha = getConfigString(AppConfig.FLUID_EFFECT_ALPHA, "0.8")
            .toFloatOrNull()
            ?.coerceIn(0f, 1f)
            ?: 0.8f
    }

    private fun edgeOverride(key: String): Int? =
        getConfigString(key).takeIf(String::isNotBlank)?.let(::parseColor)

    private fun startPreviewAnimation() {
        stopPreviewAnimation()
        previewAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { previewView.progress = it.animatedValue as Float }
            start()
        }
    }

    private fun stopPreviewAnimation() {
        previewAnimator?.cancel()
        previewAnimator = null
    }

    private fun showColorPickerDialog(
        title: String,
        configKey: String,
        allowReset: Boolean,
        onSaved: () -> Unit,
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_color_picker, null)
        val picker = dialogView.findViewById<ColorPickerView>(R.id.color_picker_view)
        val preview = dialogView.findViewById<View>(R.id.preview_swatch)
        val hex = dialogView.findViewById<EditText>(R.id.et_hex)
        val red = dialogView.findViewById<EditText>(R.id.et_r)
        val green = dialogView.findViewById<EditText>(R.id.et_g)
        val blue = dialogView.findViewById<EditText>(R.id.et_b)
        val alpha = dialogView.findViewById<EditText>(R.id.et_a)

        val stored = getConfigString(configKey).ifBlank {
            getConfigString(AppConfig.FLUID_EFFECT_COLOR, DEFAULT_COLOR)
        }
        picker.setColor(parseColor(stored))

        var isUpdating = false

        fun syncUi(color: Int) {
            if (isUpdating) return
            isUpdating = true
            setSwatchColor(preview, color)
            val colorHex = displayColor(color)
            if (hex.text.toString() != colorHex) hex.setText(colorHex)
            setTextIfChanged(red, Color.red(color).toString())
            setTextIfChanged(green, Color.green(color).toString())
            setTextIfChanged(blue, Color.blue(color).toString())
            setTextIfChanged(alpha, Color.alpha(color).toString())
            isUpdating = false
        }

        syncUi(picker.getColor())
        picker.onColorChanged = { syncUi(it) }

        hex.addTextChangedListener(simpleWatcher { value ->
            if (!isUpdating && value.length == 8) {
                runCatching { java.lang.Long.parseLong(value, 16).toInt() }.onSuccess {
                    picker.setColor(it)
                    syncUi(it)
                }
            }
        })

        fun componentWatcher(component: Char) = simpleWatcher { value ->
            if (isUpdating) return@simpleWatcher
            val componentValue = value.toIntOrNull()?.coerceIn(0, 255) ?: return@simpleWatcher
            val color = picker.getColor()
            val nextColor = when (component) {
                'r' -> Color.argb(Color.alpha(color), componentValue, Color.green(color), Color.blue(color))
                'g' -> Color.argb(Color.alpha(color), Color.red(color), componentValue, Color.blue(color))
                'b' -> Color.argb(Color.alpha(color), Color.red(color), Color.green(color), componentValue)
                'a' -> Color.argb(componentValue, Color.red(color), Color.green(color), Color.blue(color))
                else -> color
            }
            picker.setColor(nextColor)
            syncUi(nextColor)
        }

        red.addTextChangedListener(componentWatcher('r'))
        green.addTextChangedListener(componentWatcher('g'))
        blue.addTextChangedListener(componentWatcher('b'))
        alpha.addTextChangedListener(componentWatcher('a'))

        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                putConfig(configKey, displayColor(picker.getColor()))
                onSaved()
            }
            .setNegativeButton(android.R.string.cancel, null)

        if (allowReset) {
            builder.setNeutralButton(R.string.fluid_effect_color_default) { _, _ ->
                putConfig(configKey, "")
                onSaved()
            }
        }

        builder.show()
    }

    private fun applyPremiumGating() {
        val active = PremiumActivator.status(this) != PremiumActivator.Status.NotActivated
        val enabledRow = findViewById<View>(R.id.row_fluid_effect_enabled)
        val enabledSwitch = findViewById<Switch>(R.id.switch_fluid_effect_enabled)
        enabledRow.alpha = if (active) 1f else DISABLED_ALPHA
        setGroupEnabled(enabledRow, active)
        enabledSwitch.isEnabled = active
    }

    private fun setGroupEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                setGroupEnabled(view.getChildAt(index), enabled)
            }
        }
    }

    private fun setSwatchColor(view: View, color: Int) {
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * resources.displayMetrics.density
            setColor(color)
            setStroke((1f * resources.displayMetrics.density).toInt(), resources.getColor(R.color.ui_divider, null))
        }
    }

    private fun setTextIfChanged(editText: EditText, value: String) {
        if (editText.text.toString() != value) editText.setText(value)
    }

    private fun parseColor(value: String): Int {
        val normalized = value.removePrefix("#").ifBlank { DEFAULT_COLOR }
        return runCatching {
            when (normalized.length) {
                6 -> Color.rgb(
                    normalized.substring(0, 2).toInt(16),
                    normalized.substring(2, 4).toInt(16),
                    normalized.substring(4, 6).toInt(16),
                )
                8 -> java.lang.Long.parseLong(normalized, 16).toInt()
                else -> DEFAULT_COLOR.toLong(16).toInt()
            }
        }.getOrDefault(DEFAULT_COLOR.toLong(16).toInt())
    }

    private fun displayColor(color: Int): String =
        "%08X".format(color.toLong() and 0xFFFFFFFFL)

    private fun elasticityForProgress(progress: Int): String =
        "%.1fx".format(0.3f + progress.coerceIn(0, 100) * 0.027f)

    private fun simpleWatcher(onChanged: (String) -> Unit) = object : TextWatcher {
        override fun afterTextChanged(s: Editable) = onChanged(s.toString())
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
    }

    private companion object {
        const val DEFAULT_COLOR = "CCFFFFFF"
        const val DISABLED_ALPHA = 0.45f
    }
}
