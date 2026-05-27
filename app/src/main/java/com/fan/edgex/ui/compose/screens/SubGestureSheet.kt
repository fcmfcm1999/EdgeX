package com.fan.edgex.ui.compose.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfigsSync
import com.fan.edgex.ui.compose.components.ActionSelectionItem
import com.fan.edgex.ui.compose.components.ActionSelectionSheet
import com.fan.edgex.ui.compose.components.EdgeXBottomSheet
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.SecondaryActionDispatcher
import com.fan.edgex.ui.compose.components.SecondaryType
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors

private data class SubGestureDirection(
    val direction: String,
    val labelRes: Int,
)

private val subGestureDirections = listOf(
    SubGestureDirection("hold", R.string.sub_gesture_hold),
    SubGestureDirection("swipe_left", R.string.gesture_swipe_left),
    SubGestureDirection("swipe_right", R.string.gesture_swipe_right),
    SubGestureDirection("swipe_up", R.string.gesture_swipe_up),
    SubGestureDirection("swipe_down", R.string.gesture_swipe_down),
)

@Composable
fun SubGestureSheet(
    open: Boolean,
    prefKey: String,
    title: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalEdgeXColors.current
    var refreshTick by remember { mutableStateOf(0) }
    var pickingDirection by remember { mutableStateOf<SubGestureDirection?>(null) }
    var secondarySheet by remember { mutableStateOf<SecondaryType?>(null) }

    EdgeXBottomSheet(
        open = open,
        title = title.ifBlank { stringResource(R.string.action_sub_gesture) },
        onDismissRequest = {
            pickingDirection = null
            secondarySheet = null
            onDismiss()
        },
    ) {
        EdgeXListGroup {
            subGestureDirections.forEachIndexed { index, dir ->
                val childKey = AppConfig.subGestureChildKey(prefKey, dir.direction)
                val label = context.getConfigString("${childKey}_label", context.getString(R.string.action_none))
                EdgeXRow(
                    title = stringResource(dir.labelRes),
                    subtitle = label + refreshTick.let { "" },
                    icon = EdgeXIcons.Gesture,
                    onClick = { pickingDirection = dir },
                ) {
                    EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceDim)
                }
                if (index != subGestureDirections.lastIndex) EdgeXDivider()
            }
        }
    }

    val activeDir = pickingDirection
    if (activeDir != null) {
        val childKey = AppConfig.subGestureChildKey(prefKey, activeDir.direction)
        val childTitle = "$title / ${stringResource(activeDir.labelRes)}"
        ActionSelectionSheet(
            open = true,
            title = childTitle,
            onDismiss = {
                pickingDirection = null
                secondarySheet = null
            },
            excludedCodes = emptySet(),
            onSelect = { action ->
                pickingDirection = null
                if (action.needsSecondary) {
                    secondarySheet = SecondaryType.fromCode(action.code)
                } else {
                    context.putConfigsSync(
                        childKey to action.code,
                        "${childKey}_label" to context.getString(action.labelRes),
                        "${childKey}_title" to "",
                    )
                    refreshTick++
                }
            },
        )
    }

    val activeSecondary = secondarySheet
    if (activeSecondary != null && activeDir != null) {
        val childKey = AppConfig.subGestureChildKey(prefKey, activeDir.direction)
        val childTitle = "$title / ${stringResource(activeDir.labelRes)}"
        SecondaryActionDispatcher(
            type = activeSecondary,
            prefKey = childKey,
            title = childTitle,
            onDismiss = { secondarySheet = null },
            onSaved = {
                secondarySheet = null
                refreshTick++
            },
        )
    }
}
