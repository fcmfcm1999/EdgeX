package com.fan.edgex.ui

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R

class ThemeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme)
        ThemeManager.applyToActivity(this)

        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.systemWindowInsetTop, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_apply_custom).setOnClickListener { applyCustomColor() }

        renderPresetRows()
        refreshPreview()
    }

    private fun renderPresetRows() {
        val container = findViewById<LinearLayout>(R.id.preset_container)
        val currentPresetId = ThemeManager.currentPresetId(this)
        container.removeAllViews()

        ThemeManager.presets.forEach { preset ->
            val row = layoutInflater.inflate(R.layout.item_theme_preset, container, false)
            val swatch = row.findViewById<View>(R.id.swatch)
            val title = row.findViewById<TextView>(R.id.title)
            val radio = row.findViewById<RadioButton>(R.id.radio)

            ThemeManager.tintSwatch(swatch, preset.accentColor)
            title.text = getString(preset.titleRes)
            radio.isChecked = currentPresetId == preset.id
            radio.isClickable = false
            ThemeManager.applyToView(row, this)

            row.setOnClickListener {
                ThemeManager.savePreset(this, preset.id)
                ThemeManager.applyToActivity(this)
                renderPresetRows()
                refreshPreview()
                Toast.makeText(this, getString(R.string.toast_theme_saved), Toast.LENGTH_SHORT).show()
            }

            container.addView(row)
        }

        val accent = ThemeManager.currentAccent(this)
        val editRed = findViewById<EditText>(R.id.edit_red)
        val editGreen = findViewById<EditText>(R.id.edit_green)
        val editBlue = findViewById<EditText>(R.id.edit_blue)
        editRed.setText(Color.red(accent).toString())
        editGreen.setText(Color.green(accent).toString())
        editBlue.setText(Color.blue(accent).toString())

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = refreshCustomPreview()
        }
        editRed.addTextChangedListener(watcher)
        editGreen.addTextChangedListener(watcher)
        editBlue.addTextChangedListener(watcher)
    }

    private fun refreshPreview() {
        val accent = ThemeManager.currentAccent(this)
        ThemeManager.tintSwatch(findViewById(R.id.preview_swatch), accent)
        findViewById<TextView>(R.id.text_current_hex).text = ThemeManager.displayColor(accent)
    }

    private fun refreshCustomPreview() {
        val r = findViewById<EditText>(R.id.edit_red).text.toString().toIntOrNull() ?: return
        val g = findViewById<EditText>(R.id.edit_green).text.toString().toIntOrNull() ?: return
        val b = findViewById<EditText>(R.id.edit_blue).text.toString().toIntOrNull() ?: return
        if (r !in 0..255 || g !in 0..255 || b !in 0..255) return
        val color = Color.rgb(r, g, b)
        ThemeManager.tintSwatch(findViewById(R.id.custom_preview_swatch), color)
        findViewById<TextView>(R.id.text_custom_hex).text = ThemeManager.displayColor(color)
    }

    private fun applyCustomColor() {
        val redValue = findViewById<EditText>(R.id.edit_red).text.toString().toIntOrNull()
        val greenValue = findViewById<EditText>(R.id.edit_green).text.toString().toIntOrNull()
        val blueValue = findViewById<EditText>(R.id.edit_blue).text.toString().toIntOrNull()

        if (redValue !in 0..255 || greenValue !in 0..255 || blueValue !in 0..255) {
            Toast.makeText(this, getString(R.string.toast_theme_invalid_rgb), Toast.LENGTH_SHORT).show()
            return
        }

        val red = redValue ?: return
        val green = greenValue ?: return
        val blue = blueValue ?: return
        ThemeManager.saveCustomColor(this, Color.rgb(red, green, blue))
        ThemeManager.applyToActivity(this)
        renderPresetRows()
        refreshPreview()
        Toast.makeText(this, getString(R.string.toast_theme_saved), Toast.LENGTH_SHORT).show()
    }
}
