package com.fan.edgex.ui.compose.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fan.edgex.BuildConfig
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.putConfig
import com.fan.edgex.license.PremiumActivator
import com.fan.edgex.ui.PremiumActivity
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIconBox
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.EdgeXSwitchRow
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors
import com.fan.edgex.utils.DonateDialog
import com.fan.edgex.utils.UpdateChecker

@Composable
fun PremiumScreen(
    onBack: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val thanksSupport = stringResource(R.string.compose_thanks_support)
    var status by remember { mutableStateOf(PremiumActivator.status(context)) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(title = stringResource(R.string.compose_premium_title), onBack = onBack)
        PremiumHero(status)
        PremiumSectionLabel(stringResource(R.string.compose_premium_features))
        EdgeXListGroup(modifier = Modifier.padding(16.dp)) {
            premiumRows().forEachIndexed { index, row ->
                EdgeXRow(title = row.first, subtitle = row.second, icon = EdgeXIcons.Sparkle) {
                    EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurface)
                }
                if (index != premiumRows().lastIndex) EdgeXDivider()
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
private fun PremiumHero(status: PremiumActivator.Status) {
    val colors = LocalEdgeXColors.current
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
                .height(192.dp)
                .padding(24.dp),
        ) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.accent.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EdgeXIcon(EdgeXIcons.Sparkle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(12.dp))
                    Text(stringResource(R.string.compose_premium_badge), color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text(
                    stringResource(R.string.compose_premium_hero),
                    color = Color(0xFFF4F0E8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    lineHeight = 35.sp,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    text = if (status == PremiumActivator.Status.NotActivated) {
                        stringResource(R.string.compose_premium_hero_subtitle)
                    } else {
                        premiumStatusText(status)
                    },
                    color = Color(0xFFF4F0E8).copy(alpha = 0.70f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
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
                icon = EdgeXIcons.Check,
            )
            EdgeXDivider()
            EdgeXRow(
                title = stringResource(R.string.label_project_url),
                subtitle = projectUrl,
                icon = EdgeXIcons.Multi,
                onClick = { context.openUrl("https://$projectUrl") },
            ) {
                EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurface)
            }
            EdgeXDivider()
            EdgeXRow(
                title = stringResource(R.string.compose_about_support_author),
                subtitle = stringResource(R.string.compose_about_support_author_crypto),
                icon = EdgeXIcons.Sparkle,
                onClick = { DonateDialog.show(context) },
            ) {
                EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurface)
            }
        }
        PremiumSectionLabel(stringResource(R.string.compose_developer))
        EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            EdgeXRow(
                title = stringResource(R.string.compose_debug_logs),
                icon = EdgeXIcons.Theme,
                onClick = {
                    (context as? Activity)?.let(UpdateChecker::checkNow)
                },
            )
            EdgeXDivider()
            EdgeXRow(
                title = stringResource(R.string.compose_export_config),
                icon = EdgeXIcons.Pie,
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

@Composable
private fun premiumRows(): List<Pair<String, String>> =
    listOf(
        stringResource(R.string.header_edge_lighting) to stringResource(R.string.compose_premium_feature_edge_lighting),
        stringResource(R.string.compose_premium_feature_pie_title) to stringResource(R.string.compose_premium_feature_pie),
        stringResource(R.string.compose_premium_feature_multi_title) to stringResource(R.string.compose_premium_feature_multi),
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
        PremiumActivator.Status.NotActivated -> stringResource(R.string.compose_premium_unlock)
        PremiumActivator.Status.RebootRequired -> stringResource(R.string.compose_premium_status_reboot)
        PremiumActivator.Status.Installed -> stringResource(R.string.compose_premium_status_installed)
    }

private fun Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}
