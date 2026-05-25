package com.fan.edgex.ui.compose.screens

import android.content.Context
import android.content.Intent
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfigsSync
import com.fan.edgex.ui.ActionSelectionActivity
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIconBox
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXSegmentedControl
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors

private enum class SideBarSide(val id: String, val labelRes: Int, val icon: Int) {
    Left("left", R.string.compose_edge_left_short, EdgeXIcons.SideBarLeft),
    Right("right", R.string.compose_edge_right_short, EdgeXIcons.SideBarRight),
}

private data class PanelSlotUi(
    val key: String,
    val titleKey: String,
    val title: String,
    val action: String,
    val label: String,
    val icon: Int,
    val appIcon: Drawable?,
)

@Composable
fun CustomPanelScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableStateOf(0) }
    val slots = remember(refreshTick) { context.loadCustomPanelSlots() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                context.syncPanelSlotTitles(context.customPanelSlotSpecs())
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(title = stringResource(R.string.menu_custom_panel), onBack = onBack)
        PreviewSectionHeader(
            title = stringResource(R.string.compose_panel_preview),
            subtitle = stringResource(R.string.compose_custom_panel_subtitle),
        )
        CustomPanelPreview(
            slots = slots,
            onSlotClick = { slot ->
                context.openPanelActionPicker(
                    prefKey = slot.key,
                    title = slot.title,
                    excludedCodes = arrayOf(AppConfig.CUSTOM_PANEL_ACTION, AppConfig.PIE_ACTION, AppConfig.SUB_GESTURE_ACTION),
                )
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
fun SideBarScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var side by remember { mutableStateOf(SideBarSide.Left) }
    var refreshTick by remember { mutableStateOf(0) }
    val slots = remember(side, refreshTick) { context.loadSideBarSlots(side) }
    val sideLabels = mapOf(
        SideBarSide.Left to stringResource(SideBarSide.Left.labelRes),
        SideBarSide.Right to stringResource(SideBarSide.Right.labelRes),
    )
    val excluded = remember {
        arrayOf(
            AppConfig.SIDE_BAR_LEFT_ACTION,
            AppConfig.SIDE_BAR_RIGHT_ACTION,
            AppConfig.PIE_ACTION,
            AppConfig.SUB_GESTURE_ACTION,
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                context.syncPanelSlotTitles(context.sideBarSlotSpecs(side))
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(title = stringResource(R.string.menu_side_bar), onBack = onBack)
        EdgeXSegmentedControl(
            options = SideBarSide.entries,
            selected = side,
            label = { sideLabels.getValue(it) },
            onSelected = { side = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        PreviewSectionHeader(
            title = stringResource(R.string.compose_panel_preview),
            subtitle = stringResource(R.string.compose_side_bar_subtitle),
        )
        SideBarPreview(
            side = side,
            slots = slots,
            onSlotClick = { slot ->
                context.openPanelActionPicker(slot.key, slot.title, excluded)
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun PreviewSectionHeader(title: String, subtitle: String) {
    val colors = LocalEdgeXColors.current
    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 6.dp)) {
        Text(title, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(subtitle, color = colors.onSurfaceDim, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
private fun CustomPanelPreview(
    slots: List<PanelSlotUi>,
    onSlotClick: (PanelSlotUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEdgeXColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EdgeXRadius.lg))
            .background(colors.surface)
            .border(1.dp, colors.outline, RoundedCornerShape(EdgeXRadius.lg))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(AppConfig.CUSTOM_PANEL_ROWS) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(AppConfig.CUSTOM_PANEL_COLUMNS) { column ->
                    val slot = slots[row * AppConfig.CUSTOM_PANEL_COLUMNS + column]
                    PreviewSlot(
                        slot = slot,
                        onClick = { onSlotClick(slot) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SideBarPreview(
    side: SideBarSide,
    slots: List<PanelSlotUi>,
    onSlotClick: (PanelSlotUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEdgeXColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(500.dp)
            .clip(RoundedCornerShape(EdgeXRadius.lg))
            .background(colors.surface)
            .border(1.dp, colors.outline, RoundedCornerShape(EdgeXRadius.lg))
            .padding(18.dp),
    ) {
        Column(
            modifier = Modifier
                .align(if (side == SideBarSide.Left) Alignment.CenterStart else Alignment.CenterEnd)
                .width(96.dp)
                .clip(RoundedCornerShape(EdgeXRadius.lg))
                .background(colors.surface2)
                .border(1.dp, colors.outlineStrong, RoundedCornerShape(EdgeXRadius.lg))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            slots.forEach { slot ->
                PreviewSlot(
                    slot = slot,
                    onClick = { onSlotClick(slot) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun PreviewSlot(
    slot: PanelSlotUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = LocalEdgeXColors.current
    val configured = AppConfig.isActiveActionValue(slot.action)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(EdgeXRadius.sm))
            .background(if (configured) colors.accentSoft else colors.surface1)
            .border(1.dp, if (configured) colors.accent else colors.outline, RoundedCornerShape(EdgeXRadius.sm))
            .clickable(onClick = onClick)
            .padding(if (compact) 6.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PanelSlotIcon(slot = slot, configured = configured)
        if (!compact) {
            Text(
                text = slot.label,
                color = if (configured) colors.onAccentSoft else colors.onSurfaceDim,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun PanelSlotIcon(slot: PanelSlotUi, configured: Boolean) {
    val colors = LocalEdgeXColors.current
    Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
        if (slot.appIcon != null) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
                },
                update = { imageView ->
                    val drawable = slot.appIcon.constantState?.newDrawable()?.mutate() ?: slot.appIcon
                    imageView.setImageDrawable(drawable)
                },
                modifier = Modifier.size(28.dp),
            )
        } else if (configured) {
            EdgeXIcon(slot.icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        } else {
            EdgeXIconBox(
                imageVector = EdgeXIcons.Plus,
                contentDescription = null,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape),
                background = colors.surface2,
                tint = colors.onSurfaceDim,
            )
        }
    }
}

private fun Context.loadCustomPanelSlots(): List<PanelSlotUi> =
    customPanelSlotSpecs().map { spec -> loadPanelSlot(spec) }

private fun Context.loadSideBarSlots(side: SideBarSide): List<PanelSlotUi> =
    sideBarSlotSpecs(side).map { spec -> loadPanelSlot(spec) }

private fun Context.customPanelSlotSpecs(): List<Triple<String, String, String>> =
    (0 until AppConfig.CUSTOM_PANEL_ROWS).flatMap { row ->
        (0 until AppConfig.CUSTOM_PANEL_COLUMNS).map { column ->
            Triple(
                AppConfig.customPanelSlot(row, column),
                AppConfig.customPanelSlotTitle(row, column),
                getString(R.string.compose_panel_cell_title, row + 1, column + 1),
            )
        }
    }

private fun Context.sideBarSlotSpecs(side: SideBarSide): List<Triple<String, String, String>> =
    (0 until AppConfig.SIDE_BAR_SLOTS).map { index ->
        Triple(
            AppConfig.sideBarSlot(side.id, index),
            AppConfig.sideBarSlotTitle(side.id, index),
            getString(R.string.panel_slot_title, index + 1),
        )
    }

private fun Context.loadPanelSlot(spec: Triple<String, String, String>): PanelSlotUi {
    val action = getConfigString(spec.first, "none")
    val savedLabel = getConfigString("${spec.first}_label", getString(R.string.action_none))
    val label = displayPanelTitleForAction(action, savedLabel)
    val appIcon = loadPanelAppIcon(action)
    return PanelSlotUi(
        key = spec.first,
        titleKey = spec.second,
        title = spec.third,
        action = action,
        label = label,
        icon = ActionSelectionActivity.actionIconRes(action),
        appIcon = appIcon,
    )
}

private fun Context.syncPanelSlotTitles(specs: List<Triple<String, String, String>>) {
    val entries = specs.map { spec ->
        val action = getConfigString(spec.first, "none")
        val label = getConfigString("${spec.first}_label", getString(R.string.action_none))
        spec.second to displayPanelTitleForAction(action, label)
    }.toTypedArray()
    putConfigsSync(*entries)
}

private fun Context.openPanelActionPicker(prefKey: String, title: String, excludedCodes: Array<String>) {
    startActivity(
        Intent(this, ActionSelectionActivity::class.java)
            .putExtra("pref_key", prefKey)
            .putExtra("title", title)
            .putExtra(ActionSelectionActivity.EXTRA_EXCLUDED_CODES, excludedCodes),
    )
}

private fun Context.loadPanelAppIcon(action: String): Drawable? {
    val packageName = when {
        action.startsWith("launch_app:") -> action.removePrefix("launch_app:")
        action.startsWith("app_shortcut:") -> action.removePrefix("app_shortcut:").substringBefore(":")
        else -> return null
    }
    return runCatching { packageManager.getApplicationIcon(packageName).foregroundOrSelf() }.getOrNull()
}

private fun Context.displayPanelTitleForAction(action: String, savedLabel: String): String {
    if (action.isBlank() || action == "none") return getString(R.string.action_none)
    return when {
        action.startsWith("launch_app:") -> appLabel(action.removePrefix("launch_app:"))
            ?: stripKnownPrefix(savedLabel, "App:", "App: ", "应用：", "应用:", "应用: ")
                .ifBlank { getString(R.string.action_launch_app) }
        action.startsWith("app_shortcut:") -> stripKnownPrefix(
            savedLabel,
            "Shortcut:",
            "Shortcut: ",
            "快捷方式:",
            "快捷方式: ",
            "快捷方式：",
        ).ifBlank {
            val packageName = action.removePrefix("app_shortcut:").substringBefore(":")
            appLabel(packageName) ?: getString(R.string.action_app_shortcut)
        }
        action.startsWith("shell:") -> shellCommandTitle(action, savedLabel)
        else -> savedLabel.ifBlank { action }
    }
}

private fun Context.appLabel(packageName: String): String? = runCatching {
    val appInfo = packageManager.getApplicationInfo(packageName, 0)
    appInfo.loadLabel(packageManager).toString()
}.getOrNull()

private fun Context.shellCommandTitle(action: String, savedLabel: String): String {
    val command = action.removePrefix("shell:").split(":", limit = 2).getOrNull(1).orEmpty().trim()
    val saved = savedLabel.trim()
    return when {
        saved.isNotBlank() &&
            saved != getString(R.string.action_shell_command) &&
            saved != "Shell" &&
            saved != "Shell Command" &&
            saved != "Shell 命令" -> saved
        command.isNotBlank() -> command
        else -> getString(R.string.action_shell_command)
    }
}

private fun stripKnownPrefix(value: String, vararg prefixes: String): String {
    val trimmed = value.trim()
    val match = prefixes.firstOrNull { trimmed.startsWith(it) } ?: return trimmed
    return trimmed.removePrefix(match).trim()
}

private fun Drawable.foregroundOrSelf(): Drawable =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this is AdaptiveIconDrawable) {
        foreground?.mutate() ?: mutate()
    } else {
        mutate()
    }
