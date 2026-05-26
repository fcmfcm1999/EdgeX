package com.fan.edgex.ui.compose.screens

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.KeyEvent
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import com.fan.edgex.config.putConfigsSync
import com.fan.edgex.ui.ActionSelectionActivity
import com.fan.edgex.ui.ConditionActionActivity
import com.fan.edgex.ui.MultiActionsListActivity
import com.fan.edgex.ui.ShellCommandActivity
import com.fan.edgex.ui.ShortcutSelectionActivity
import com.fan.edgex.ui.SubGestureActivity
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
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private data class KeyUiItem(
    val keyCode: Int,
    val titleRes: Int,
    val icon: Int,
)

private data class KeyTrigger(
    val id: String,
    val labelRes: Int,
)

private val keyItems = listOf(
    KeyUiItem(KeyEvent.KEYCODE_VOLUME_UP, R.string.key_volume_up, EdgeXIcons.VolumeUp),
    KeyUiItem(KeyEvent.KEYCODE_VOLUME_DOWN, R.string.key_volume_down, EdgeXIcons.VolumeDown),
    KeyUiItem(KeyEvent.KEYCODE_POWER, R.string.key_power, EdgeXIcons.Power),
)

private val keyTriggers = listOf(
    KeyTrigger("click", R.string.gesture_click),
    KeyTrigger("double_click", R.string.gesture_double_click),
    KeyTrigger("long_press", R.string.gesture_long_press),
)

