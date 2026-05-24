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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfigsSync
import com.fan.edgex.ui.ActionSelectionActivity
import com.fan.edgex.ui.compose.components.EdgeXBottomSheet
import com.fan.edgex.ui.compose.components.EdgeXChip
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIconBox
import com.fan.edgex.ui.compose.components.EdgeXIconButton
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.EdgeXSegmentedControl
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors

private enum class GestureFilter(val label: String) {
    Visual("可视化"),
    List("列表"),
}

private data class GestureZone(
    val id: String,
    val edge: String,
    val label: String,
    val short: String,
    val lowPriority: Boolean = false,
)

private data class GestureOption(
    val id: String,
    val label: String,
    val icon: ImageVector,
)

private data class GestureAction(
    val code: String,
    val label: String,
    val icon: ImageVector,
)

private data class GestureScreenState(
    val zones: Map<String, Map<String, String>>,
    val labels: Map<String, Map<String, String>>,
) {
    fun count(zoneId: String): Int =
        zones[zoneId].orEmpty().values.count { it.isNotBlank() && it != "none" }

    fun total(): Int =
        zones.keys.sumOf(::count)

    fun activeZones(): Int =
        zones.keys.count { count(it) > 0 }

    fun actionCode(zoneId: String, gestureId: String): String =
        zones[zoneId]?.get(gestureId).orEmpty().ifBlank { "none" }

    fun actionLabel(zoneId: String, gestureId: String): String =
        labels[zoneId]?.get(gestureId).orEmpty().ifBlank { "无" }
}

private val zones = listOf(
    GestureZone("left_top", "L", "左·上", "L↑"),
    GestureZone("left_mid", "L", "左·中", "L•"),
    GestureZone("left_bottom", "L", "左·下", "L↓"),
    GestureZone("left", "L", "左边缘", "L", lowPriority = true),
    GestureZone("right_top", "R", "右·上", "R↑"),
    GestureZone("right_mid", "R", "右·中", "R•"),
    GestureZone("right_bottom", "R", "右·下", "R↓"),
    GestureZone("right", "R", "右边缘", "R", lowPriority = true),
    GestureZone("top_left", "T", "上·左", "T←"),
    GestureZone("top_mid", "T", "上·中", "T•"),
    GestureZone("top_right", "T", "上·右", "T→"),
    GestureZone("top", "T", "上边缘", "T", lowPriority = true),
    GestureZone("bottom_left", "B", "下·左", "B←"),
    GestureZone("bottom_mid", "B", "下·中", "B•"),
    GestureZone("bottom_right", "B", "下·右", "B→"),
    GestureZone("bottom", "B", "下边缘", "B", lowPriority = true),
)

private val baseGestures = listOf(
    GestureOption("click", "单击", EdgeXIcons.Gesture),
    GestureOption("double_click", "双击", EdgeXIcons.Gesture),
    GestureOption("long_press", "长按", EdgeXIcons.Gesture),
)

private val directActions = listOf(
    GestureAction("none", "无", EdgeXIcons.Check),
    GestureAction("back", "返回", EdgeXIcons.Back),
    GestureAction("home", "主页", EdgeXIcons.Pie),
    GestureAction("recents", "最近应用", EdgeXIcons.Multi),
    GestureAction("lock_screen", "锁屏", EdgeXIcons.Keys),
    GestureAction("screenshot", "截屏", EdgeXIcons.Theme),
    GestureAction(AppConfig.PARTIAL_SCREENSHOT_ACTION, "局部截屏", EdgeXIcons.Theme),
    GestureAction("expand_notifications", "通知栏", EdgeXIcons.Sparkle),
    GestureAction("toggle_flashlight", "手电筒", EdgeXIcons.Sparkle),
    GestureAction("brightness_up", "亮度+", EdgeXIcons.Sparkle),
    GestureAction("brightness_down", "亮度-", EdgeXIcons.Sparkle),
    GestureAction("volume_up", "音量+", EdgeXIcons.Keys),
    GestureAction("volume_down", "音量-", EdgeXIcons.Keys),
    GestureAction("freezer_drawer", "冰箱抽屉", EdgeXIcons.Freeze),
    GestureAction("refreeze", "重新冻结", EdgeXIcons.Freeze),
    GestureAction("clear_background", "清后台", EdgeXIcons.Multi),
    GestureAction("kill_app", "结束应用", EdgeXIcons.Multi),
    GestureAction("prev_app", "上一应用", EdgeXIcons.Back),
    GestureAction("next_app", "下一应用", EdgeXIcons.ChevronRight),
    GestureAction("pie", "Pie 菜单", EdgeXIcons.Pie),
    GestureAction(AppConfig.CUSTOM_PANEL_ACTION, "自定义面板", EdgeXIcons.Multi),
    GestureAction(AppConfig.SIDE_BAR_LEFT_ACTION, "左侧边栏", EdgeXIcons.Back),
    GestureAction(AppConfig.SIDE_BAR_RIGHT_ACTION, "右侧边栏", EdgeXIcons.ChevronRight),
)

