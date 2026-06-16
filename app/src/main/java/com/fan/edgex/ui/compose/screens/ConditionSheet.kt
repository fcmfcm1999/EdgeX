package com.fan.edgex.ui.compose.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fan.edgex.R
import com.fan.edgex.config.ConditionStore
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import com.fan.edgex.ui.compose.components.ActionSelectionSheet
import com.fan.edgex.ui.compose.components.ConditionPickerSheet
import com.fan.edgex.ui.compose.components.EdgeXBottomSheet
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.SecondaryActionDispatcher
import com.fan.edgex.ui.compose.components.SecondaryType
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors

@Composable
fun ConditionSheet(
    open: Boolean,
    prefKey: String,
    title: String,
    excludedCodes: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalEdgeXColors.current
    val none = stringResource(R.string.action_none)
    var refreshTick by remember { mutableStateOf(0) }
    var condId by remember { mutableStateOf("") }
    var pickingBranch by remember { mutableStateOf<String?>(null) } // "then" or "else"
    var secondarySheet by remember { mutableStateOf<SecondaryType?>(null) }
    var showConditionPicker by remember { mutableStateOf(false) }

    // Resolve or create condition ID when sheet opens
    if (open && condId.isBlank()) {
        val existing = context.getConfigString(prefKey, "")
        val extracted = ConditionStore.extractId(existing)
        condId = extracted ?: System.currentTimeMillis().toString()
        context.putConfig(prefKey, ConditionStore.buildActionCode(condId))
    }

    EdgeXBottomSheet(
        open = open,
        title = title.ifBlank { stringResource(R.string.action_condition) },
        onDismissRequest = {
            pickingBranch = null
            secondarySheet = null
            onSaved()
        },
    ) {
        val ifLabel = context.getConfigString(ConditionStore.condIfLabelKey(condId), none)
        val thenLabel = context.getConfigString(ConditionStore.condThenLabelKey(condId), none)
        val elseLabel = context.getConfigString(ConditionStore.condElseLabelKey(condId), none)

        EdgeXListGroup {
            // If row - opens Compose ConditionPickerSheet
            EdgeXRow(
                title = stringResource(R.string.cond_label_if),
                subtitle = ifLabel + refreshTick.let { "" },
                icon = EdgeXIcons.If,
                onClick = { showConditionPicker = true },
            ) {
                EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceDim)
            }
            EdgeXDivider()

            // Then row
            EdgeXRow(
                title = stringResource(R.string.cond_label_then),
                subtitle = thenLabel + refreshTick.let { "" },
                icon = EdgeXIcons.Condition,
                onClick = { pickingBranch = "then" },
            ) {
                EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceDim)
            }
            EdgeXDivider()

            // Else row
            EdgeXRow(
                title = stringResource(R.string.cond_label_else),
                subtitle = elseLabel + refreshTick.let { "" },
                icon = EdgeXIcons.Condition,
                onClick = { pickingBranch = "else" },
            ) {
                EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceDim)
            }
        }
    }

    ConditionPickerSheet(
        open = showConditionPicker,
        onDismiss = { showConditionPicker = false },
        onSelect = { item ->
            context.putConfig(ConditionStore.condIfKey(condId), item.code)
            context.putConfig(ConditionStore.condIfLabelKey(condId), context.getString(item.labelRes))
            showConditionPicker = false
            refreshTick++
        },
    )

    val activeBranch = pickingBranch
    if (activeBranch != null && secondarySheet == null) {
        val branchPrefKey = if (activeBranch == "then") {
            ConditionStore.condThenKey(condId)
        } else {
            ConditionStore.condElseKey(condId)
        }
        val branchTitle = stringResource(if (activeBranch == "then") R.string.cond_label_then else R.string.cond_label_else)
        ActionSelectionSheet(
            open = true,
            title = branchTitle,
            onDismiss = {
                pickingBranch = null
                secondarySheet = null
            },
            excludedCodes = excludedCodes,
            onSelect = { action ->
                if (action.needsSecondary) {
                    secondarySheet = SecondaryType.fromCode(action.code)
                } else {
                    pickingBranch = null
                    context.putConfig(branchPrefKey, action.code)
                    context.putConfig("${branchPrefKey}_label", context.getString(action.labelRes))
                    refreshTick++
                    // Update the condition summary label
                    val ifLbl = context.getConfigString(ConditionStore.condIfLabelKey(condId), none)
                    val thenLbl = context.getConfigString(ConditionStore.condThenLabelKey(condId), none)
                    val elseLbl = context.getConfigString(ConditionStore.condElseLabelKey(condId), none)
                    context.putConfig("${prefKey}_label", "if($ifLbl){$thenLbl} else {$elseLbl}")
                }
            },
        )
    }

    val activeSecondary = secondarySheet
    if (activeSecondary != null && activeBranch != null) {
        val branchPrefKey = if (activeBranch == "then") {
            ConditionStore.condThenKey(condId)
        } else {
            ConditionStore.condElseKey(condId)
        }
        val branchTitle = stringResource(if (activeBranch == "then") R.string.cond_label_then else R.string.cond_label_else)
        SecondaryActionDispatcher(
            type = activeSecondary,
            prefKey = branchPrefKey,
            title = branchTitle,
            excludedCodes = excludedCodes,
            onDismiss = {
                secondarySheet = null
                pickingBranch = null
            },
            onSaved = {
                secondarySheet = null
                pickingBranch = null
                refreshTick++
                val ifLbl = context.getConfigString(ConditionStore.condIfLabelKey(condId), none)
                val thenLbl = context.getConfigString(ConditionStore.condThenLabelKey(condId), none)
                val elseLbl = context.getConfigString(ConditionStore.condElseLabelKey(condId), none)
                context.putConfig("${prefKey}_label", "if($ifLbl){$thenLbl} else {$elseLbl}")
            },
        )
    }
}
