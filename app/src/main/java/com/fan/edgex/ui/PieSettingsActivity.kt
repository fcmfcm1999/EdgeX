package com.fan.edgex.ui

import android.content.Intent
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigString

class PieSettingsActivity : AppCompatActivity() {

    private data class RowInfo(
        val edge: String,
        val ring: Int,
        val slot: Int,
        val view: View,
    )

    private val rows = mutableListOf<RowInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pie_settings)
        ThemeManager.applyToActivity(this)

        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.getInsets(android.view.WindowInsets.Type.statusBars()).top, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.pie_sections_container)
        val inflater = LayoutInflater.from(this)
        val density = resources.displayMetrics.density

        val edgeStringRes = mapOf(
            "left" to R.string.pie_edge_left,
            "right" to R.string.pie_edge_right,
            "top" to R.string.pie_edge_top,
            "bottom" to R.string.pie_edge_bottom,
        )

        for (edge in AppConfig.PIE_EDGES) {
            val sectionHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                val tv = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                setBackgroundResource(tv.resourceId)
                isClickable = true
                isFocusable = true
                val pad = (16 * density).toInt()
                setPadding(pad, pad, pad, pad)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val titleView = TextView(this).apply {
                text = getString(edgeStringRes[edge] ?: R.string.pie_edge_left)
                setTextColor(resources.getColor(R.color.ui_text_primary, theme))
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val arrowView = ImageView(this).apply {
                setImageResource(R.drawable.ic_expand_more)
                imageTintList = resources.getColorStateList(R.color.ui_text_secondary, theme)
                layoutParams = LinearLayout.LayoutParams(
                    (24 * density).toInt(),
                    (24 * density).toInt(),
                )
            }

            sectionHeader.addView(titleView)
            sectionHeader.addView(arrowView)
            container.addView(sectionHeader)

            val contentLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }

            val dividerH = (1 * density).toInt()
            val dividerMargin = (4 * density).toInt()
            val rowPad = (4 * density).toInt()
            var firstRow = true

            for (ring in 1..AppConfig.PIE_RINGS) {
                for (slot in 0 until AppConfig.PIE_SLOTS_PER_RING) {
                    if (!firstRow) {
                        val divider = View(this).also { d ->
                            d.setBackgroundColor(resources.getColor(R.color.ui_divider, theme))
                            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dividerH)
                            lp.topMargin = dividerMargin
                            lp.bottomMargin = dividerMargin
                            d.layoutParams = lp
                        }
                        contentLayout.addView(divider)
                    }
                    firstRow = false

                    val row = inflater.inflate(R.layout.item_gesture_action, contentLayout, false)
                    val tv2 = TypedValue()
                    theme.resolveAttribute(android.R.attr.selectableItemBackground, tv2, true)
                    row.setBackgroundResource(tv2.resourceId)
                    row.isClickable = true
                    row.isFocusable = true
                    row.setPadding(rowPad, 0, rowPad, 0)
                    row.findViewById<TextView>(R.id.action_title).text = getString(R.string.pie_ring_slot_label, ring, slot + 1)

                    val capturedEdge = edge
                    val capturedRing = ring
                    val capturedSlot = slot
                    row.setOnClickListener {
                        startActivity(
                            Intent(this, ActionSelectionActivity::class.java)
                                .putExtra("pref_key", AppConfig.pieSlot(capturedEdge, capturedRing, capturedSlot))
                                .putExtra("title", getString(R.string.pie_ring_slot_label, capturedRing, capturedSlot + 1))
                        )
                    }

                    rows.add(RowInfo(edge, ring, slot, row))
                    contentLayout.addView(row)
                }
            }

            container.addView(contentLayout)

            var expanded = false
            sectionHeader.setOnClickListener {
                expanded = !expanded
                contentLayout.visibility = if (expanded) View.VISIBLE else View.GONE
                arrowView.rotation = if (expanded) 180f else 0f
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLabels()
    }

    private fun refreshLabels() {
        for (info in rows) {
            val action = getConfigString(
                AppConfig.pieSlot(info.edge, info.ring, info.slot),
                "none"
            )
            val rawLabel = getConfigString(
                AppConfig.pieSlotLabel(info.edge, info.ring, info.slot),
                getString(R.string.action_none),
            )
            val label = ActionSelectionActivity.resolveActionLabel(this, action, rawLabel)
            info.view.findViewById<TextView>(R.id.action_subtitle)?.text = label
        }
    }
}
