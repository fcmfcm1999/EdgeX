package com.fan.edgex.ui.compose.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.fan.edgex.ui.compose.components.ActionSelectionSheet
import com.fan.edgex.ui.compose.components.EdgeXBottomSheet

import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIconBox

import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.EdgeXSegmentedControl
import com.fan.edgex.ui.compose.components.EdgeXSwitchRow
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.components.PhoneFrame
import com.fan.edgex.ui.compose.components.PreviewSectionHeader
import com.fan.edgex.ui.compose.components.SecondaryActionDispatcher
import com.fan.edgex.ui.compose.components.SecondaryType
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

private const val GESTURE_PHONE_FRAME_WIDTH_DP = 176f
private const val GESTURE_PHONE_FRAME_HEIGHT_DP = 320f
private const val GESTURE_EDGE_STRIP_DP = 12f
private const val GESTURE_EDGE_HIT_DP = 16f

private data class GestureZoneGeometry(
    val stripThicknessPx: Float,
    val hitThicknessPx: Float,
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
    var secondarySheet by remember { mutableStateOf<SecondaryType?>(null) }
    val filterLabels = mapOf(
        GestureFilter.Visual to stringResource(GestureFilter.Visual.labelRes),
        GestureFilter.List to stringResource(GestureFilter.List.labelRes),
    )
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
        }
        if (filter == GestureFilter.Visual) {
            PreviewSectionHeader(
                title = stringResource(R.string.compose_panel_preview),
                subtitle = stringResource(R.string.compose_gesture_preview_subtitle),
            )
            GestureZoneCanvas(
                state = state,
                onZoneClick = { selectedZone = it },
                modifier = Modifier.padding(top = 4.dp),
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

    val activeZone = selectedZone
    val activeGesture = pickingActionFor
    if (activeZone != null && activeGesture != null && secondarySheet == null) {
        val prefKey = AppConfig.gestureAction(activeZone.id, activeGesture.id)
        val gestureTitle = stringResource(
            R.string.compose_title_pair,
            stringResource(activeZone.labelRes),
            stringResource(activeGesture.labelRes),
        )
        ActionSelectionSheet(
            open = true,
            title = gestureTitle,
            onDismiss = {
                pickingActionFor = null
            },
            excludedCodes = emptySet(),
            onSelect = { action ->
                if (action.needsSecondary) {
                    secondarySheet = SecondaryType.fromCode(action.code)
                } else {
                    context.putConfigsSync(
                        prefKey to action.code,
                        AppConfig.gestureActionLabel(activeZone.id, activeGesture.id) to context.getString(action.labelRes),
                    )
                    refresh()
                    pickingActionFor = null
                    val actionLabel = context.getString(action.labelRes)
                    showToast(if (action.code == "none") removedToast else setActionToastTemplate.format(actionLabel))
                }
            },
        )
    }

    if (activeZone != null && activeGesture != null && secondarySheet != null) {
        val prefKey = AppConfig.gestureAction(activeZone.id, activeGesture.id)
        val gestureTitle = stringResource(
            R.string.compose_title_pair,
            stringResource(activeZone.labelRes),
            stringResource(activeGesture.labelRes),
        )
        SecondaryActionDispatcher(
            type = secondarySheet,
            prefKey = prefKey,
            title = gestureTitle,
            onDismiss = { secondarySheet = null },
            onSaved = {
                secondarySheet = null
                pickingActionFor = null
                refresh()
            },
        )
    }
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
    val density = LocalDensity.current
    val accentColor = colors.accent
    val unconfiguredFill = Color.White.copy(alpha = 0.06f)
    val unconfiguredStroke = Color.White.copy(alpha = 0.15f)
    val configuredStroke = accentColor.copy(alpha = 0.80f)
    val currentState by rememberUpdatedState(state)
    val currentOnClick by rememberUpdatedState(onZoneClick)

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val phoneWidth = minOf(maxWidth, 320.dp)
        val phoneHeight = phoneWidth * (GESTURE_PHONE_FRAME_HEIGHT_DP / GESTURE_PHONE_FRAME_WIDTH_DP)
        val scale = phoneWidth.value / GESTURE_PHONE_FRAME_WIDTH_DP
        val geometry = remember(phoneWidth, density) {
            with(density) {
                GestureZoneGeometry(
                    stripThicknessPx = GESTURE_EDGE_STRIP_DP.dp.toPx() * scale,
                    hitThicknessPx = GESTURE_EDGE_HIT_DP.dp.toPx() * scale,
                )
            }
        }
        PhoneFrame(
            width = phoneWidth,
            height = phoneHeight,
            modifier = Modifier.pointerInput(geometry) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val hitZone = hitTestEdgeZone(offset, w, h, geometry)
                    if (hitZone != null) {
                        val zone = zones.firstOrNull { it.id == hitZone }
                        if (zone != null) currentOnClick(zone)
                    }
                }
            },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                drawEdgeStrips(
                    w = w, h = h,
                    stripThickness = geometry.stripThicknessPx,
                    state = state,
                    accentColor = accentColor,
                    unconfiguredFill = unconfiguredFill,
                    unconfiguredStroke = unconfiguredStroke,
                    configuredStroke = configuredStroke,
                )
            }
        }
    }
}

