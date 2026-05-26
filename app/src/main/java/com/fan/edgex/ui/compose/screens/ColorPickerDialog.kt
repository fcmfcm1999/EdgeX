package com.fan.edgex.ui.compose.screens

import android.content.Context
import android.graphics.Color
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.fan.edgex.R
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import com.fan.edgex.ui.ColorPickerView

internal object ColorPickerDialog {

    fun show(
        context: Context,
        title: String,
        configKey: String,
        defaultColor: String,
        allowReset: Boolean = false,
        onColorSaved: (Int) -> Unit,
    ) {
        val dialogView = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null)
        val picker = dialogView.findViewById<ColorPickerView>(R.id.color_picker_view)
        val preview = dialogView.findViewById<android.view.View>(R.id.preview_swatch)
        val hex = dialogView.findViewById<EditText>(R.id.et_hex)
        val red = dialogView.findViewById<EditText>(R.id.et_r)
        val green = dialogView.findViewById<EditText>(R.id.et_g)
        val blue = dialogView.findViewById<EditText>(R.id.et_b)
        val alpha = dialogView.findViewById<EditText>(R.id.et_a)

        val stored = context.getConfigString(configKey).ifBlank { defaultColor }
        picker.setColor(parseColor(stored))

        var isUpdating = false

        fun syncUi(color: Int) {
            if (isUpdating) return
            isUpdating = true
            preview.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 8f * context.resources.displayMetrics.density
                setColor(color)
                setStroke(
                    (1f * context.resources.displayMetrics.density).toInt(),
                    ContextCompat.getColor(context, R.color.ui_divider),
                )
            }
            val colorHex = formatColor(color)
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

        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val picked = picker.getColor()
                context.putConfig(configKey, formatColor(picked))
                onColorSaved(picked)
            }
            .setNegativeButton(android.R.string.cancel, null)

        if (allowReset) {
            builder.setNeutralButton(R.string.fluid_effect_color_default) { _, _ ->
                context.putConfig(configKey, "")
                onColorSaved(parseColor(defaultColor))
            }
        }

        builder.show()
    }
}

private fun parseColor(value: String): Int {
    val normalized = value.removePrefix("#").ifBlank { "CCFFFFFF" }
    return runCatching {
        when (normalized.length) {
            6 -> Color.rgb(
                normalized.substring(0, 2).toInt(16),
                normalized.substring(2, 4).toInt(16),
                normalized.substring(4, 6).toInt(16),
            )
            8 -> java.lang.Long.parseLong(normalized, 16).toInt()
            else -> Color.parseColor("#CCFFFFFF")
        }
    }.getOrDefault(Color.parseColor("#CCFFFFFF"))
}

private fun formatColor(color: Int): String =
    "#%08X".format(color.toLong() and 0xFFFFFFFFL)

private fun simpleWatcher(onChanged: (String) -> Unit) = object : android.text.TextWatcher {
    override fun afterTextChanged(s: android.text.Editable) = onChanged(s.toString())
    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
}

private fun setTextIfChanged(editText: EditText, value: String) {
    if (editText.text.toString() != value) editText.setText(value)
}
