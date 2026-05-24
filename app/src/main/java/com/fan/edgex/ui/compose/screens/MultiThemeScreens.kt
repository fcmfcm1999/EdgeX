package com.fan.edgex.ui.compose.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.MultiAction
import com.fan.edgex.config.MultiActionStore
import com.fan.edgex.config.broadcastFullConfigSnapshot
import com.fan.edgex.config.configPrefs
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import com.fan.edgex.config.requestHookActionExecution
import com.fan.edgex.ui.MultiActionEditActivity
import com.fan.edgex.ui.ThemeManager
import com.fan.edgex.ui.compose.components.EdgeXChip
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIconBox
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.EdgeXSwitchRow
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.theme.EdgeXAccent
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors

@Composable
fun MultiScreen(
    onBack: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    val items = remember(refreshTick) { MultiActionStore.getAll(context.configPrefs()) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            EdgeXTopBar(title = "组合动作", onBack = onBack)
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    text = "保存常用\n动作序列",
                    color = LocalEdgeXColors.current.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    lineHeight = 32.sp,
                )
                Text(
                    text = "${items.size} 个组合动作 · 可从手势、按键和 Pie 中复用",
                    color = LocalEdgeXColors.current.onSurfaceDim,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (items.isEmpty()) {
                EmptyMultiState(onCreate = {
                    context.createMultiAction()
                    refreshTick++
                })
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items.forEach { item ->
                        MultiActionCard(
                            item = item,
                            onEdit = {
                                context.openMultiActionEdit(item.id)
                                refreshTick++
                            },
                            onRun = {
                                context.requestHookActionExecution(MultiActionStore.actionCode(item.id))
                                showToast("已请求执行: ${item.name}")
                            },
                        )
                    }
                    Button(
                        onClick = {
                            context.createMultiAction()
                            refreshTick++
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LocalEdgeXColors.current.accent,
                            contentColor = LocalEdgeXColors.current.onAccent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("新建组合动作", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun EmptyMultiState(onCreate: () -> Unit) {
    val colors = LocalEdgeXColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(EdgeXRadius.xl),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EdgeXIconBox(EdgeXIcons.Multi, contentDescription = null)
            Text("还没有组合动作", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("创建后可以把多个系统动作串成一次触发。", color = colors.onSurfaceDim, fontSize = 13.sp)
            Button(
                onClick = onCreate,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            ) {
                Text("新建", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MultiActionCard(item: MultiAction, onEdit: () -> Unit, onRun: () -> Unit) {
    val colors = LocalEdgeXColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EdgeXRadius.lg),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EdgeXIconBox(EdgeXIcons.Multi, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${item.steps.size} 个步骤", color = colors.onSurfaceDim, fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                item.steps.take(3).forEach { step ->
                    EdgeXChip(label = step.label, selected = false, onClick = {}, modifier = Modifier.weight(1f))
                }
                if (item.steps.size > 3) {
                    EdgeXChip(label = "+${item.steps.size - 3}", selected = true, onClick = {})
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surface1, contentColor = colors.onSurface),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("编辑", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onRun,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("执行", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ThemeScreen(
    onBack: () -> Unit,
    onThemeChanged: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var accent by remember { mutableStateOf(EdgeXAccent.fromId(context.getConfigString(AppConfig.UI_ACCENT, EdgeXAccent.Green.id))) }
    var dark by remember { mutableStateOf(context.getConfigBool(AppConfig.UI_DARK_MODE)) }
    var red by remember { mutableIntStateOf((accent.lightAccent.toArgb() shr 16) and 0xFF) }
    var green by remember { mutableIntStateOf((accent.lightAccent.toArgb() shr 8) and 0xFF) }
    var blue by remember { mutableIntStateOf(accent.lightAccent.toArgb() and 0xFF) }
    val customColor = Color(red, green, blue)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(title = "主题", onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                text = "选择你的\n交互色彩",
                color = LocalEdgeXColors.current.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                lineHeight = 32.sp,
            )
            Text(
                text = "Accent 会立即应用到新 UI，旧页面同步使用近似自定义色",
                color = LocalEdgeXColors.current.onSurfaceDim,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        AccentSwatches(
            selected = accent,
            onSelected = {
                accent = it
                red = (it.lightAccent.toArgb() shr 16) and 0xFF
                green = (it.lightAccent.toArgb() shr 8) and 0xFF
                blue = it.lightAccent.toArgb() and 0xFF
                context.saveUiTheme(it, dark)
                onThemeChanged()
                showToast("主题已保存")
            },
        )
        EdgeXListGroup(modifier = Modifier.padding(16.dp)) {
            EdgeXSwitchRow(
                title = "深色模式",
                subtitle = if (dark) "使用深色暖中性色板" else "使用浅色暖中性色板",
                checked = dark,
                onCheckedChange = {
                    dark = it
                    context.putConfig(AppConfig.UI_DARK_MODE, it)
                    onThemeChanged()
                },
                icon = EdgeXIcons.Theme,
            )
        }
        RgbSliders(
            red = red,
            green = green,
            blue = blue,
            onRed = { red = it },
            onGreen = { green = it },
            onBlue = { blue = it },
            onApply = {
                context.putConfig(AppConfig.UI_ACCENT, "custom")
                ThemeManager.saveCustomColor(context, customColor.toArgb())
                onThemeChanged()
                showToast("自定义颜色已保存")
            },
        )
        ThemePreview(color = customColor)
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun AccentSwatches(selected: EdgeXAccent, onSelected: (EdgeXAccent) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EdgeXAccent.entries.forEach { accent ->
            val isSelected = selected == accent
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.lightAccent)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) Color.White else LocalEdgeXColors.current.outlineStrong,
                        shape = CircleShape,
                    )
                    .clickable { onSelected(accent) },
            )
        }
    }
}

@Composable
private fun RgbSliders(
    red: Int,
    green: Int,
    blue: Int,
    onRed: (Int) -> Unit,
    onGreen: (Int) -> Unit,
    onBlue: (Int) -> Unit,
    onApply: () -> Unit,
) {
    val color = Color(red, green, blue)
    EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
        RgbRow("R", red, onRed)
        EdgeXDivider()
        RgbRow("G", green, onGreen)
        EdgeXDivider()
        RgbRow("B", blue, onBlue)
        EdgeXDivider()
        EdgeXRow(
            title = "#%02X%02X%02X".format(red, green, blue),
            subtitle = "自定义 RGB",
            icon = EdgeXIcons.Theme,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color),
            )
        }
    }
    Button(
        onClick = onApply,
        colors = ButtonDefaults.buttonColors(
            containerColor = LocalEdgeXColors.current.accent,
            contentColor = LocalEdgeXColors.current.onAccent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text("应用自定义颜色", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RgbRow(label: String, value: Int, onValue: (Int) -> Unit) {
    val colors = LocalEdgeXColors.current
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = colors.onSurface, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(value.toString(), color = colors.onSurfaceDim, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.surface2,
            ),
        )
    }
}

@Composable
private fun ThemePreview(color: Color) {
    val colors = LocalEdgeXColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(EdgeXRadius.lg),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(EdgeXRadius.sm))
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                EdgeXIcon(EdgeXIcons.Gesture, contentDescription = null, tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("实时预览", color = colors.onSurface, fontWeight = FontWeight.Bold)
                Text("新的强调色将用于按钮、开关和高亮区域", color = colors.onSurfaceDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun Context.createMultiAction() {
    val id = MultiActionStore.generateId()
    startActivity(
        Intent(this, MultiActionEditActivity::class.java)
            .putExtra(MultiActionEditActivity.EXTRA_ID, id)
            .putExtra(MultiActionEditActivity.EXTRA_IS_NEW, true),
    )
}

private fun Context.openMultiActionEdit(id: String) {
    startActivity(
        Intent(this, MultiActionEditActivity::class.java)
            .putExtra(MultiActionEditActivity.EXTRA_ID, id),
    )
}

private fun Context.saveUiTheme(accent: EdgeXAccent, dark: Boolean) {
    putConfig(AppConfig.UI_ACCENT, accent.id)
    putConfig(AppConfig.UI_DARK_MODE, dark)
    ThemeManager.saveCustomColor(this, accent.lightAccent.toArgb())
}
