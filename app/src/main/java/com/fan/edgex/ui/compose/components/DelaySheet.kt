package com.fan.edgex.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fan.edgex.R
import com.fan.edgex.config.ActionFlowStore
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DelaySheet(
    open: Boolean,
    initialDurationMs: Long = ActionFlowStore.DEFAULT_DELAY_MS,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    val colors = LocalEdgeXColors.current
    var text by remember(open, initialDurationMs) {
        mutableStateOf(initialDurationMs.coerceAtLeast(0L).toString())
    }
    val parsed = text.trim().toLongOrNull()?.takeIf { it in 0L..ActionFlowStore.MAX_DELAY_MS }
    val presets = listOf(200L, 500L, 1000L, 2000L, 5000L, 10000L)

    EdgeXBottomSheet(
        open = open,
        title = stringResource(R.string.action_delay),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    if (input.all(Char::isDigit) && input.length <= 8) {
                        text = input
                    }
                },
                label = { Text(stringResource(R.string.delay_duration_label)) },
                placeholder = { Text(stringResource(R.string.delay_duration_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(EdgeXRadius.sm),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.outline,
                    cursorColor = colors.accent,
                ),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = parsed == preset,
                        onClick = { text = preset.toString() },
                        label = { Text(ActionFlowStore.formatDelayDuration(preset)) },
                    )
                }
            }

            Button(
                onClick = {
                    val value = parsed ?: return@Button
                    onSave(value)
                },
                enabled = parsed != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold)
            }
        }
    }
}
