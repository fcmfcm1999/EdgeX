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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fan.edgex.BuildConfig
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
    var status by remember { mutableStateOf(PremiumActivator.status(context)) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(title = "Premium", onBack = onBack)
        PremiumHero(status)
        EdgeXListGroup(modifier = Modifier.padding(16.dp)) {
            premiumRows().forEachIndexed { index, row ->
                EdgeXRow(title = row.first, subtitle = row.second, icon = EdgeXIcons.Sparkle)
                if (index != premiumRows().lastIndex) EdgeXDivider()
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    context.startActivity(Intent(context, PremiumActivity::class.java))
                    status = PremiumActivator.status(context)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalEdgeXColors.current.accent,
                    contentColor = LocalEdgeXColors.current.onAccent,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (status == PremiumActivator.Status.NotActivated) "激活 Premium" else "管理 Premium", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    DonateDialog.show(context)
                    showToast("感谢支持 EdgeX")
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalEdgeXColors.current.surface1,
                    contentColor = LocalEdgeXColors.current.onSurface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("捐赠 / 订阅选项", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun PremiumHero(status: PremiumActivator.Status) {
    val colors = LocalEdgeXColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(EdgeXRadius.xl),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF1B1E14)),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f)),
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.radialGradient(
                        listOf(colors.accent.copy(alpha = 0.36f), androidx.compose.ui.graphics.Color.Transparent),
                    ),
                )
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EdgeXIconBox(
                    imageVector = EdgeXIcons.Sparkle,
                    contentDescription = null,
                    background = colors.accentSoft,
                    tint = colors.onAccentSoft,
                )
                Text("Premium", color = androidx.compose.ui.graphics.Color(0xFFE7E6D5), fontWeight = FontWeight.Bold, fontSize = 34.sp)
                Text(
                    text = premiumStatusText(status),
                    color = androidx.compose.ui.graphics.Color(0xFFC6C5B3),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                )
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
    var debug by remember { mutableStateOf(context.getConfigBool(AppConfig.DEBUG_MATRIX)) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(title = "关于", onBack = onBack)
        AboutHeader()
        EdgeXListGroup(modifier = Modifier.padding(16.dp)) {
            EdgeXRow(
                title = "更新日志",
                subtitle = "检查 GitHub Releases",
                icon = EdgeXIcons.Sparkle,
                onClick = {
                    (context as? Activity)?.let(UpdateChecker::checkNow)
                },
            )
            EdgeXDivider()
            EdgeXRow(
                title = "反馈",
                subtitle = "github.com/fcmfcm1999/EdgeX",
                icon = EdgeXIcons.Multi,
                onClick = { context.openUrl("https://github.com/fcmfcm1999/EdgeX/issues") },
            )
            EdgeXDivider()
            EdgeXRow(
                title = "开源许可",
                subtitle = "查看项目仓库",
                icon = EdgeXIcons.Pie,
                onClick = { context.openUrl("https://github.com/fcmfcm1999/EdgeX") },
            )
            EdgeXDivider()
            EdgeXSwitchRow(
                title = "调试模式",
                subtitle = "显示手势触发区域",
                checked = debug,
                onCheckedChange = {
                    debug = it
                    context.putConfig(AppConfig.DEBUG_MATRIX, it)
                    showToast(if (it) "调试模式已开启" else "调试模式已关闭")
                },
                icon = EdgeXIcons.Theme,
            )
            EdgeXDivider()
            EdgeXRow(
                title = "捐赠",
                subtitle = "Buy me more AI token",
                icon = EdgeXIcons.Sparkle,
                onClick = { DonateDialog.show(context) },
            )
        }
        Text(
            text = "Build ${BuildConfig.VERSION_CODE} · ${BuildConfig.APPLICATION_ID}",
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text("E", color = colors.onAccentSoft, fontWeight = FontWeight.Bold, fontSize = 30.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("EdgeX", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                color = colors.onSurfaceDim,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun premiumRows(): List<Pair<String, String>> =
    listOf(
        "Edge Lighting" to "9 种通知光效",
        "Pie 菜单进阶" to "双环快速动作",
        "组合动作无上限" to "串联多个系统动作",
        "Fluid Effect" to "边缘触发反馈",
    )

private fun premiumStatusText(status: PremiumActivator.Status): String =
    when (status) {
        PremiumActivator.Status.NotActivated -> "解锁全部高级功能"
        PremiumActivator.Status.RebootRequired -> "已安装，重启后生效"
        PremiumActivator.Status.Installed -> "Premium 已激活"
    }

private fun Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}
