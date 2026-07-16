package com.fan.edgex.ui.compose.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.fan.edgex.BuildConfig
import com.fan.edgex.R
import com.fan.edgex.license.PremiumActivator
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIconBox
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors
import com.fan.edgex.utils.DonateDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

@Composable
fun PremiumScreen(
    onBack: () -> Unit,
    onOpenEdgeLighting: () -> Unit,
    onOpenFluidEffect: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(PremiumActivator.status(context)) }
    var deactivating by remember { mutableStateOf(false) }
    var showActivationDialog by remember { mutableStateOf(false) }
    var showDeactivateDialog by remember { mutableStateOf(false) }

    var updateCheckStarted by remember { mutableStateOf(false) }
    var updateCheckInProgress by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf<String?>(null) }
    var availableUpdate by remember { mutableStateOf<PremiumActivator.DexUpdateStatus.Available?>(null) }
    var isUpdating by remember { mutableStateOf(false) }

    fun refreshStatus() {
        status = PremiumActivator.status(context)
    }

    LaunchedEffect(status) {
        if (status == PremiumActivator.Status.NotActivated || updateCheckStarted || updateCheckInProgress) return@LaunchedEffect
        updateCheckStarted = true
        updateCheckInProgress = true
        updateStatusText = context.getString(R.string.premium_dex_update_checking)
        availableUpdate = null

        thread(name = "EdgeXPremiumComposeUpdateCheck") {
            val result = PremiumActivator.checkInstalledDexUpdate(context.applicationContext)
            context.mainExecutor.execute {
                updateCheckInProgress = false
                result.onSuccess { updateStatus ->
                    when (updateStatus) {
                        is PremiumActivator.DexUpdateStatus.Available -> {
                            availableUpdate = updateStatus
                            updateStatusText = context.getString(
                                R.string.premium_dex_update_available,
                                updateStatus.info.hashPrefix,
                            )
                        }
                        PremiumActivator.DexUpdateStatus.UpToDate -> {
                            availableUpdate = null
                            updateStatusText = context.getString(R.string.premium_dex_update_up_to_date)
                        }
                        PremiumActivator.DexUpdateStatus.NotInstalled,
                        PremiumActivator.DexUpdateStatus.MissingActivationCode -> {
                            availableUpdate = null
                            updateStatusText = null
                        }
                    }
                }.onFailure { error ->
                    availableUpdate = null
                    updateStatusText = context.getString(
                        R.string.premium_update_failed,
                        error.message ?: error.javaClass.simpleName,
                    )
                }
            }
        }
    }

    val onUpdate: () -> Unit = {
        isUpdating = true
        thread(name = "EdgeXPremiumComposeUpdate") {
            val result = PremiumActivator.updateInstalledDexIfNeeded(context.applicationContext)
            context.mainExecutor.execute {
                isUpdating = false
                result.onSuccess { updateResult ->
                    availableUpdate = null
                    updateCheckStarted = true
                    updateStatusText = when (updateResult) {
                        PremiumActivator.UpdateResult.Updated -> {
                            refreshStatus()
                            showToast(context.getString(R.string.premium_update_success))
                            context.getString(R.string.premium_update_success)
                        }
                        PremiumActivator.UpdateResult.UpToDate -> context.getString(R.string.premium_dex_update_up_to_date)
                        PremiumActivator.UpdateResult.NotInstalled,
                        PremiumActivator.UpdateResult.SkippedMissingActivationCode -> null
                    }
                }.onFailure { error ->
                    updateStatusText = context.getString(
                        R.string.premium_update_failed,
                        error.message ?: error.javaClass.simpleName,
                    )
                    showToast(updateStatusText!!)
                }
            }
        }
    }

    if (showActivationDialog) {
        ActivationCodeDialog(
            onDismiss = { showActivationDialog = false },
            onActivated = {
                showActivationDialog = false
                updateCheckStarted = false
                updateCheckInProgress = false
                updateStatusText = null
                availableUpdate = null
                isUpdating = false
                refreshStatus()
            },
        )
    }

    if (showDeactivateDialog) {
        DeactivateConfirmDialog(
            onDismiss = { showDeactivateDialog = false },
            onConfirm = {
                showDeactivateDialog = false
                deactivating = true
                deactivateSupporterExtras(context) { message ->
                    deactivating = false
                    updateCheckStarted = false
                    updateCheckInProgress = false
                    updateStatusText = null
                    availableUpdate = null
                    isUpdating = false
                    refreshStatus()
                    showToast(message)
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(title = stringResource(R.string.compose_premium_title), onBack = onBack)
        SupporterExtrasHero(
            status = status,
            deactivating = deactivating,
            onActivate = { showActivationDialog = true },
            onDeactivate = { showDeactivateDialog = true },
            updateStatusText = updateStatusText,
            updateCheckInProgress = updateCheckInProgress,
            availableUpdate = availableUpdate,
            isUpdating = isUpdating,
            onUpdate = onUpdate,
        )
        PremiumSectionLabel(stringResource(R.string.compose_premium_features))
        EdgeXListGroup(modifier = Modifier.padding(16.dp)) {
            val rows = premiumRows(
                onOpenEdgeLighting = onOpenEdgeLighting,
                onOpenFluidEffect = onOpenFluidEffect,
            )
            rows.forEachIndexed { index, row ->
                EdgeXRow(title = row.title, subtitle = row.subtitle, icon = row.icon, onClick = row.onClick) {
                    EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurface)
                }
                if (index != rows.lastIndex) EdgeXDivider()
            }
        }
        PremiumSectionLabel(stringResource(R.string.compose_support_methods))
        SupportGrid(showToast = showToast)
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun SupporterExtrasHero(
    status: PremiumActivator.Status,
    deactivating: Boolean,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    updateStatusText: String?,
    updateCheckInProgress: Boolean,
    availableUpdate: PremiumActivator.DexUpdateStatus.Available?,
    isUpdating: Boolean,
    onUpdate: () -> Unit,
) {
    val colors = LocalEdgeXColors.current
    val activated = status != PremiumActivator.Status.NotActivated
    val dotColor = when (status) {
        PremiumActivator.Status.NotActivated -> Color(0xFFEF5350)
        PremiumActivator.Status.RebootRequired -> Color(0xFFFFA726)
        PremiumActivator.Status.Installed -> Color(0xFF4CAF50)
    }
    val statusTextColor = when (status) {
        PremiumActivator.Status.NotActivated -> colors.onSurfaceDim
        PremiumActivator.Status.RebootRequired -> colors.onSurfaceDim
        PremiumActivator.Status.Installed -> colors.accentSoft
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 20.dp),
        shape = RoundedCornerShape(EdgeXRadius.xl),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1E14)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.radialGradient(
                        listOf(colors.accent.copy(alpha = 0.32f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(520f, 20f),
                        radius = 280f,
                    ),
                )
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.onAccentSoft)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(dotColor),
                    )
                    Text(premiumStatusText(status), color = statusTextColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text(
                    stringResource(R.string.compose_premium_hero),
                    color = Color(0xFFF4F0E8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    lineHeight = 35.sp,
                )
                premiumDetailText(status)?.let {
                    Text(it, color = Color(0xFFF4F0E8).copy(alpha = 0.78f), fontSize = 12.sp, lineHeight = 17.sp)
                }
                updateStatusText?.let {
                    Text(
                        it,
                        color = colors.accentSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 17.sp
                    )
                }
                if (activated) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (availableUpdate != null || isUpdating) {
                            Button(
                                onClick = onUpdate,
                                enabled = !isUpdating && !deactivating,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(if (isUpdating) R.string.premium_update_downloading else R.string.premium_update_download))
                            }
                        }
                        Button(
                            onClick = onDeactivate,
                            enabled = !deactivating && !isUpdating,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                contentColor = Color(0xFFEF5350),
                                disabledContainerColor = Color.White.copy(alpha = 0.04f),
                                disabledContentColor = Color(0xFFEF5350).copy(alpha = 0.5f),
                            ),
                        ) {
                            Text(stringResource(if (deactivating) R.string.premium_deactivating else R.string.premium_deactivate))
                        }
                    }
                } else {
                    Button(onClick = onActivate, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.premium_activate))
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportGrid(showToast: (String) -> Unit) {
    val context = LocalContext.current
    val thanksSupport = stringResource(R.string.compose_thanks_support)
    val scanDonate = stringResource(R.string.compose_scan_donate)
    val clickJump = stringResource(R.string.compose_donate_click_jump)
    val viewAddress = stringResource(R.string.compose_donate_view_address)
    data class MethodItem(val title: String, val subtitle: String, val icon: Int, val iconSize: Dp = 22.dp)
    val methods = listOf(
        listOf(
            MethodItem(stringResource(R.string.donate_alipay), scanDonate, EdgeXIcons.Alipay) to {
                DonateDialog.showAlipayQr(context)
                showToast(thanksSupport)
            },
            MethodItem(stringResource(R.string.donate_wechat), scanDonate, EdgeXIcons.WechatPay) to {
                DonateDialog.showWechatQr(context)
                showToast(thanksSupport)
            },
        ),
        listOf(
            MethodItem(stringResource(R.string.donate_kofi), clickJump, EdgeXIcons.KoFi) to {
                DonateDialog.openKofi(context)
                showToast(thanksSupport)
            },
            MethodItem(stringResource(R.string.compose_donate_crypto_short), viewAddress, EdgeXIcons.Eth, 33.dp) to {
                DonateDialog.showCryptoAddresses(context)
                showToast(thanksSupport)
            },
        ),
    )
    val colors = LocalEdgeXColors.current
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        methods.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (item, action) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(EdgeXRadius.lg),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(1.dp, colors.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        onClick = action,
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            EdgeXIconBox(
                                imageVector = item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                iconSize = item.iconSize,
                            )
                            Column {
                                Text(item.title, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(item.subtitle, color = colors.onSurfaceDim, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    showToast: (String) -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenSupportAuthor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val projectUrl = stringResource(R.string.value_project_url)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(title = stringResource(R.string.menu_about), onBack = onBack)
        AboutHeader()
        EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            EdgeXRow(
                title = stringResource(R.string.label_author),
                subtitle = stringResource(R.string.value_author),
                icon = EdgeXIcons.Person,
            )
            EdgeXDivider()
            EdgeXRow(
                title = stringResource(R.string.label_project_url),
                subtitle = projectUrl,
                icon = EdgeXIcons.Link,
                onClick = { context.openUrl("https://$projectUrl") },
            ) {
                EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurface)
            }
            EdgeXDivider()
            EdgeXRow(
                title = stringResource(R.string.compose_about_edgey),
                subtitle = stringResource(R.string.compose_about_edgey_subtitle),
                icon = EdgeXIcons.GooglePlay,
                onClick = { context.openUrl("https://play.google.com/store/apps/details?id=com.fan.EdgeY") },
            ) {
                EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurface)
            }
            EdgeXDivider()
            EdgeXRow(
                title = stringResource(R.string.label_version),
                subtitle = "v${BuildConfig.VERSION_NAME}",
                icon = EdgeXIcons.Info,
                onClick = onCheckForUpdates,
            ) {
                EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurface)
            }
            EdgeXDivider()
            EdgeXRow(
                title = stringResource(R.string.compose_about_support_author),
                subtitle = stringResource(R.string.compose_about_support_author_crypto),
                icon = EdgeXIcons.Donate,
                onClick = onOpenSupportAuthor,
            ) {
                EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurface)
            }
        }
        PremiumSectionLabel(stringResource(R.string.compose_developer))
        EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            EdgeXRow(
                title = stringResource(R.string.compose_debug_logs),
                icon = EdgeXIcons.DeveloperMode,
                onClick = { showToast(context.getString(R.string.compose_coming_soon)) },
            )
        }
        Text(
            text = stringResource(R.string.compose_build_info, BuildConfig.VERSION_CODE, BuildConfig.APPLICATION_ID),
            color = LocalEdgeXColors.current.onSurfaceDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun AboutHeader() {
    val colors = LocalEdgeXColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(colors.accent, colors.accentPress))),
            contentAlignment = Alignment.Center,
        ) {
            EdgeXIcon(R.drawable.ic_launcher_foreground, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(60.dp))
        }
        Text(stringResource(R.string.app_name), color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 32.sp, modifier = Modifier.padding(top = 16.dp))
        Text(
            text = stringResource(R.string.compose_version_info, BuildConfig.VERSION_NAME),
            color = colors.onSurfaceDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.compose_about_description),
            color = colors.onSurface2,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

