package com.fan.edgex.ui

import android.content.Context
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

class ActionSelectionActivity : AppCompatActivity() {

    data class ActionItem(val label: String, val code: String, val iconRes: Int)

    private val actions get() = listOf(
        ActionItem(getString(R.string.action_default), "default", R.drawable.ic_action_dot),
        ActionItem(getString(R.string.action_none), "none", R.drawable.ic_action_dot),
        ActionItem(getString(R.string.action_back), "back", R.drawable.ic_arrow_back),
        ActionItem(getString(R.string.action_home), "home", R.drawable.ic_edge_panel),
        ActionItem(getString(R.string.action_recents), "recents", R.drawable.ic_edge_panel),
        ActionItem(getString(R.string.action_freezer_drawer), "freezer_drawer", R.drawable.ic_freezer),
        ActionItem(getString(R.string.action_refreeze), "refreeze", R.drawable.ic_freezer),
        ActionItem(getString(R.string.action_screenshot), "screenshot", R.drawable.ic_camera),
        ActionItem(getString(R.string.action_universal_copy), "universal_copy", R.drawable.ic_content_copy),
        ActionItem(getString(R.string.action_app_shortcut), "app_shortcut", R.drawable.ic_apps)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_action_selection)

        // Header Insets
        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.systemWindowInsetTop, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Get Args
        val title = intent.getStringExtra("title") ?: "Action"
        val prefKey = intent.getStringExtra("pref_key") ?: "unknown"
        
        findViewById<TextView>(R.id.tv_subtitle).text = title

        // List
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ActionAdapter(actions) { item ->
            if (item.code == "app_shortcut") {
                // Launch ShortcutSelectionActivity
                val intent = Intent(this, ShortcutSelectionActivity::class.java)
                intent.putExtra("pref_key", prefKey)
                startActivity(intent)
                finish()
            } else {
                // Save Selection
                val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
                prefs.edit().putString(prefKey, item.code).apply() // Valid Code: "freezer_drawer" etc
                // Saving "Label" for simple display updating in previous screen.
                prefs.edit().putString(prefKey + "_label", item.label).apply()
                
                // Notify Hook to refresh (CRITICAL for instant update)
                contentResolver.notifyChange(android.net.Uri.parse("content://com.fan.edgex.provider/config"), null)
                
                finish()
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
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
