package com.fan.edgex.ui.compose.screens

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import com.fan.edgex.ui.ActionSelectionActivity
import com.fan.edgex.ui.compose.components.EdgeXBottomSheet
import com.fan.edgex.ui.compose.components.EdgeXChip
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIconBox
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.EdgeXSegmentedControl
import com.fan.edgex.ui.compose.components.EdgeXSwitchRow
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors

private data class KeyUiItem(
    val keyCode: Int,
    val title: String,
    val icon: ImageVector,
)

private data class KeyTrigger(
    val id: String,
    val label: String,
)

private val keyItems = listOf(
    KeyUiItem(KeyEvent.KEYCODE_VOLUME_UP, "音量+", EdgeXIcons.Keys),
    KeyUiItem(KeyEvent.KEYCODE_VOLUME_DOWN, "音量-", EdgeXIcons.Keys),
    KeyUiItem(KeyEvent.KEYCODE_POWER, "电源键", EdgeXIcons.Keys),
)

private val keyTriggers = listOf(
    KeyTrigger("click", "单击"),
    KeyTrigger("double_click", "双击"),
    KeyTrigger("long_press", "长按"),
)

@Composable
fun KeysScreen(
    onBack: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var masterEnabled by remember { mutableStateOf(context.getConfigBool(AppConfig.KEYS_ENABLED)) }
    var selectedKey by remember { mutableStateOf<KeyUiItem?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(title = "按键", onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                text = "硬件按键\n重新映射",
                color = LocalEdgeXColors.current.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                lineHeight = 32.sp,
            )
            Text(
                text = "音量键、电源键支持单击、双击和长按",
                color = LocalEdgeXColors.current.onSurfaceDim,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        EdgeXListGroup(modifier = Modifier.padding(16.dp)) {
            EdgeXSwitchRow(
                title = "启用按键重映射",
                subtitle = if (masterEnabled) "system_server 将拦截已配置按键" else "按键动作不会生效",
                checked = masterEnabled,
                onCheckedChange = {
                    masterEnabled = it
                    context.putConfig(AppConfig.KEYS_ENABLED, it)
                    showToast(if (it) "按键已启用" else "按键已停用")
                },
                icon = EdgeXIcons.Keys,
            )
        }
        EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            keyItems.forEachIndexed { index, item ->
                EdgeXRow(
                    title = item.title,
                    subtitle = context.keySubtitle(item.keyCode, refreshTick),
                    icon = item.icon,
                    onClick = { selectedKey = item },
                ) {
                    EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurfaceDim)
                }
                if (index != keyItems.lastIndex) EdgeXDivider()
            }
        }
    }

    KeyDetailSheet(
        key = selectedKey,
        refreshTick = refreshTick,
        onDismiss = { selectedKey = null },
        onTriggerClick = { key, trigger ->
            context.openActionPicker(
                prefKey = AppConfig.keyAction(key.keyCode, trigger.id),
                title = "${key.title} / ${trigger.label}",
            )
            selectedKey = null
            refreshTick++
        },
    )
}

@Composable
private fun KeyDetailSheet(
    key: KeyUiItem?,
    refreshTick: Int,
    onDismiss: () -> Unit,
    onTriggerClick: (KeyUiItem, KeyTrigger) -> Unit,
) {
    val context = LocalContext.current
    EdgeXBottomSheet(open = key != null, title = key?.title.orEmpty(), onDismissRequest = onDismiss) {
        if (key == null) return@EdgeXBottomSheet
        EdgeXListGroup {
            keyTriggers.forEachIndexed { index, trigger ->
                EdgeXRow(
                    title = trigger.label,
                    subtitle = context.getConfigString(
                        AppConfig.keyActionLabel(key.keyCode, trigger.id),
                        "无",
                    ) + refreshTick.let { "" },
                    icon = EdgeXIcons.Gesture,
                    onClick = { onTriggerClick(key, trigger) },
                ) {
                    EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurfaceDim)
                }
                if (index != keyTriggers.lastIndex) EdgeXDivider()
            }
        }
    }
}

private enum class PieEdge(val id: String, val label: String) {
    Left("left", "左"),
    Right("right", "右"),
    Top("top", "上"),
    Bottom("bottom", "下"),
}

@Composable
fun PieScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var edge by remember { mutableStateOf(PieEdge.Left) }
    var refreshTick by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(title = "Pie 菜单", onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                text = "边缘 Pie\n快速动作环",
                color = LocalEdgeXColors.current.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                lineHeight = 32.sp,
            )
            Text(
                text = "选择边缘后编辑双环动作槽位",
                color = LocalEdgeXColors.current.onSurfaceDim,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        EdgeXSegmentedControl(
            options = PieEdge.entries,
            selected = edge,
            label = { it.label },
            onSelected = { edge = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        PiePreview(edge = edge, refreshTick = refreshTick)
        EdgeXListGroup(modifier = Modifier.padding(16.dp)) {
            for (ring in 0 until AppConfig.PIE_RINGS) {
                for (slot in 0 until AppConfig.PIE_SLOTS_PER_RING) {
                    val title = "第 ${ring + 1} 环 · 槽位 ${slot + 1}"
                    val label = context.getConfigString(
                        AppConfig.pieSlotLabel(edge.id, ring, slot),
                        "无",
                    ) + refreshTick.let { "" }
                    EdgeXRow(
                        title = title,
                        subtitle = label,
                        icon = EdgeXIcons.Pie,
                        onClick = {
                            context.openActionPicker(
                                prefKey = AppConfig.pieSlot(edge.id, ring, slot),
                                title = "${edge.label}边缘 / $title",
                            )
                            refreshTick++
                        },
                    ) {
                        EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurfaceDim)
                    }
                    if (!(ring == AppConfig.PIE_RINGS - 1 && slot == AppConfig.PIE_SLOTS_PER_RING - 1)) {
                        EdgeXDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PiePreview(edge: PieEdge, refreshTick: Int) {
    val colors = LocalEdgeXColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(EdgeXRadius.xl),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("${edge.label}边缘", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            for (ring in 0 until AppConfig.PIE_RINGS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    for (slot in 0 until AppConfig.PIE_SLOTS_PER_RING) {
                        val configured = LocalContext.current.getConfigString(
                            AppConfig.pieSlot(edge.id, ring, slot),
                            "none",
                        ) != "none" || refreshTick < 0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (ring == 0) 52.dp else 44.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (configured) colors.accentSoft else colors.surface1)
                                .border(1.dp, colors.outline, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${slot + 1}",
                                color = if (configured) colors.onAccentSoft else colors.onSurfaceDim,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Context.keySubtitle(keyCode: Int, refreshTick: Int): String {
    val labels = keyTriggers.mapNotNull { trigger ->
        val label = getConfigString(AppConfig.keyActionLabel(keyCode, trigger.id))
        label.takeIf { it.isNotBlank() && it != "None" && it != "无" }?.let { "${trigger.label}: $it" }
    }
    return labels.joinToString(" · ").ifEmpty { "未配置" } + refreshTick.let { "" }
}

private fun Context.openActionPicker(prefKey: String, title: String) {
    startActivity(
        Intent(this, ActionSelectionActivity::class.java)
            .putExtra("title", title)
            .putExtra("pref_key", prefKey),
    )
}
