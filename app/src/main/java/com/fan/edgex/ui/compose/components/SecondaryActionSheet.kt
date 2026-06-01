package com.fan.edgex.ui.compose.components

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import com.fan.edgex.R
import com.fan.edgex.config.putConfigsSync
import com.fan.edgex.ui.MultiActionsListActivity
import com.fan.edgex.ui.compose.screens.ConditionSheet
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
            else -> null
        }
    }
}

@Composable
fun SecondaryActionDispatcher(
    type: SecondaryType?,
    prefKey: String,
    title: String,
    excludedCodes: Set<String> = emptySet(),
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
                excludedCodes = excludedCodes,
                onDismiss = onDismiss,
                onSaved = onSaved,
            )
        }

        SecondaryType.Condition -> {
            ConditionSheet(
                open = true,
                prefKey = prefKey,
                title = title,
                excludedCodes = excludedCodes,
                onDismiss = onDismiss,
                onSaved = onSaved,
            )
        }

        SecondaryType.MultiAction -> {
            context.startActivity(
                Intent(context, MultiActionsListActivity::class.java)
                    .putExtra(MultiActionsListActivity.EXTRA_MODE, MultiActionsListActivity.MODE_PICK)
                    .putExtra(MultiActionsListActivity.EXTRA_PREF_KEY, prefKey)
                    .putExtra(MultiActionsListActivity.EXTRA_TITLE, title)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            onDismiss()
        }

        null -> {}
    }
}
