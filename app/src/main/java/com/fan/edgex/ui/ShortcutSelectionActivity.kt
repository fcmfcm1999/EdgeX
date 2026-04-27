package com.fan.edgex.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.edgex.R
import com.fan.edgex.config.putConfig
import java.util.Locale

class ShortcutSelectionActivity : AppCompatActivity() {

    data class ShortcutItem(
        val packageName: String,
        val shortcutId: String,
        val label: String,
        val appLabel: String,
        val icon: android.graphics.drawable.Drawable?
    )

    private val allShortcuts = mutableListOf<ShortcutItem>()
    private val displayedShortcuts = mutableListOf<ShortcutItem>()
    private lateinit var adapter: ShortcutAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shortcut_selection)
        ThemeManager.applyToActivity(this)

        // Header Insets
        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.getInsets(android.view.WindowInsets.Type.statusBars()).top, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Get Args
        val prefKey = intent.getStringExtra("pref_key") ?: "unknown"

        // RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ShortcutAdapter(displayedShortcuts) { item ->
            putConfig(prefKey, "app_shortcut:${item.packageName}:${item.shortcutId}")
            putConfig("${prefKey}_label", getString(R.string.label_shortcut_prefix, item.label))
            finish()
        }
        recyclerView.adapter = adapter

        // Setup Search
        setupSearch()
        // Load Shortcuts
        loadShortcuts()
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

        etSearch.addTextChangedListener { text ->
            filterShortcuts(text.toString())
        }
    }

    private fun filterShortcuts(query: String) {
        displayedShortcuts.clear()
        if (query.isEmpty()) {
            displayedShortcuts.addAll(allShortcuts)
        } else {
            val q = query.lowercase(Locale.getDefault())
            displayedShortcuts.addAll(allShortcuts.filter {
                it.label.lowercase().contains(q) || it.appLabel.lowercase().contains(q) || it.packageName.contains(q)
            })
        }
        adapter.notifyDataSetChanged()
    }

    private fun loadShortcuts() {
        Thread {
            try {
                val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
                val pm = packageManager

                val mainIntent = Intent(Intent.ACTION_MAIN, null)
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = pm.queryIntentActivities(mainIntent, 0)

                val tempList = mutableListOf<ShortcutItem>()

                for (app in apps) {
                    val packageName = app.activityInfo.packageName
                    val appLabel = app.loadLabel(pm).toString()

                    try {
                        val query = android.content.pm.LauncherApps.ShortcutQuery()
                        query.setQueryFlags(
                            android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                        )
                        query.setPackage(packageName)

                        val appShortcuts = launcherApps.getShortcuts(query, android.os.Process.myUserHandle()) ?: emptyList()

                        for (shortcut in appShortcuts) {
                            val icon = try {
                                launcherApps.getShortcutIconDrawable(shortcut, 0)
                            } catch (e: Exception) {
                                app.loadIcon(pm)
                            }

                            tempList.add(
                                ShortcutItem(
                                    packageName = packageName,
                                    shortcutId = shortcut.id,
                                    label = shortcut.shortLabel?.toString() ?: shortcut.longLabel?.toString() ?: getString(R.string.key_not_configured),
                                    appLabel = appLabel,
                                    icon = icon
                                )
                            )
                        }
                    } catch (e: SecurityException) {
                        // Likely not default launcher
                    } catch (e: Exception) {
                        // Other validation errors
                    }
                }

                tempList.sortWith(compareBy({ it.appLabel }, { it.label }))

                runOnUiThread {
                    allShortcuts.clear()
                    allShortcuts.addAll(tempList)
                    if (allShortcuts.isEmpty()) {
                        loadShortcutsViaRoot()
                    } else {
                        filterShortcuts(findViewById<EditText>(R.id.et_search).text.toString())
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                loadShortcutsViaRoot()
            }
        }.start()
    }

    private fun loadShortcutsViaRoot() {
        Thread {
            android.util.Log.d("EdgeX_Dump", "Starting Root Dump...")
            val rootShortcuts = mutableListOf<ShortcutItem>()
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "dumpsys shortcut"))

                Thread {
                    val errReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))
                    var errLine: String?
                    while (errReader.readLine().also { errLine = it } != null) {
                        android.util.Log.e("EdgeX_Dump", "STDERR: $errLine")
                    }
                }.start()

                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))

                var line: String?
                var currentPackage: String? = null
                var currentId: String? = null
                var currentLabel: String? = null
                val pm = packageManager

                while (reader.readLine().also { line = it } != null) {
                    val l = line!!.trim()

                    if (l.startsWith("Package:") && l.contains("uid=")) {
                        val parts = l.split("\\s+".toRegex())
                        if (parts.size >= 2) {
                            currentPackage = parts[1]
                        }
                    }

                    if (l.startsWith("ShortcutInfo") && l.contains("id=")) {
                        val afterId = l.substringAfter("id=")
                        currentId = afterId.substringBefore(",").substringBefore(" ").trim()
                        currentLabel = null
                    } else if (l.startsWith("id=")) {
                        currentId = l.substringAfter("id=").trim()
                    }

                    if (l.startsWith("packageName=")) {
                        currentPackage = l.substringAfter("packageName=").trim()
                    }

                    if (l.startsWith("shortLabel=")) {
                        val raw = l.substringAfter("shortLabel=")
                        if (raw.contains(", resId=")) {
                            currentLabel = raw.substringBefore(", resId=")
                        } else {
                            currentLabel = raw.substringBefore(",")
                        }
                        currentLabel = currentLabel?.trim()

                        if (currentPackage != null && currentId != null && currentLabel != null) {
                            android.util.Log.d("EdgeX_Dump", "Found: $currentPackage / $currentId / $currentLabel")
                            try {
                                val exists = rootShortcuts.any { it.packageName == currentPackage && it.shortcutId == currentId }
                                if (!exists) {
                                    val appInfo = pm.getApplicationInfo(currentPackage!!, 0)
                                    rootShortcuts.add(ShortcutItem(
                                        packageName = currentPackage!!,
                                        shortcutId = currentId!!,
                                        label = currentLabel!!,
                                        appLabel = appInfo.loadLabel(pm).toString(),
                                        icon = appInfo.loadIcon(pm)
                                    ))
                                }
                                currentId = null
                                currentLabel = null
                            } catch (e: Exception) {}
                        }
                    }
                }
                reader.close()
                process.waitFor()

            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("EdgeX_Dump", "Error: ${e.message}")
            }

            runOnUiThread {
                if (rootShortcuts.isNotEmpty()) {
                    allShortcuts.clear()
                    allShortcuts.addAll(rootShortcuts)
                    allShortcuts.sortWith(compareBy({ it.appLabel }, { it.label }))
                    filterShortcuts(findViewById<EditText>(R.id.et_search).text.toString())
                    Toast.makeText(this, getString(R.string.toast_shortcuts_loaded_via_root, rootShortcuts.size), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.toast_no_shortcuts_found), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    inner class ShortcutAdapter(
        private val items: List<ShortcutItem>,
        private val onClick: (ShortcutItem) -> Unit
    ) : RecyclerView.Adapter<ShortcutAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val title: TextView = view.findViewById(R.id.app_name)
            val subtitle: TextView = view.findViewById(R.id.app_package)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_list, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.label
            holder.subtitle.text = item.appLabel
            holder.icon.setImageDrawable(item.icon)
            ThemeManager.applyToView(holder.itemView, this@ShortcutSelectionActivity)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
