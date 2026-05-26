package com.fan.edgex.ui.compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    ActionSelectionItem("pie", R.string.action_pie, EdgeXIcons.Pie, needsSecondary = true),
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
    val filtered = if (query.isBlank()) items else items.filter {
        stringResource(it.labelRes).contains(query, ignoreCase = true)
    }

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
                        icon = action.icon,
                        onClick = {
                            searchQuery = ""
                            onSelect(action)
                        },
                    )
                    if (index != filtered.lastIndex) EdgeXDivider()
                }
            }
        }
    }
}