private data class PremiumRow(
    val title: String,
    val subtitle: String,
    val icon: Int,
    val onClick: () -> Unit,
)

@Composable
private fun premiumRows(
    onOpenEdgeLighting: () -> Unit,
    onOpenFluidEffect: () -> Unit,
): List<PremiumRow> =
    listOf(
        PremiumRow(
            title = stringResource(R.string.header_edge_lighting),
            subtitle = stringResource(R.string.menu_edge_lighting_desc),
            icon = EdgeXIcons.EdgeLighting,
            onClick = onOpenEdgeLighting,
        ),
        PremiumRow(
            title = stringResource(R.string.header_fluid_effect),
            subtitle = stringResource(R.string.menu_fluid_effect_desc),
            icon = EdgeXIcons.FluidEffect,
            onClick = onOpenFluidEffect,
        ),
    )

@Composable
private fun PremiumSectionLabel(label: String) {
    Text(
        text = label,
        color = LocalEdgeXColors.current.onSurfaceDim,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 10.dp),
    )
}

@Composable
private fun premiumStatusText(status: PremiumActivator.Status): String =
    when (status) {
        PremiumActivator.Status.NotActivated -> stringResource(R.string.menu_premium_not_activated)
        PremiumActivator.Status.RebootRequired -> stringResource(R.string.compose_premium_status_reboot)
        PremiumActivator.Status.Installed -> stringResource(R.string.compose_premium_status_installed)
    }

