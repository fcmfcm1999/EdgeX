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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fan.edgex.R
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
    KeyUiItem(KeyEvent.KEYCODE_BACK, R.string.key_back, EdgeXIcons.Back),
    KeyUiItem(KeyEvent.KEYCODE_HOME, R.string.key_home, EdgeXIcons.Home),
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
                    showToast(context.getString(if (it) R.string.compose_keys_enabled_toast else R.string.compose_keys_disabled_toast))
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
        onTriggerClick = { key, trigger ->
            context.openActionPicker(
                prefKey = AppConfig.keyAction(key.keyCode, trigger.id),
                title = context.getString(R.string.compose_title_pair, context.getString(key.titleRes), context.getString(trigger.labelRes)),
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
    EdgeXBottomSheet(open = key != null, title = key?.let { stringResource(it.titleRes) }.orEmpty(), onDismissRequest = onDismiss) {
        if (key == null) return@EdgeXBottomSheet
        EdgeXListGroup {
            keyTriggers.forEachIndexed { index, trigger ->
                EdgeXRow(
                    title = stringResource(trigger.labelRes),
                    subtitle = context.getConfigString(
                        AppConfig.keyActionLabel(key.keyCode, trigger.id),
                        stringResource(R.string.action_none),
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

private enum class PieEdge(val id: String, val labelRes: Int) {
    Left("left", R.string.compose_edge_left_short),
    Right("right", R.string.compose_edge_right_short),
    Top("top", R.string.compose_edge_top_short),
    Bottom("bottom", R.string.compose_edge_bottom_short),
}

@Composable
fun PieScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var edge by remember { mutableStateOf(PieEdge.Left) }
    var refreshTick by remember { mutableStateOf(0) }
    val edgeLabels = mapOf(
        PieEdge.Left to stringResource(R.string.compose_edge_label, stringResource(PieEdge.Left.labelRes)),
        PieEdge.Right to stringResource(R.string.compose_edge_label, stringResource(PieEdge.Right.labelRes)),
        PieEdge.Top to stringResource(R.string.compose_edge_label, stringResource(PieEdge.Top.labelRes)),
        PieEdge.Bottom to stringResource(R.string.compose_edge_label, stringResource(PieEdge.Bottom.labelRes)),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(title = stringResource(R.string.header_pie_settings), onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.compose_pie_hero),
                color = LocalEdgeXColors.current.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 31.sp,
            )
            Text(
                text = stringResource(R.string.compose_pie_subtitle),
                color = LocalEdgeXColors.current.onSurfaceDim,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        EdgeXSegmentedControl(
            options = PieEdge.entries,
            selected = edge,
            label = { edgeLabels.getValue(it) },
            onSelected = { edge = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        PiePreview(edge = edge, refreshTick = refreshTick)
        KeySectionLabel(stringResource(R.string.compose_pie_ring_1))
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
                Text(stringResource(R.string.compose_pie_center), color = colors.onAccentSoft, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
        context.getConfigString(AppConfig.pieSlotLabel(edge.id, 0, slot), context.getString(R.string.action_none))
            .takeIf { it.isNotBlank() && it != context.getString(R.string.action_none) }
            ?: listOf(
                context.getString(R.string.action_back),
                context.getString(R.string.action_home),
                context.getString(R.string.action_recents),
                context.getString(R.string.action_lock_screen),
                context.getString(R.string.action_expand_notifications),
                context.getString(R.string.action_toggle_flashlight),
            ).getOrElse(slot) { context.getString(R.string.compose_slot_number, slot + 1) }
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
                                    title = context.getString(R.string.compose_pie_slot_title, context.getString(edge.labelRes), slot + 1),
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
