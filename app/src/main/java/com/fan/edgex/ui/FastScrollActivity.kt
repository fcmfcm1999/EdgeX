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

class FastScrollActivity : AppCompatActivity() {

    data class ScrollOption(
        val label: String,
        val code: String,
        @DrawableRes val iconRes: Int,
    )

    private val options get() = listOf(
        ScrollOption(getString(R.string.action_scroll_to_top),    "to_top",    R.drawable.ic_scroll_to_top),
        ScrollOption(getString(R.string.action_scroll_to_bottom), "to_bottom", R.drawable.ic_scroll_to_bottom),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_action_selection)
        ThemeManager.applyToActivity(this)

        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.getInsets(android.view.WindowInsets.Type.statusBars()).top, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_subtitle).text = getString(R.string.header_fast_scroll)

        val prefKey = intent.getStringExtra("pref_key") ?: "unknown"

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = OptionsAdapter(options) { option ->
            putConfig(prefKey, "fast_scroll:${option.code}")
            putConfig("${prefKey}_label", option.label)
            finish()
        }
    }

    inner class OptionsAdapter(
        private val items: List<ScrollOption>,
        private val onClick: (ScrollOption) -> Unit,
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
            ThemeManager.applyToView(holder.itemView, this@FastScrollActivity)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
