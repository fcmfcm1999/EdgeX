package com.fan.edgex.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors

data class ActionSelectionItem(
    val code: String,
    val labelRes: Int,
    val icon: Int,
    val needsSecondary: Boolean = false,
)

val allActionSelectionItems = listOf(
    ActionSelectionItem("none", R.string.action_none, EdgeXIcons.Check),
    ActionSelectionItem("back", R.string.action_back, EdgeXIcons.Back),
    ActionSelectionItem("home", R.string.action_home, EdgeXIcons.Home),
    ActionSelectionItem("recents", R.string.action_recents, EdgeXIcons.Recents),
    ActionSelectionItem("expand_notifications", R.string.action_expand_notifications, EdgeXIcons.Notifications),
    ActionSelectionItem("shell_command", R.string.action_shell_command, EdgeXIcons.Terminal, needsSecondary = true),
    ActionSelectionItem("sub_gesture", R.string.action_sub_gesture, EdgeXIcons.SubGesture, needsSecondary = true),
    ActionSelectionItem("pie", R.string.action_pie, EdgeXIcons.Pie),
    ActionSelectionItem("launch_app", R.string.action_launch_app, EdgeXIcons.LaunchApp, needsSecondary = true),
    ActionSelectionItem("app_shortcut", R.string.action_app_shortcut, EdgeXIcons.AppShortcut, needsSecondary = true),
    ActionSelectionItem("clear_background", R.string.action_clear_background, EdgeXIcons.ClearBackground),
    ActionSelectionItem("freezer_drawer", R.string.action_freezer_drawer, EdgeXIcons.Freeze),
    ActionSelectionItem("refreeze", R.string.action_refreeze, EdgeXIcons.Refreeze),
    ActionSelectionItem("screenshot", R.string.action_screenshot, EdgeXIcons.Screenshot),
    ActionSelectionItem(AppConfig.PARTIAL_SCREENSHOT_ACTION, R.string.action_partial_screenshot, EdgeXIcons.PartialScreenshot),
    ActionSelectionItem("clipboard", R.string.action_clipboard, EdgeXIcons.Clipboard),
    ActionSelectionItem("universal_copy", R.string.action_universal_copy, EdgeXIcons.UniversalCopy),
    ActionSelectionItem("lock_screen", R.string.action_lock_screen, EdgeXIcons.Lock),
    ActionSelectionItem("kill_app", R.string.action_kill_app, EdgeXIcons.KillApp),
    ActionSelectionItem("prev_app", R.string.action_prev_app, EdgeXIcons.PrevApp),
    ActionSelectionItem("next_app", R.string.action_next_app, EdgeXIcons.NextApp),
    ActionSelectionItem("brightness_up", R.string.action_brightness_up, EdgeXIcons.BrightnessUp),
    ActionSelectionItem("brightness_down", R.string.action_brightness_down, EdgeXIcons.BrightnessDown),
    ActionSelectionItem("volume_up", R.string.action_volume_up, EdgeXIcons.VolumeUp),
    ActionSelectionItem("volume_down", R.string.action_volume_down, EdgeXIcons.VolumeDown),
    ActionSelectionItem("music_control", R.string.action_music_control, EdgeXIcons.Music, needsSecondary = true),
    ActionSelectionItem("fast_scroll", R.string.action_fast_scroll, EdgeXIcons.FastScroll, needsSecondary = true),
    ActionSelectionItem("multi_action", R.string.action_multi_action, EdgeXIcons.Multi, needsSecondary = true),
    ActionSelectionItem("condition", R.string.action_condition, EdgeXIcons.Condition, needsSecondary = true),
    ActionSelectionItem(AppConfig.CUSTOM_PANEL_ACTION, R.string.action_custom_panel, EdgeXIcons.CustomPanel),
    ActionSelectionItem(AppConfig.SIDE_BAR_LEFT_ACTION, R.string.action_left_side_bar, EdgeXIcons.SideBarLeft),
    ActionSelectionItem(AppConfig.SIDE_BAR_RIGHT_ACTION, R.string.action_right_side_bar, EdgeXIcons.SideBarRight),
    ActionSelectionItem("toggle_flashlight", R.string.action_toggle_flashlight, EdgeXIcons.Flashlight),
    ActionSelectionItem("toggle_wifi", R.string.action_toggle_wifi, EdgeXIcons.Wifi),
    ActionSelectionItem("toggle_mobile_data", R.string.action_toggle_mobile_data, EdgeXIcons.MobileData),
    ActionSelectionItem("game_mode", R.string.action_game_mode, EdgeXIcons.GameMode),
)

@Composable
fun ActionSelectionSheet(
    open: Boolean,
    title: String,
    onDismiss: () -> Unit,
    excludedCodes: Set<String>,
    onSelect: (ActionSelectionItem) -> Unit,
) {
    val colors = LocalEdgeXColors.current
    var searchQuery by remember { mutableStateOf("") }

    val items = remember(excludedCodes) {
        allActionSelectionItems.filter { it.code !in excludedCodes }
    }

    val query = searchQuery.trim()
    val filtered = items.filter { item ->
        query.isBlank() || stringResource(item.labelRes).contains(query, ignoreCase = true)
    }

    val shellItem = filtered.find { it.code == "shell_command" }
    val musicItem = filtered.find { it.code == "music_control" }
    val others = filtered.filter { it.code != "shell_command" && it.code != "music_control" }

    EdgeXBottomSheet(
        open = open,
        title = title,
        onDismissRequest = {
            searchQuery = ""
            onDismiss()
        },
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            placeholder = { Text(stringResource(R.string.compose_search_actions_hint), color = colors.onSurfaceDim) },
            singleLine = true,
            shape = RoundedCornerShape(EdgeXRadius.sm),
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        EdgeXIcon(EdgeXIcons.Back, contentDescription = "Clear", tint = colors.onSurfaceDim)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.outline,
                cursorColor = colors.accent,
            ),
        )

        // Action Grid List
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val finalRows = if (searchQuery.isNotBlank()) {
                filtered.chunked(2)
            } else {
                val baseRows = others.chunked(2)
                val specialRow = listOfNotNull(musicItem, shellItem)
                val fRows = mutableListOf<List<ActionSelectionItem>>()
                var inserted = false
                baseRows.forEach { row ->
                    fRows.add(row)
                    if (!inserted && specialRow.isNotEmpty() && row.any { it.code == "expand_notifications" || it.code == "sub_gesture" }) {
                        fRows.add(specialRow)
                        inserted = true
                    }
                }
                if (!inserted && specialRow.isNotEmpty()) {
                    fRows.add(specialRow)
                }
                fRows
            }

            finalRows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionGridItem(
                        action = rowItems[0],
                        onClick = {
                            searchQuery = ""
                            onSelect(rowItems[0])
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (rowItems.size > 1) {
                        ActionGridItem(
                            action = rowItems[1],
                            onClick = {
                                searchQuery = ""
                                onSelect(rowItems[1])
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun ActionGridItem(
    action: ActionSelectionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEdgeXColors.current
    Box(
        modifier = modifier
            .testTag("gesture_action_${action.code}")
            .clip(RoundedCornerShape(EdgeXRadius.sm))
            .background(colors.surface1)
            .border(1.dp, colors.outline, RoundedCornerShape(EdgeXRadius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(EdgeXRadius.xs))
                    .background(colors.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                EdgeXIcon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = colors.onAccentSoft,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = stringResource(action.labelRes),
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (action.needsSecondary) {
                EdgeXIcon(
                    imageVector = EdgeXIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.onSurfaceDim,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
