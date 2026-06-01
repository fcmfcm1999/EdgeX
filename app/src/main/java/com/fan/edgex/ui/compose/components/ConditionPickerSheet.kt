package com.fan.edgex.ui.compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fan.edgex.R

data class ConditionItem(
    val labelRes: Int,
    val code: String,
    val iconRes: Int,
)

val allConditionItems = listOf(
    ConditionItem(R.string.cond_auto_brightness, "auto_brightness", R.drawable.ic_brightness_up),
    ConditionItem(R.string.cond_auto_rotate, "auto_rotate", R.drawable.ic_screen_rotation),
    ConditionItem(R.string.cond_wifi_enabled, "wifi_enabled", R.drawable.ic_wifi),
    ConditionItem(R.string.cond_mobile_data, "mobile_data", R.drawable.ic_mobile_data),
    ConditionItem(R.string.cond_location, "location", R.drawable.ic_location),
    ConditionItem(R.string.cond_bluetooth, "bluetooth", R.drawable.ic_bluetooth),
    ConditionItem(R.string.cond_nfc, "nfc", R.drawable.ic_nfc),
    ConditionItem(R.string.cond_power_connected, "power_connected", R.drawable.ic_power),
    ConditionItem(R.string.cond_wifi_connected, "wifi_connected", R.drawable.ic_wifi),
    ConditionItem(R.string.cond_network_connected, "network_connected", R.drawable.ic_link),
    ConditionItem(R.string.cond_media_playing, "media_playing", R.drawable.ic_music),
    ConditionItem(R.string.cond_screen_portrait, "screen_portrait", R.drawable.ic_screen_portrait),
    ConditionItem(R.string.cond_screen_landscape, "screen_landscape", R.drawable.ic_screen_landscape),
)

@Composable
fun ConditionPickerSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    onSelect: (ConditionItem) -> Unit,
) {
    EdgeXBottomSheet(
        open = open,
        title = stringResource(R.string.header_condition_if),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            EdgeXListGroup {
                allConditionItems.forEachIndexed { index, item ->
                    EdgeXRow(
                        title = stringResource(item.labelRes),
                        icon = item.iconRes,
                        onClick = { onSelect(item) },
                    )
                    if (index != allConditionItems.lastIndex) EdgeXDivider()
                }
            }
        }
    }
}
