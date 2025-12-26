package com.fan.edgex.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.edgex.R

class ShortcutSelectionActivity : AppCompatActivity() {

    data class ShortcutItem(
        val packageName: String,
        val shortcutId: String,
        val label: String,
        val appLabel: String,
        val icon: android.graphics.drawable.Drawable?
    )

    private val shortcuts = mutableListOf<ShortcutItem>()
    private lateinit var adapter: ShortcutAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shortcut_selection)

        // Header Insets
        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.systemWindowInsetTop, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Get Args
        val prefKey = intent.getStringExtra("pref_key") ?: "unknown"

        // RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ShortcutAdapter(shortcuts) { item ->
            // Save Selection
            val actionCode = "app_shortcut:${item.packageName}:${item.shortcutId}"
            val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
            prefs.edit().putString(prefKey, actionCode).apply()
            prefs.edit().putString(prefKey + "_label", "快捷方式: ${item.label}").apply()
            
            // Notify Hook
            contentResolver.notifyChange(android.net.Uri.parse("content://com.fan.edgex.provider/config"), null)
            
            finish()
        }
        recyclerView.adapter = adapter

        // Load Shortcuts
        loadShortcuts()
    }

    private fun loadShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            Toast.makeText(this, "需要 Android 7.1 及以上版本", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
                val pm = packageManager
                
                // Get all launcher apps
                val mainIntent = Intent(Intent.ACTION_MAIN, null)
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = pm.queryIntentActivities(mainIntent, 0)
                
                val tempList = mutableListOf<ShortcutItem>()
                
                for (app in apps) {
                    val packageName = app.activityInfo.packageName
                    val appLabel = app.loadLabel(pm).toString()
                    
                    try {
                        // Query shortcuts for this app
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
                                    label = shortcut.shortLabel?.toString() ?: shortcut.longLabel?.toString() ?: "Unknown",
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
                
                // Sort by app name, then shortcut name
                tempList.sortWith(compareBy({ it.appLabel }, { it.label }))
                
                runOnUiThread {
                    if (shortcuts.isEmpty()) {
                        loadShortcutsViaRoot()
                    } else {
                        shortcuts.clear()
                        shortcuts.addAll(tempList)
                        adapter.notifyDataSetChanged()
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
                
                // Read Error Request
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
                    
                    // Log output for debugging
                    // android.util.Log.d("EdgeX_Dump", l)
                    
                    // Method 1: Header "Package: com.foo"
                    if (l.startsWith("Package:") && l.contains("uid=")) {
                         val parts = l.split("\\s+".toRegex())
                         if (parts.size >= 2) {
                             currentPackage = parts[1]
                         }
                    } 
                    
                    // Method 2: ShortcutInfo line (Newer Android)
                    // Example: ShortcutInfo {id=entrust, flags=...
                    if (l.startsWith("ShortcutInfo") && l.contains("id=")) {
                         // Extract ID
                         val afterId = l.substringAfter("id=")
                         // ID ends at comma or space
                         currentId = afterId.substringBefore(",").substringBefore(" ").trim()
                         currentLabel = null
                         // Don't reset package yet, might relay on Header
                    } else if (l.startsWith("id=")) {
                         // Older Format
                         currentId = l.substringAfter("id=").trim()
                    }
                    
                    // Extra Properties
                    if (l.startsWith("packageName=")) {
                         currentPackage = l.substringAfter("packageName=").trim()
                    }
                    
                    if (l.startsWith("shortLabel=")) {
                         // Format: shortLabel=委托, resId=... or just shortLabel=Name
                         val raw = l.substringAfter("shortLabel=")
                         if (raw.contains(", resId=")) {
                             currentLabel = raw.substringBefore(", resId=")
                         } else {
                             currentLabel = raw.substringBefore(",") // generic safety
                         }
                         currentLabel = currentLabel?.trim()
                         
                         // Commit if ready
                         if (currentPackage != null && currentId != null && currentLabel != null) {
                             android.util.Log.d("EdgeX_Dump", "Found: $currentPackage / $currentId / $currentLabel")
                             try {
                                 // Check duplicates?
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
                     shortcuts.clear()
                     shortcuts.addAll(rootShortcuts)
                     shortcuts.sortWith(compareBy({ it.appLabel }, { it.label }))
                     adapter.notifyDataSetChanged()
                     Toast.makeText(this, "通过 Root 加载了 ${rootShortcuts.size} 个快捷方式", Toast.LENGTH_SHORT).show()
                 } else {
                     Toast.makeText(this, "未找到快捷方式 (Root 尝试失败)", Toast.LENGTH_SHORT).show()
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
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
