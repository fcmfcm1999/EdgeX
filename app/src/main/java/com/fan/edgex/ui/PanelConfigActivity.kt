package com.fan.edgex.ui

import android.content.Intent
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig

class PanelConfigActivity : AppCompatActivity() {

    private lateinit var mode: String
    private lateinit var content: LinearLayout
    private val slotRows = mutableListOf<SlotRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_CUSTOM
        buildLayout()
        ThemeManager.applyToActivity(this)
    }

    override fun onResume() {
        super.onResume()
        syncRuntimeTitles()
        refreshSlots()
        ThemeManager.applyToActivity(this)
    }

    private fun buildLayout() {
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.ui_background, theme))
        }

        val header = LinearLayout(this).apply {
            id = R.id.header_container
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(resources.getColor(R.color.ui_header_background, theme))
            setPadding((8 * dp).toInt(), 0, (16 * dp).toInt(), 0)
            minimumHeight = resources.getDimensionPixelSize(
                androidx.appcompat.R.dimen.abc_action_bar_default_height_material
            )
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(
                    view.paddingLeft,
                    insets.getInsets(android.view.WindowInsets.Type.statusBars()).top,
                    view.paddingRight,
                    view.paddingBottom,
                )
                insets
            }
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(resources.getColor(R.color.ui_header_text, theme))
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt()))
        header.addView(TextView(this).apply {
            text = titleForMode()
            textSize = 20f
            setTextColor(resources.getColor(R.color.ui_header_text, theme))
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, (56 * dp).toInt(), 1f))
        root.addView(header)

        val scrollView = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * dp).toInt(), 0, (24 * dp).toInt())
        }
        scrollView.addView(content)
        root.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))

        setContentView(root)
        populateContent()
    }

    private fun populateContent() {
        content.removeAllViews()
        slotRows.clear()
        if (mode == MODE_CUSTOM) {
            repeat(AppConfig.CUSTOM_PANEL_ROWS) { row ->
                addSection(getString(R.string.panel_row_title, row + 1))
                val line = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                }
                repeat(AppConfig.CUSTOM_PANEL_COLUMNS) { column ->
                    line.addView(createSlotRow(
                        title = getString(R.string.panel_column_title, column + 1),
                        prefKey = AppConfig.customPanelSlot(row, column),
                    ))
                }
                content.addView(line)
            }
        } else {
            val side = sideForMode()
            repeat(AppConfig.SIDE_BAR_SLOTS) { index ->
                content.addView(createSlotRow(
                    title = getString(R.string.panel_slot_title, index + 1),
                    prefKey = AppConfig.sideBarSlot(side, index),
                ))
            }
        }
    }

    private fun createSlotRow(title: String, prefKey: String): View {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            background = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).use {
                it.getDrawable(0)
            }
            isClickable = true
            isFocusable = true
        }
        val iconBox = FrameLayout(this).apply {
            tag = "theme_icon_bg"
            background = resources.getDrawable(R.drawable.circle_background_teal, theme)
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_action_dot)
            setColorFilter(resources.getColor(R.color.ui_icon_tint, theme))
        }
        iconBox.addView(icon, FrameLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt(), Gravity.CENTER))
        row.addView(iconBox, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()))

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), 0, 0, 0)
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(resources.getColor(R.color.ui_text_primary, theme))
        }
        val subtitle = TextView(this).apply {
            textSize = 13f
            setTextColor(resources.getColor(R.color.ui_text_secondary, theme))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        texts.addView(titleView)
        texts.addView(subtitle)
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.setOnClickListener {
            startActivity(Intent(this, ActionSelectionActivity::class.java)
                .putExtra("pref_key", prefKey)
                .putExtra("title", title)
                .putExtra(ActionSelectionActivity.EXTRA_EXCLUDED_CODES, excludedActionCodes()))
        }
        slotRows += SlotRow(prefKey, icon, subtitle)
        ThemeManager.applyToView(row, this)
        return row
    }

    private fun addSection(title: String) {
        val dp = resources.displayMetrics.density
        content.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(resources.getColor(R.color.ui_text_primary, theme))
            setPadding((16 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        })
    }

    private fun refreshSlots() {
        slotRows.forEach { slot ->
            val action = getConfigString(slot.prefKey, "none")
            val savedLabel = getConfigString("${slot.prefKey}_label", getString(R.string.action_none))
            val label = displayTitleForAction(action, savedLabel)
            slot.subtitle.text = label
            val usesAppIcon = action.startsWith("launch_app:") || action.startsWith("app_shortcut:")
            val iconSize = if (usesAppIcon) 34 else 24
            slot.icon.layoutParams = FrameLayout.LayoutParams(
                (iconSize * resources.displayMetrics.density).toInt(),
                (iconSize * resources.displayMetrics.density).toInt(),
                Gravity.CENTER,
            )
            slot.icon.setImageDrawable(drawableForAction(action))
            if (usesAppIcon) {
                slot.icon.clearColorFilter()
            } else {
                slot.icon.setColorFilter(resources.getColor(R.color.ui_icon_tint, theme))
            }
        }
    }

    private fun syncRuntimeTitles() {
        slotRows.forEach { slot ->
            val action = getConfigString(slot.prefKey, "none")
            val label = getConfigString("${slot.prefKey}_label")
            val title = displayTitleForAction(action, label)
            if (title.isNotBlank()) {
                putConfig("${slot.prefKey}_title", title)
            }
        }
    }

    private fun titleForMode(): String = when (mode) {
        MODE_SIDE_LEFT -> getString(R.string.menu_left_side_bar)
        MODE_SIDE_RIGHT -> getString(R.string.menu_right_side_bar)
        else -> getString(R.string.menu_custom_panel)
    }

    private fun sideForMode(): String = if (mode == MODE_SIDE_RIGHT) "right" else "left"

    private fun excludedActionCodes(): Array<String> = when (mode) {
        MODE_SIDE_LEFT -> arrayOf(AppConfig.SIDE_BAR_LEFT_ACTION, AppConfig.SIDE_BAR_RIGHT_ACTION, AppConfig.PIE_ACTION, "sub_gesture")
        MODE_SIDE_RIGHT -> arrayOf(AppConfig.SIDE_BAR_LEFT_ACTION, AppConfig.SIDE_BAR_RIGHT_ACTION, AppConfig.PIE_ACTION, "sub_gesture")
        else -> arrayOf(AppConfig.CUSTOM_PANEL_ACTION, AppConfig.PIE_ACTION, "sub_gesture")
    }

    private fun drawableForAction(action: String): android.graphics.drawable.Drawable? {
        if (action.startsWith("launch_app:")) {
            val packageName = action.removePrefix("launch_app:")
            val appIcon = runCatching {
                packageManager.getApplicationIcon(packageName)
            }.getOrNull()
            if (appIcon != null) return appIcon.foregroundOrSelf()
        }
        if (action.startsWith("app_shortcut:")) {
            val packageName = action.removePrefix("app_shortcut:").substringBefore(":")
            val appIcon = runCatching {
                packageManager.getApplicationIcon(packageName)
            }.getOrNull()
            if (appIcon != null) return appIcon.foregroundOrSelf()
        }
        return resources.getDrawable(iconForAction(action), theme)
    }

    private fun displayTitleForAction(action: String, savedLabel: String): String {
        if (action.isBlank() || action == "none") return getString(R.string.action_none)
        return when {
            action.startsWith("launch_app:") -> appLabel(action.removePrefix("launch_app:"))
                ?: stripKnownPrefix(savedLabel, "App:", "App: ", "应用：", "应用:", "应用: ")
                    .ifBlank { getString(R.string.action_launch_app) }
            action.startsWith("app_shortcut:") -> stripKnownPrefix(
                savedLabel,
                "Shortcut:",
                "Shortcut: ",
                "快捷方式:",
                "快捷方式: ",
                "快捷方式：",
            ).ifBlank {
                val packageName = action.removePrefix("app_shortcut:").substringBefore(":")
                appLabel(packageName) ?: getString(R.string.action_app_shortcut)
            }
            action.startsWith("shell:") -> shellCommandTitle(action, savedLabel)
            else -> savedLabel.ifBlank { action }
        }
    }

    private fun appLabel(packageName: String): String? = runCatching {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        appInfo.loadLabel(packageManager).toString()
    }.getOrNull()

    private fun shellCommandTitle(action: String, savedLabel: String): String {
        val command = action.removePrefix("shell:").split(":", limit = 2).getOrNull(1).orEmpty().trim()
        val saved = savedLabel.trim()
        return when {
            saved.isNotBlank() &&
                saved != getString(R.string.action_shell_command) &&
                saved != "Shell" &&
                saved != "Shell Command" &&
                saved != "Shell 命令" -> saved
            command.isNotBlank() -> command
            else -> getString(R.string.action_shell_command)
        }
    }

    private fun stripKnownPrefix(value: String, vararg prefixes: String): String {
        val trimmed = value.trim()
        val match = prefixes.firstOrNull { trimmed.startsWith(it) } ?: return trimmed
        return trimmed.removePrefix(match).trim()
    }

    private fun Drawable.foregroundOrSelf(): Drawable =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this is AdaptiveIconDrawable) {
            foreground?.mutate() ?: mutate()
        } else {
            mutate()
        }

    private fun iconForAction(action: String): Int = when {
        action == "back" -> R.drawable.ic_arrow_back
        action == "home" -> R.drawable.ic_home
        action == "recent" || action == "recents" -> R.drawable.ic_recents
        action == "expand_notifications" -> R.drawable.ic_notifications
        action == "shell_command" || action.startsWith("shell:") -> R.drawable.ic_terminal
        action == "sub_gesture" -> R.drawable.ic_sub_gesture
        action.startsWith("launch_app:") -> R.drawable.ic_launch_app
        action.startsWith("app_shortcut:") -> R.drawable.ic_app_shortcut
        action == "clear_background" -> R.drawable.ic_clear_recent
        action == "freezer_drawer" -> R.drawable.ic_freezer
        action == "refreeze" -> R.drawable.ic_refreeze
        action == "screenshot" -> R.drawable.ic_camera
        action == "clipboard" -> R.drawable.ic_paste
        action == "universal_copy" -> R.drawable.ic_content_copy
        action == "lock_screen" -> R.drawable.ic_power
        action == "kill_app" -> R.drawable.ic_kill_app
        action == "brightness_up" -> R.drawable.ic_brightness_up
        action == "brightness_down" -> R.drawable.ic_brightness_down
        action == "volume_up" -> R.drawable.ic_volume_up
        action == "volume_down" -> R.drawable.ic_volume_down
        action.startsWith("music_control:") -> R.drawable.ic_music
        action.startsWith("multi_action:") -> R.drawable.ic_multi_action
        action == AppConfig.CUSTOM_PANEL_ACTION -> R.drawable.ic_apps
        action == AppConfig.SIDE_BAR_LEFT_ACTION -> R.drawable.ic_side_bar_left
        action == AppConfig.SIDE_BAR_RIGHT_ACTION -> R.drawable.ic_side_bar_right
        action == "toggle_wifi" -> R.drawable.ic_wifi
        action == "toggle_mobile_data" -> R.drawable.ic_mobile_data
        else -> R.drawable.ic_action_dot
    }

    private data class SlotRow(
        val prefKey: String,
        val icon: ImageView,
        val subtitle: TextView,
    )

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_CUSTOM = "custom"
        const val MODE_SIDE_LEFT = "side_left"
        const val MODE_SIDE_RIGHT = "side_right"
    }
}
