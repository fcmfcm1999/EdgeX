package com.fan.edgex.ui

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigString

class PieSettingsActivity : AppCompatActivity() {

    private val slotViews = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pie_settings)
        ThemeManager.applyToActivity(this)

        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.getInsets(android.view.WindowInsets.Type.statusBars()).top, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.slots_container)
        val inflater = LayoutInflater.from(this)
        val density = resources.displayMetrics.density
        val dividerH = (1 * density).toInt()
        val dividerMargin = (4 * density).toInt()
        val rowPad = (4 * density).toInt()

        for (i in 0 until AppConfig.PIE_MAX_SLOTS) {
            if (i > 0) {
                val divider = View(this).also { d ->
                    d.setBackgroundColor(resources.getColor(R.color.ui_divider, theme))
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dividerH)
                    lp.topMargin = dividerMargin
                    lp.bottomMargin = dividerMargin
                    d.layoutParams = lp
                }
                container.addView(divider)
            }

            val row = inflater.inflate(R.layout.item_gesture_action, container, false)
            val tv = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            row.setBackgroundResource(tv.resourceId)
            row.isClickable = true
            row.isFocusable = true
            row.setPadding(rowPad, 0, rowPad, 0)
            row.findViewById<TextView>(R.id.action_title).text = getString(R.string.pie_slot_label, i + 1)

            val slotIndex = i
            row.setOnClickListener {
                startActivity(
                    Intent(this, ActionSelectionActivity::class.java)
                        .putExtra("pref_key", AppConfig.pieSlot(slotIndex))
                        .putExtra("title", getString(R.string.pie_slot_label, slotIndex + 1))
                )
            }
            slotViews.add(row)
            container.addView(row)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLabels()
    }

    private fun refreshLabels() {
        for (i in 0 until AppConfig.PIE_MAX_SLOTS) {
            val label = getConfigString(AppConfig.pieSlotLabel(i), getString(R.string.action_none))
            slotViews.getOrNull(i)?.findViewById<TextView>(R.id.action_subtitle)?.text = label
        }
    }
}