@Composable
fun KeysScreen(
    onBack: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keysEnabledToast = stringResource(R.string.compose_keys_enabled_toast)
    val keysDisabledToast = stringResource(R.string.compose_keys_disabled_toast)
    var masterEnabled by remember { mutableStateOf(context.getConfigBool(AppConfig.KEYS_ENABLED)) }
    var selectedKey by remember { mutableStateOf<KeyUiItem?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(title = stringResource(R.string.header_keys), onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.compose_keys_hero),
                color = LocalEdgeXColors.current.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                lineHeight = 32.sp,
            )
        }
        EdgeXListGroup(modifier = Modifier.padding(16.dp)) {
            EdgeXSwitchRow(
                title = stringResource(R.string.keys_global_switch),
                subtitle = stringResource(R.string.keys_global_switch_desc),
                checked = masterEnabled,
                onCheckedChange = {
                    masterEnabled = it
                    context.putConfig(AppConfig.KEYS_ENABLED, it)
                    showToast(if (it) keysEnabledToast else keysDisabledToast)
                },
            )
        }
        KeySectionLabel(stringResource(R.string.compose_key_mapping))
        EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            keyItems.forEachIndexed { index, item ->
                EdgeXRow(
                    title = stringResource(item.titleRes),
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
        onTriggerClick = { key, trigger, title ->
            context.openActionPicker(
                prefKey = AppConfig.keyAction(key.keyCode, trigger.id),
                title = title,
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
    onTriggerClick: (KeyUiItem, KeyTrigger, String) -> Unit,
) {
    val context = LocalContext.current
    EdgeXBottomSheet(open = key != null, title = key?.let { stringResource(it.titleRes) }.orEmpty(), onDismissRequest = onDismiss) {
        if (key == null) return@EdgeXBottomSheet
        EdgeXListGroup {
            keyTriggers.forEachIndexed { index, trigger ->
                val actionTitle = stringResource(
                    R.string.compose_title_pair,
                    stringResource(key.titleRes),
                    stringResource(trigger.labelRes),
                )
                EdgeXRow(
                    title = stringResource(trigger.labelRes),
                    subtitle = context.getConfigString(
                        AppConfig.keyActionLabel(key.keyCode, trigger.id),
                        stringResource(R.string.action_none),
                    ) + refreshTick.let { "" },
                    icon = EdgeXIcons.Gesture,
                    onClick = { onTriggerClick(key, trigger, actionTitle) },
                ) {
                    EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = LocalEdgeXColors.current.onSurfaceDim)
                }
                if (index != keyTriggers.lastIndex) EdgeXDivider()
            }
        }
    }
}

private enum class PieEdge(val id: String, val labelRes: Int) {
    Left("left", R.string.compose_edge_left_short),
    Right("right", R.string.compose_edge_right_short),
    Top("top", R.string.compose_edge_top_short),
    Bottom("bottom", R.string.compose_edge_bottom_short),
}

private data class PieSector(val ring: Int, val slot: Int)

private data class PieSlotUi(
    val sector: PieSector,
    val label: String,
    val action: String,
    val icon: Int?,
    val appIcon: Drawable?,
)

private data class PieGeometry(
    val edge: PieEdge,
    val anchor: Offset,
    val innerDeadR: Float,
    val ring0OuterR: Float,
    val ring1InnerR: Float,
    val outerR: Float,
)

private data class PieActionChoice(
    val code: String,
    val labelRes: Int,
    val icon: Int,
    val needsDetail: Boolean = false,
)

private data class PieAppItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

private val pieActionChoices = listOf(
    PieActionChoice("none", R.string.action_none, EdgeXIcons.Check),
    PieActionChoice("back", R.string.action_back, EdgeXIcons.Back),
    PieActionChoice("home", R.string.action_home, EdgeXIcons.Home),
    PieActionChoice("recents", R.string.action_recents, EdgeXIcons.Recents),
    PieActionChoice("expand_notifications", R.string.action_expand_notifications, EdgeXIcons.Notifications),
    PieActionChoice("lock_screen", R.string.action_lock_screen, EdgeXIcons.Lock),
    PieActionChoice("screenshot", R.string.action_screenshot, EdgeXIcons.Screenshot),
    PieActionChoice(AppConfig.PARTIAL_SCREENSHOT_ACTION, R.string.action_partial_screenshot, EdgeXIcons.PartialScreenshot),
    PieActionChoice("toggle_flashlight", R.string.action_toggle_flashlight, EdgeXIcons.Flashlight),
    PieActionChoice("brightness_up", R.string.action_brightness_up, EdgeXIcons.BrightnessUp),
    PieActionChoice("brightness_down", R.string.action_brightness_down, EdgeXIcons.BrightnessDown),
    PieActionChoice("volume_up", R.string.action_volume_up, EdgeXIcons.VolumeUp),
    PieActionChoice("volume_down", R.string.action_volume_down, EdgeXIcons.VolumeDown),
    PieActionChoice("freezer_drawer", R.string.action_freezer_drawer, EdgeXIcons.Freeze),
    PieActionChoice("refreeze", R.string.action_refreeze, EdgeXIcons.Refreeze),
    PieActionChoice("clear_background", R.string.action_clear_background, EdgeXIcons.ClearBackground),
    PieActionChoice("kill_app", R.string.action_kill_app, EdgeXIcons.KillApp),
    PieActionChoice("prev_app", R.string.action_prev_app, EdgeXIcons.PrevApp),
    PieActionChoice("next_app", R.string.action_next_app, EdgeXIcons.NextApp),
    PieActionChoice("clipboard", R.string.action_clipboard, EdgeXIcons.Clipboard),
    PieActionChoice("universal_copy", R.string.action_universal_copy, EdgeXIcons.UniversalCopy),
    PieActionChoice("toggle_wifi", R.string.action_toggle_wifi, EdgeXIcons.Wifi),
    PieActionChoice("toggle_mobile_data", R.string.action_toggle_mobile_data, EdgeXIcons.MobileData),
    PieActionChoice("game_mode", R.string.action_game_mode, EdgeXIcons.GameMode),
    PieActionChoice(AppConfig.CUSTOM_PANEL_ACTION, R.string.action_custom_panel, EdgeXIcons.CustomPanel),
    PieActionChoice(AppConfig.SIDE_BAR_LEFT_ACTION, R.string.action_left_side_bar, EdgeXIcons.SideBarLeft),
    PieActionChoice(AppConfig.SIDE_BAR_RIGHT_ACTION, R.string.action_right_side_bar, EdgeXIcons.SideBarRight),
    PieActionChoice("multi_action", R.string.action_multi_action, EdgeXIcons.Multi, needsDetail = true),
    PieActionChoice("condition", R.string.action_condition, EdgeXIcons.Condition, needsDetail = true),
    PieActionChoice("shell_command", R.string.action_shell_command, EdgeXIcons.Terminal, needsDetail = true),
    PieActionChoice("sub_gesture", R.string.action_sub_gesture, EdgeXIcons.SubGesture, needsDetail = true),
    PieActionChoice("launch_app", R.string.action_launch_app, EdgeXIcons.LaunchApp, needsDetail = true),
    PieActionChoice("app_shortcut", R.string.action_app_shortcut, EdgeXIcons.AppShortcut, needsDetail = true),
    PieActionChoice("music_control", R.string.action_music_control, EdgeXIcons.Music, needsDetail = true),
)

@Composable
fun PieScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalEdgeXColors.current
    var edge by remember { mutableStateOf(PieEdge.Left) }
    var refreshTick by remember { mutableStateOf(0) }
    var selectedSector by remember { mutableStateOf<PieSector?>(null) }
    var sizeScale by remember { mutableStateOf(context.getPieSizeScale()) }
    var followThemeColor by remember { mutableStateOf(context.getConfigString(AppConfig.PIE_COLOR).isBlank()) }
    var customPieColor by remember { mutableStateOf(context.getPieCustomColor(colors.accent)) }
    var pickingSector by remember { mutableStateOf<PieSector?>(null) }
    var appPickerSector by remember { mutableStateOf<PieSector?>(null) }
    var musicPickerSector by remember { mutableStateOf<PieSector?>(null) }
    val edgeLabels = mapOf(
        PieEdge.Left to stringResource(R.string.compose_edge_label, stringResource(PieEdge.Left.labelRes)),
        PieEdge.Right to stringResource(R.string.compose_edge_label, stringResource(PieEdge.Right.labelRes)),
        PieEdge.Top to stringResource(R.string.compose_edge_label, stringResource(PieEdge.Top.labelRes)),
        PieEdge.Bottom to stringResource(R.string.compose_edge_label, stringResource(PieEdge.Bottom.labelRes)),
    )
    val slots = remember(edge, refreshTick) { context.loadPieSlots(edge) }
    val previewColor = if (followThemeColor) colors.accent else customPieColor

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(title = stringResource(R.string.header_pie_settings), onBack = onBack)
        EdgeXSegmentedControl(
            options = PieEdge.entries,
            selected = edge,
            label = { edgeLabels.getValue(it) },
            onSelected = {
                edge = it
                selectedSector = null
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        PieModelEditor(
            edge = edge,
            slots = slots,
            selectedSector = selectedSector,
            sizeScale = sizeScale,
            pieColor = previewColor,
            onSlotClick = { sector ->
                selectedSector = sector
                pickingSector = sector
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        PieOptionsPanel(
            sizeScale = sizeScale,
            onSizeScaleChange = {
                sizeScale = it
                context.putConfigsSync(AppConfig.PIE_SIZE_SCALE to "%.2f".format(Locale.US, it))
            },
            followThemeColor = followThemeColor,
            pieColor = customPieColor,
            onFollowThemeColorChange = {
                followThemeColor = it
                if (it) {
                    context.putConfigsSync(AppConfig.PIE_COLOR to "")
                } else {
                    customPieColor = colors.accent
                    context.putConfigsSync(AppConfig.PIE_COLOR to colors.accent.toHexString())
                }
            },
            onPieColorChange = {
                customPieColor = it
                followThemeColor = false
                context.putConfigsSync(AppConfig.PIE_COLOR to it.toHexString())
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        Spacer(modifier = Modifier.height(28.dp))
    }

    val sheetSector = pickingSector
    val sheetTitle = sheetSector?.let { sector ->
        "${stringResource(edge.labelRes)} / ${stringResource(R.string.pie_ring_slot_label, sector.ring, sector.slot + 1)}"
    }.orEmpty()
    val sheetPrefKey = sheetSector?.let { AppConfig.pieSlot(edge.id, it.ring, it.slot) }.orEmpty()
    PieActionSheet(
        sector = sheetSector,
        title = sheetTitle,
        selectedAction = if (sheetPrefKey.isBlank()) "none" else context.getConfigString(sheetPrefKey, "none"),
        onDismiss = { pickingSector = null },
        onPick = { action, actionLabel ->
            val sector = sheetSector ?: return@PieActionSheet
            val prefKey = AppConfig.pieSlot(edge.id, sector.ring, sector.slot)
            when (action.code) {
                "launch_app" -> {
                    pickingSector = null
                    appPickerSector = sector
                }
                "music_control" -> {
                    pickingSector = null
                    musicPickerSector = sector
                }
                else -> {
                    if (action.needsDetail) {
                        context.startPieActionDetail(prefKey, sheetTitle, action)
                    } else {
                        context.savePieAction(prefKey, action.code, actionLabel)
                    }
                    pickingSector = null
                    refreshTick++
                }
            }
        },
    )
    PieAppPickerSheet(
        open = appPickerSector != null,
        onDismiss = { appPickerSector = null },
        onPick = { app ->
            val sector = appPickerSector ?: return@PieAppPickerSheet
            val prefKey = AppConfig.pieSlot(edge.id, sector.ring, sector.slot)
            context.putConfigsSync(
                prefKey to "launch_app:${app.packageName}",
                "${prefKey}_label" to app.label,
                "${prefKey}_title" to app.label,
            )
            appPickerSector = null
            refreshTick++
        },
    )
    PieMusicPickerSheet(
        open = musicPickerSector != null,
        onDismiss = { musicPickerSector = null },
        onPick = { code, label ->
            val sector = musicPickerSector ?: return@PieMusicPickerSheet
            val prefKey = AppConfig.pieSlot(edge.id, sector.ring, sector.slot)
            context.savePieAction(prefKey, "music_control:$code", label)
            musicPickerSector = null
            refreshTick++
        },
    )
}

@Composable
private fun PieModelEditor(
    edge: PieEdge,
    slots: List<PieSlotUi>,
    selectedSector: PieSector?,
    sizeScale: Float,
    pieColor: Color,
    onSlotClick: (PieSector) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEdgeXColors.current
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val phoneWidth = minOf(maxWidth, 320.dp)
        val phoneHeight = phoneWidth * (PIE_PHONE_FRAME_HEIGHT_DP / PIE_PHONE_FRAME_WIDTH_DP)
        val scale = phoneWidth / PIE_PHONE_FRAME_WIDTH_DP.dp
        val geometry = with(density) {
            pieGeometry(
                edge = edge,
                width = phoneWidth.toPx(),
                height = phoneHeight.toPx(),
                sizeScale = sizeScale,
                sideInset = (18.dp * scale).toPx(),
                edgePadding = (24.dp * scale).toPx(),
            )
        }

        PhoneFrame(width = phoneWidth, height = phoneHeight) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(edge, slots, geometry) {
                        detectTapGestures { tapOffset ->
                            geometry.hitTest(tapOffset)?.let(onSlotClick)
                        }
                    },
            ) {
                drawPieModel(geometry, slots, selectedSector, pieColor, Color(0xFF303644), colors.outline)
            }

            slots.forEach { slot ->
                if (!slot.isConfigured) return@forEach
                val center = geometry.sectorCenter(slot.sector)
                val isSelected = selectedSector != null && slot.sector == selectedSector
                val iconBoxSize = with(density) {
                    val (innerR, outerR) = geometry.ringRadii(slot.sector.ring)
                    val ringThicknessDp = (outerR - innerR).toDp()
                    val baseSize = (ringThicknessDp * 0.58f).coerceIn(20.dp * scale, 30.dp * scale)
                    if (isSelected) baseSize * 1.12f else baseSize
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(iconBoxSize)
                        .offset {
                            val halfSize = with(density) { (iconBoxSize / 2f).toPx() }
                            IntOffset(
                                x = (center.x - halfSize).roundToInt(),
                                y = (center.y - halfSize).roundToInt(),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    PieSlotIcon(slot = slot, tint = Color.White, iconSize = iconBoxSize)
                }
            }
        }
    }
}

private fun Context.loadPieSlots(edge: PieEdge): List<PieSlotUi> {
    val none = getString(R.string.action_none)
    return (0 until AppConfig.PIE_RINGS).flatMap { ringIndex ->
        val ring = ringIndex + 1
        (0 until AppConfig.PIE_SLOTS_PER_RING).map { slot ->
            val action = getConfigString(AppConfig.pieSlot(edge.id, ring, slot), "none")
            val label = getConfigString(AppConfig.pieSlotLabel(edge.id, ring, slot), none)
                .takeIf { it.isNotBlank() }
                ?: none
            val appIcon = loadLaunchAppIcon(action)
            PieSlotUi(
                sector = PieSector(ring, slot),
                label = label,
                action = action,
                icon = if (AppConfig.isActiveActionValue(action) && appIcon == null) {
                    ActionSelectionActivity.actionIconRes(action)
                } else {
                    null
                },
                appIcon = appIcon,
            )
        }
    }
}

private val PieSlotUi.isConfigured: Boolean
    get() = AppConfig.isActiveActionValue(action)

private fun Context.loadLaunchAppIcon(action: String): Drawable? {
    if (!action.startsWith("launch_app:")) return null
    val packageName = action.removePrefix("launch_app:")
    return runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
}

@Composable
private fun PieSlotIcon(slot: PieSlotUi, tint: Color, iconSize: Dp) {
    if (slot.appIcon != null) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
            },
            update = { imageView ->
                val drawable = slot.appIcon.constantState?.newDrawable()?.mutate() ?: slot.appIcon
                imageView.setImageDrawable(drawable)
            },
            modifier = Modifier.size(iconSize),
        )
    } else {
        slot.icon?.let { icon ->
            EdgeXIcon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun PieActionSheet(
    sector: PieSector?,
    title: String,
    selectedAction: String,
    onDismiss: () -> Unit,
    onPick: (PieActionChoice, String) -> Unit,
) {
    val colors = LocalEdgeXColors.current
    var searchQuery by remember { mutableStateOf("") }
    EdgeXBottomSheet(
        open = sector != null,
        title = title,
        onDismissRequest = {
            searchQuery = ""
            onDismiss()
        },
    ) {
        if (sector == null) return@EdgeXBottomSheet
        val labeledActions = pieActionChoices.map { action -> action to stringResource(action.labelRes) }
        val query = searchQuery.trim()
        val filtered = if (query.isBlank()) {
            labeledActions
        } else {
            labeledActions.filter { (action, label) ->
                label.contains(query, ignoreCase = true) || action.code.contains(query, ignoreCase = true)
            }
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
                filtered.forEachIndexed { index, (action, label) ->
                    EdgeXRow(
                        title = label,
                        subtitle = action.code,
                        icon = action.icon,
                        onClick = { onPick(action, label) },
                    ) {
                        val selected = selectedAction == action.code ||
                            (action.code == "launch_app" && selectedAction.startsWith("launch_app:")) ||
                            (action.code == "music_control" && selectedAction.startsWith("music_control:")) ||
                            (action.code == "multi_action" && selectedAction.startsWith("multi_action:")) ||
                            (action.code == "condition" && selectedAction.startsWith("condition:"))
                        if (selected) {
                            EdgeXIcon(EdgeXIcons.Check, contentDescription = null, tint = colors.accent)
                        }
                    }
                    if (index != filtered.lastIndex) EdgeXDivider()
                }
            }
        }
    }
}

@Composable
private fun PieAppPickerSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    onPick: (PieAppItem) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalEdgeXColors.current
    var apps by remember { mutableStateOf(emptyList<PieAppItem>()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(open) {
        if (open && apps.isEmpty()) {
            apps = withContext(Dispatchers.IO) { context.loadPieLaunchableApps() }
        }
        if (!open) query = ""
    }
    EdgeXBottomSheet(open = open, title = stringResource(R.string.action_launch_app), onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            placeholder = { Text(stringResource(R.string.hint_search_apps), color = colors.onSurfaceDim) },
            singleLine = true,
            shape = RoundedCornerShape(EdgeXRadius.sm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.outline,
                cursorColor = colors.accent,
            ),
        )
        val filtered = remember(apps, query) {
            val q = query.trim()
            if (q.isBlank()) {
                apps
            } else {
                apps.filter {
                    it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            EdgeXListGroup {
                filtered.forEachIndexed { index, app ->
                    PieAppRow(app = app, onClick = { onPick(app) })
                    if (index != filtered.lastIndex) EdgeXDivider()
                }
            }
        }
    }
}

@Composable
private fun PieAppRow(app: PieAppItem, onClick: () -> Unit) {
    val colors = LocalEdgeXColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (app.icon != null) {
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                        }
                    },
                    update = { imageView ->
                        val drawable = app.icon.constantState?.newDrawable()?.mutate() ?: app.icon
                        imageView.setImageDrawable(drawable)
                    },
                    modifier = Modifier.size(30.dp),
                )
            } else {
                EdgeXIconBox(EdgeXIcons.LaunchApp, contentDescription = null)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                color = colors.onSurfaceDim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PieMusicPickerSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    onPick: (String, String) -> Unit,
) {
    EdgeXBottomSheet(open = open, title = stringResource(R.string.header_music_control), onDismissRequest = onDismiss) {
        val options = listOf(
            Triple("play_pause", R.string.action_music_play_pause, R.drawable.ic_music_play_pause),
            Triple("stop", R.string.action_music_stop, R.drawable.ic_music_stop),
            Triple("previous", R.string.action_music_previous, R.drawable.ic_music_previous),
            Triple("next", R.string.action_music_next, R.drawable.ic_music_next),
        )
        EdgeXListGroup {
            options.forEachIndexed { index, option ->
                val label = stringResource(option.second)
                val actionLabel = stringResource(R.string.label_music_prefix, label)
                EdgeXRow(title = label, icon = option.third, onClick = { onPick(option.first, actionLabel) })
                if (index != options.lastIndex) EdgeXDivider()
            }
        }
    }
}

private fun Context.loadPieLaunchableApps(): List<PieAppItem> {
    val pm = packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { info ->
            PieAppItem(
                packageName = info.activityInfo.packageName,
                label = info.loadLabel(pm).toString(),
                icon = runCatching { info.loadIcon(pm) }.getOrNull(),
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}

private fun Context.savePieAction(prefKey: String, action: String, label: String) {
    putConfigsSync(
        prefKey to action,
        "${prefKey}_label" to label,
        "${prefKey}_title" to "",
    )
}

private fun Context.startPieActionDetail(prefKey: String, title: String, action: PieActionChoice) {
    val intent = when (action.code) {
        "app_shortcut" -> Intent(this, ShortcutSelectionActivity::class.java)
            .putExtra("pref_key", prefKey)
        "shell_command" -> Intent(this, ShellCommandActivity::class.java)
            .putExtra("pref_key", prefKey)
        "sub_gesture" -> {
            putConfigsSync(
                prefKey to "sub_gesture",
                "${prefKey}_label" to getString(R.string.action_sub_gesture),
            )
            Intent(this, SubGestureActivity::class.java)
                .putExtra("pref_key", prefKey)
                .putExtra("title", title)
        }
        "multi_action" -> Intent(this, MultiActionsListActivity::class.java)
            .putExtra(MultiActionsListActivity.EXTRA_MODE, MultiActionsListActivity.MODE_PICK)
            .putExtra(MultiActionsListActivity.EXTRA_PREF_KEY, prefKey)
            .putExtra(MultiActionsListActivity.EXTRA_TITLE, title)
        "condition" -> Intent(this, ConditionActionActivity::class.java)
            .putExtra("pref_key", prefKey)
            .putExtra("title", title)
        else -> return
    }
    startActivity(intent)
}

@Composable
private fun PieOptionsPanel(
    sizeScale: Float,
    onSizeScaleChange: (Float) -> Unit,
    followThemeColor: Boolean,
    pieColor: Color,
    onFollowThemeColorChange: (Boolean) -> Unit,
    onPieColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalEdgeXColors.current
    EdgeXListGroup(modifier = modifier) {
        PieSizeRow(sizeScale = sizeScale, onSizeScaleChange = onSizeScaleChange)
        EdgeXDivider()
        EdgeXRow(
            title = stringResource(R.string.compose_pie_color_follow_theme),
            subtitle = if (followThemeColor) colors.accent.toHexString() else pieColor.toHexString(),
            icon = EdgeXIcons.Theme,
            onClick = { onFollowThemeColorChange(!followThemeColor) },
        ) {
            EdgeXSwitch(checked = followThemeColor, onCheckedChange = onFollowThemeColorChange)
        }
        if (!followThemeColor) {
            EdgeXDivider()
            EdgeXRow(
                title = pieColor.toHexString(),
                subtitle = stringResource(R.string.compose_pie_color_custom),
                icon = EdgeXIcons.Pie,
                onClick = {
                    ColorPickerDialog.show(
                        context = context,
                        title = context.getString(R.string.compose_pie_color_custom),
                        configKey = AppConfig.PIE_COLOR,
                        defaultColor = "%08X".format(colors.accent.toArgb().toLong() and 0xFFFFFFFFL),
                    ) { picked -> onPieColorChange(Color(picked)) }
                },
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(pieColor),
                )
            }
        }
    }
}

@Composable
private fun PieSizeRow(sizeScale: Float, onSizeScaleChange: (Float) -> Unit) {
    val colors = LocalEdgeXColors.current
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.compose_pie_size),
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(sizeScale * 100).roundToInt()}%",
                color = colors.onSurfaceDim,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
        }
        Slider(
            value = sizeScale,
            onValueChange = { value ->
                onSizeScaleChange((value * 100).roundToInt() / 100f)
            },
            valueRange = 0.8f..1.2f,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.surface2,
            ),
        )
    }
}

