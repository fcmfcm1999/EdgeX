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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
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
import com.fan.edgex.ui.compose.components.EdgeXSwitchRow
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors

private enum class GestureFilter(val labelRes: Int) {
    Visual(R.string.compose_view_visual),
    List(R.string.compose_view_list),
}

private data class GestureZone(
    val id: String,
    val edge: String,
    val labelRes: Int,
    val short: String,
    val lowPriority: Boolean = false,
)

private data class GestureOption(
    val id: String,
    val labelRes: Int,
    val icon: Int,
)

private data class GestureAction(
    val code: String,
    val labelRes: Int,
    val icon: Int,
    val needsSecondary: Boolean = false,
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
        labels[zoneId]?.get(gestureId).orEmpty()
}

private val zones = listOf(
    GestureZone("left_top", "L", R.string.zone_left_top, "L↑"),
    GestureZone("left_mid", "L", R.string.zone_left_mid, "L•"),
    GestureZone("left_bottom", "L", R.string.zone_left_bottom, "L↓"),
    GestureZone("left", "L", R.string.zone_left_full, "L", lowPriority = true),
    GestureZone("right_top", "R", R.string.zone_right_top, "R↑"),
    GestureZone("right_mid", "R", R.string.zone_right_mid, "R•"),
    GestureZone("right_bottom", "R", R.string.zone_right_bottom, "R↓"),
    GestureZone("right", "R", R.string.zone_right_full, "R", lowPriority = true),
    GestureZone("top_left", "T", R.string.zone_top_left, "T←"),
    GestureZone("top_mid", "T", R.string.zone_top_mid, "T•"),
    GestureZone("top_right", "T", R.string.zone_top_right, "T→"),
    GestureZone("top", "T", R.string.zone_top_full, "T", lowPriority = true),
    GestureZone("bottom_left", "B", R.string.zone_bottom_left, "B←"),
    GestureZone("bottom_mid", "B", R.string.zone_bottom_mid, "B•"),
    GestureZone("bottom_right", "B", R.string.zone_bottom_right, "B→"),
    GestureZone("bottom", "B", R.string.zone_bottom_full, "B", lowPriority = true),
)

private val baseGestures = listOf(
    GestureOption("click", R.string.gesture_click, EdgeXIcons.Gesture),
    GestureOption("double_click", R.string.gesture_double_click, EdgeXIcons.Gesture),
    GestureOption("long_press", R.string.gesture_long_press, EdgeXIcons.Gesture),
)

private val directActions = listOf(
    GestureAction("none", R.string.action_none, EdgeXIcons.Check),
    GestureAction("back", R.string.action_back, EdgeXIcons.Back),
    GestureAction("home", R.string.action_home, EdgeXIcons.Home),
    GestureAction("recents", R.string.action_recents, EdgeXIcons.Recents),
    GestureAction("expand_notifications", R.string.action_expand_notifications, EdgeXIcons.Notifications),
    GestureAction("lock_screen", R.string.action_lock_screen, EdgeXIcons.Lock),
    GestureAction("screenshot", R.string.action_screenshot, EdgeXIcons.Screenshot),
    GestureAction(AppConfig.PARTIAL_SCREENSHOT_ACTION, R.string.action_partial_screenshot, EdgeXIcons.PartialScreenshot),
    GestureAction("toggle_flashlight", R.string.action_toggle_flashlight, EdgeXIcons.Flashlight),
    GestureAction("brightness_up", R.string.action_brightness_up, EdgeXIcons.BrightnessUp),
    GestureAction("brightness_down", R.string.action_brightness_down, EdgeXIcons.BrightnessDown),
    GestureAction("volume_up", R.string.action_volume_up, EdgeXIcons.VolumeUp),
    GestureAction("volume_down", R.string.action_volume_down, EdgeXIcons.VolumeDown),
    GestureAction("freezer_drawer", R.string.action_freezer_drawer, EdgeXIcons.Freeze),
    GestureAction("refreeze", R.string.action_refreeze, EdgeXIcons.Refreeze),
    GestureAction("clear_background", R.string.action_clear_background, EdgeXIcons.ClearBackground),
    GestureAction("kill_app", R.string.action_kill_app, EdgeXIcons.KillApp),
    GestureAction("prev_app", R.string.action_prev_app, EdgeXIcons.PrevApp),
    GestureAction("next_app", R.string.action_next_app, EdgeXIcons.NextApp),
    GestureAction("clipboard", R.string.action_clipboard, EdgeXIcons.Clipboard),
    GestureAction("universal_copy", R.string.action_universal_copy, EdgeXIcons.UniversalCopy),
    GestureAction("toggle_wifi", R.string.action_toggle_wifi, EdgeXIcons.Wifi),
    GestureAction("toggle_mobile_data", R.string.action_toggle_mobile_data, EdgeXIcons.MobileData),
    GestureAction("game_mode", R.string.action_game_mode, EdgeXIcons.GameMode),
    GestureAction("pie", R.string.action_pie, EdgeXIcons.Pie),
    GestureAction(AppConfig.CUSTOM_PANEL_ACTION, R.string.action_custom_panel, EdgeXIcons.CustomPanel),
    GestureAction(AppConfig.SIDE_BAR_LEFT_ACTION, R.string.action_left_side_bar, EdgeXIcons.SideBarLeft),
    GestureAction(AppConfig.SIDE_BAR_RIGHT_ACTION, R.string.action_right_side_bar, EdgeXIcons.SideBarRight),
    GestureAction("multi_action", R.string.action_multi_action, EdgeXIcons.Multi, needsSecondary = true),
    GestureAction("condition", R.string.action_condition, EdgeXIcons.Condition, needsSecondary = true),
    GestureAction("shell_command", R.string.action_shell_command, EdgeXIcons.Terminal, needsSecondary = true),
    GestureAction("sub_gesture", R.string.action_sub_gesture, EdgeXIcons.SubGesture, needsSecondary = true),
    GestureAction("launch_app", R.string.action_launch_app, EdgeXIcons.LaunchApp, needsSecondary = true),
    GestureAction("app_shortcut", R.string.action_app_shortcut, EdgeXIcons.AppShortcut, needsSecondary = true),
    GestureAction("music_control", R.string.action_music_control, EdgeXIcons.Music, needsSecondary = true),
)

