package com.fan.edgex.ui.compose.screens

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfigsSync
import com.fan.edgex.ui.compose.components.ActionSelectionItem
import com.fan.edgex.ui.compose.components.ActionSelectionSheet
import com.fan.edgex.ui.compose.components.EdgeXBottomSheet
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIconBox
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.SecondaryActionDispatcher
import com.fan.edgex.ui.compose.components.SecondaryType
import com.fan.edgex.ui.compose.theme.EdgeXRadius
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
    excludedCodes: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalEdgeXColors.current
    var refreshTick by remember { mutableStateOf(0) }
    var pickingDirection by remember { mutableStateOf<SubGestureDirection?>(null) }
    var secondarySheet by remember { mutableStateOf<SecondaryType?>(null) }

    LaunchedEffect(prefKey, open) {
        if (open) {
            val existing = context.getConfigString(prefKey, "")
            if (existing != "sub_gesture") {
                context.putConfigsSync(
                    prefKey to "sub_gesture",
                    "${prefKey}_label" to context.getString(R.string.action_sub_gesture),
                    "${prefKey}_title" to ""
                )
            }
        }
    }

    EdgeXBottomSheet(
        open = open,
        title = title.ifBlank { stringResource(R.string.action_sub_gesture) },
        onDismissRequest = {
            pickingDirection = null
            secondarySheet = null
            onSaved()
        },
    ) {
        val none = stringResource(R.string.action_none)
        EdgeXListGroup {
            subGestureDirections.forEachIndexed { index, dir ->
                val childKey = AppConfig.subGestureChildKey(prefKey, dir.direction)
                val action = context.getConfigString(childKey, "none")
                val rawLabel = context.getConfigString("${childKey}_label", "")
                val label = com.fan.edgex.ui.ActionSelectionActivity.resolveActionLabel(context, action, rawLabel)
                SubGestureRow(
                    title = stringResource(dir.labelRes),
                    subtitle = label + refreshTick.let { "" },
                    actionCode = action,
                    onClick = { pickingDirection = dir },
                ) {
                    EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceDim)
                }
                if (index != subGestureDirections.lastIndex) EdgeXDivider()
            }
        }
    }
 
    val activeDir = pickingDirection
    if (activeDir != null && secondarySheet == null) {
        val childKey = AppConfig.subGestureChildKey(prefKey, activeDir.direction)
        val childTitle = "$title / ${stringResource(activeDir.labelRes)}"
        ActionSelectionSheet(
            open = true,
            title = childTitle,
            onDismiss = {
                pickingDirection = null
                secondarySheet = null
            },
            excludedCodes = excludedCodes,
            onSelect = { action ->
                if (action.needsSecondary) {
                    secondarySheet = SecondaryType.fromCode(action.code)
                } else {
                    pickingDirection = null
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
            excludedCodes = excludedCodes,
            onDismiss = {
                secondarySheet = null
                pickingDirection = null
            },
            onSaved = {
                secondarySheet = null
                pickingDirection = null
                refreshTick++
            },
        )
    }
}

@Composable
private fun SubGestureRow(
    title: String,
    subtitle: String?,
    actionCode: String,
    onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
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

        trailing()
    }
}
