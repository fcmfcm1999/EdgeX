package com.fan.edgex.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.edgex.R
import com.fan.edgex.config.putConfig

class MusicControlActivity : AppCompatActivity() {

    data class MusicOption(
        val label: String,
        val code: String,
        @DrawableRes val iconRes: Int,
    )

    private val options get() = listOf(
        MusicOption(getString(R.string.action_music_play_pause), "play_pause", R.drawable.ic_music_play_pause),
        MusicOption(getString(R.string.action_music_stop),       "stop",       R.drawable.ic_music_stop),
        MusicOption(getString(R.string.action_music_previous),   "previous",   R.drawable.ic_music_previous),
        MusicOption(getString(R.string.action_music_next),       "next",       R.drawable.ic_music_next),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_action_selection)
        ThemeManager.applyToActivity(this)

        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.systemWindowInsetTop, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_subtitle).text = getString(R.string.header_music_control)

        val prefKey = intent.getStringExtra("pref_key") ?: "unknown"

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = OptionsAdapter(options) { option ->
            putConfig(prefKey, "music_control:${option.code}")
            putConfig("${prefKey}_label", getString(R.string.label_music_prefix, option.label))
            finish()
        }
    }

    inner class OptionsAdapter(
        private val items: List<MusicOption>,
        private val onClick: (MusicOption) -> Unit,
    ) : RecyclerView.Adapter<OptionsAdapter.ViewHolder>() {

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
            ThemeManager.applyToView(holder.itemView, this@MusicControlActivity)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