@Composable
fun GesturesScreen(
    onBack: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(context.readGestureScreenState()) }
    var filter by remember { mutableStateOf(GestureFilter.Visual) }
    var selectedZone by remember { mutableStateOf<GestureZone?>(null) }
    var pickingActionFor by remember { mutableStateOf<GestureOption?>(null) }

    fun refresh() {
        state = context.readGestureScreenState()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(
            title = "手势",
            onBack = onBack,
            trailing = {
                EdgeXIconButton(onClick = { filter = if (filter == GestureFilter.Visual) GestureFilter.List else GestureFilter.Visual }) {
                    EdgeXIcon(EdgeXIcons.Search, contentDescription = "切换视图", tint = LocalEdgeXColors.current.onSurface)
                }
            },
        )
        GestureHeader(state)
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EdgeXSegmentedControl(
                options = GestureFilter.entries,
                selected = filter,
                label = { it.label },
                onSelected = { filter = it },
                modifier = Modifier.weight(1f),
            )
            EdgeXChip(label = "导入", selected = false, onClick = { showToast("导入将在后续迁移") })
        }
        if (filter == GestureFilter.Visual) {
            GestureZoneCanvas(
                state = state,
                onZoneClick = { selectedZone = it },
                modifier = Modifier.padding(top = 14.dp),
            )
            GestureSectionLabel("全边缘 · 低优先级")
            FullEdgeGrid(
                state = state,
                onZoneClick = { selectedZone = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            AllZoneList(
                state = state,
                onZoneClick = { selectedZone = it },
                modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    ZoneSheet(
        zone = selectedZone,
        state = state,
        onDismiss = {
            selectedZone = null
            pickingActionFor = null
        },
        onPickAction = { pickingActionFor = it },
    )

    ActionSheet(
        zone = selectedZone,
        gesture = pickingActionFor,
        state = state,
        onDismiss = { pickingActionFor = null },
        onAction = { zone, gesture, action ->
            context.saveGestureAction(zone.id, gesture.id, action)
            refresh()
            pickingActionFor = null
            showToast(if (action.code == "none") "已移除" else "已设置: ${action.label}")
        },
        onOpenLegacy = { zone, gesture ->
            context.openLegacyActionPicker(zone, gesture)
            selectedZone = null
            pickingActionFor = null
        },
    )
}

@Composable
private fun GestureHeader(state: GestureScreenState) {
    val colors = LocalEdgeXColors.current
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = "点击屏幕\n边缘配置手势",
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 32.sp,
        )
        Text(
            text = "${state.total()} 个手势 · 12 子区域 + 4 全边缘",
            color = colors.onSurfaceDim,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun GestureZoneCanvas(
    state: GestureScreenState,
    onZoneClick: (GestureZone) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEdgeXColors.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 216.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(30.dp))
                .background(colors.surface2)
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            EdgeRow(zonesFor("T", full = false), state, onZoneClick)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                EdgeColumn(zonesFor("L", full = false), state, onZoneClick)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(26.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(26.dp)),
                )
                EdgeColumn(zonesFor("R", full = false), state, onZoneClick)
            }
            EdgeRow(zonesFor("B", full = false), state, onZoneClick)
        }
    }
}

@Composable
private fun EdgeRow(
    rowZones: List<GestureZone>,
    state: GestureScreenState,
    onZoneClick: (GestureZone) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        rowZones.forEach {
            ZonePill(
                zone = it,
                count = state.count(it.id),
                onClick = { onZoneClick(it) },
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp),
            )
        }
    }
}

@Composable
private fun EdgeColumn(
    columnZones: List<GestureZone>,
    state: GestureScreenState,
    onZoneClick: (GestureZone) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(32.dp),
    ) {
        columnZones.forEach {
            ZonePill(
                zone = it,
                count = state.count(it.id),
                onClick = { onZoneClick(it) },
                modifier = Modifier
                    .height(92.dp)
                    .width(24.dp),
            )
        }
    }
}

