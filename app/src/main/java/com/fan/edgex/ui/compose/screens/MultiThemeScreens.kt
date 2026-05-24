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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.MultiAction
import com.fan.edgex.config.MultiActionStore
import com.fan.edgex.config.configPrefs
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import com.fan.edgex.config.putConfigsSync
import com.fan.edgex.ui.MultiActionEditActivity
import com.fan.edgex.ui.ThemeManager
import com.fan.edgex.ui.compose.components.EdgeXChip
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIconBox
import com.fan.edgex.ui.compose.components.EdgeXIconButton
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
                    text = "一个手势\n触发动作序列",
                    color = LocalEdgeXColors.current.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    lineHeight = 32.sp,
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
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
        if (items.isNotEmpty()) {
            FloatingActionButton(
                onClick = {
                    context.createMultiAction()
                    refreshTick++
                },
                containerColor = LocalEdgeXColors.current.accent,
                contentColor = LocalEdgeXColors.current.onAccent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(22.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EdgeXIcon(EdgeXIcons.Plus, contentDescription = null)
                    Text("新建", fontWeight = FontWeight.Bold)
                }
            }
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
private fun MultiActionCard(item: MultiAction, onEdit: () -> Unit) {
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
                EdgeXIcon(EdgeXIcons.Multi, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(22.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${item.steps.size} 个步骤", color = colors.onSurfaceDim, fontSize = 12.sp)
                }
                EdgeXIconButton(onClick = onEdit) {
                    EdgeXIcon(EdgeXIcons.Theme, contentDescription = "编辑", tint = colors.onSurface)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                item.steps.take(3).forEachIndexed { index, step ->
                    EdgeXChip(label = "${index + 1} · ${step.label}", selected = false, onClick = {})
                    if (index < item.steps.take(3).lastIndex) {
                        EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceDim, modifier = Modifier.size(14.dp))
                    }
                }
                if (item.steps.size > 3) {
                    EdgeXChip(label = "+${item.steps.size - 3}", selected = true, onClick = {})
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
        ThemeHero(accent)
        ThemeSectionLabel("预设主题")
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
        ThemeSectionLabel("深色模式")
        EdgeXListGroup(modifier = Modifier.padding(16.dp)) {
            EdgeXSwitchRow(
                title = "深色模式",
                subtitle = "跟随系统或手动切换",
                checked = dark,
                onCheckedChange = {
                    dark = it
                    context.putConfig(AppConfig.UI_DARK_MODE, it)
                    onThemeChanged()
                },
                icon = EdgeXIcons.Theme,
                modifier = Modifier.testTag("theme_dark_mode"),
            )
        }
        ThemeSectionLabel("自定义 RGB")
        RgbSliders(
            red = red,
            green = green,
            blue = blue,
            onRed = { red = it },
            onGreen = { green = it },
            onBlue = { blue = it },
            onApply = {
                ThemeManager.saveCustomColor(context, customColor.toArgb())
                context.putConfigsSync(AppConfig.UI_ACCENT to "custom")
                onThemeChanged()
                showToast("自定义颜色已保存")
            },
        )
        ThemePreview(color = customColor)
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun ThemeHero(accent: EdgeXAccent) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 20.dp),
        shape = RoundedCornerShape(EdgeXRadius.xl),
        colors = CardDefaults.cardColors(containerColor = accent.lightAccent),
        border = BorderStroke(0.dp, Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .height(144.dp)
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(620f, -20f),
                        radius = 280f,
                    ),
                )
                .padding(24.dp),
        ) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text("当前主题", color = Color.White.copy(alpha = 0.70f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(
                    accentName(accent),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "#%06X".format(accent.lightAccent.toArgb() and 0xFFFFFF),
                    color = Color.White.copy(alpha = 0.86f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun AccentSwatches(selected: EdgeXAccent, onSelected: (EdgeXAccent) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EdgeXAccent.entries.forEach { accent ->
                val isSelected = selected == accent
                Box(
                    modifier = Modifier
                        .testTag("theme_accent_${accent.id}")
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(accent.lightAccent)
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) LocalEdgeXColors.current.onSurface else Color.Transparent,
                            shape = RoundedCornerShape(18.dp),
                        )
                        .clickable { onSelected(accent) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            EdgeXAccent.entries.forEach { accent ->
                Text(
                    text = accentName(accent),
                    color = LocalEdgeXColors.current.onSurfaceDim,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ThemeSectionLabel(label: String) {
    Text(
        text = label,
        color = LocalEdgeXColors.current.onSurfaceDim,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 10.dp),
    )
}

private fun accentName(accent: EdgeXAccent): String =
    when (accent) {
        EdgeXAccent.Green -> "默认"
        EdgeXAccent.Blue -> "海洋"
        EdgeXAccent.Coral -> "炭火"
        EdgeXAccent.Violet -> "雪松"
        EdgeXAccent.Amber -> "琥珀"
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
    Box(
        modifier = Modifier
            .testTag("theme_custom_apply")
            .fillMaxWidth()
            .padding(16.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(LocalEdgeXColors.current.accent)
            .clickable(onClick = onApply),
        contentAlignment = Alignment.Center,
    ) {
        Text("应用自定义颜色", color = LocalEdgeXColors.current.onAccent, fontWeight = FontWeight.Bold)
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
