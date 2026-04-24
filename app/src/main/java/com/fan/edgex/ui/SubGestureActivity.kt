package com.fan.edgex.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigString

class SubGestureActivity : AppCompatActivity() {

    private data class SlotSpec(
        val rowId: Int,
        val labelRes: Int,
        val direction: String,
    )

    private val slots = listOf(
        SlotSpec(R.id.row_sub_hold,        R.string.sub_gesture_hold,        "hold"),
        SlotSpec(R.id.row_sub_swipe_left,  R.string.gesture_swipe_left,      "swipe_left"),
        SlotSpec(R.id.row_sub_swipe_right, R.string.gesture_swipe_right,     "swipe_right"),
        SlotSpec(R.id.row_sub_swipe_up,    R.string.gesture_swipe_up,        "swipe_up"),
        SlotSpec(R.id.row_sub_swipe_down,  R.string.gesture_swipe_down,      "swipe_down"),
    )

    private lateinit var parentKey: String
    private lateinit var parentTitle: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sub_gesture)
        ThemeManager.applyToActivity(this)

        parentKey = intent.getStringExtra("pref_key") ?: ""
        parentTitle = intent.getStringExtra("title") ?: getString(R.string.action_sub_gesture)

        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.systemWindowInsetTop, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_subtitle).text = parentTitle

        slots.forEach { slot ->
            val row = findViewById<View>(slot.rowId)
            row.findViewById<TextView>(R.id.action_title).text = getString(slot.labelRes)
            row.setOnClickListener {
                val childKey = AppConfig.subGestureChildKey(parentKey, slot.direction)
                startActivity(
                    Intent(this, ActionSelectionActivity::class.java)
                        .putExtra("title", "$parentTitle / ${getString(slot.labelRes)}")
                        .putExtra("pref_key", childKey)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSubtitles()
    }

    private fun refreshSubtitles() {
        slots.forEach { slot ->
            val childKey = AppConfig.subGestureChildKey(parentKey, slot.direction)
            val label = getConfigString("${childKey}_label", getString(R.string.action_none))
            val row = findViewById<View>(slot.rowId)
            row.findViewById<TextView>(R.id.action_subtitle).text = label
        }
    }
}
