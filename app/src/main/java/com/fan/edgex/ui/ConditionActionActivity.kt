package com.fan.edgex.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R
import com.fan.edgex.config.ConditionStore
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig

@Deprecated("Use Compose ConditionSheet instead")
class ConditionActionActivity : AppCompatActivity() {

    private lateinit var prefKey: String
    private lateinit var condId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_condition_action)
        ThemeManager.applyToActivity(this)

        prefKey = intent.getStringExtra("pref_key") ?: run { finish(); return }
        val title = intent.getStringExtra("title") ?: getString(R.string.action_condition)

        condId = resolveOrCreateId()

        putConfig(prefKey, ConditionStore.buildActionCode(condId))

        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(view.paddingLeft, insets.getInsets(android.view.WindowInsets.Type.statusBars()).top, view.paddingRight, view.paddingBottom)
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_subtitle).text = title

        // 如果 row
        val rowIf = findViewById<View>(R.id.row_condition_if)
        rowIf.findViewById<TextView>(R.id.action_title).text = getString(R.string.cond_label_if)
        rowIf.setOnClickListener {
            startActivity(
                Intent(this, ConditionPickerActivity::class.java)
                    .putExtra(ConditionPickerActivity.EXTRA_COND_ID, condId)
            )
        }

        // 然后 row
        val rowThen = findViewById<View>(R.id.row_condition_then)
        rowThen.findViewById<TextView>(R.id.action_title).text = getString(R.string.cond_label_then)
        rowThen.setOnClickListener {
            startActivity(
                Intent(this, ActionSelectionActivity::class.java)
                    .putExtra("pref_key", ConditionStore.condThenKey(condId))
                    .putExtra("title", getString(R.string.cond_label_then))
            )
        }

        // 否则 row
        val rowElse = findViewById<View>(R.id.row_condition_else)
        rowElse.findViewById<TextView>(R.id.action_title).text = getString(R.string.cond_label_else)
        rowElse.setOnClickListener {
            startActivity(
                Intent(this, ActionSelectionActivity::class.java)
                    .putExtra("pref_key", ConditionStore.condElseKey(condId))
                    .putExtra("title", getString(R.string.cond_label_else))
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSubtitles()
    }

    private fun refreshSubtitles() {
        val none = getString(R.string.action_none)

        val ifLabel = getConfigString(ConditionStore.condIfLabelKey(condId), none)
        val thenLabel = getConfigString(ConditionStore.condThenLabelKey(condId), none)
        val elseLabel = getConfigString(ConditionStore.condElseLabelKey(condId), none)

        findViewById<View>(R.id.row_condition_if).findViewById<TextView>(R.id.action_subtitle).text = ifLabel
        findViewById<View>(R.id.row_condition_then).findViewById<TextView>(R.id.action_subtitle).text = thenLabel
        findViewById<View>(R.id.row_condition_else).findViewById<TextView>(R.id.action_subtitle).text = elseLabel

        putConfig("${prefKey}_label", "if($ifLabel){$thenLabel} else {$elseLabel}")
    }

    private fun resolveOrCreateId(): String {
        val existing = getConfigString(prefKey, "")
        val extracted = ConditionStore.extractId(existing)
        if (extracted != null) return extracted
        return System.currentTimeMillis().toString()
    }
}