private fun Context.getPieSizeScale(): Float =
    getConfigString(AppConfig.PIE_SIZE_SCALE)
        .toFloatOrNull()
        ?.coerceIn(0.8f, 1.2f)
        ?: AppConfig.PIE_SIZE_SCALE_DEFAULT

private fun Context.getPieCustomColor(themeColor: Color): Color =
    getConfigString(AppConfig.PIE_COLOR)
        .takeIf { it.isNotBlank() }
        ?.let { hex ->
            runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
        }
        ?: themeColor

private fun Color.toHexString(): String =
    "#%06X".format(Locale.US, toArgb() and 0xFFFFFF)

private val Color.redByte: Int get() = (red * 255).roundToInt().coerceIn(0, 255)
private val Color.greenByte: Int get() = (green * 255).roundToInt().coerceIn(0, 255)
private val Color.blueByte: Int get() = (blue * 255).roundToInt().coerceIn(0, 255)

private fun pieGeometry(
    edge: PieEdge,
    width: Float,
    height: Float,
    sizeScale: Float,
    sideInset: Float,
    edgePadding: Float,
): PieGeometry {
    val maxOuterR = when (edge) {
        PieEdge.Left, PieEdge.Right -> min(width - sideInset - edgePadding, (height - edgePadding) / 1.98f)
        PieEdge.Top, PieEdge.Bottom -> min((width - edgePadding) / 1.98f, height - sideInset - edgePadding)
    }.coerceAtLeast(1f)
    val outerR = maxOuterR / 1.2f * sizeScale.coerceIn(0.8f, 1.2f)
    val anchor = when (edge) {
        PieEdge.Left -> Offset(sideInset, height / 2f)
        PieEdge.Right -> Offset(width - sideInset, height / 2f)
        PieEdge.Top -> Offset(width / 2f, sideInset)
        PieEdge.Bottom -> Offset(width / 2f, height - sideInset)
    }
    return PieGeometry(
        edge = edge,
        anchor = anchor,
        innerDeadR = outerR * 90f / 250f,
        ring0OuterR = outerR * 154f / 250f,
        ring1InnerR = outerR * 164f / 250f,
        outerR = outerR,
    )
}

