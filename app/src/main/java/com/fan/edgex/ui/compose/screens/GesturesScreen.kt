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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
import com.fan.edgex.config.GestureZoneGeometryCalculator
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
import com.fan.edgex.ui.compose.components.EdgeXSwitch
import com.fan.edgex.ui.compose.components.EdgeXSwitchRow
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.components.PhoneFrame
import com.fan.edgex.ui.compose.components.PreviewSectionHeader
import com.fan.edgex.ui.compose.components.SecondaryActionDispatcher
import com.fan.edgex.ui.compose.components.SecondaryType
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.roundToInt


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
    val enabled: Map<String, Boolean>,
    val splitPoints: Map<String, Pair<Int, Int>>,
    val thicknesses: Map<String, Int>,
) {
    fun count(zoneId: String): Int =
        zones[zoneId].orEmpty().values.count { it.isNotBlank() && it != "none" }

    fun total(): Int =
        zones.keys.sumOf(::count)

    fun activeZones(): Int =
        enabled.values.count { it }

    fun actionCode(zoneId: String, gestureId: String): String =
        zones[zoneId]?.get(gestureId).orEmpty().ifBlank { "none" }

    fun actionLabel(zoneId: String, gestureId: String): String =
        labels[zoneId]?.get(gestureId).orEmpty()

    fun zoneEnabled(zoneId: String): Boolean =
        enabled[zoneId] == true
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

    fun setZoneEnabled(zone: GestureZone, enabled: Boolean) {
        context.putConfig(AppConfig.zoneEnabled(zone.id), enabled)
        refresh()
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
                onZoneEnabledChange = ::setZoneEnabled,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            AllZoneList(
                state = state,
                onZoneClick = { selectedZone = it },
                onZoneEnabledChange = ::setZoneEnabled,
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
        onZoneEnabledChange = ::setZoneEnabled,
        onPickAction = { pickingActionFor = it },
        onRefresh = ::refresh,
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
    val configuration = LocalConfiguration.current
    val refWidth = minOf(configuration.screenWidthDp, configuration.screenHeightDp).toFloat()
    val refHeight = maxOf(configuration.screenWidthDp, configuration.screenHeightDp).toFloat()
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
        PhoneFrame(
            width = phoneWidth,
            height = phoneHeight,
            modifier = Modifier.pointerInput(scale, state) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val hitZone = hitTestEdgeZone(
                        offset = offset,
                        w = w,
                        h = h,
                        refWidth = refWidth,
                        refHeight = refHeight,
                        scale = scale,
                        state = currentState,
                        density = density
                    )
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
                    refWidth = refWidth,
                    refHeight = refHeight,
                    state = currentState,
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
    refWidth: Float,
    refHeight: Float,
    state: GestureScreenState,
    accentColor: Color,
    unconfiguredFill: Color,
    unconfiguredStroke: Color,
    configuredStroke: Color,
) {
    val cornerR = CornerRadius(2.dp.toPx())
    val strokeW = 1.dp.toPx()

    fun drawSegment(x: Float, y: Float, segW: Float, segH: Float, active: Boolean) {
        drawRoundRect(
            color = if (active) accentColor.copy(alpha = 0.55f) else unconfiguredFill,
            topLeft = Offset(x, y),
            size = Size(segW, segH),
            cornerRadius = cornerR,
        )
        drawRoundRect(
            color = if (active) configuredStroke else unconfiguredStroke,
            topLeft = Offset(x, y),
            size = Size(segW, segH),
            cornerRadius = cornerR,
            style = Stroke(width = strokeW),
        )
    }

    fun drawFullEdgeDashed(x: Float, y: Float, segW: Float, segH: Float) {
        drawRoundRect(
            color = accentColor.copy(alpha = 0.15f),
            topLeft = Offset(x, y),
            size = Size(segW, segH),
            cornerRadius = cornerR,
        )
        drawRoundRect(
            color = configuredStroke,
            topLeft = Offset(x, y),
            size = Size(segW, segH),
            cornerRadius = cornerR,
            style = Stroke(
                width = strokeW * 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            ),
        )
    }

    val leftRightCornerOffset = (12f / refHeight) * h
    val topBottomCornerOffset = (12f / refWidth) * w

    // Left edge
    val leftSplits = state.splitPoints["left"] ?: Pair(33, 66)
    val leftActiveH = h - 2 * leftRightCornerOffset
    val leftP1 = leftRightCornerOffset + leftActiveH * (leftSplits.first / 100f)
    val leftP2 = leftRightCornerOffset + leftActiveH * (leftSplits.second / 100f)

    val leftTopThick = ((state.thicknesses["left_top"] ?: 12).toFloat() / refWidth) * w
    drawSegment(0f, leftRightCornerOffset, leftTopThick, leftP1 - leftRightCornerOffset, state.zoneEnabled("left_top"))
    val leftMidThick = ((state.thicknesses["left_mid"] ?: 12).toFloat() / refWidth) * w
    drawSegment(0f, leftP1, leftMidThick, leftP2 - leftP1, state.zoneEnabled("left_mid"))
    val leftBottomThick = ((state.thicknesses["left_bottom"] ?: 12).toFloat() / refWidth) * w
    drawSegment(0f, leftP2, leftBottomThick, h - leftRightCornerOffset - leftP2, state.zoneEnabled("left_bottom"))

    if (state.zoneEnabled("left")) {
        val leftFullThick = ((state.thicknesses["left"] ?: 12).toFloat() / refWidth) * w
        drawFullEdgeDashed(0f, leftRightCornerOffset, leftFullThick, leftActiveH)
    }

    // Right edge
    val rightSplits = state.splitPoints["right"] ?: Pair(33, 66)
    val rightActiveH = h - 2 * leftRightCornerOffset
    val rightP1 = leftRightCornerOffset + rightActiveH * (rightSplits.first / 100f)
    val rightP2 = leftRightCornerOffset + rightActiveH * (rightSplits.second / 100f)

    val rightTopThick = ((state.thicknesses["right_top"] ?: 12).toFloat() / refWidth) * w
    drawSegment(w - rightTopThick, leftRightCornerOffset, rightTopThick, rightP1 - leftRightCornerOffset, state.zoneEnabled("right_top"))
    val rightMidThick = ((state.thicknesses["right_mid"] ?: 12).toFloat() / refWidth) * w
    drawSegment(w - rightMidThick, rightP1, rightMidThick, rightP2 - rightP1, state.zoneEnabled("right_mid"))
    val rightBottomThick = ((state.thicknesses["right_bottom"] ?: 12).toFloat() / refWidth) * w
    drawSegment(w - rightBottomThick, rightP2, rightBottomThick, h - leftRightCornerOffset - rightP2, state.zoneEnabled("right_bottom"))

    if (state.zoneEnabled("right")) {
        val rightFullThick = ((state.thicknesses["right"] ?: 12).toFloat() / refWidth) * w
        drawFullEdgeDashed(w - rightFullThick, leftRightCornerOffset, rightFullThick, rightActiveH)
    }

    // Top edge
    val topSplits = state.splitPoints["top"] ?: Pair(33, 66)
    val topActiveW = w - 2 * topBottomCornerOffset
    val topP1 = topBottomCornerOffset + topActiveW * (topSplits.first / 100f)
    val topP2 = topBottomCornerOffset + topActiveW * (topSplits.second / 100f)

    val topLeftThick = ((state.thicknesses["top_left"] ?: 12).toFloat() / refHeight) * h
    drawSegment(topBottomCornerOffset, 0f, topP1 - topBottomCornerOffset, topLeftThick, state.zoneEnabled("top_left"))
    val topMidThick = ((state.thicknesses["top_mid"] ?: 12).toFloat() / refHeight) * h
    drawSegment(topP1, 0f, topP2 - topP1, topMidThick, state.zoneEnabled("top_mid"))
    val topRightThick = ((state.thicknesses["top_right"] ?: 12).toFloat() / refHeight) * h
    drawSegment(topP2, 0f, w - topBottomCornerOffset - topP2, topRightThick, state.zoneEnabled("top_right"))

    if (state.zoneEnabled("top")) {
        val topFullThick = ((state.thicknesses["top"] ?: 12).toFloat() / refHeight) * h
        drawFullEdgeDashed(topBottomCornerOffset, 0f, topActiveW, topFullThick)
    }

    // Bottom edge
    val bottomSplits = state.splitPoints["bottom"] ?: Pair(33, 66)
    val bottomActiveW = w - 2 * topBottomCornerOffset
    val bottomP1 = topBottomCornerOffset + bottomActiveW * (bottomSplits.first / 100f)
    val bottomP2 = topBottomCornerOffset + bottomActiveW * (bottomSplits.second / 100f)

    val bottomLeftThick = ((state.thicknesses["bottom_left"] ?: 12).toFloat() / refHeight) * h
    drawSegment(topBottomCornerOffset, h - bottomLeftThick, bottomP1 - topBottomCornerOffset, bottomLeftThick, state.zoneEnabled("bottom_left"))
    val bottomMidThick = ((state.thicknesses["bottom_mid"] ?: 12).toFloat() / refHeight) * h
    drawSegment(bottomP1, h - bottomMidThick, bottomP2 - bottomP1, bottomMidThick, state.zoneEnabled("bottom_mid"))
    val bottomRightThick = ((state.thicknesses["bottom_right"] ?: 12).toFloat() / refHeight) * h
    drawSegment(bottomP2, h - bottomRightThick, w - topBottomCornerOffset - bottomP2, bottomRightThick, state.zoneEnabled("bottom_right"))

    if (state.zoneEnabled("bottom")) {
        val bottomFullThick = ((state.thicknesses["bottom"] ?: 12).toFloat() / refHeight) * h
        drawFullEdgeDashed(topBottomCornerOffset, h - bottomFullThick, bottomActiveW, bottomFullThick)
    }
}

private fun hitTestEdgeZone(
    offset: Offset,
    w: Float,
    h: Float,
    refWidth: Float,
    refHeight: Float,
    scale: Float,
    state: GestureScreenState,
    density: Density
): String? {
    val x = offset.x
    val y = offset.y

    val leftRightCornerOffset = (12f / refHeight) * h
    val topBottomCornerOffset = (12f / refWidth) * w

    // 1. LEFT edge
    val leftSplits = state.splitPoints["left"] ?: Pair(33, 66)
    val leftActiveH = h - 2 * leftRightCornerOffset
    val leftP1 = leftRightCornerOffset + leftActiveH * (leftSplits.first / 100f)
    val leftP2 = leftRightCornerOffset + leftActiveH * (leftSplits.second / 100f)

    val leftSeg = when {
        y < leftP1 -> 0
        y < leftP2 -> 1
        else -> 2
    }
    val leftZoneId = when (leftSeg) {
        0 -> "left_top"
        1 -> "left_mid"
        else -> "left_bottom"
    }
    val leftThickPx = ((state.thicknesses[leftZoneId] ?: 12).toFloat() / refWidth) * w
    val leftHitThreshold = maxOf(leftThickPx, with(density) { 16.dp.toPx() } * scale)
    if (x in 0f..leftHitThreshold && y in leftRightCornerOffset..(h - leftRightCornerOffset)) {
        return leftZoneId
    }

    // 2. RIGHT edge
    val rightSplits = state.splitPoints["right"] ?: Pair(33, 66)
    val rightActiveH = h - 2 * leftRightCornerOffset
    val rightP1 = leftRightCornerOffset + rightActiveH * (rightSplits.first / 100f)
    val rightP2 = leftRightCornerOffset + rightActiveH * (rightSplits.second / 100f)

    val rightSeg = when {
        y < rightP1 -> 0
        y < rightP2 -> 1
        else -> 2
    }
    val rightZoneId = when (rightSeg) {
        0 -> "right_top"
        1 -> "right_mid"
        else -> "right_bottom"
    }
    val rightThickPx = ((state.thicknesses[rightZoneId] ?: 12).toFloat() / refWidth) * w
    val rightHitThreshold = maxOf(rightThickPx, with(density) { 16.dp.toPx() } * scale)
    if (x in (w - rightHitThreshold)..w && y in leftRightCornerOffset..(h - leftRightCornerOffset)) {
        return rightZoneId
    }

    // 3. TOP edge
    val topSplits = state.splitPoints["top"] ?: Pair(33, 66)
    val topActiveW = w - 2 * topBottomCornerOffset
    val topP1 = topBottomCornerOffset + topActiveW * (topSplits.first / 100f)
    val topP2 = topBottomCornerOffset + topActiveW * (topSplits.second / 100f)

    val topSeg = when {
        x < topP1 -> 0
        x < topP2 -> 1
        else -> 2
    }
    val topZoneId = when (topSeg) {
        0 -> "top_left"
        1 -> "top_mid"
        else -> "top_right"
    }
    val topThickPx = ((state.thicknesses[topZoneId] ?: 12).toFloat() / refHeight) * h
    val topHitThreshold = maxOf(topThickPx, with(density) { 16.dp.toPx() } * scale)
    if (y in 0f..topHitThreshold && x in topBottomCornerOffset..(w - topBottomCornerOffset)) {
        return topZoneId
    }

    // 4. BOTTOM edge
    val bottomSplits = state.splitPoints["bottom"] ?: Pair(33, 66)
    val bottomActiveW = w - 2 * topBottomCornerOffset
    val bottomP1 = topBottomCornerOffset + bottomActiveW * (bottomSplits.first / 100f)
    val bottomP2 = topBottomCornerOffset + bottomActiveW * (bottomSplits.second / 100f)

    val bottomSeg = when {
        x < bottomP1 -> 0
        x < bottomP2 -> 1
        else -> 2
    }
    val bottomZoneId = when (bottomSeg) {
        0 -> "bottom_left"
        1 -> "bottom_mid"
        else -> "bottom_right"
    }
    val bottomThickPx = ((state.thicknesses[bottomZoneId] ?: 12).toFloat() / refHeight) * h
    val bottomHitThreshold = maxOf(bottomThickPx, with(density) { 16.dp.toPx() } * scale)
    if (y in (h - bottomHitThreshold)..h && x in topBottomCornerOffset..(w - topBottomCornerOffset)) {
        return bottomZoneId
    }

    return null
}

@Composable
private fun FullEdgeGrid(
    state: GestureScreenState,
    onZoneClick: (GestureZone) -> Unit,
    onZoneEnabledChange: (GestureZone, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        zones.filter { it.lowPriority }.forEach { zone ->
            ZoneCard(
                zone = zone,
                count = state.count(zone.id),
                enabled = state.zoneEnabled(zone.id),
                onClick = { onZoneClick(zone) },
                onEnabledChange = { onZoneEnabledChange(zone, it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AllZoneList(
    state: GestureScreenState,
    onZoneClick: (GestureZone) -> Unit,
    onZoneEnabledChange: (GestureZone, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        zones.forEach { zone ->
            ZoneCard(
                zone = zone,
                count = state.count(zone.id),
                enabled = state.zoneEnabled(zone.id),
                onClick = { onZoneClick(zone) },
                onEnabledChange = { onZoneEnabledChange(zone, it) },
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
    enabled: Boolean,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEdgeXColors.current
    val countLabel = if (count == 0) {
        stringResource(R.string.key_not_configured)
    } else {
        stringResource(R.string.compose_action_count, count)
    }
    val statusLabel = stringResource(if (enabled) R.string.compose_enabled else R.string.compose_disabled)
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
                Text("$statusLabel · $countLabel", color = colors.onSurfaceDim, fontSize = 11.sp)
            }
            EdgeXSwitch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }
    }
}

@Composable
private fun ZoneSheet(
    zone: GestureZone?,
    state: GestureScreenState,
    onDismiss: () -> Unit,
    onZoneEnabledChange: (GestureZone, Boolean) -> Unit,
    onPickAction: (GestureOption) -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalEdgeXColors.current

    EdgeXBottomSheet(
        open = zone != null,
        title = zone?.let { stringResource(it.labelRes) }.orEmpty(),
        onDismissRequest = onDismiss,
    ) {
        if (zone == null) return@EdgeXBottomSheet
        val fallbackEdge = AppConfig.fallbackEdgeZone(zone.id)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            // 启用/禁用开关
            EdgeXListGroup {
                EdgeXSwitchRow(
                    title = stringResource(R.string.compose_zone_enabled),
                    subtitle = stringResource(if (state.zoneEnabled(zone.id)) R.string.compose_enabled else R.string.compose_disabled),
                    checked = state.zoneEnabled(zone.id),
                    onCheckedChange = { onZoneEnabledChange(zone, it) },
                )
            }

            // 宽度/比例滑块
            Spacer(modifier = Modifier.height(12.dp))
            EdgeXListGroup {
                val thickness = state.thicknesses[zone.id] ?: 16
                val thicknessLabel = stringResource(R.string.compose_zone_thickness_width)
                ConfigSlider(
                    label = thicknessLabel,
                    valueText = "$thickness dp",
                    value = thickness,
                    range = 8..32,
                    onValue = { newVal ->
                        context.putConfig(AppConfig.zoneThicknessKey(zone.id), newVal.toString())
                        onRefresh()
                    }
                )

                if (fallbackEdge != null) {
                    val splits = state.splitPoints[fallbackEdge] ?: Pair(33, 66)
                    val isFirst = zone.id.endsWith("_top") || zone.id.endsWith("_left")
                    val isMid = zone.id.endsWith("_mid")
                    val isLast = zone.id.endsWith("_bottom") || zone.id.endsWith("_right")

                    val splitsLabel = stringResource(R.string.compose_zone_proportion)

                    if (isFirst) {
                        ConfigSlider(
                            label = splitsLabel,
                            valueText = "${splits.first}%",
                            value = splits.first,
                            range = 10..80,
                            onValue = { newVal ->
                                val adjusted = GestureZoneGeometryCalculator.adjustFirst(newVal, splits.second)
                                context.putConfigsSync(
                                    AppConfig.zoneSplitFirstPercentKey(fallbackEdge) to adjusted.first.toString(),
                                    AppConfig.zoneSplitSecondPercentKey(fallbackEdge) to adjusted.second.toString()
                                )
                                onRefresh()
                            }
                        )
                    } else if (isMid) {
                        val midH = splits.second - splits.first
                        ConfigSlider(
                            label = splitsLabel,
                            valueText = "$midH%",
                            value = midH,
                            range = 10..80,
                            onValue = { newVal ->
                                val adjusted = GestureZoneGeometryCalculator.adjustMiddleHeight(newVal, splits.first, splits.second)
                                context.putConfigsSync(
                                    AppConfig.zoneSplitFirstPercentKey(fallbackEdge) to adjusted.first.toString(),
                                    AppConfig.zoneSplitSecondPercentKey(fallbackEdge) to adjusted.second.toString()
                                )
                                onRefresh()
                            }
                        )
                    } else if (isLast) {
                        val lastH = 100 - splits.second
                        ConfigSlider(
                            label = splitsLabel,
                            valueText = "$lastH%",
                            value = lastH,
                            range = 10..80,
                            onValue = { newVal ->
                                val adjusted = GestureZoneGeometryCalculator.adjustSecond(splits.first, 100 - newVal)
                                context.putConfigsSync(
                                    AppConfig.zoneSplitFirstPercentKey(fallbackEdge) to adjusted.first.toString(),
                                    AppConfig.zoneSplitSecondPercentKey(fallbackEdge) to adjusted.second.toString()
                                )
                                onRefresh()
                            }
                        )
                    }
                }

                EdgeXDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            if (fallbackEdge != null) {
                                context.putConfigsSync(
                                    AppConfig.zoneSplitFirstPercentKey(fallbackEdge) to "33",
                                    AppConfig.zoneSplitSecondPercentKey(fallbackEdge) to "66",
                                    AppConfig.zoneThicknessKey(zone.id) to "16"
                                )
                            } else {
                                context.putConfig(AppConfig.zoneThicknessKey(zone.id), "16")
                            }
                            onRefresh()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.accent)
                    ) {
                        Text(stringResource(R.string.compose_zone_reset_default), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }

            // 手势动作列表
            Spacer(modifier = Modifier.height(12.dp))
            EdgeXListGroup {
                val gestures = gesturesFor(zone.edge)
                gestures.forEachIndexed { index, gesture ->
                    val actionCode = state.actionCode(zone.id, gesture.id)
                    GestureRow(
                        title = stringResource(gesture.labelRes),
                        subtitle = state.actionLabel(zone.id, gesture.id),
                        actionCode = actionCode,
                        onClick = { onPickAction(gesture) },
                    )
                    if (index != gestures.lastIndex) EdgeXDivider()
                }
            }

            // 底部安全距离
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConfigSlider(
    label: String,
    valueText: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    onValue: (Int) -> Unit,
) {
    val colors = LocalEdgeXColors.current
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = colors.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), fontSize = 14.sp)
            Text(valueText, color = colors.onSurfaceDim, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = {
                val stepped = ((it.roundToInt() - range.first) / step) * step + range.first
                onValue(stepped.coerceIn(range.first, range.last))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.surface2,
            ),
        )
    }
}

@Composable
private fun GestureRow(
    title: String,
    subtitle: String?,
    actionCode: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalEdgeXColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            val isApp = actionCode.startsWith("launch_app:")
            val isShortcut = actionCode.startsWith("app_shortcut:")

            var appDrawable by remember(actionCode) { mutableStateOf<android.graphics.drawable.Drawable?>(null) }

            if (isApp || isShortcut) {
                val pkg = if (isApp) {
                    actionCode.removePrefix("launch_app:")
                } else {
                    actionCode.removePrefix("app_shortcut:").substringBefore(":")
                }

                LaunchedEffect(pkg) {
                    appDrawable = runCatching {
                        context.packageManager.getApplicationIcon(pkg)
                    }.getOrNull()
                }
            }

            if (appDrawable != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(EdgeXRadius.sm))
                        .background(colors.accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                        },
                        update = { imageView ->
                            imageView.setImageDrawable(appDrawable)
                        },
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                val iconRes = com.fan.edgex.ui.ActionSelectionActivity.actionIconRes(actionCode)
                EdgeXIconBox(
                    imageVector = iconRes,
                    contentDescription = null,
                    background = colors.accentSoft,
                    tint = colors.onAccentSoft
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = colors.onSurfaceDim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceDim)
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
            val action = actionsByZone[zone.id]?.get(gesture.id).orEmpty()
            val rawLabel = getConfigString(AppConfig.gestureActionLabel(zone.id, gesture.id), getString(R.string.action_none))
            gesture.id to com.fan.edgex.ui.ActionSelectionActivity.resolveActionLabel(this, action, rawLabel)
        }
    }
    val enabledByZone = zones.associate { zone ->
        zone.id to getConfigBool(AppConfig.zoneEnabled(zone.id), default = zoneHasConfiguredAction(zone.id, actionsByZone))
    }
    val edges = listOf("left", "right", "top", "bottom")
    val splitPoints = edges.associateWith { edge ->
        val calculator = GestureZoneGeometryCalculator { key, def -> getConfigString(key, def) }
        calculator.getSplits(edge)
    }
    val thicknesses = zones.associate { zone ->
        val calculator = GestureZoneGeometryCalculator { key, def -> getConfigString(key, def) }
        zone.id to calculator.getThicknessDp(zone.id)
    }
    return GestureScreenState(actionsByZone, labelsByZone, enabledByZone, splitPoints, thicknesses)
}

private fun zoneHasConfiguredAction(zoneId: String, actionsByZone: Map<String, Map<String, String>>): Boolean =
    actionsByZone[zoneId].orEmpty().values.any(AppConfig::isActiveActionValue)
