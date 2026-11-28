package com.fan.edgex.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fan.edgex.R
import com.fan.edgex.config.ActionFlowStore
import com.fan.edgex.config.ActionPickerScope
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import com.fan.edgex.config.putConfigsSync
import com.fan.edgex.ui.compose.components.ActionSelectionSheet
import com.fan.edgex.ui.compose.components.EdgeXBottomSheet
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.SecondaryActionDispatcher
import com.fan.edgex.ui.compose.components.SecondaryType
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors

@Composable
fun RepeatSheet(
    open: Boolean,
    prefKey: String,
    title: String,
    scope: ActionPickerScope,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalEdgeXColors.current
    val none = stringResource(R.string.action_none)
    var refreshTick by remember { mutableIntStateOf(0) }
    var repeatId by remember(open, prefKey) { mutableStateOf("") }
    var pickingAction by remember { mutableStateOf(false) }
    var secondarySheet by remember { mutableStateOf<SecondaryType?>(null) }
    var showCountDialog by remember { mutableStateOf(false) }

    if (open && repeatId.isBlank()) {
        val existing = context.getConfigString(prefKey, "")
        val extracted = ActionFlowStore.extractRepeatId(existing)
        repeatId = extracted ?: System.currentTimeMillis().toString()
        val countKey = ActionFlowStore.repeatCountKey(repeatId)
        if (context.getConfigString(countKey, "").isBlank()) {
            context.putConfig(countKey, ActionFlowStore.DEFAULT_REPEAT_COUNT.toString())
        }
    }

    fun updateSummaryAndPersist(id: String) {
        val count = ActionFlowStore.parseRepeatCount(
            context.getConfigString(ActionFlowStore.repeatCountKey(id), ActionFlowStore.DEFAULT_REPEAT_COUNT.toString()),
        )
        val childLabel = context.getConfigString(ActionFlowStore.repeatActionLabelKey(id), none).ifBlank { none }
        val summary = context.getString(
            R.string.repeat_summary,
            context.getString(R.string.action_repeat),
            count,
            childLabel,
        )
        context.putConfigsSync(
            prefKey to ActionFlowStore.buildRepeatCode(id),
            "${prefKey}_label" to summary,
        )
    }

    val currentCount = remember(repeatId, refreshTick, open) {
        if (repeatId.isBlank()) ActionFlowStore.DEFAULT_REPEAT_COUNT
        else ActionFlowStore.parseRepeatCount(
            context.getConfigString(ActionFlowStore.repeatCountKey(repeatId), ActionFlowStore.DEFAULT_REPEAT_COUNT.toString()),
        )
    }
    val currentActionLabel = remember(repeatId, refreshTick, open) {
        if (repeatId.isBlank()) none
        else context.getConfigString(ActionFlowStore.repeatActionLabelKey(repeatId), none).ifBlank { none }
    }

    EdgeXBottomSheet(
        open = open,
        title = title.ifBlank { stringResource(R.string.action_repeat) },
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EdgeXListGroup {
                EdgeXRow(
                    title = stringResource(R.string.repeat_count_label),
                    subtitle = currentCount.toString(),
                    icon = EdgeXIcons.Repeat,
                    onClick = { showCountDialog = true },
                ) {
                    EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceDim)
                }
                EdgeXDivider()
                EdgeXRow(
                    title = stringResource(R.string.repeat_action_label),
                    subtitle = currentActionLabel,
                    icon = EdgeXIcons.Multi,
                    onClick = { pickingAction = true },
                ) {
                    EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceDim)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    if (repeatId.isNotBlank()) {
                        updateSummaryAndPersist(repeatId)
                    }
                    onSaved()
                },
                enabled = repeatId.isNotBlank() && currentCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
                shape = RoundedCornerShape(EdgeXRadius.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showCountDialog && repeatId.isNotBlank()) {
        var input by remember(showCountDialog, currentCount) { mutableStateOf(currentCount.toString()) }
        val parsed = input.trim().toIntOrNull()?.takeIf { it in 1..ActionFlowStore.MAX_REPEAT_COUNT }
        AlertDialog(
            onDismissRequest = { showCountDialog = false },
            title = { Text(stringResource(R.string.repeat_count_label)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { v ->
                        if (v.all(Char::isDigit) && v.length <= 6) input = v
                    },
                    placeholder = { Text(stringResource(R.string.repeat_count_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val count = parsed ?: return@TextButton
                        context.putConfig(ActionFlowStore.repeatCountKey(repeatId), count.toString())
                        refreshTick++
                        showCountDialog = false
                    },
                    enabled = parsed != null,
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCountDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    val childPrefKey = ActionFlowStore.repeatActionKey(repeatId)
    if (pickingAction && secondarySheet == null && repeatId.isNotBlank()) {
        ActionSelectionSheet(
            open = true,
            title = stringResource(R.string.repeat_action_label),
            scope = scope,
            onDismiss = {
                pickingAction = false
                secondarySheet = null
            },
            onSelect = { action ->
                if (action.needsSecondary) {
                    secondarySheet = SecondaryType.fromCode(action.code)
                } else {
                    pickingAction = false
                    context.putConfigsSync(
                        childPrefKey to action.code,
                        "${childPrefKey}_label" to context.getString(action.labelRes),
                    )
                    refreshTick++
                }
            },
        )
    }

    val activeSecondary = secondarySheet
    if (activeSecondary != null && pickingAction && repeatId.isNotBlank()) {
        SecondaryActionDispatcher(
            type = activeSecondary,
            prefKey = childPrefKey,
            title = stringResource(R.string.repeat_action_label),
            scope = scope,
            onDismiss = {
                secondarySheet = null
                pickingAction = false
            },
            onSaved = {
                secondarySheet = null
                pickingAction = false
                refreshTick++
            },
        )
    }
}