private fun DrawScope.drawPieModel(
    geometry: PieGeometry,
    slots: List<PieSlotUi>,
    selectedSector: PieSector?,
    accent: Color,
    surface: Color,
    outline: Color,
) {
    slots.forEach { slot ->
        val isSelected = selectedSector != null && slot.sector == selectedSector
        val path = geometry.sectorPath(slot.sector)
        val fillColor = when {
            !slot.isConfigured && isSelected -> accent.copy(alpha = 0.46f)
            !slot.isConfigured -> accent.copy(alpha = 0.18f)
            isSelected -> accent.copy(alpha = 0.98f)
            slot.sector.ring == 1 -> accent.copy(alpha = 0.80f)
            else -> accent.copy(alpha = 0.64f)
        }
        drawPath(path, surface.copy(alpha = 0.22f))
        drawPath(path, fillColor)
        drawPath(
            path = path,
            color = if (isSelected) Color.White.copy(alpha = 0.88f) else outline.copy(alpha = 0.72f),
            style = Stroke(width = if (isSelected) 2.4.dp.toPx() else 1.4.dp.toPx()),
        )
    }
}

private fun PieGeometry.sectorPath(sector: PieSector): Path {
    val (innerR, outerR) = ringRadii(sector.ring)
    val startAngle = sectorStartAngle(sector.slot) + PIE_SECTOR_GAP_DEG / 2f
    val sweep = PIE_FAN_ARC_DEG / AppConfig.PIE_SLOTS_PER_RING - PIE_SECTOR_GAP_DEG
    return Path().apply {
        arcTo(Rect(anchor.x - outerR, anchor.y - outerR, anchor.x + outerR, anchor.y + outerR), startAngle, sweep, true)
        arcTo(Rect(anchor.x - innerR, anchor.y - innerR, anchor.x + innerR, anchor.y + innerR), startAngle + sweep, -sweep, false)
        close()
    }
}

