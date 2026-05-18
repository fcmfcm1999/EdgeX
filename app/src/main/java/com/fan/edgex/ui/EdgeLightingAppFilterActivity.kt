package com.fan.edgex.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import org.json.JSONArray
import java.util.Locale

class EdgeLightingAppFilterActivity : AppCompatActivity() {

    private data class AppItem(
        val packageName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable?,
    )

    private val allApps = mutableListOf<AppItem>()
    private val displayedApps = mutableListOf<AppItem>()
    private val selectedPackages = linkedSetOf<String>()
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edge_lighting_app_filter)
        ThemeManager.applyToActivity(this)

        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.getInsets(android.view.WindowInsets.Type.statusBars()).top, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        selectedPackages.addAll(parsePackageList(getConfigString(AppConfig.EDGE_LIGHTING_APP_LIST)))
        setupAppList()
        loadApps()
    }

    private fun setupAppList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_edge_lighting_apps)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter(displayedApps) { item ->
            if (!selectedPackages.remove(item.packageName)) {
                selectedPackages.add(item.packageName)
            }
            saveSelectedPackages()
            refreshSelectedSummary()
            adapter.notifyDataSetChanged()
        }
        recyclerView.adapter = adapter

        findViewById<EditText>(R.id.et_edge_lighting_app_search).addTextChangedListener {
            filterApps(it.toString())
        }
        findViewById<Button>(R.id.btn_edge_lighting_clear_apps).setOnClickListener {
            selectedPackages.clear()
            saveSelectedPackages()
            refreshSelectedSummary()
            adapter.notifyDataSetChanged()
        }
        refreshSelectedSummary()
    }

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val apps = pm.getInstalledApplications(0)
                .map { info ->
                    AppItem(
                        packageName = info.packageName,
                        label = info.loadLabel(pm).toString(),
                        icon = runCatching { info.loadIcon(pm) }.getOrNull(),
                    )
                }
                .sortedBy { it.label.lowercase(Locale.getDefault()) }

            runOnUiThread {
                allApps.clear()
                allApps.addAll(apps)
                filterApps(findViewById<EditText>(R.id.et_edge_lighting_app_search).text.toString())
            }
        }.start()
    }

    private fun filterApps(query: String) {
        val q = query.lowercase(Locale.getDefault())
        displayedApps.clear()
        displayedApps.addAll(
            if (q.isBlank()) allApps
            else allApps.filter { it.label.lowercase(Locale.getDefault()).contains(q) || it.packageName.contains(q) },
        )
        adapter.notifyDataSetChanged()
    }

    private fun saveSelectedPackages() {
        val value = if (selectedPackages.isEmpty()) "" else JSONArray(selectedPackages.toList()).toString()
        putConfig(AppConfig.EDGE_LIGHTING_APP_LIST, value)
    }

    private fun refreshSelectedSummary() {
        findViewById<TextView>(R.id.text_edge_lighting_app_summary).text =
            if (selectedPackages.isEmpty()) {
                getString(R.string.edge_lighting_app_filter_all)
            } else {
                getString(R.string.edge_lighting_app_filter_selected, selectedPackages.size)
            }
    }

    private fun parsePackageList(value: String): Set<String> {
        if (value.isBlank()) return emptySet()
        return runCatching {
            val array = JSONArray(value)
            buildSet {
                for (index in 0 until array.length()) {
                    val packageName = array.optString(index).trim()
                    if (packageName.isNotEmpty()) add(packageName)
                }
            }
        }.getOrElse {
            value.split(",").mapNotNullTo(mutableSetOf()) { it.trim().takeIf(String::isNotEmpty) }
        }
    }

    private inner class AppAdapter(
        private val items: List<AppItem>,
        private val onClick: (AppItem) -> Unit,
    ) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val name: TextView = view.findViewById(R.id.app_name)
            val pkg: TextView = view.findViewById(R.id.app_package)
            val frozen: TextView = view.findViewById(R.id.tv_frozen_status)
            val checkbox: CheckBox = view.findViewById(R.id.cb_include)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_list, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.name.text = item.label
            holder.pkg.text = item.packageName
            holder.icon.setImageDrawable(item.icon)
            holder.frozen.visibility = View.GONE
            holder.checkbox.isChecked = item.packageName in selectedPackages
            holder.itemView.setOnClickListener { onClick(item) }
            ThemeManager.applyToView(holder.itemView, this@EdgeLightingAppFilterActivity)
        }

        override fun getItemCount(): Int = items.size
    }
}
