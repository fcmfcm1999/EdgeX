package com.fan.edgex.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fan.edgex.R

@Composable
fun MusicControlSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    onPick: (code: String, label: String) -> Unit,
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