private fun PieGeometry.sectorCenter(sector: PieSector): Offset {
    val (innerR, outerR) = ringRadii(sector.ring)
    val sweep = PIE_FAN_ARC_DEG / AppConfig.PIE_SLOTS_PER_RING - PIE_SECTOR_GAP_DEG
    val angle = Math.toRadians((sectorStartAngle(sector.slot) + PIE_SECTOR_GAP_DEG / 2f + sweep / 2f).toDouble())
    val radius = (innerR + outerR) / 2f
    return Offset(
        x = anchor.x + radius * cos(angle).toFloat(),
        y = anchor.y + radius * sin(angle).toFloat(),
    )
}

private fun PieGeometry.hitTest(offset: Offset): PieSector? {
    val dx = offset.x - anchor.x
    val dy = offset.y - anchor.y
    val dist = sqrt(dx * dx + dy * dy)
    val ring = when {
        dist >= innerDeadR && dist <= ring0OuterR -> 1
        dist >= ring1InnerR && dist <= outerR -> 2
        else -> return null
    }
    val angle = normalizeAngle(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat())
    val step = PIE_FAN_ARC_DEG / AppConfig.PIE_SLOTS_PER_RING
    for (slot in 0 until AppConfig.PIE_SLOTS_PER_RING) {
        val start = normalizeAngle(fanStartAngle() + slot * step)
        if (isAngleInArc(angle, start, step)) return PieSector(ring, slot)
    }
    return null
}

