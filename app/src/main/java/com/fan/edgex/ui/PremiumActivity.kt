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
    private var updateCheckStarted = false
    private var updateCheckInProgress = false
    private var updateStatusText: String? = null
    private var availableUpdate: PremiumActivator.DexUpdateStatus.Available? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)
        ThemeManager.applyToActivity(this)

        findViewById<View>(R.id.header_container).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                view.paddingLeft,
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top,
                view.paddingRight,
                view.paddingBottom,
            )
            insets
        }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<View>(R.id.item_edge_lighting).setOnClickListener {
            startActivity(Intent(this, EdgeLightingSettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_update).setOnClickListener {
            performUpdate()
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
                iconColorArgb = ThemeManager.currentAccent(this),
                titleRes = R.string.menu_premium_not_activated,
                descText = getString(R.string.premium_desc_not_activated),
            )
            PremiumActivator.Status.RebootRequired -> StateVisuals(
                iconRes = R.drawable.ic_restart_alt,
                iconColorArgb = ThemeManager.currentAccent(this),
                titleRes = R.string.premium_status_reboot,
                descText = buildString {
                    append(getString(R.string.premium_desc_reboot))
                    PremiumActivator.getActivationCode(this@PremiumActivity)?.let {
                        append("\n")
                        append(getString(R.string.premium_code_label, it))
                    }
                },
            )
            PremiumActivator.Status.Installed -> StateVisuals(
                iconRes = R.drawable.ic_supporter_extra,
                iconColorArgb = getColor(R.color.ui_icon_bg),
                titleRes = R.string.premium_status_active,
                descText = buildString {
                    PremiumActivator.getActivationCode(this@PremiumActivity)?.let {
                        append(getString(R.string.premium_code_label, it))
                    }
                }.takeIf { it.isNotEmpty() },
            )
        }

        val iconBg = findViewById<FrameLayout>(R.id.icon_status_bg)
        iconBg.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(visuals.iconColorArgb)
        }
        findViewById<ImageView>(R.id.icon_status).apply {
            setImageResource(visuals.iconRes)
            setColorFilter(ThemeManager.onAccentColor(visuals.iconColorArgb))
        }
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

        refreshDexInfo(status)
        startUpdateCheckIfNeeded(status)
    }

    private fun refreshDexInfo(status: PremiumActivator.Status) {
        val dexInfoView = findViewById<TextView>(R.id.text_dex_info)
        val btnUpdate = findViewById<Button>(R.id.btn_update)
        val activated = status != PremiumActivator.Status.NotActivated

        if (!activated) {
            updateCheckStarted = false
            updateCheckInProgress = false
            updateStatusText = null
            availableUpdate = null
            dexInfoView.visibility = View.GONE
            btnUpdate.visibility = View.GONE
            return
        }

        val lines = mutableListOf<String>()
        PremiumActivator.getDexInfo(this)?.let { info ->
            val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(Date(info.installedAtMs))
            lines += getString(R.string.premium_dex_info, info.apiVersion, info.hashPrefix, time)
        }
        updateStatusText?.let { lines += it }

        dexInfoView.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
        dexInfoView.text = lines.joinToString("\n")
        btnUpdate.visibility = if (availableUpdate != null && !updateCheckInProgress) View.VISIBLE else View.GONE
        btnUpdate.isEnabled = availableUpdate != null && !updateCheckInProgress
        if (!updateCheckInProgress) {
            btnUpdate.setText(R.string.premium_update_download)
        }
    }

    private fun startUpdateCheckIfNeeded(status: PremiumActivator.Status) {
        if (status == PremiumActivator.Status.NotActivated || updateCheckStarted || updateCheckInProgress) return

        updateCheckStarted = true
        updateCheckInProgress = true
        updateStatusText = getString(R.string.premium_dex_update_checking)
        availableUpdate = null
        refreshDexInfo(status)

        val appContext = applicationContext
        thread(name = "EdgeXPremiumUpdateCheck") {
            val result = PremiumActivator.checkInstalledDexUpdate(appContext)
            runOnUiThread {
                updateCheckInProgress = false
                result.onSuccess { updateStatus ->
                    when (updateStatus) {
                        is PremiumActivator.DexUpdateStatus.Available -> {
                            availableUpdate = updateStatus
                            updateStatusText = getString(
                                R.string.premium_dex_update_available,
                                updateStatus.info.hashPrefix,
                            )
                        }
                        PremiumActivator.DexUpdateStatus.UpToDate -> {
                            availableUpdate = null
                            updateStatusText = getString(R.string.premium_dex_update_up_to_date)
                        }
                        PremiumActivator.DexUpdateStatus.NotInstalled,
                        PremiumActivator.DexUpdateStatus.MissingActivationCode -> {
                            availableUpdate = null
                            updateStatusText = null
                        }
                    }
                }.onFailure {
                    availableUpdate = null
                    updateStatusText = getString(
                        R.string.premium_update_failed,
                        it.message ?: it.javaClass.simpleName,
                    )
                }
                refreshDexInfo(PremiumActivator.status(this))
            }
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

    private fun performUpdate() {
        val btnUpdate = findViewById<Button>(R.id.btn_update)
        btnUpdate.isEnabled = false
        btnUpdate.setText(R.string.premium_update_downloading)
        updateCheckInProgress = true
        refreshDexInfo(PremiumActivator.status(this))

        val appContext = applicationContext
        thread(name = "EdgeXPremiumUpdateDownload") {
            val result = PremiumActivator.updateInstalledDexIfNeeded(appContext)
            runOnUiThread {
                updateCheckInProgress = false
                result.onSuccess { updateResult ->
                    availableUpdate = null
                    updateCheckStarted = true
                    updateStatusText = when (updateResult) {
                        PremiumActivator.UpdateResult.Updated -> getString(R.string.premium_update_success)
                        PremiumActivator.UpdateResult.UpToDate -> getString(R.string.premium_dex_update_up_to_date)
                        PremiumActivator.UpdateResult.NotInstalled,
                        PremiumActivator.UpdateResult.SkippedMissingActivationCode -> null
                    }
                    if (updateResult == PremiumActivator.UpdateResult.Updated) {
                        Toast.makeText(this, R.string.premium_update_success, Toast.LENGTH_LONG).show()
                    }
                }.onFailure {
                    updateStatusText = getString(
                        R.string.premium_update_failed,
                        it.message ?: it.javaClass.simpleName,
                    )
                    Toast.makeText(this, updateStatusText, Toast.LENGTH_LONG).show()
                }
                refreshStatus()
            }
        }
    }

}
