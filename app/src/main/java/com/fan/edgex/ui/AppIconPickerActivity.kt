package com.fan.edgex.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.edgex.R

class AppIconPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ICON_REF = "icon_ref"
    }

    private data class AppEntry(val packageName: String, val label: String, val icon: Drawable)

    private lateinit var adapter: AppAdapter
    private val allApps = mutableListOf<AppEntry>()
    private val filtered = mutableListOf<AppEntry>()

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) returnCustomIcon(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_icon_picker)
        ThemeManager.applyToActivity(this)

        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                view.paddingLeft,
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top,
                view.paddingRight,
                view.paddingBottom,
            )
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        adapter = AppAdapter(filtered) { entry ->
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_ICON_REF, "${MultiActionIconUtils.PREFIX_APP}${entry.packageName}"))
            finish()
        }
        recyclerView.layoutManager = GridLayoutManager(this, 4)
        recyclerView.adapter = adapter

        findViewById<View>(R.id.btn_from_gallery).setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        val etSearch = findViewById<EditText>(R.id.et_search)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { applyFilter(s?.toString() ?: "") }
        })

        loadApps()
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        allApps.clear()
        allApps.addAll(
            resolveInfos
                .map { ri ->
                    AppEntry(
                        packageName = ri.activityInfo.packageName,
                        label = ri.loadLabel(pm).toString(),
                        icon = ri.loadIcon(pm),
                    )
                }
                .sortedBy { it.label.lowercase() }
        )
        applyFilter("")
    }

    private fun applyFilter(query: String) {
        filtered.clear()
        if (query.isBlank()) {
            filtered.addAll(allApps)
        } else {
            val q = query.lowercase()
            filtered.addAll(allApps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) })
        }
        adapter.notifyDataSetChanged()
    }

    private fun returnCustomIcon(uri: Uri) {
        val filename = MultiActionIconUtils.saveCustomIconFromUri(this, uri) ?: return
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_ICON_REF, "${MultiActionIconUtils.PREFIX_CUSTOM}$filename"))
        finish()
    }

    private class AppAdapter(
        private val items: List<AppEntry>,
        private val onClick: (AppEntry) -> Unit,
    ) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.iv_app_icon)
            val label: TextView = v.findViewById(R.id.tv_app_label)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app_icon, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = items[position]
            holder.icon.setImageDrawable(entry.icon)
            holder.label.text = entry.label
            holder.itemView.setOnClickListener { onClick(entry) }
        }

        override fun getItemCount() = items.size
    }
}
