package com.fan.edgex.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.putConfig

@Deprecated("Use Compose ActionSelectionSheet instead")
class ActionSelectionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EXCLUDED_CODES = "excluded_codes"

        fun actionIconRes(code: String): Int = when {
            code.isEmpty() || code == "none" -> R.drawable.ic_action_dot
            code == "back" -> R.drawable.ic_arrow_back
            code == "home" -> R.drawable.ic_home
            code == "recents" -> R.drawable.ic_recents
            code == "expand_notifications" -> R.drawable.ic_notifications
            code.startsWith("shell:") -> R.drawable.ic_terminal
            code == "sub_gesture" -> R.drawable.ic_sub_gesture
            code == "pie" -> R.drawable.ic_pie_menu
            code.startsWith("launch_app:") -> R.drawable.ic_launch_app
            code.startsWith("app_shortcut:") -> R.drawable.ic_app_shortcut
            code == "clear_background" -> R.drawable.ic_clear_recent
            code == "freezer_drawer" -> R.drawable.ic_freezer
            code == "refreeze" -> R.drawable.ic_refreeze
            code == "screenshot" -> R.drawable.ic_camera
            code == AppConfig.PARTIAL_SCREENSHOT_ACTION -> R.drawable.ic_partial_screenshot
            code == "clipboard" -> R.drawable.ic_paste
            code == "universal_copy" -> R.drawable.ic_content_copy
            code == "lock_screen" -> R.drawable.ic_power
            code == "kill_app" -> R.drawable.ic_kill_app
            code == "prev_app" -> R.drawable.ic_prev_app
            code == "next_app" -> R.drawable.ic_next_app
            code == "brightness_up" -> R.drawable.ic_brightness_up
            code == "brightness_down" -> R.drawable.ic_brightness_down
            code == "volume_up" -> R.drawable.ic_volume_up
            code == "volume_down" -> R.drawable.ic_volume_down
            code.startsWith("music_control:") -> R.drawable.ic_music
            code.startsWith("fast_scroll:") -> when (code.removePrefix("fast_scroll:")) {
                "to_top" -> R.drawable.ic_scroll_to_top
                "to_bottom" -> R.drawable.ic_scroll_to_bottom
                else -> R.drawable.ic_fast_scroll
            }
            code.startsWith("multi_action:") -> R.drawable.ic_multi_action
            code.startsWith("condition:") -> R.drawable.ic_condition
            code == AppConfig.CUSTOM_PANEL_ACTION -> R.drawable.ic_apps
            code == AppConfig.SIDE_BAR_LEFT_ACTION -> R.drawable.ic_side_bar_left
            code == AppConfig.SIDE_BAR_RIGHT_ACTION -> R.drawable.ic_side_bar_right
            code == "toggle_flashlight" -> R.drawable.ic_flashlight
            code == "toggle_wifi" -> R.drawable.ic_wifi
            code == "toggle_mobile_data" -> R.drawable.ic_mobile_data
            code == "game_mode" -> R.drawable.ic_game_mode
            else -> R.drawable.ic_action_dot
        }

        fun applyActionIcon(context: Context, code: String, imageView: ImageView) {
            if (code.startsWith("launch_app:")) {
                val pkg = code.removePrefix("launch_app:")
                val icon = runCatching {
                    context.packageManager.getApplicationIcon(pkg)
                }.getOrNull()
                if (icon != null) {
                    imageView.setImageDrawable(icon)
                    imageView.imageTintList = null
                    return
                }
            }
            imageView.setImageResource(actionIconRes(code))
            imageView.imageTintList = ColorStateList.valueOf(Color.WHITE)
        }

        fun resolveActionLabel(context: Context, code: String, fallbackLabel: String): String {
            if (code.isEmpty() || code == "none") {
                return context.getString(R.string.action_none)
            }
            if (code.startsWith("music_control:")) {
                val subCode = code.removePrefix("music_control:")
                val resId = when (subCode) {
                    "play_pause" -> R.string.action_music_play_pause
                    "stop" -> R.string.action_music_stop
                    "previous" -> R.string.action_music_previous
                    "next" -> R.string.action_music_next
                    else -> R.string.action_music_control
                }
                return context.getString(R.string.label_music_prefix, context.getString(resId))
            }
            if (code.startsWith("fast_scroll:")) {
                val subCode = code.removePrefix("fast_scroll:")
                val resId = when (subCode) {
                    "to_top" -> R.string.action_scroll_to_top
                    "to_bottom" -> R.string.action_scroll_to_bottom
                    else -> R.string.action_fast_scroll
                }
                return context.getString(resId)
            }
            val resId = when (code) {
                "back" -> R.string.action_back
                "home" -> R.string.action_home
                "recents" -> R.string.action_recents
                "expand_notifications" -> R.string.action_expand_notifications
                "clear_background" -> R.string.action_clear_background
                "freezer_drawer" -> R.string.action_freezer_drawer
                "refreeze" -> R.string.action_refreeze
                "screenshot" -> R.string.action_screenshot
                AppConfig.PARTIAL_SCREENSHOT_ACTION -> R.string.action_partial_screenshot
                "clipboard" -> R.string.action_clipboard
                "universal_copy" -> R.string.action_universal_copy
                "lock_screen" -> R.string.action_lock_screen
                "kill_app" -> R.string.action_kill_app
                "prev_app" -> R.string.action_prev_app
                "next_app" -> R.string.action_next_app
                "brightness_up" -> R.string.action_brightness_up
                "brightness_down" -> R.string.action_brightness_down
                "volume_up" -> R.string.action_volume_up
                "volume_down" -> R.string.action_volume_down
                "toggle_flashlight" -> R.string.action_toggle_flashlight
                "toggle_wifi" -> R.string.action_toggle_wifi
                "toggle_mobile_data" -> R.string.action_toggle_mobile_data
                "game_mode" -> R.string.action_game_mode
                "sub_gesture" -> R.string.action_sub_gesture
                "pie" -> R.string.action_pie
                AppConfig.CUSTOM_PANEL_ACTION -> R.string.action_custom_panel
                AppConfig.SIDE_BAR_LEFT_ACTION -> R.string.action_left_side_bar
                AppConfig.SIDE_BAR_RIGHT_ACTION -> R.string.action_right_side_bar
                else -> 0
            }
            if (resId != 0) {
                return context.getString(resId)
            }
            return fallbackLabel
        }
    }

    data class ActionItem(val label: String, val code: String, val iconRes: Int)

    private fun actions(excludedCodes: Set<String>) = listOf(
        ActionItem(getString(R.string.action_none), "none", R.drawable.ic_action_dot),
        ActionItem(getString(R.string.action_back), "back", R.drawable.ic_arrow_back),
        ActionItem(getString(R.string.action_home), "home", R.drawable.ic_home),
        ActionItem(getString(R.string.action_recents), "recents", R.drawable.ic_recents),
        ActionItem(getString(R.string.action_expand_notifications), "expand_notifications", R.drawable.ic_notifications),
        ActionItem(getString(R.string.action_shell_command), "shell_command", R.drawable.ic_terminal),
        ActionItem(getString(R.string.action_sub_gesture), "sub_gesture", R.drawable.ic_sub_gesture),
        ActionItem(getString(R.string.action_pie), "pie", R.drawable.ic_pie_menu),
        ActionItem(getString(R.string.action_launch_app), "launch_app", R.drawable.ic_launch_app),
        ActionItem(getString(R.string.action_app_shortcut), "app_shortcut", R.drawable.ic_app_shortcut),
        ActionItem(getString(R.string.action_clear_background), "clear_background", R.drawable.ic_clear_recent),
        ActionItem(getString(R.string.action_freezer_drawer), "freezer_drawer", R.drawable.ic_freezer),
        ActionItem(getString(R.string.action_refreeze), "refreeze", R.drawable.ic_refreeze),
        ActionItem(getString(R.string.action_screenshot), "screenshot", R.drawable.ic_camera),
        ActionItem(getString(R.string.action_partial_screenshot), AppConfig.PARTIAL_SCREENSHOT_ACTION, R.drawable.ic_partial_screenshot),
        ActionItem(getString(R.string.action_clipboard), "clipboard", R.drawable.ic_paste),
        ActionItem(getString(R.string.action_universal_copy), "universal_copy", R.drawable.ic_content_copy),
        ActionItem(getString(R.string.action_lock_screen), "lock_screen", R.drawable.ic_power),
        ActionItem(getString(R.string.action_kill_app), "kill_app", R.drawable.ic_kill_app),
        ActionItem(getString(R.string.action_prev_app), "prev_app", R.drawable.ic_prev_app),
        ActionItem(getString(R.string.action_next_app), "next_app", R.drawable.ic_next_app),
        ActionItem(getString(R.string.action_brightness_up), "brightness_up", R.drawable.ic_brightness_up),
        ActionItem(getString(R.string.action_brightness_down), "brightness_down", R.drawable.ic_brightness_down),
        ActionItem(getString(R.string.action_volume_up), "volume_up", R.drawable.ic_volume_up),
        ActionItem(getString(R.string.action_volume_down), "volume_down", R.drawable.ic_volume_down),
        ActionItem(getString(R.string.action_music_control), "music_control", R.drawable.ic_music),
        ActionItem(getString(R.string.action_fast_scroll), "fast_scroll", R.drawable.ic_fast_scroll),
        ActionItem(getString(R.string.action_multi_action), "multi_action", R.drawable.ic_multi_action),
        ActionItem(getString(R.string.action_condition), "condition", R.drawable.ic_condition),
        ActionItem(getString(R.string.action_custom_panel), AppConfig.CUSTOM_PANEL_ACTION, R.drawable.ic_apps),
        ActionItem(getString(R.string.action_left_side_bar), AppConfig.SIDE_BAR_LEFT_ACTION, R.drawable.ic_side_bar_left),
        ActionItem(getString(R.string.action_right_side_bar), AppConfig.SIDE_BAR_RIGHT_ACTION, R.drawable.ic_side_bar_right),
        ActionItem(getString(R.string.action_toggle_flashlight), "toggle_flashlight", R.drawable.ic_flashlight),
        ActionItem(getString(R.string.action_toggle_wifi), "toggle_wifi", R.drawable.ic_wifi),
        ActionItem(getString(R.string.action_toggle_mobile_data), "toggle_mobile_data", R.drawable.ic_mobile_data),
        ActionItem(getString(R.string.action_game_mode), "game_mode", R.drawable.ic_game_mode),
    ).filter { it.code !in excludedCodes }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_action_selection)
        ThemeManager.applyToActivity(this)

        // Header Insets
        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.getInsets(android.view.WindowInsets.Type.statusBars()).top, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Get Args
        val title = intent.getStringExtra("title") ?: getString(R.string.header_action_selection)
        val prefKey = intent.getStringExtra("pref_key") ?: "unknown"

        findViewById<TextView>(R.id.tv_subtitle).text = title

        // List
        val excludedCodes = (intent.getStringArrayExtra(EXTRA_EXCLUDED_CODES)?.toSet() ?: emptySet()).let { base ->
            if (prefKey.startsWith("pie_")) base + "pie" else base
        }
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = ActionAdapter(actions(excludedCodes)) { item ->
            when (item.code) {
                "app_shortcut" -> {
                    startActivity(Intent(this, ShortcutSelectionActivity::class.java)
                        .putExtra("pref_key", prefKey))
                    finish()
                }
                "shell_command" -> {
                    startActivity(Intent(this, ShellCommandActivity::class.java)
                        .putExtra("pref_key", prefKey))
                    finish()
                }
                "sub_gesture" -> {
                    putConfig(prefKey, "sub_gesture")
                    putConfig("${prefKey}_label", getString(R.string.action_sub_gesture))
                    startActivity(Intent(this, SubGestureActivity::class.java)
                        .putExtra("pref_key", prefKey)
                        .putExtra("title", title)
                        .putExtra(EXTRA_EXCLUDED_CODES, intent.getStringArrayExtra(EXTRA_EXCLUDED_CODES)))
                    finish()
                }
                "pie" -> {
                    putConfig(prefKey, "pie")
                    putConfig("${prefKey}_label", getString(R.string.action_pie))
                    finish()
                }
                "launch_app" -> {
                    startActivity(Intent(this, AppSelectionActivity::class.java)
                        .putExtra("pref_key", prefKey))
                    finish()
                }
                "music_control" -> {
                    startActivity(Intent(this, MusicControlActivity::class.java)
                        .putExtra("pref_key", prefKey))
                    finish()
                }
                "fast_scroll" -> {
                    startActivity(Intent(this, FastScrollActivity::class.java)
                        .putExtra("pref_key", prefKey))
                    finish()
                }
                "multi_action" -> {
                    startActivity(Intent(this, MultiActionsListActivity::class.java)
                        .putExtra(MultiActionsListActivity.EXTRA_MODE, MultiActionsListActivity.MODE_PICK)
                        .putExtra(MultiActionsListActivity.EXTRA_PREF_KEY, prefKey)
                        .putExtra(MultiActionsListActivity.EXTRA_TITLE, title))
                    finish()
                }
                "condition" -> {
                    startActivity(Intent(this, ConditionActionActivity::class.java)
                        .putExtra("pref_key", prefKey)
                        .putExtra("title", title)
                        .putExtra(EXTRA_EXCLUDED_CODES, intent.getStringArrayExtra(EXTRA_EXCLUDED_CODES)))
                    finish()
                }
                else -> {
                    putConfig(prefKey, item.code)
                    putConfig("${prefKey}_label", item.label)
                    finish()
                }
            }
        }
        recyclerView.adapter = adapter

        val etSearch = findViewById<EditText>(R.id.et_search)
        val titleBlock = findViewById<View>(R.id.title_block)
        val btnSearch = findViewById<ImageView>(R.id.btn_search)

        etSearch.addTextChangedListener { adapter.filter(it?.toString().orEmpty()) }

        btnSearch.setOnClickListener {
            if (etSearch.isGone) {
                titleBlock.isGone = true
                etSearch.isVisible = true
                etSearch.requestFocus()
            } else {
                if (etSearch.text.isEmpty()) {
                    etSearch.isGone = true
                    titleBlock.isVisible = true
                } else {
                    etSearch.text.clear()
                }
            }
        }
    }

    inner class ActionAdapter(
        private val allItems: List<ActionItem>,
        val onClick: (ActionItem) -> Unit,
    ) : RecyclerView.Adapter<ActionAdapter.ViewHolder>() {

        private var displayItems = allItems.toMutableList()

        fun filter(query: String) {
            displayItems = if (query.isBlank()) {
                allItems.toMutableList()
            } else {
                allItems.filter { it.label.contains(query, ignoreCase = true) }.toMutableList()
            }
            notifyDataSetChanged()
        }

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.icon)
            val title: TextView = v.findViewById(R.id.title)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_action_selection, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = displayItems[position]
            holder.title.text = item.label
            holder.icon.setImageResource(item.iconRes)
            ThemeManager.applyToView(holder.itemView, this@ActionSelectionActivity)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = displayItems.size
    }
}
