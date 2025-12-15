package com.fan.edgex.ui

import android.content.Context
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

    private val actions = listOf(
        ActionItem("默认 (Default)", "default", R.drawable.ic_action_dot),
        ActionItem("无 (None)", "none", R.drawable.ic_action_dot),
        ActionItem("返回 (Back)", "back", R.drawable.ic_arrow_back),
        ActionItem("主页 (Home)", "home", R.drawable.ic_edge_panel), // Placeholder icon
        ActionItem("最近应用 (Recents)", "recents", R.drawable.ic_edge_panel), // Placeholder icon
        ActionItem("冰箱抽屉 (Freezer Drawer)", "freezer_drawer", R.drawable.ic_freezer),
        ActionItem("重新冻结 (Refreeze)", "refreeze", R.drawable.ic_freezer),
        ActionItem("截屏 (Screenshot)", "screenshot", R.drawable.ic_camera)
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
            // Save Selection
            val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
            prefs.edit().putString(prefKey, item.label).apply() 
            // Saving "Label" for simple display updating in previous screen. 
            // Ideally should save 'code' and map back, but for this prototype UI I'll save current display text too or just finish.
            // Let's save the CODE for logic, but we need to update the UI on the other side. 
            // I'll save the visual label too for easy UI binding on return? No, let's just save code.
            prefs.edit().putString(prefKey + "_label", item.label).apply()
            
            finish()
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