@Composable
private fun ZonePill(
    zone: GestureZone,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEdgeXColors.current
    val configured = count > 0
    Box(
        modifier = modifier
            .testTag("gesture_zone_${zone.id}")
            .clip(RoundedCornerShape(12.dp))
            .background(if (configured) colors.accentSoft else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (configured) Color.Transparent else colors.outlineStrong,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = zone.short,
            color = if (configured) colors.onAccentSoft else colors.onSurfaceDim,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun FullEdgeGrid(
    state: GestureScreenState,
    onZoneClick: (GestureZone) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        zones.filter { it.lowPriority }.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { zone ->
                    ZoneCard(zone, state.count(zone.id), onClick = { onZoneClick(zone) }, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AllZoneList(
    state: GestureScreenState,
    onZoneClick: (GestureZone) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        zones.forEach { zone ->
            ZoneCard(
                zone = zone,
                count = state.count(zone.id),
                onClick = { onZoneClick(zone) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GestureSectionLabel(label: String) {
    Text(
        text = label,
        color = LocalEdgeXColors.current.onSurfaceDim,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 10.dp),
    )
}

@Composable
private fun ZoneCard(
    zone: GestureZone,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEdgeXColors.current
    Card(
        modifier = modifier
            .testTag("gesture_zone_${zone.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(EdgeXRadius.md),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EdgeXIconBox(EdgeXIcons.Gesture, contentDescription = null, modifier = Modifier.size(36.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(zone.label, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(if (count == 0) "未配置" else "$count 个动作", color = colors.onSurfaceDim, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ZoneSheet(
    zone: GestureZone?,
    state: GestureScreenState,
    onDismiss: () -> Unit,
    onPickAction: (GestureOption) -> Unit,
) {
    EdgeXBottomSheet(
        open = zone != null,
        title = zone?.label.orEmpty(),
        onDismissRequest = onDismiss,
    ) {
        if (zone == null) return@EdgeXBottomSheet
        EdgeXListGroup {
            val gestures = gesturesFor(zone.edge)
            gestures.forEachIndexed { index, gesture ->
                EdgeXRow(
                    title = gesture.label,
                    subtitle = state.actionLabel(zone.id, gesture.id),
                    icon = gesture.icon,
                    onClick = { onPickAction(gesture) },
                ) {
                    EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurfaceDim)
                }
                if (index != gestures.lastIndex) EdgeXDivider()
            }
        }
    }
}

@Composable
private fun ActionSheet(
    zone: GestureZone?,
    gesture: GestureOption?,
    state: GestureScreenState,
    onDismiss: () -> Unit,
    onAction: (GestureZone, GestureOption, GestureAction) -> Unit,
    onOpenLegacy: (GestureZone, GestureOption) -> Unit,
) {
    EdgeXBottomSheet(
        open = zone != null && gesture != null,
        title = gesture?.label.orEmpty(),
        onDismissRequest = onDismiss,
    ) {
        if (zone == null || gesture == null) return@EdgeXBottomSheet
        val selected = state.actionCode(zone.id, gesture.id)
        EdgeXListGroup {
            directActions.forEachIndexed { index, action ->
                EdgeXRow(
                    title = action.label,
                    subtitle = action.code,
                    icon = action.icon,
                    modifier = Modifier.testTag("gesture_action_${action.code}"),
                    onClick = { onAction(zone, gesture, action) },
                ) {
                    if (selected == action.code) {
                        EdgeXIcon(EdgeXIcons.Check, contentDescription = null, tint = LocalEdgeXColors.current.accent)
                    }
                }
                if (index != directActions.lastIndex) EdgeXDivider()
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        EdgeXChip(
            label = "更多动作",
            selected = false,
            onClick = { onOpenLegacy(zone, gesture) },
        )
    }
}

private fun gesturesFor(edge: String): List<GestureOption> {
    val mainSwipe = when (edge) {
        "L" -> GestureOption("swipe_right", "右划", EdgeXIcons.ChevronRight)
        "R" -> GestureOption("swipe_left", "左划", EdgeXIcons.Back)
        "T" -> GestureOption("swipe_down", "下划", EdgeXIcons.ChevronRight)
        else -> GestureOption("swipe_up", "上划", EdgeXIcons.ChevronRight)
    }
    val perpendicular = when (edge) {
        "L", "R" -> listOf(
            GestureOption("swipe_up", "上划", EdgeXIcons.ChevronRight),
            GestureOption("swipe_down", "下划", EdgeXIcons.ChevronRight),
        )
        else -> listOf(
            GestureOption("swipe_left", "左划", EdgeXIcons.Back),
            GestureOption("swipe_right", "右划", EdgeXIcons.ChevronRight),
        )
    }
    return baseGestures + mainSwipe + perpendicular
}

private fun zonesFor(edge: String, full: Boolean): List<GestureZone> =
    zones.filter { it.edge == edge && (full || !it.lowPriority) }

private fun Context.readGestureScreenState(): GestureScreenState {
    val actionsByZone = zones.associate { zone ->
        zone.id to gesturesFor(zone.edge).associate { gesture ->
            gesture.id to getConfigString(AppConfig.gestureAction(zone.id, gesture.id), "none")
        }
    }
    val labelsByZone = zones.associate { zone ->
        zone.id to gesturesFor(zone.edge).associate { gesture ->
            gesture.id to getConfigString(AppConfig.gestureActionLabel(zone.id, gesture.id), "无")
        }
    }
    return GestureScreenState(actionsByZone, labelsByZone)
}

private fun Context.saveGestureAction(zoneId: String, gestureId: String, action: GestureAction) {
    val key = AppConfig.gestureAction(zoneId, gestureId)
    putConfigsSync(
        key to action.code,
        AppConfig.gestureActionLabel(zoneId, gestureId) to action.label,
    )
}

private fun Context.openLegacyActionPicker(zone: GestureZone, gesture: GestureOption) {
    val key = AppConfig.gestureAction(zone.id, gesture.id)
    startActivity(
        Intent(this, ActionSelectionActivity::class.java)
            .putExtra("title", "${zone.label} / ${gesture.label}")
            .putExtra("pref_key", key),
    )
}
