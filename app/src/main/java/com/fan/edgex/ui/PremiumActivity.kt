package com.fan.edgex.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fan.edgex.R
import com.fan.edgex.license.PremiumActivator
import com.fan.edgex.utils.ActivationDialog

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
        val activated = status != PremiumActivator.Status.NotActivated

        val statusRes = when (status) {
            PremiumActivator.Status.NotActivated -> R.string.menu_premium_not_activated
            PremiumActivator.Status.RebootRequired -> R.string.menu_premium_reboot_required
            PremiumActivator.Status.Installed -> R.string.menu_premium_installed
        }
        findViewById<TextView>(R.id.text_status).text = getString(statusRes)

        val codeView = findViewById<TextView>(R.id.text_activation_code)
        val code = PremiumActivator.getActivationCode(this)
        if (activated && code != null) {
            codeView.visibility = View.VISIBLE
            codeView.text = getString(R.string.premium_code_label, maskCode(code))
        } else {
            codeView.visibility = View.GONE
        }

        val btnActivate = findViewById<Button>(R.id.btn_activate)
        val btnDeactivate = findViewById<Button>(R.id.btn_deactivate)

        if (activated) {
            btnActivate.visibility = View.GONE
            btnDeactivate.visibility = View.VISIBLE
        } else {
            btnActivate.visibility = View.VISIBLE
            btnDeactivate.visibility = View.GONE
        }

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
            .setPositiveButton(R.string.premium_deactivate) { _, _ ->
                PremiumActivator.deactivate(this)
                Toast.makeText(this, R.string.premium_deactivate_success, Toast.LENGTH_SHORT).show()
                refreshStatus()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun maskCode(code: String): String {
        if (code.length <= 4) return "****"
        return code.take(4) + "*".repeat(code.length - 4)
    }
}
