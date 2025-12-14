package com.fan.edgex.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R

class GesturesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gestures)

        // Immersive Header Fix
        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.systemWindowInsetTop, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Initialize Zones
        setupZone(findViewById(R.id.zone_left_mid), "左中", isLeft = true)
        setupZone(findViewById(R.id.zone_left_bottom), "左下", isLeft = true)
        
        setupZone(findViewById(R.id.zone_right_top), "右上", isLeft = false)
        setupZone(findViewById(R.id.zone_right_mid), "右中", isLeft = false)
        setupZone(findViewById(R.id.zone_right_bottom), "右下", isLeft = false)
    }

    private fun setupZone(root: View, title: String, isLeft: Boolean) {
        // Set Title
        root.findViewById<TextView>(R.id.title).text = title

        // Expand/Collapse Logic
        val header = root.findViewById<View>(R.id.header)
        val body = root.findViewById<View>(R.id.body)
        val arrow = root.findViewById<ImageView>(R.id.arrow)

        header.setOnClickListener {
            if (body.visibility == View.VISIBLE) {
                body.visibility = View.GONE
                arrow.animate().rotation(0f).start()
            } else {
                body.visibility = View.VISIBLE
                arrow.animate().rotation(180f).start()
            }
        }

        // Setup Action Labels
        setupAction(root.findViewById(R.id.action_click), "单击")
        setupAction(root.findViewById(R.id.action_double_click), "双击")
        setupAction(root.findViewById(R.id.action_long_press), "长按")
        setupAction(root.findViewById(R.id.action_swipe_up), "上划")
        setupAction(root.findViewById(R.id.action_swipe_down), "下划")

        // Conditional Swipe
        if (isLeft) {
            // Left Zone has Swipe Right
            setupAction(root.findViewById(R.id.action_swipe_right), "右划")
        } else {
            // Right Zone has Swipe Left
             setupAction(root.findViewById(R.id.action_swipe_left), "左划")
        }
    }

    private fun setupAction(actionView: View, label: String) {
        actionView.findViewById<TextView>(R.id.action_title).text = label
        
        // Example: Set "Screenshot" for Swipe Down just to look like the screenshot
        if (label == "下划") {
             actionView.findViewById<TextView>(R.id.action_subtitle).text = "截屏"
             actionView.findViewById<ImageView>(R.id.action_icon).setImageResource(R.drawable.ic_camera)
             actionView.findViewById<ImageView>(R.id.action_icon).setPadding(5,5,5,5)
        }
    }
}
