package com.fan.edgex.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fan.edgex.R

@Composable
fun FastScrollSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    onPick: (code: String, label: String) -> Unit,
) {
    EdgeXBottomSheet(open = open, title = stringResource(R.string.header_fast_scroll), onDismissRequest = onDismiss) {
        val options = listOf(
            Triple("to_top", R.string.action_scroll_to_top, R.drawable.ic_scroll_to_top),
            Triple("to_bottom", R.string.action_scroll_to_bottom, R.drawable.ic_scroll_to_bottom),
        )
        EdgeXListGroup {
            options.forEachIndexed { index, option ->
                val label = stringResource(option.second)
                EdgeXRow(title = label, icon = option.third, onClick = { onPick(option.first, label) })
                if (index != options.lastIndex) EdgeXDivider()
            }
        }
    }
}
