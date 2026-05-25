package com.fan.edgex.ui.compose.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.fan.edgex.BuildConfig
import com.fan.edgex.R
import com.fan.edgex.license.PremiumActivator
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors
import com.fan.edgex.utils.ActivationDialog
import com.fan.edgex.utils.DonateDialog
import com.fan.edgex.utils.UpdateChecker
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
    val thanksSupport = stringResource(R.string.compose_thanks_support)
    var status by remember { mutableStateOf(PremiumActivator.status(context)) }
    var deactivating by remember { mutableStateOf(false) }

    fun refreshStatus() {
        status = PremiumActivator.status(context)
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
            onActivate = {
                ActivationDialog.show(context) { refreshStatus() }
            },
            onDeactivate = {
                showDeactivateConfirmDialog(context) {
                    deactivating = true
                    deactivateSupporterExtras(context) { message ->
                        deactivating = false
                        refreshStatus()
                        showToast(message)
                    }
                }
            },
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
        SupportGrid(
            onDonate = {
                DonateDialog.show(context)
                showToast(thanksSupport)
            },
        )
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun SupporterExtrasHero(
    status: PremiumActivator.Status,
    deactivating: Boolean,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
) {
    val colors = LocalEdgeXColors.current
    val activated = status != PremiumActivator.Status.NotActivated
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
                        .background(colors.accent.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EdgeXIcon(
                        imageVector = when (status) {
                            PremiumActivator.Status.NotActivated -> EdgeXIcons.Info
                            PremiumActivator.Status.RebootRequired -> EdgeXIcons.Restart
                            PremiumActivator.Status.Installed -> EdgeXIcons.Sparkle
                        },
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(premiumStatusText(status), color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text(
                    stringResource(R.string.compose_premium_hero),
                    color = Color(0xFFF4F0E8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    lineHeight = 35.sp,
                )
                Text(
                    text = premiumStatusDescription(status),
                    color = Color(0xFFF4F0E8).copy(alpha = 0.70f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
                premiumDetailText(status)?.let {
                    Text(it, color = Color(0xFFF4F0E8).copy(alpha = 0.78f), fontSize = 12.sp, lineHeight = 17.sp)
                }
                if (activated) {
                    Button(onClick = onDeactivate, enabled = !deactivating, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (deactivating) R.string.premium_deactivating else R.string.premium_deactivate))
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
private fun SupportGrid(onDonate: () -> Unit) {
    val methods = listOf(
        listOf(stringResource(R.string.donate_alipay) to Color(0xFFCFE0FA), stringResource(R.string.donate_wechat) to Color(0xFFC7EFCC)),
        listOf(stringResource(R.string.donate_kofi) to Color(0xFFFBD7CF), stringResource(R.string.compose_donate_crypto_short) to Color(0xFFE1D5FA)),
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        methods.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (title, color) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(EdgeXRadius.lg),
                        colors = CardDefaults.cardColors(containerColor = LocalEdgeXColors.current.surface),
                        border = BorderStroke(1.dp, LocalEdgeXColors.current.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        onClick = onDonate,
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(color),
                            )
                            Column {
                                Text(title, color = LocalEdgeXColors.current.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(stringResource(R.string.compose_scan_donate), color = LocalEdgeXColors.current.onSurfaceDim, fontSize = 11.sp)
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
                title = stringResource(R.string.compose_about_support_author),
                subtitle = stringResource(R.string.compose_about_support_author_crypto),
                icon = EdgeXIcons.Donate,
                onClick = { DonateDialog.show(context) },
            ) {
                EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurface)
            }
        }
        PremiumSectionLabel(stringResource(R.string.compose_developer))
        EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            EdgeXRow(
                title = stringResource(R.string.compose_debug_logs),
                icon = EdgeXIcons.Info,
                onClick = {
                    (context as? Activity)?.let(UpdateChecker::checkNow)
                },
            )
            EdgeXDivider()
            EdgeXRow(
                title = stringResource(R.string.compose_export_config),
                icon = EdgeXIcons.Link,
                onClick = { context.openUrl("https://$projectUrl") },
            ) {
                EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurface)
            }
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
            EdgeXIcon(EdgeXIcons.Gesture, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(40.dp))
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
private fun premiumStatusDescription(status: PremiumActivator.Status): String =
    when (status) {
        PremiumActivator.Status.NotActivated -> stringResource(R.string.compose_premium_hero_subtitle)
        PremiumActivator.Status.RebootRequired -> stringResource(R.string.premium_desc_reboot)
        PremiumActivator.Status.Installed -> stringResource(R.string.premium_status_active)
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

private fun showDeactivateConfirmDialog(context: Context, onConfirm: () -> Unit) {
    AlertDialog.Builder(context)
        .setTitle(R.string.premium_deactivate_confirm_title)
        .setMessage(R.string.premium_deactivate_confirm_message)
        .setPositiveButton(R.string.premium_deactivate) { _, _ -> onConfirm() }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
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