@Composable
fun GesturesScreen(
    onBack: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(context.readGestureScreenState()) }
    var gesturesEnabled by remember { mutableStateOf(context.getConfigBool(AppConfig.GESTURES_ENABLED)) }
    var filter by remember { mutableStateOf(GestureFilter.Visual) }
    var selectedZone by remember { mutableStateOf<GestureZone?>(null) }
    var pickingActionFor by remember { mutableStateOf<GestureOption?>(null) }
    val filterLabels = mapOf(
        GestureFilter.Visual to stringResource(GestureFilter.Visual.labelRes),
        GestureFilter.List to stringResource(GestureFilter.List.labelRes),
    )
    val importPendingToast = stringResource(R.string.compose_import_pending_toast)
    val removedToast = stringResource(R.string.compose_removed)
    val setActionToastTemplate = stringResource(R.string.compose_set_action_toast, "%s")

    fun refresh() {
        state = context.readGestureScreenState()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(
            title = stringResource(R.string.header_gestures),
            onBack = onBack,
            trailing = {
                EdgeXIconButton(onClick = { filter = if (filter == GestureFilter.Visual) GestureFilter.List else GestureFilter.Visual }) {
                    EdgeXIcon(EdgeXIcons.Search, contentDescription = stringResource(R.string.compose_view_visual), tint = LocalEdgeXColors.current.onSurface)
                }
            },
        )
        GestureHeader(state)
        EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            EdgeXSwitchRow(
                title = stringResource(R.string.compose_gestures_enabled),
                subtitle = stringResource(R.string.compose_gestures_enabled_desc),
                checked = gesturesEnabled,
                onCheckedChange = {
                    gesturesEnabled = it
                    context.putConfig(AppConfig.GESTURES_ENABLED, it)
                    showToast(context.getString(if (it) R.string.compose_gestures_enabled_toast else R.string.compose_gestures_disabled_toast))
                },
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EdgeXSegmentedControl(
                options = GestureFilter.entries,
                selected = filter,
                label = { filterLabels.getValue(it) },
                onSelected = { filter = it },
                modifier = Modifier.weight(1f),
            )
            EdgeXChip(label = stringResource(R.string.compose_import), selected = false, onClick = { showToast(importPendingToast) })
        }
        if (filter == GestureFilter.Visual) {
            GestureZoneCanvas(
                state = state,
                onZoneClick = { selectedZone = it },
                modifier = Modifier.padding(top = 14.dp),
            )
            GestureSectionLabel(stringResource(R.string.compose_full_edge_low_priority))
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
            val actionLabel = context.getString(action.labelRes)
            showToast(if (action.code == "none") removedToast else setActionToastTemplate.format(actionLabel))
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
            text = stringResource(R.string.compose_gestures_hero),
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 32.sp,
        )
        Text(
            text = stringResource(R.string.compose_gestures_subtitle, state.total()),
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
                Text(stringResource(zone.labelRes), color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(if (count == 0) stringResource(R.string.key_not_configured) else stringResource(R.string.compose_action_count, count), color = colors.onSurfaceDim, fontSize = 11.sp)
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
        title = zone?.let { stringResource(it.labelRes) }.orEmpty(),
        onDismissRequest = onDismiss,
    ) {
        if (zone == null) return@EdgeXBottomSheet
        EdgeXListGroup {
            val gestures = gesturesFor(zone.edge)
            gestures.forEachIndexed { index, gesture ->
                EdgeXRow(
                    title = stringResource(gesture.labelRes),
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
    val colors = LocalEdgeXColors.current
    var searchQuery by remember { mutableStateOf("") }
    EdgeXBottomSheet(
        open = zone != null && gesture != null,
        title = gesture?.let { stringResource(it.labelRes) }.orEmpty(),
        onDismissRequest = {
            searchQuery = ""
            onDismiss()
        },
    ) {
        if (zone == null || gesture == null) return@EdgeXBottomSheet
        val selected = state.actionCode(zone.id, gesture.id)
        val query = searchQuery.trim()
        val filtered = if (query.isBlank()) directActions else directActions.filter { action ->
            val label = stringResource(action.labelRes)
            label.contains(query, ignoreCase = true) || action.code.contains(query, ignoreCase = true)
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            placeholder = { Text(stringResource(R.string.compose_search_actions_hint), color = colors.onSurfaceDim) },
            singleLine = true,
            shape = RoundedCornerShape(EdgeXRadius.sm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.outline,
                cursorColor = colors.accent,
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            EdgeXListGroup {
                filtered.forEachIndexed { index, action ->
                    EdgeXRow(
                        title = stringResource(action.labelRes),
                        subtitle = action.code,
                        icon = action.icon,
                        modifier = Modifier.testTag("gesture_action_${action.code}"),
                        onClick = {
                            if (action.needsSecondary) {
                                onOpenLegacy(zone, gesture)
                            } else {
                                onAction(zone, gesture, action)
                            }
                        },
                    ) {
                        if (selected == action.code) {
                            EdgeXIcon(EdgeXIcons.Check, contentDescription = null, tint = colors.accent)
                        }
                    }
                    if (index != filtered.lastIndex) EdgeXDivider()
                }
            }
        }
    }
}

private fun gesturesFor(edge: String): List<GestureOption> {
    val mainSwipe = when (edge) {
        "L" -> GestureOption("swipe_right", R.string.gesture_swipe_right, EdgeXIcons.ChevronRight)
        "R" -> GestureOption("swipe_left", R.string.gesture_swipe_left, EdgeXIcons.Back)
        "T" -> GestureOption("swipe_down", R.string.gesture_swipe_down, EdgeXIcons.ChevronRight)
        else -> GestureOption("swipe_up", R.string.gesture_swipe_up, EdgeXIcons.ChevronRight)
    }
    val perpendicular = when (edge) {
        "L", "R" -> listOf(
            GestureOption("swipe_up", R.string.gesture_swipe_up, EdgeXIcons.ChevronRight),
            GestureOption("swipe_down", R.string.gesture_swipe_down, EdgeXIcons.ChevronRight),
        )
        else -> listOf(
            GestureOption("swipe_left", R.string.gesture_swipe_left, EdgeXIcons.Back),
            GestureOption("swipe_right", R.string.gesture_swipe_right, EdgeXIcons.ChevronRight),
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
            gesture.id to getConfigString(AppConfig.gestureActionLabel(zone.id, gesture.id), getString(R.string.action_none))
        }
    }
    return GestureScreenState(actionsByZone, labelsByZone)
}

private fun Context.saveGestureAction(zoneId: String, gestureId: String, action: GestureAction) {
    val key = AppConfig.gestureAction(zoneId, gestureId)
    putConfigsSync(
        key to action.code,
        AppConfig.gestureActionLabel(zoneId, gestureId) to getString(action.labelRes),
    )
}

private fun Context.openLegacyActionPicker(zone: GestureZone, gesture: GestureOption) {
    val key = AppConfig.gestureAction(zone.id, gesture.id)
    startActivity(
        Intent(this, ActionSelectionActivity::class.java)
            .putExtra("title", getString(R.string.compose_title_pair, getString(zone.labelRes), getString(gesture.labelRes)))
            .putExtra("pref_key", key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