private fun DrawScope.drawEdgeStrips(
    w: Float, h: Float,
    stripThickness: Float,
    state: GestureScreenState,
    accentColor: Color,
    unconfiguredFill: Color,
    unconfiguredStroke: Color,
    configuredStroke: Color,
) {
    val leftZones = listOf("left_top", "left_mid", "left_bottom")
    val rightZones = listOf("right_top", "right_mid", "right_bottom")
    val topZones = listOf("top_left", "top_mid", "top_right")
    val bottomZones = listOf("bottom_left", "bottom_mid", "bottom_right")
    val cornerR = CornerRadius(2.dp.toPx())
    val strokeW = 1.dp.toPx()

    // Left/Right edge segments: avoid corners, range is stripThickness .. h - stripThickness
    val vSegmentH = (h - 2 * stripThickness) / 3f
    // Top/Bottom edge segments: avoid corners, range is stripThickness .. w - stripThickness
    val hSegmentW = (w - 2 * stripThickness) / 3f

    fun drawSegment(x: Float, y: Float, segW: Float, segH: Float, configured: Boolean) {
        drawRoundRect(
            color = if (configured) accentColor.copy(alpha = 0.55f) else unconfiguredFill,
            topLeft = Offset(x, y),
            size = Size(segW, segH),
            cornerRadius = cornerR,
        )
        drawRoundRect(
            color = if (configured) configuredStroke else unconfiguredStroke,
            topLeft = Offset(x, y),
            size = Size(segW, segH),
            cornerRadius = cornerR,
            style = Stroke(width = strokeW),
        )
    }

    // Left edge
    val leftFull = state.count("left") > 0
    for (i in 0..2) {
        val configured = leftFull || state.count(leftZones[i]) > 0
        drawSegment(0f, stripThickness + i * vSegmentH, stripThickness, vSegmentH, configured)
    }

    // Right edge
    val rightFull = state.count("right") > 0
    for (i in 0..2) {
        val configured = rightFull || state.count(rightZones[i]) > 0
        drawSegment(w - stripThickness, stripThickness + i * vSegmentH, stripThickness, vSegmentH, configured)
    }

    // Top edge
    val topFull = state.count("top") > 0
    for (i in 0..2) {
        val configured = topFull || state.count(topZones[i]) > 0
        drawSegment(stripThickness + i * hSegmentW, 0f, hSegmentW, stripThickness, configured)
    }

    // Bottom edge
    val bottomFull = state.count("bottom") > 0
    for (i in 0..2) {
        val configured = bottomFull || state.count(bottomZones[i]) > 0
        drawSegment(stripThickness + i * hSegmentW, h - stripThickness, hSegmentW, stripThickness, configured)
    }
}

private fun hitTestEdgeZone(offset: Offset, w: Float, h: Float, geometry: GestureZoneGeometry): String? {
    val stripThickness = geometry.stripThicknessPx
    val hitThickness = geometry.hitThicknessPx
    val x = offset.x
    val y = offset.y

    val vSegmentH = (h - 2 * stripThickness) / 3f
    val hSegmentW = (w - 2 * stripThickness) / 3f
    val suffixesHV = listOf("top", "mid", "bottom")
    val suffixesVH = listOf("left", "mid", "right")

    // Left edge
    if (x in 0f..hitThickness && y in 0f..h) {
        val seg = ((y - stripThickness) / vSegmentH).toInt().coerceIn(0, 2)
        return "left_${suffixesHV[seg]}"
    }
    // Right edge
    if (x in (w - hitThickness)..w && y in 0f..h) {
        val seg = ((y - stripThickness) / vSegmentH).toInt().coerceIn(0, 2)
        return "right_${suffixesHV[seg]}"
    }
    // Top edge
    if (y in 0f..hitThickness && x in 0f..w) {
        val seg = ((x - stripThickness) / hSegmentW).toInt().coerceIn(0, 2)
        return "top_${suffixesVH[seg]}"
    }
    // Bottom edge
    if (y in (h - hitThickness)..h && x in 0f..w) {
        val seg = ((x - stripThickness) / hSegmentW).toInt().coerceIn(0, 2)
        return "bottom_${suffixesVH[seg]}"
    }

    return null
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
