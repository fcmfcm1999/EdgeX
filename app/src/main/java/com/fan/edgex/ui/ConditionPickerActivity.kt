package com.fan.edgex.ui

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
import com.fan.edgex.config.ConditionStore
import com.fan.edgex.config.putConfig

@Deprecated("Use Compose ConditionPickerSheet instead")
class ConditionPickerActivity : AppCompatActivity() {

    data class ConditionItem(val label: String, val code: String, val iconRes: Int)

    private val conditions get() = listOf(
        ConditionItem(getString(R.string.cond_auto_brightness),  "auto_brightness",  R.drawable.ic_brightness_up),
        ConditionItem(getString(R.string.cond_auto_rotate),      "auto_rotate",      R.drawable.ic_screen_rotation),
        ConditionItem(getString(R.string.cond_wifi_enabled),     "wifi_enabled",     R.drawable.ic_wifi),
        ConditionItem(getString(R.string.cond_mobile_data),      "mobile_data",      R.drawable.ic_mobile_data),
        ConditionItem(getString(R.string.cond_location),         "location",         R.drawable.ic_location),
        ConditionItem(getString(R.string.cond_bluetooth),        "bluetooth",        R.drawable.ic_bluetooth),
        ConditionItem(getString(R.string.cond_nfc),              "nfc",              R.drawable.ic_nfc),
        ConditionItem(getString(R.string.cond_power_connected),  "power_connected",  R.drawable.ic_power),
        ConditionItem(getString(R.string.cond_wifi_connected),   "wifi_connected",   R.drawable.ic_wifi),
        ConditionItem(getString(R.string.cond_network_connected),"network_connected",R.drawable.ic_link),
        ConditionItem(getString(R.string.cond_media_playing),    "media_playing",    R.drawable.ic_music),
        ConditionItem(getString(R.string.cond_screen_portrait),  "screen_portrait",  R.drawable.ic_screen_portrait),
        ConditionItem(getString(R.string.cond_screen_landscape), "screen_landscape", R.drawable.ic_screen_landscape),
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
        findViewById<TextView>(R.id.tv_subtitle).text = getString(R.string.header_condition_if)

        val condId = intent.getStringExtra(EXTRA_COND_ID) ?: run { finish(); return }

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ConditionAdapter(conditions) { item ->
            putConfig(ConditionStore.condIfKey(condId), item.code)
            putConfig(ConditionStore.condIfLabelKey(condId), item.label)
            finish()
        }
    }

    inner class ConditionAdapter(
        private val items: List<ConditionItem>,
        private val onClick: (ConditionItem) -> Unit,
    ) : RecyclerView.Adapter<ConditionAdapter.ViewHolder>() {

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
            ThemeManager.applyToView(holder.itemView, this@ConditionPickerActivity)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        const val EXTRA_COND_ID = "cond_id"
    }
}
