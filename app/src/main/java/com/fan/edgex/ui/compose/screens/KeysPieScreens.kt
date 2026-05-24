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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class KeyUiItem(
    val keyCode: Int,
    val title: String,
    val icon: Int,
)

private data class KeyTrigger(
    val id: String,
    val label: String,
)

private val keyItems = listOf(
    KeyUiItem(KeyEvent.KEYCODE_VOLUME_UP, "音量加", EdgeXIcons.Keys),
    KeyUiItem(KeyEvent.KEYCODE_VOLUME_DOWN, "音量减", EdgeXIcons.Keys),
    KeyUiItem(KeyEvent.KEYCODE_POWER, "电源键", EdgeXIcons.Keys),
    KeyUiItem(KeyEvent.KEYCODE_BACK, "返回键", EdgeXIcons.Back),
    KeyUiItem(KeyEvent.KEYCODE_HOME, "主页键", EdgeXIcons.Pie),
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
                text = "硬件按键\n变成你的快捷键",
                color = LocalEdgeXColors.current.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                lineHeight = 32.sp,
            )
        }
        EdgeXListGroup(modifier = Modifier.padding(16.dp)) {
            EdgeXSwitchRow(
                title = "启用按键动作",
                subtitle = "拦截硬件按键以触发动作",
                checked = masterEnabled,
                onCheckedChange = {
                    masterEnabled = it
                    context.putConfig(AppConfig.KEYS_ENABLED, it)
                    showToast(if (it) "按键已启用" else "按键已停用")
                },
            )
        }
        KeySectionLabel("按键映射")
        EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            keyItems.forEachIndexed { index, item ->
                EdgeXRow(
                    title = item.title,
                    subtitle = context.keySubtitle(
                        keyCode = item.keyCode,
                        refreshTick = refreshTick,
                        defaultConfigured = masterEnabled && index < 3,
                    ),
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
                text = "环形菜单\n触手可及",
                color = LocalEdgeXColors.current.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 31.sp,
            )
            Text(
                text = "从边缘划入唤起 · 2 个环 · 6 项/环",
                color = LocalEdgeXColors.current.onSurfaceDim,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        EdgeXSegmentedControl(
            options = PieEdge.entries,
            selected = edge,
            label = { "${it.label}边缘" },
            onSelected = { edge = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        PiePreview(edge = edge, refreshTick = refreshTick)
        KeySectionLabel("环 · 1 (内层)")
        PieInnerRingEditor(edge = edge, refreshTick = refreshTick, onEdited = { refreshTick++ })
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun PiePreview(edge: PieEdge, refreshTick: Int) {
    val colors = LocalEdgeXColors.current
    val innerIcons = listOf(EdgeXIcons.Back, EdgeXIcons.Pie, EdgeXIcons.Check, EdgeXIcons.Freeze, EdgeXIcons.Sparkle, EdgeXIcons.ChevronRight)
    val outerIcons = listOf(EdgeXIcons.Theme, EdgeXIcons.Search, EdgeXIcons.Keys, EdgeXIcons.Keys, EdgeXIcons.Theme, EdgeXIcons.Sparkle)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(top = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(280.dp)) {
            outerIcons.forEachIndexed { index, icon ->
                PieCircle(
                    icon = icon,
                    selected = false,
                    size = 56,
                    radius = 110f,
                    index = index,
                    count = outerIcons.size,
                )
            }
            innerIcons.forEachIndexed { index, icon ->
                PieCircle(
                    icon = icon,
                    selected = true,
                    size = 48,
                    radius = 56f,
                    index = index,
                    count = innerIcons.size,
                )
            }
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .offset(x = 100.dp, y = 100.dp)
                    .clip(CircleShape)
                    .background(colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text("中心", color = colors.onAccentSoft, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PieCircle(
    icon: Int,
    selected: Boolean,
    size: Int,
    radius: Float,
    index: Int,
    count: Int,
) {
    val colors = LocalEdgeXColors.current
    val angle = (index.toFloat() / count.toFloat()) * (PI * 2.0) - PI / 2.0
    val left = (cos(angle) * radius + 112f).toFloat()
    val top = (sin(angle) * radius + 112f).toFloat()
    Box(
        modifier = Modifier
            .size(size.dp)
            .offset(x = left.dp, y = top.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.surface)
            .border(1.dp, if (selected) Color.Transparent else colors.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        EdgeXIcon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.onAccent else colors.onSurface2,
            modifier = Modifier.size(if (selected) 18.dp else 20.dp),
        )
    }
}

@Composable
private fun PieInnerRingEditor(edge: PieEdge, refreshTick: Int, onEdited: () -> Unit) {
    val context = LocalContext.current
    val labels = (0 until AppConfig.PIE_SLOTS_PER_RING).map { slot ->
        context.getConfigString(AppConfig.pieSlotLabel(edge.id, 0, slot), "无")
            .takeIf { it.isNotBlank() && it != "无" }
            ?: listOf("返回", "主页", "最近应用", "锁屏", "通知栏", "手电筒").getOrElse(slot) { "槽位 ${slot + 1}" }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(EdgeXRadius.lg),
        colors = CardDefaults.cardColors(containerColor = LocalEdgeXColors.current.surface),
        border = BorderStroke(1.dp, LocalEdgeXColors.current.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.chunked(3).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { indexInRow, label ->
                        val slot = rowIndex * 3 + indexInRow
                        EdgeXChip(
                            label = label + refreshTick.let { "" },
                            selected = true,
                            onClick = {
                                context.openActionPicker(
                                    prefKey = AppConfig.pieSlot(edge.id, 0, slot),
                                    title = "${edge.label}边缘 / 环 1 · 槽位 ${slot + 1}",
                                )
                                onEdited()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeySectionLabel(label: String) {
    Text(
        text = label,
        color = LocalEdgeXColors.current.onSurfaceDim,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 10.dp),
    )
}

private fun Context.keySubtitle(keyCode: Int, refreshTick: Int, defaultConfigured: Boolean): String {
    val labels = keyTriggers.mapNotNull { trigger ->
        val label = getConfigString(AppConfig.keyActionLabel(keyCode, trigger.id))
        label.takeIf { it.isNotBlank() && it != "None" && it != "无" }?.let { "${trigger.label}: $it" }
    }
    return labels.joinToString(" · ").ifEmpty {
        if (defaultConfigured) "单击 · 双击 · 长按 已配置" else "未配置"
    } + refreshTick.let { "" }
}

private fun Context.openActionPicker(prefKey: String, title: String) {
    startActivity(
        Intent(this, ActionSelectionActivity::class.java)
            .putExtra("title", title)
            .putExtra("pref_key", prefKey),
    )
}
