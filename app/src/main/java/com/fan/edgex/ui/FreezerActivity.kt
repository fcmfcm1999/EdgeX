package com.fan.edgex.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fan.edgex.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class FreezerActivity : AppCompatActivity() {

    data class AppItem(
        val info: ApplicationInfo,
        val label: String,
        val isFrozen: Boolean,
        var isChecked: Boolean = false
    )

    private val allApps = mutableListOf<AppItem>()
    private val displayedApps = mutableListOf<AppItem>()
    private val freezerList = mutableSetOf<String>() // Set of package names
    private lateinit var adapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_freezer)

        // Header Insets
        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.systemWindowInsetTop, view.paddingRight, view.paddingBottom)
            insets
        }

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Recycler View
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AppListAdapter(displayedApps, 
            onClick = { app -> showAppDialog(app) },
            onCheckChanged = { app, isChecked -> 
                app.isChecked = isChecked
                updateFreezerList(app.info.packageName, isChecked)
            }
        )
        recyclerView.adapter = adapter

        // Search Logic
        setupSearch()

        // Load Apps
        loadApps()
    }
    
    private fun setupSearch() {
        val btnSearch = findViewById<ImageView>(R.id.btn_search)
        val etSearch = findViewById<EditText>(R.id.et_search)
        val tvTitle = findViewById<TextView>(R.id.tv_title)
        
        btnSearch.setOnClickListener {
            if (etSearch.visibility == View.GONE) {
                // Open Search
                tvTitle.visibility = View.GONE
                etSearch.visibility = View.VISIBLE
                etSearch.requestFocus()
            } else {
                // Close/Clear
                if (etSearch.text.isEmpty()) {
                    etSearch.visibility = View.GONE
                    tvTitle.visibility = View.VISIBLE
                } else {
                    etSearch.text.clear()
                }
            }
        }

        etSearch.addTextChangedListener { text ->
             filterApps(text.toString())
        }
    }

    private fun loadApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Load saved list
            val prefs = getSharedPreferences("config", MODE_PRIVATE)
            val savedString = prefs.getString("freezer_app_list", "") ?: ""
            freezerList.clear()
            if (savedString.isNotEmpty()) {
                freezerList.addAll(savedString.split(","))
            }

            val pm = packageManager
            val rawApps = pm.getInstalledApplications(0)
            
            val list = rawApps.map { info ->
                AppItem(
                    info = info,
                    label = info.loadLabel(pm).toString(),
                    isFrozen = !info.enabled,
                    isChecked = freezerList.contains(info.packageName)
                )
            }.sortedBy { it.label.lowercase() }

            allApps.clear()
            allApps.addAll(list)
            
            withContext(Dispatchers.Main) {
                filterApps("")
            }
        }
    }
    
    private fun updateFreezerList(packageName: String, add: Boolean) {
        if (add) {
            freezerList.add(packageName)
        } else {
            freezerList.remove(packageName)
        }
        
        // Save to Prefs
        val joined = freezerList.joinToString(",")
        getSharedPreferences("config", MODE_PRIVATE)
            .edit()
            .putString("freezer_app_list", joined)
            .apply()
            
        // Notify Provider
        contentResolver.notifyChange(Uri.parse("content://com.fan.edgex.provider/config"), null)
    }

    private fun filterApps(query: String) {
        displayedApps.clear()
        if (query.isEmpty()) {
            displayedApps.addAll(allApps)
        } else {
            val q = query.lowercase(Locale.getDefault())
            displayedApps.addAll(allApps.filter { 
                it.label.lowercase().contains(q) || it.info.packageName.contains(q)
            })
        }
        adapter.notifyDataSetChanged()
    }

    private fun showAppDialog(app: AppItem) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_app_options, null)
        
        view.findViewById<TextView>(R.id.dialog_title).text = app.label
        
        val btnFreeze = view.findViewById<View>(R.id.btn_freeze)
        val btnUnfreeze = view.findViewById<View>(R.id.btn_unfreeze)
        
        if (app.isFrozen) {
            btnFreeze.visibility = View.GONE
            btnUnfreeze.visibility = View.VISIBLE
        } else {
            btnFreeze.visibility = View.VISIBLE
            btnUnfreeze.visibility = View.GONE
        }

        view.findViewById<View>(R.id.btn_run).setOnClickListener {
             try {
                 val intent = packageManager.getLaunchIntentForPackage(app.info.packageName)
                 if (intent != null) {
                     startActivity(intent)
                     dialog.dismiss()
                 } else {
                     Toast.makeText(this, "Cannot launch this app", Toast.LENGTH_SHORT).show()
                 }
             } catch (e: Exception) {
                 Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
             }
        }
        
        view.findViewById<View>(R.id.btn_info).setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${app.info.packageName}")
            startActivity(intent)
            dialog.dismiss()
        }
        
        btnFreeze.setOnClickListener {
            if (runRootCommand("pm disable ${app.info.packageName}")) {
                Toast.makeText(this, "Frozen ${app.label}", Toast.LENGTH_SHORT).show()
                refreshApp(app)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Failed to freeze. Check Root.", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnUnfreeze.setOnClickListener {
             if (runRootCommand("pm enable ${app.info.packageName}")) {
                Toast.makeText(this, "Unfrozen ${app.label}", Toast.LENGTH_SHORT).show()
                refreshApp(app)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Failed to unfreeze. Check Root.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.setContentView(view)
        dialog.show()
    }
    
    private fun refreshApp(app: AppItem) {
        loadApps()
    }

    private fun runRootCommand(cmd: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            process.waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    inner class AppListAdapter(
        private val apps: List<AppItem>,
        private val onClick: (AppItem) -> Unit,
        private val onCheckChanged: (AppItem, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val name: TextView = view.findViewById(R.id.app_name)
            val pkg: TextView = view.findViewById(R.id.app_package)
            val frozen: TextView = view.findViewById(R.id.tv_frozen_status)
            val checkbox: android.widget.CheckBox = view.findViewById(R.id.cb_include)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_list, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.name.text = app.label
            holder.pkg.text = app.info.packageName
            holder.icon.setImageDrawable(app.info.loadIcon(packageManager))
            
            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = app.isChecked
            
            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                onCheckChanged(app, isChecked)
            }
            
            if (app.isFrozen) {
                holder.frozen.visibility = View.VISIBLE
                holder.name.alpha = 0.5f
            } else {
                holder.frozen.visibility = View.GONE
                holder.name.alpha = 1.0f
            }
            
            holder.itemView.setOnClickListener { onClick(app) }
        }

        override fun getItemCount() = apps.size
    }
}
