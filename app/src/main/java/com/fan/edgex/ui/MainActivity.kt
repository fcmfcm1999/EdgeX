package com.fan.edgex.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.ImageView
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: AppAdapter
    private val selectedPackages = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById<ListView>(R.id.list_apps)
        val btnSave = findViewById<Button>(R.id.btn_save)

        // Load previously selected apps
        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        val savedSet = prefs.getStringSet("selected_apps", emptySet())
        if (savedSet != null) {
            selectedPackages.addAll(savedSet)
        }

        // Load installed apps
        val pm = packageManager
        val apps = pm.getInstalledApplications(0).filter { 
            // Filter out some system noise if needed, or just show all
            it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }.sortedBy { it.loadLabel(pm).toString() }

        adapter = AppAdapter(this, apps, selectedPackages)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val app = apps[position]
            if (selectedPackages.contains(app.packageName)) {
                selectedPackages.remove(app.packageName)
            } else {
                selectedPackages.add(app.packageName)
            }
            adapter.notifyDataSetChanged()
        }

        btnSave.setOnClickListener {
            savePreferences()
        }
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        prefs.edit().putStringSet("selected_apps", selectedPackages).apply()
        
        Toast.makeText(this, "Saved! Reboot SystemUI/Device may be needed.", Toast.LENGTH_SHORT).show()
    }

    inner class AppAdapter(
        private val context: Context,
        private val apps: List<ApplicationInfo>,
        private val selected: Set<String>
    ) : BaseAdapter() {

        override fun getCount(): Int = apps.size
        override fun getItem(position: Int): Any = apps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        @SuppressLint("ViewHolder")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_app, parent, false)
            val app = apps[position]
            
            val iconView = view.findViewById<ImageView>(R.id.img_icon)
            val textView = view.findViewById<CheckedTextView>(R.id.text_name)

            iconView.setImageDrawable(app.loadIcon(context.packageManager))
            textView.text = app.loadLabel(context.packageManager)
            textView.isChecked = selected.contains(app.packageName)

            return view
        }
    }
}
