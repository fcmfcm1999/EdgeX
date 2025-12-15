package com.fan.edgex.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Fix for "Header too tall": fitsSystemWindows="true" defaults to applying ALL system insets (Top + Bottom).
        // Since this view is at the top, we only want the Top Inset (Status Bar).
        // The Bottom Inset (Nav Bar) was erroneously adding huge padding to the bottom of the header.
        findViewById<android.view.View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                view.paddingLeft,
                insets.systemWindowInsetTop, // Only apply status bar height
                view.paddingRight,
                view.paddingBottom // Keep original bottom padding (0 or defined)
            )
            // Return insets to let them propagate if needed, but we consumed what we needed
            insets
        }
        
        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        val cbGestures = findViewById<android.widget.CheckBox>(R.id.checkbox_gestures)
        cbGestures.isChecked = prefs.getBoolean("gestures_enabled", true)
        cbGestures.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("gestures_enabled", isChecked).apply()
            // Notify Hook to refresh
            contentResolver.notifyChange(android.net.Uri.parse("content://com.fan.edgex.provider/config"), null)
        }

        findViewById<View>(R.id.item_gestures).setOnClickListener {
            // Only allow entering if allowed? Or always allow? 
            // User: "Only when enabled ... gestures take effect". This usually means background logic.
            // But just in case, I'll allow configuration access always (standard UX).
            startActivity(android.content.Intent(this, GesturesActivity::class.java))
        }

        findViewById<View>(R.id.item_freezer).setOnClickListener {
            startActivity(android.content.Intent(this, FreezerActivity::class.java))
        }
    }
}
