package com.fan.edgex.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.CompoundButtonCompat
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig

object ThemeManager {
    const val PRESET_DEFAULT = "default"
    const val PRESET_CLASSIC = "classic"
    const val PRESET_CEDAR = "cedar"
    const val PRESET_OCEAN = "ocean"
    const val PRESET_EMBER = "ember"
    const val PRESET_CUSTOM = "custom"

    private const val DEFAULT_CUSTOM_COLOR = "#326D32"

    data class ThemePreset(
        val id: String,
        @StringRes val titleRes: Int,
        val accentColor: Int,
    )

    val presets = listOf(
        ThemePreset(PRESET_DEFAULT, R.string.theme_preset_default, Color.parseColor("#326D32")),
        ThemePreset(PRESET_CLASSIC, R.string.theme_preset_classic, Color.parseColor("#00796B")),
        ThemePreset(PRESET_CEDAR, R.string.theme_preset_cedar, Color.parseColor("#496B3D")),
        ThemePreset(PRESET_OCEAN, R.string.theme_preset_ocean, Color.parseColor("#2F6F8F")),
        ThemePreset(PRESET_EMBER, R.string.theme_preset_ember, Color.parseColor("#C56B2A")),
    )

    fun currentPresetId(context: Context): String =
        context.getConfigString(AppConfig.THEME_PRESET, PRESET_DEFAULT)

    fun currentAccent(context: Context): Int {
        val presetId = currentPresetId(context)
        if (presetId == PRESET_CUSTOM) {
            return parseColorOrDefault(context.getConfigString(AppConfig.THEME_CUSTOM_COLOR, DEFAULT_CUSTOM_COLOR))
        }
        return presets.firstOrNull { it.id == presetId }?.accentColor
            ?: presets.first { it.id == PRESET_DEFAULT }.accentColor
    }

    fun onAccentColor(accentColor: Int): Int =
        if (ColorUtils.calculateLuminance(accentColor) > 0.45) Color.BLACK else Color.WHITE

    fun displayColor(color: Int): String =
        String.format("#%06X", 0xFFFFFF and color)

    fun savePreset(context: Context, presetId: String) {
        context.putConfig(AppConfig.THEME_PRESET, presetId)
    }

    fun saveCustomColor(context: Context, color: Int) {
        context.putConfig(AppConfig.THEME_CUSTOM_COLOR, displayColor(color))
        context.putConfig(AppConfig.THEME_PRESET, PRESET_CUSTOM)
    }

    fun applyToActivity(activity: AppCompatActivity) {
        val root = activity.findViewById<View>(android.R.id.content) ?: return
        applyToView(root, activity)
    }

    fun applyToView(view: View, context: Context) {
        val accent = currentAccent(context)
        val onAccent = onAccentColor(accent)
        val secondaryOnAccent = ColorUtils.setAlphaComponent(onAccent, 179)
        applyRecursively(view, accent, onAccent, secondaryOnAccent)
    }

    fun tintSwatch(view: View, color: Int) {
        tintBackground(view, color)
    }

    private fun applyRecursively(view: View, accent: Int, onAccent: Int, secondaryOnAccent: Int) {
        if (view.id == R.id.header_container) {
            view.setBackgroundColor(accent)
            tintHeaderContent(view, onAccent, secondaryOnAccent)
        }

        if (view.tag == "theme_icon_bg") {
            tintBackground(view, accent)
        }

        when (view) {
            is android.widget.CompoundButton -> {
                CompoundButtonCompat.setButtonTintList(view, ColorStateList.valueOf(accent))
            }
            is Button -> {
                ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(accent))
                view.setTextColor(onAccent)
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyRecursively(view.getChildAt(index), accent, onAccent, secondaryOnAccent)
            }
        }
    }

    private fun tintHeaderContent(view: View, onAccent: Int, secondaryOnAccent: Int) {
        when (view) {
            is ImageView -> view.setColorFilter(onAccent)
            is EditText -> {
                view.setTextColor(onAccent)
                view.setHintTextColor(secondaryOnAccent)
            }
            is TextView -> {
                val color = if (view.id == R.id.tv_subtitle) secondaryOnAccent else onAccent
                view.setTextColor(color)
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                tintHeaderContent(view.getChildAt(index), onAccent, secondaryOnAccent)
            }
        }
    }

    private fun tintBackground(view: View, color: Int) {
        val background = view.background?.mutate() ?: return
        when (background) {
            is GradientDrawable -> background.setColor(color)
            else -> DrawableCompat.setTint(background, color)
        }
        view.background = background
    }

    private fun parseColorOrDefault(value: String): Int =
        runCatching { Color.parseColor(value) }.getOrElse { Color.parseColor(DEFAULT_CUSTOM_COLOR) }
}
