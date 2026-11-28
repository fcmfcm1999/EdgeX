package com.fan.edgex.ui.compose.components

import androidx.compose.runtime.Composable
import com.fan.edgex.R
import com.fan.edgex.config.ActionFlowStore
import com.fan.edgex.config.ActionPickerScope
import com.fan.edgex.config.MultiActionStore
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfigsSync
import com.fan.edgex.ui.compose.screens.ConditionSheet
import com.fan.edgex.ui.compose.screens.MultiActionPickerSheet
import com.fan.edgex.ui.compose.screens.RepeatSheet
import com.fan.edgex.ui.compose.screens.SubGestureSheet

enum class SecondaryType {
    AppPicker,
    MusicControl,
    FastScroll,
    ShellCommand,
    AppShortcut,
    SubGesture,
    Condition,
    MultiAction,
    Delay,
    Repeat,
    ;

    companion object {
        fun fromCode(code: String): SecondaryType? = when (code) {
            "launch_app" -> AppPicker
            "music_control" -> MusicControl
            "fast_scroll" -> FastScroll
            "shell_command" -> ShellCommand
            "app_shortcut" -> AppShortcut
            "sub_gesture" -> SubGesture
            "condition" -> Condition
            "multi_action" -> MultiAction
            ActionFlowStore.ACTION_DELAY_MENU -> Delay
            ActionFlowStore.ACTION_REPEAT_MENU -> Repeat
            else -> null
        }
    }
}

@Composable
fun SecondaryActionDispatcher(
    type: SecondaryType?,
    prefKey: String,
    title: String,
    scope: ActionPickerScope,
    onCreateMultiAction: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    when (type) {
        SecondaryType.AppPicker -> {
            AppPickerSheet(
                open = true,
                onDismiss = onDismiss,
                onPick = { app ->
                    context.putConfigsSync(
                        prefKey to "launch_app:${app.packageName}",
                        "${prefKey}_label" to app.label,
                        "${prefKey}_title" to app.label,
                    )
                    onSaved()
                },
            )
        }

        SecondaryType.MusicControl -> {
            MusicControlSheet(
                open = true,
                onDismiss = onDismiss,
                onPick = { code, label ->
                    context.putConfigsSync(
                        prefKey to "music_control:$code",
                        "${prefKey}_label" to label,
                        "${prefKey}_title" to "",
                    )
                    onSaved()
                },
            )
        }

        SecondaryType.FastScroll -> {
            FastScrollSheet(
                open = true,
                onDismiss = onDismiss,
                onPick = { code, label ->
                    context.putConfigsSync(
                        prefKey to "fast_scroll:$code",
                        "${prefKey}_label" to label,
                        "${prefKey}_title" to "",
                    )
                    onSaved()
                },
            )
        }

        SecondaryType.ShellCommand -> {
            ShellCommandSheet(
                open = true,
                prefKey = prefKey,
                onDismiss = onDismiss,
                onSave = onSaved,
            )
        }

        SecondaryType.AppShortcut -> {
            AppShortcutPickerSheet(
                open = true,
                onDismiss = onDismiss,
                onPick = { shortcut ->
                    context.putConfigsSync(
                        prefKey to "app_shortcut:${shortcut.packageName}:${shortcut.shortcutId}",
                        "${prefKey}_label" to shortcut.label,
                        "${prefKey}_title" to shortcut.label,
                    )
                    onSaved()
                },
            )
        }

        SecondaryType.SubGesture -> {
            SubGestureSheet(
                open = true,
                prefKey = prefKey,
                title = title,
                scope = scope.enterSubGesture(),
                onDismiss = onDismiss,
                onSaved = onSaved,
            )
        }

        SecondaryType.Condition -> {
            ConditionSheet(
                open = true,
                prefKey = prefKey,
                title = title,
                scope = scope.enterConditionBranch(),
                onDismiss = onDismiss,
                onSaved = onSaved,
            )
        }

        SecondaryType.MultiAction -> {
            MultiActionPickerSheet(
                open = true,
                currentId = "",
                onCreate = onCreateMultiAction,
                onDismiss = onDismiss,
                onPick = { action ->
                    context.putConfigsSync(
                        prefKey to MultiActionStore.actionCode(action.id),
                        "${prefKey}_label" to action.name,
                        "${prefKey}_title" to action.name,
                    )
                    onSaved()
                },
            )
        }

        SecondaryType.Delay -> {
            val initialMs = ActionFlowStore.parseDelayMs(context.getConfigString(prefKey, ""))
                ?: ActionFlowStore.DEFAULT_DELAY_MS
            DelaySheet(
                open = true,
                initialDurationMs = initialMs,
                onDismiss = onDismiss,
                onSave = { durationMs ->
                    val label = context.getString(
                        R.string.delay_summary,
                        context.getString(R.string.action_delay),
                        ActionFlowStore.formatDelayDuration(durationMs),
                    )
                    context.putConfigsSync(
                        prefKey to ActionFlowStore.buildDelayCode(durationMs),
                        "${prefKey}_label" to label,
                        "${prefKey}_title" to label,
                    )
                    onSaved()
                },
            )
        }

        SecondaryType.Repeat -> {
            RepeatSheet(
                open = true,
                prefKey = prefKey,
                title = title,
                scope = scope.enterRepeatChild(),
                onDismiss = onDismiss,
                onSaved = onSaved,
            )
        }

        null -> {}
    }
}
