package com.fan.edgex.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.edgex.R
import com.fan.edgex.config.putConfig
import java.util.Locale

class AppSelectionActivity : AppCompatActivity() {

    data class AppItem(
        val packageName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable?,
    )

    private val allApps = mutableListOf<AppItem>()
    private val displayedApps = mutableListOf<AppItem>()
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shortcut_selection)
        ThemeManager.applyToActivity(this)

        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.getInsets(android.view.WindowInsets.Type.statusBars()).top, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_title).setText(R.string.header_app_selection)

        val prefKey = intent.getStringExtra("pref_key") ?: "unknown"

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter(displayedApps) { item ->
            putConfig(prefKey, "launch_app:${item.packageName}")
            putConfig("${prefKey}_label", getString(R.string.label_app_prefix, item.label))
            finish()
        }
        recyclerView.adapter = adapter

        setupSearch()
        loadApps()
    }

    private fun setupSearch() {
        val btnSearch = findViewById<ImageView>(R.id.btn_search)
        val etSearch = findViewById<EditText>(R.id.et_search)
        val tvTitle = findViewById<TextView>(R.id.tv_title)

        btnSearch.setOnClickListener {
            if (etSearch.isGone) {
                tvTitle.isGone = true
                etSearch.isVisible = true
                etSearch.requestFocus()
            } else {
                if (etSearch.text.isEmpty()) {
                    etSearch.isGone = true
                    tvTitle.isVisible = true
                } else {
                    etSearch.text.clear()
                }
            }
        }

        etSearch.addTextChangedListener { filterApps(it.toString()) }
    }

    private fun filterApps(query: String) {
        displayedApps.clear()
        if (query.isEmpty()) {
            displayedApps.addAll(allApps)
        } else {
            val q = query.lowercase(Locale.getDefault())
            displayedApps.addAll(allApps.filter {
                it.label.lowercase().contains(q) || it.packageName.contains(q)
            })
        }
        adapter.notifyDataSetChanged()
    }

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(mainIntent, 0)
                .map { ri ->
                    AppItem(
                        packageName = ri.activityInfo.packageName,
                        label = ri.loadLabel(pm).toString(),
                        icon = try { ri.loadIcon(pm) } catch (_: Exception) { null },
                    )
                }
                .sortedBy { it.label }

            runOnUiThread {
                allApps.clear()
                allApps.addAll(apps)
                filterApps(findViewById<EditText>(R.id.et_search).text.toString())
            }
        }.start()
    }

    inner class AppAdapter(
        private val items: List<AppItem>,
        private val onClick: (AppItem) -> Unit,
    ) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val title: TextView = view.findViewById(R.id.app_name)
            val subtitle: TextView = view.findViewById(R.id.app_package)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app_list, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.label
            holder.subtitle.text = item.packageName
            holder.icon.setImageDrawable(item.icon)
            ThemeManager.applyToView(holder.itemView, this@AppSelectionActivity)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