@Composable
private fun premiumDetailText(status: PremiumActivator.Status): String? {
    val context = LocalContext.current
    return when (status) {
        PremiumActivator.Status.NotActivated -> stringResource(R.string.premium_desc_not_activated)
        PremiumActivator.Status.RebootRequired -> PremiumActivator.getActivationCode(context)?.let {
            stringResource(R.string.premium_code_label, it)
        }
        PremiumActivator.Status.Installed -> buildList {
            PremiumActivator.getActivationCode(context)?.let {
                add(context.getString(R.string.premium_code_label, it))
            }
            PremiumActivator.getDexInfo(context)?.let { info ->
                val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(info.installedAtMs))
                add(context.getString(R.string.premium_dex_info, info.apiVersion, info.hashPrefix, time))
            }
        }.joinToString("\n").takeIf { it.isNotEmpty() }
    }
}

@Composable
private fun DeactivateConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.premium_deactivate_confirm_title)) },
        text = { Text(stringResource(R.string.premium_deactivate_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.premium_deactivate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun ActivationCodeDialog(
    onDismiss: () -> Unit,
    onActivated: () -> Unit,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.premium_activation_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = errorMessage ?: stringResource(R.string.premium_activation_message),
                    color = if (errorMessage != null) {
                        androidx.compose.material3.MaterialTheme.colorScheme.error
                    } else {
                        LocalEdgeXColors.current.onSurface2
                    },
                    fontSize = 14.sp,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    enabled = !loading,
                    placeholder = { Text(stringResource(R.string.premium_activation_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (code.isBlank()) {
                        errorMessage = context.getString(R.string.premium_activation_error_empty_code)
                        return@TextButton
                    }
                    loading = true
                    errorMessage = null
                    keyboardController?.hide()
                    thread(name = "EdgeXPremiumActivation") {
                        val result = PremiumActivator.activate(context.applicationContext, code)
                        result.onSuccess {
                            context.mainExecutor.execute {
                                Toast.makeText(context, R.string.premium_activation_success, Toast.LENGTH_LONG).show()
                                onActivated()
                            }
                        }.onFailure { error ->
                            context.mainExecutor.execute {
                                loading = false
                                errorMessage = context.getString(
                                    R.string.premium_activation_failed,
                                    activationFailureMessage(context, error),
                                )
                            }
                        }
                    }
                },
                enabled = !loading,
            ) {
                Text(
                    text = if (loading) stringResource(R.string.premium_activation_in_progress)
                    else stringResource(R.string.premium_activate),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

private fun activationFailureMessage(context: Context, throwable: Throwable): String {
    val rawMessage = throwable.message.orEmpty()
    val message = rawMessage.lowercase()
    return when {
        message.contains("activation code is empty") ->
            context.getString(R.string.premium_activation_error_empty_code)
        message.contains("invalid code") || message.contains("activation failed (404)") ->
            context.getString(R.string.premium_activation_error_invalid_code)
        message.contains("api url is not configured") ->
            context.getString(R.string.premium_activation_error_not_configured)
        message.contains("downloaded premium dex hash mismatch") ->
            context.getString(R.string.premium_activation_error_package_verify)
        message.contains("unsupported premium version") ->
            context.getString(R.string.premium_activation_error_unsupported_version)
        message.contains("download failed") ->
            context.getString(R.string.premium_activation_error_download)
        message.contains("root install failed") || message.contains("permission denied") ->
            context.getString(R.string.premium_activation_error_root)
        message.contains("timeout") ||
            message.contains("failed to connect") ||
            message.contains("unable to resolve host") ||
            throwable.javaClass.name.startsWith("java.net.") ->
            context.getString(R.string.premium_activation_error_network)
        message.contains("activation failed (") || message.contains("request failed (") ->
            context.getString(R.string.premium_activation_error_server)
        else -> rawMessage.ifBlank { throwable.javaClass.simpleName }
    }
}

private fun deactivateSupporterExtras(context: Context, onComplete: (String) -> Unit) {
    val appContext = context.applicationContext
    thread(name = "EdgeXPremiumComposeDeactivate") {
        val result = PremiumActivator.deactivate(appContext)
        context.mainExecutor.execute {
            val message = result.fold(
                onSuccess = { context.getString(R.string.premium_deactivate_success) },
                onFailure = {
                    context.getString(
                        R.string.premium_activation_failed,
                        it.message ?: it.javaClass.simpleName,
                    )
                },
            )
            onComplete(message)
        }
    }
}

private fun Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}
