package com.fan.edgex.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R
import com.fan.edgex.license.PremiumActivator
import com.fan.edgex.utils.ActivationDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class PremiumActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)
        ThemeManager.applyToActivity(this)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<View>(R.id.item_edge_lighting).setOnClickListener {
            startActivity(Intent(this, EdgeLightingSettingsActivity::class.java))
        }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.applyToActivity(this)
        refreshStatus()
    }

    private fun refreshStatus() {
        val status = PremiumActivator.status(this)

        data class StateVisuals(
            val iconRes: Int,
            val iconColorArgb: Int,
            val titleRes: Int,
            val descText: String?,
        )

        val visuals = when (status) {
            PremiumActivator.Status.NotActivated -> StateVisuals(
                iconRes = R.drawable.ic_info,
                iconColorArgb = 0xFFBDBDBD.toInt(),
                titleRes = R.string.menu_premium_not_activated,
                descText = getString(R.string.premium_desc_not_activated),
            )
            PremiumActivator.Status.RebootRequired -> StateVisuals(
                iconRes = R.drawable.ic_restart_alt,
                iconColorArgb = 0xFFFF9800.toInt(),
                titleRes = R.string.premium_status_reboot,
                descText = buildString {
                    append(getString(R.string.premium_desc_reboot))
                    PremiumActivator.getActivationCode(this@PremiumActivity)?.let {
                        append("\n")
                        append(getString(R.string.premium_code_label, maskCode(it)))
                    }
                },
            )
            PremiumActivator.Status.Installed -> StateVisuals(
                iconRes = R.drawable.ic_donate,
                iconColorArgb = getColor(R.color.ui_icon_bg),
                titleRes = R.string.premium_status_active,
                descText = buildString {
                    PremiumActivator.getActivationCode(this@PremiumActivity)?.let {
                        append(getString(R.string.premium_code_label, maskCode(it)))
                    }
                    PremiumActivator.getDexInfo(this@PremiumActivity)?.let { info ->
                        if (isNotEmpty()) append("\n")
                        val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                            .format(Date(info.installedAtMs))
                        append("DEX ${info.hashPrefix}  ·  $time")
                    }
                }.takeIf { it.isNotEmpty() },
            )
        }

        val iconBg = findViewById<FrameLayout>(R.id.icon_status_bg)
        iconBg.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(visuals.iconColorArgb)
        }
        findViewById<ImageView>(R.id.icon_status).setImageResource(visuals.iconRes)
        findViewById<TextView>(R.id.text_status).setText(visuals.titleRes)

        val codeView = findViewById<TextView>(R.id.text_activation_code)
        if (!visuals.descText.isNullOrEmpty()) {
            codeView.visibility = View.VISIBLE
            codeView.text = visuals.descText
        } else {
            codeView.visibility = View.GONE
        }

        val activated = status != PremiumActivator.Status.NotActivated
        val btnActivate = findViewById<Button>(R.id.btn_activate)
        val btnDeactivate = findViewById<Button>(R.id.btn_deactivate)

        btnActivate.visibility = if (activated) View.GONE else View.VISIBLE
        btnDeactivate.visibility = if (activated) View.VISIBLE else View.GONE
        btnDeactivate.setText(R.string.premium_deactivate)
        btnDeactivate.isEnabled = true

        btnActivate.setOnClickListener {
            ActivationDialog.show(this) { refreshStatus() }
        }
        btnDeactivate.setOnClickListener {
            showDeactivateConfirmDialog()
        }
    }

    private fun showDeactivateConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.premium_deactivate_confirm_title)
            .setMessage(R.string.premium_deactivate_confirm_message)
            .setPositiveButton(R.string.premium_deactivate) { _, _ -> performDeactivate() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performDeactivate() {
        val btnDeactivate = findViewById<Button>(R.id.btn_deactivate)
        btnDeactivate.isEnabled = false
        btnDeactivate.text = getString(R.string.premium_deactivating)

        thread(name = "EdgeXPremiumDeactivate") {
            val result = PremiumActivator.deactivate(applicationContext)
            runOnUiThread {
                btnDeactivate.isEnabled = true
                result.onSuccess {
                    Toast.makeText(this, R.string.premium_deactivate_success, Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(
                        this,
                        getString(R.string.premium_activation_failed, it.message ?: it.javaClass.simpleName),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                refreshStatus()
            }
        }
    }

    private fun maskCode(code: String): String {
        if (code.length <= 4) return "****"
        return code.take(4) + "*".repeat(code.length - 4)
    }
}
