package com.fan.edgex.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.edgex.R
import com.fan.edgex.config.putConfig

class ActionSelectionActivity : AppCompatActivity() {

    data class ActionItem(val label: String, val code: String, val iconRes: Int)

    private val actions get() = listOf(
        ActionItem(getString(R.string.action_none), "none", R.drawable.ic_action_dot),
        ActionItem(getString(R.string.action_back), "back", R.drawable.ic_arrow_back),
        ActionItem(getString(R.string.action_home), "home", R.drawable.ic_home),
        ActionItem(getString(R.string.action_recents), "recents", R.drawable.ic_recents),
        ActionItem(getString(R.string.action_expand_notifications), "expand_notifications", R.drawable.ic_notifications),
        ActionItem(getString(R.string.action_shell_command), "shell_command", R.drawable.ic_terminal),
        ActionItem(getString(R.string.action_sub_gesture), "sub_gesture", R.drawable.ic_sub_gesture),
        ActionItem(getString(R.string.action_launch_app), "launch_app", R.drawable.ic_launch_app),
        ActionItem(getString(R.string.action_app_shortcut), "app_shortcut", R.drawable.ic_app_shortcut),
        ActionItem(getString(R.string.action_clear_background), "clear_background", R.drawable.ic_clear_recent),
        ActionItem(getString(R.string.action_freezer_drawer), "freezer_drawer", R.drawable.ic_freezer),
        ActionItem(getString(R.string.action_refreeze), "refreeze", R.drawable.ic_refreeze),
        ActionItem(getString(R.string.action_screenshot), "screenshot", R.drawable.ic_camera),
        ActionItem(getString(R.string.action_universal_copy), "universal_copy", R.drawable.ic_content_copy),
        ActionItem(getString(R.string.action_lock_screen), "lock_screen", R.drawable.ic_power),
        ActionItem(getString(R.string.action_kill_app), "kill_app", R.drawable.ic_kill_app),
        ActionItem(getString(R.string.action_brightness_up), "brightness_up", R.drawable.ic_brightness_up),
        ActionItem(getString(R.string.action_brightness_down), "brightness_down", R.drawable.ic_brightness_down),
        ActionItem(getString(R.string.action_volume_up), "volume_up", R.drawable.ic_volume_up),
        ActionItem(getString(R.string.action_volume_down), "volume_down", R.drawable.ic_volume_down),
        ActionItem(getString(R.string.action_music_control), "music_control", R.drawable.ic_music),
    )

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
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ActionAdapter(actions) { item ->
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
                        .putExtra("title", title))
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
                else -> {
                    putConfig(prefKey, item.code)
                    putConfig("${prefKey}_label", item.label)
                    finish()
                }
            }
        }
    }

    inner class ActionAdapter(val items: List<ActionItem>, val onClick: (ActionItem) -> Unit) : RecyclerView.Adapter<ActionAdapter.ViewHolder>() {
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.icon)
            val title: TextView = v.findViewById(R.id.title)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_action_selection, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.label
            holder.icon.setImageResource(item.iconRes)
            ThemeManager.applyToView(holder.itemView, this@ActionSelectionActivity)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