private fun PieGeometry.ringRadii(ring: Int): Pair<Float, Float> =
    if (ring == 1) innerDeadR to ring0OuterR else ring1InnerR to outerR

private fun PieGeometry.sectorStartAngle(slot: Int): Float =
    fanStartAngle() + slot * (PIE_FAN_ARC_DEG / AppConfig.PIE_SLOTS_PER_RING)

private fun PieGeometry.fanStartAngle(): Float = when (edge) {
    PieEdge.Right -> 100f
    PieEdge.Left -> -80f
    PieEdge.Bottom -> 190f
    PieEdge.Top -> 10f
}

private fun isAngleInArc(angle: Float, start: Float, sweep: Float): Boolean {
    val end = normalizeAngle(start + sweep)
    return if (start <= end) angle in start..end else angle >= start || angle <= end
}

private fun normalizeAngle(angle: Float): Float = ((angle % 360f) + 360f) % 360f

private const val PIE_FAN_ARC_DEG = 160f
private const val PIE_SECTOR_GAP_DEG = 1.5f
private const val PIE_PHONE_FRAME_WIDTH_DP = 176f
private const val PIE_PHONE_FRAME_HEIGHT_DP = 320f

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
        label.takeIf { it.isNotBlank() && it != "None" && it != getString(R.string.action_none) }
            ?.let { getString(R.string.compose_trigger_label, getString(trigger.labelRes), it) }
    }
    return labels.joinToString(" · ").ifEmpty {
        if (defaultConfigured) getString(R.string.compose_key_configured_default) else getString(R.string.key_not_configured)
    } + refreshTick.let { "" }
}

private fun Context.openActionPicker(prefKey: String, title: String) {
    startActivity(
        Intent(this, ActionSelectionActivity::class.java)
            .putExtra("title", title)
            .putExtra("pref_key", prefKey),
    )
}
