package com.fan.edgex.ui.compose.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.MultiAction
import com.fan.edgex.config.MultiActionStep
import com.fan.edgex.config.MultiActionStore
import com.fan.edgex.config.broadcastFullConfigSnapshot
import com.fan.edgex.config.configPrefs
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import com.fan.edgex.config.putConfigsSync
import com.fan.edgex.config.requestHookActionExecution
import com.fan.edgex.ui.AppIconPickerActivity
import com.fan.edgex.ui.ConditionActionActivity
import com.fan.edgex.ui.MultiActionIconUtils
import com.fan.edgex.ui.ShortcutSelectionActivity
import com.fan.edgex.ui.SubGestureActivity
import com.fan.edgex.ui.ThemeManager
import com.fan.edgex.ui.compose.components.EdgeXBottomSheet
import com.fan.edgex.ui.compose.components.EdgeXChip
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIconBox
import com.fan.edgex.ui.compose.components.EdgeXIconButton
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.EdgeXSegmentedControl
import com.fan.edgex.ui.compose.components.EdgeXSwitchRow
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.theme.EdgeXAccent
import com.fan.edgex.ui.compose.theme.EdgeXRadius
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class MultiStepAction(
    val code: String,
    val labelRes: Int,
    val icon: Int,
    val needsDetail: Boolean = false,
)

private data class MultiAppItem(
    val packageName: String,
    val label: String,
)

private val multiStepActions = listOf(
    MultiStepAction("back", R.string.action_back, EdgeXIcons.Back),
    MultiStepAction("home", R.string.action_home, EdgeXIcons.Home),
    MultiStepAction("recents", R.string.action_recents, EdgeXIcons.Recents),
    MultiStepAction("expand_notifications", R.string.action_expand_notifications, EdgeXIcons.Notifications),
    MultiStepAction("lock_screen", R.string.action_lock_screen, EdgeXIcons.Lock),
    MultiStepAction("screenshot", R.string.action_screenshot, EdgeXIcons.Screenshot),
    MultiStepAction(AppConfig.PARTIAL_SCREENSHOT_ACTION, R.string.action_partial_screenshot, EdgeXIcons.PartialScreenshot),
    MultiStepAction("toggle_flashlight", R.string.action_toggle_flashlight, EdgeXIcons.Flashlight),
    MultiStepAction("brightness_up", R.string.action_brightness_up, EdgeXIcons.BrightnessUp),
    MultiStepAction("brightness_down", R.string.action_brightness_down, EdgeXIcons.BrightnessDown),
    MultiStepAction("volume_up", R.string.action_volume_up, EdgeXIcons.VolumeUp),
    MultiStepAction("volume_down", R.string.action_volume_down, EdgeXIcons.VolumeDown),
    MultiStepAction("freezer_drawer", R.string.action_freezer_drawer, EdgeXIcons.Freeze),
    MultiStepAction("refreeze", R.string.action_refreeze, EdgeXIcons.Refreeze),
    MultiStepAction("clear_background", R.string.action_clear_background, EdgeXIcons.ClearBackground),
    MultiStepAction("kill_app", R.string.action_kill_app, EdgeXIcons.KillApp),
    MultiStepAction("prev_app", R.string.action_prev_app, EdgeXIcons.PrevApp),
    MultiStepAction("next_app", R.string.action_next_app, EdgeXIcons.NextApp),
    MultiStepAction("clipboard", R.string.action_clipboard, EdgeXIcons.Clipboard),
    MultiStepAction("universal_copy", R.string.action_universal_copy, EdgeXIcons.UniversalCopy),
    MultiStepAction("toggle_wifi", R.string.action_toggle_wifi, EdgeXIcons.Wifi),
    MultiStepAction("toggle_mobile_data", R.string.action_toggle_mobile_data, EdgeXIcons.MobileData),
    MultiStepAction("game_mode", R.string.action_game_mode, EdgeXIcons.GameMode),
    MultiStepAction("pie", R.string.action_pie, EdgeXIcons.Pie),
    MultiStepAction(AppConfig.CUSTOM_PANEL_ACTION, R.string.action_custom_panel, EdgeXIcons.CustomPanel),
    MultiStepAction(AppConfig.SIDE_BAR_LEFT_ACTION, R.string.action_left_side_bar, EdgeXIcons.SideBarLeft),
    MultiStepAction(AppConfig.SIDE_BAR_RIGHT_ACTION, R.string.action_right_side_bar, EdgeXIcons.SideBarRight),
    MultiStepAction("music_control", R.string.action_music_control, EdgeXIcons.Music, needsDetail = true),
    MultiStepAction("multi_action", R.string.action_multi_action, EdgeXIcons.Multi, needsDetail = true),
    MultiStepAction("shell_command", R.string.action_shell_command, EdgeXIcons.Terminal, needsDetail = true),
    MultiStepAction("launch_app", R.string.action_launch_app, EdgeXIcons.LaunchApp, needsDetail = true),
    MultiStepAction("app_shortcut", R.string.action_app_shortcut, EdgeXIcons.AppShortcut, needsDetail = true),
    MultiStepAction("condition", R.string.action_condition, EdgeXIcons.Condition, needsDetail = true),
    MultiStepAction("sub_gesture", R.string.action_sub_gesture, EdgeXIcons.SubGesture, needsDetail = true),
)

@Composable
fun MultiScreen(
    onBack: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var creatingNew by remember { mutableStateOf(false) }
    var optionsAction by remember { mutableStateOf<MultiAction?>(null) }
    var renameAction by remember { mutableStateOf<MultiAction?>(null) }
    var deleteAction by remember { mutableStateOf<MultiAction?>(null) }
    var pendingIconAction by remember { mutableStateOf<MultiAction?>(null) }
    val items = remember(refreshTick) { MultiActionStore.getAll(context.configPrefs()) }
    val iconPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val action = pendingIconAction ?: return@rememberLauncherForActivityResult
        pendingIconAction = null
        if (result.resultCode == Activity.RESULT_OK) {
            val newRef = result.data?.getStringExtra(AppIconPickerActivity.EXTRA_ICON_REF) ?: return@rememberLauncherForActivityResult
            MultiActionIconUtils.deleteIfCustom(context, action.iconRef)
            MultiActionStore.save(context.configPrefs(), action.copy(iconRef = newRef))
            context.broadcastFullConfigSnapshot()
            refreshTick++
        }
    }

    if (editingId != null) {
        MultiActionEditorScreen(
            id = editingId.orEmpty(),
            isNew = creatingNew,
            onBack = {
                editingId = null
                creatingNew = false
                refreshTick++
            },
            showToast = showToast,
            modifier = modifier,
        )
        return
    }

    fun create() {
        editingId = MultiActionStore.generateId()
        creatingNew = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            EdgeXTopBar(title = stringResource(R.string.action_multi_action), onBack = onBack)
            if (items.isEmpty()) {
                EmptyMultiState(onCreate = {
                    create()
                })
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items.forEach { item ->
                        MultiActionCard(
                            item = item,
                            onOptions = { optionsAction = item },
                            onEdit = {
                                editingId = item.id
                                creatingNew = false
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
        if (items.isNotEmpty()) {
            FloatingActionButton(
                onClick = { create() },
                containerColor = LocalEdgeXColors.current.accent,
                contentColor = LocalEdgeXColors.current.onAccent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(22.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EdgeXIcon(EdgeXIcons.Plus, contentDescription = null)
                    Text(stringResource(R.string.compose_new), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    MultiActionOptionsDialog(
        action = optionsAction,
        onDismiss = { optionsAction = null },
        onEdit = {
            editingId = it.id
            creatingNew = false
            optionsAction = null
        },
        onRename = {
            renameAction = it
            optionsAction = null
        },
        onEditIcon = {
            pendingIconAction = it
            optionsAction = null
            iconPickerLauncher.launch(Intent(context, AppIconPickerActivity::class.java))
        },
        onExecute = {
            context.requestHookActionExecution(MultiActionStore.actionCode(it.id))
            optionsAction = null
        },
        onDelete = {
            deleteAction = it
            optionsAction = null
        },
    )

    RenameDialog(
        open = renameAction != null,
        title = stringResource(R.string.action_rename),
        initial = renameAction?.name.orEmpty(),
        hint = stringResource(R.string.multi_action_edit_name_hint),
        onDismiss = { renameAction = null },
        onSave = { newName ->
            val action = renameAction ?: return@RenameDialog
            MultiActionStore.save(context.configPrefs(), action.copy(name = newName.ifBlank { action.name }))
            context.broadcastFullConfigSnapshot()
            renameAction = null
            refreshTick++
        },
    )

    DeleteMultiActionDialog(
        action = deleteAction,
        onDismiss = { deleteAction = null },
        onConfirm = {
            MultiActionIconUtils.deleteIfCustom(context, it.iconRef)
            MultiActionStore.delete(context.configPrefs(), it.id)
            context.broadcastFullConfigSnapshot()
            deleteAction = null
            refreshTick++
        },
    )
}

@Composable
private fun MultiActionEditorScreen(
    id: String,
    isNew: Boolean,
    onBack: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val existing = remember(id, isNew) { if (isNew) null else MultiActionStore.get(context.configPrefs(), id) }
    var name by remember(id, isNew) { mutableStateOf(existing?.name ?: id) }
    var steps by remember(id, isNew) { mutableStateOf(existing?.steps?.toList().orEmpty()) }
    var iconRef by remember(id, isNew) { mutableStateOf(existing?.iconRef.orEmpty()) }
    var modified by remember(id, isNew) { mutableStateOf(isNew) }
    var showRename by remember { mutableStateOf(false) }
    var showUnsaved by remember { mutableStateOf(false) }
    var choosingAction by remember { mutableStateOf(false) }
    var editingStepIndex by remember { mutableStateOf<Int?>(null) }
    var optionsStepIndex by remember { mutableStateOf<Int?>(null) }
    var shellTargetIndex by remember { mutableStateOf<Int?>(null) }
    var multiTargetIndex by remember { mutableStateOf<Int?>(null) }
    var musicTargetIndex by remember { mutableStateOf<Int?>(null) }
    var appTargetIndex by remember { mutableStateOf<Int?>(null) }
    var pendingLegacyIndex by remember { mutableStateOf<Int?>(null) }
    var showRenameStep by remember { mutableStateOf(false) }

    fun save() {
        MultiActionStore.save(context.configPrefs(), MultiAction(id, name, steps.toMutableList(), iconRef))
        context.broadcastFullConfigSnapshot()
        modified = false
        showToast(context.getString(R.string.action_saved))
    }

    fun applyStep(index: Int?, step: MultiActionStep) {
        steps = if (index == null) {
            steps + step
        } else {
            steps.toMutableList().also { it[index] = step }
        }
        modified = true
        choosingAction = false
        editingStepIndex = null
    }

    fun handleBack() {
        if (modified) {
            showUnsaved = true
        } else {
            onBack()
        }
    }

    DisposableEffect(lifecycleOwner, pendingLegacyIndex) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME || pendingLegacyIndex == null) return@LifecycleEventObserver
            val tempKey = MultiActionStore.tempStepKey()
            val code = context.getConfigString(tempKey)
            val label = context.getConfigString("${tempKey}_label")
            if (code.isNotBlank() && code != "none") {
                applyStep(pendingLegacyIndex, MultiActionStep(code, label.ifBlank { code }))
                context.putConfig(tempKey, "")
                context.putConfig("${tempKey}_label", "")
            }
            pendingLegacyIndex = null
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler { handleBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(
            title = stringResource(R.string.header_multi_action_edit),
            onBack = { handleBack() },
            trailing = {
                EdgeXIconButton(onClick = { save() }, tonal = true) {
                    EdgeXIcon(EdgeXIcons.Save, contentDescription = stringResource(R.string.btn_save), tint = LocalEdgeXColors.current.onAccentSoft)
                }
            },
        )
        MultiEditHeader(
            name = name,
            stepCount = steps.size,
            onRename = { showRename = true },
        )
        if (steps.isEmpty()) {
            EmptyMultiSteps(onAdd = {
                editingStepIndex = null
                choosingAction = true
            })
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                steps.forEachIndexed { index, step ->
                    MultiStepCard(
                        index = index,
                        step = step,
                        onEdit = {
                            editingStepIndex = index
                            choosingAction = true
                        },
                        onMore = { optionsStepIndex = index },
                    )
                }
                Button(
                    onClick = {
                        editingStepIndex = null
                        choosingAction = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalEdgeXColors.current.accent,
                        contentColor = LocalEdgeXColors.current.onAccent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    EdgeXIcon(EdgeXIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.multi_action_add_step), fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }

    RenameDialog(
        open = showRename,
        title = stringResource(R.string.action_rename),
        initial = name,
        hint = stringResource(R.string.multi_action_edit_name_hint),
        onDismiss = { showRename = false },
        onSave = {
            name = it.ifBlank { name }
            modified = true
            showRename = false
        },
    )

    UnsavedDialog(
        open = showUnsaved,
        onDismiss = { showUnsaved = false },
        onSave = {
            save()
            onBack()
        },
        onDiscard = { onBack() },
    )

    StepOptionsSheet(
        step = optionsStepIndex?.let { steps[it] },
        canMoveUp = optionsStepIndex?.let { it > 0 } == true,
        canMoveDown = optionsStepIndex?.let { it < steps.lastIndex } == true,
        onDismiss = { optionsStepIndex = null },
        onEditAction = {
            editingStepIndex = optionsStepIndex
            optionsStepIndex = null
            choosingAction = true
        },
        onRename = {
            editingStepIndex = optionsStepIndex
            optionsStepIndex = null
            showRenameStep = true
        },
        onCopy = {
            val index = optionsStepIndex ?: return@StepOptionsSheet
            steps = steps.toMutableList().also { it.add(index + 1, it[index].copy()) }
            modified = true
            optionsStepIndex = null
        },
        onMoveUp = {
            val index = optionsStepIndex ?: return@StepOptionsSheet
            if (index <= 0) return@StepOptionsSheet
            steps = steps.toMutableList().also {
                val step = it.removeAt(index)
                it.add(index - 1, step)
            }
            modified = true
            optionsStepIndex = null
        },
        onMoveDown = {
            val index = optionsStepIndex ?: return@StepOptionsSheet
            if (index >= steps.lastIndex) return@StepOptionsSheet
            steps = steps.toMutableList().also {
                val step = it.removeAt(index)
                it.add(index + 1, step)
            }
            modified = true
            optionsStepIndex = null
        },
        onExecute = {
            optionsStepIndex?.let { context.requestHookActionExecution(steps[it].code) }
            optionsStepIndex = null
        },
        onDelete = {
            val index = optionsStepIndex ?: return@StepOptionsSheet
            steps = steps.toMutableList().also { it.removeAt(index) }
            modified = true
            optionsStepIndex = null
        },
    )

    val stepForRename = editingStepIndex?.let { steps.getOrNull(it) }
    RenameDialog(
        open = showRenameStep && stepForRename != null,
        title = stringResource(R.string.multi_action_step_edit_icon_name),
        initial = stepForRename?.label.orEmpty(),
        hint = stringResource(R.string.multi_action_step_edit_label_hint),
        onDismiss = { showRenameStep = false },
        onSave = { newLabel ->
            val index = editingStepIndex ?: return@RenameDialog
            val step = steps[index]
            steps = steps.toMutableList().also { it[index] = step.copy(label = newLabel.ifBlank { step.label }) }
            modified = true
            showRenameStep = false
            editingStepIndex = null
        },
    )

    StepActionSheet(
        open = choosingAction,
        onDismiss = {
            choosingAction = false
            editingStepIndex = null
        },
        onAction = { action ->
            val targetIndex = editingStepIndex
            when (action.code) {
                "shell_command" -> {
                    shellTargetIndex = targetIndex
                    choosingAction = false
                }
                "multi_action" -> {
                    multiTargetIndex = targetIndex
                    choosingAction = false
                }
                "music_control" -> {
                    musicTargetIndex = targetIndex
                    choosingAction = false
                }
                "launch_app" -> {
                    appTargetIndex = targetIndex
                    choosingAction = false
                }
                "app_shortcut", "condition", "sub_gesture" -> {
                    pendingLegacyIndex = targetIndex
                    context.startLegacyStepDetail(action.code, targetIndex?.let { steps[it] })
                    choosingAction = false
                }
                else -> applyStep(targetIndex, MultiActionStep(action.code, context.getString(action.labelRes)))
            }
        },
    )

    ShellCommandDialog(
        open = shellTargetIndex != null,
        existing = shellTargetIndex?.let { steps.getOrNull(it)?.code },
        onDismiss = { shellTargetIndex = null },
        onSave = { command, runAsRoot ->
            if (command.isBlank()) {
                showToast(context.getString(R.string.toast_shell_command_empty))
            } else {
                applyStep(shellTargetIndex, MultiActionStep("shell:$runAsRoot:$command", command))
                shellTargetIndex = null
            }
        },
    )

    MusicPickerSheet(
        open = musicTargetIndex != null,
        onDismiss = { musicTargetIndex = null },
        onPick = { code, label ->
            applyStep(musicTargetIndex, MultiActionStep("music_control:$code", context.getString(R.string.label_music_prefix, label)))
            musicTargetIndex = null
        },
    )

    MultiActionPickerSheet(
        open = multiTargetIndex != null,
        currentId = id,
        onDismiss = { multiTargetIndex = null },
        onPick = { item ->
            applyStep(multiTargetIndex, MultiActionStep(MultiActionStore.actionCode(item.id), item.name))
            multiTargetIndex = null
        },
    )

    AppPickerSheet(
        open = appTargetIndex != null,
        onDismiss = { appTargetIndex = null },
        onPick = { app ->
            applyStep(appTargetIndex, MultiActionStep("launch_app:${app.packageName}", app.label))
            appTargetIndex = null
        },
    )
}

@Composable
private fun EmptyMultiState(onCreate: () -> Unit) {
    val colors = LocalEdgeXColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(EdgeXRadius.xl),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EdgeXIconBox(EdgeXIcons.Multi, contentDescription = null)
            Text(stringResource(R.string.compose_empty_multi_title), color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(stringResource(R.string.compose_empty_multi_subtitle), color = colors.onSurfaceDim, fontSize = 13.sp)
            Button(
                onClick = onCreate,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            ) {
                Text(stringResource(R.string.compose_new), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MultiActionCard(
    item: MultiAction,
    onOptions: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = LocalEdgeXColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOptions),
        shape = RoundedCornerShape(EdgeXRadius.lg),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MultiActionIcon(item.iconRef, modifier = Modifier.size(36.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(stringResource(R.string.compose_step_count, item.steps.size), color = colors.onSurfaceDim, fontSize = 12.sp)
                }
                EdgeXIconButton(onClick = onEdit) {
                    EdgeXIcon(EdgeXIcons.Edit, contentDescription = stringResource(R.string.compose_edit), tint = colors.onSurface)
                }
            }
            val previewSteps = item.steps.take(2)
            if (previewSteps.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    previewSteps.forEachIndexed { index, step ->
                        StepPreviewChip(
                            label = stringResource(R.string.compose_step_chip, index + 1, step.label),
                            selected = false,
                            modifier = Modifier.weight(1f),
                        )
                        if (index < previewSteps.lastIndex) {
                            EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceDim, modifier = Modifier.size(14.dp))
                        }
                    }
                    val remaining = item.steps.size - previewSteps.size
                    if (remaining > 0) {
                        EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceDim, modifier = Modifier.size(14.dp))
                        StepPreviewChip(
                            label = stringResource(R.string.compose_plus_count, remaining),
                            selected = true,
                            modifier = Modifier.width(48.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepPreviewChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEdgeXColors.current
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accentSoft else colors.surface1)
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                },
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) colors.onAccentSoft else colors.onSurface2,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MultiActionIcon(
    iconRef: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(EdgeXRadius.sm))
            .background(LocalEdgeXColors.current.accentSoft)
            .padding(7.dp),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
            },
            update = { imageView ->
                MultiActionIconUtils.applyTo(imageView.context, imageView, iconRef)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MultiActionOptionsDialog(
    action: MultiAction?,
    onDismiss: () -> Unit,
    onEdit: (MultiAction) -> Unit,
    onRename: (MultiAction) -> Unit,
    onEditIcon: (MultiAction) -> Unit,
    onExecute: (MultiAction) -> Unit,
    onDelete: (MultiAction) -> Unit,
) {
    if (action == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(action.name) },
        text = {
            Column {
                DialogOptionRow(
                    title = stringResource(R.string.action_edit),
                    icon = EdgeXIcons.Edit,
                    onClick = { onEdit(action) },
                )
                DialogOptionRow(
                    title = stringResource(R.string.action_rename),
                    icon = EdgeXIcons.Edit,
                    onClick = { onRename(action) },
                )
                DialogOptionRow(
                    title = stringResource(R.string.multi_action_option_edit_icon),
                    icon = EdgeXIcons.Multi,
                    onClick = { onEditIcon(action) },
                )
                DialogOptionRow(
                    title = stringResource(R.string.action_execute),
                    icon = EdgeXIcons.Execute,
                    onClick = { onExecute(action) },
                )
                DialogOptionRow(
                    title = stringResource(R.string.action_delete),
                    icon = EdgeXIcons.ClearBackground,
                    onClick = { onDelete(action) },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun DialogOptionRow(
    title: String,
    icon: Int,
    onClick: () -> Unit,
) {
    val colors = LocalEdgeXColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EdgeXRadius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EdgeXIconBox(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
        )
        Text(title, color = colors.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun DeleteMultiActionDialog(
    action: MultiAction?,
    onDismiss: () -> Unit,
    onConfirm: (MultiAction) -> Unit,
) {
    if (action == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.multi_action_option_delete_confirm, action.name)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(action) }) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun MultiEditHeader(
    name: String,
    stepCount: Int,
    onRename: () -> Unit,
) {
    val colors = LocalEdgeXColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 14.dp)
            .clickable(onClick = onRename),
        shape = RoundedCornerShape(EdgeXRadius.xl),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EdgeXIconBox(EdgeXIcons.Multi, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.compose_step_count, stepCount), color = colors.onSurfaceDim, fontSize = 13.sp)
            }
            EdgeXIcon(EdgeXIcons.Edit, contentDescription = stringResource(R.string.action_rename), tint = colors.onSurfaceDim)
        }
    }
}

@Composable
private fun EmptyMultiSteps(onAdd: () -> Unit) {
    val colors = LocalEdgeXColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(EdgeXRadius.lg),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EdgeXIconBox(EdgeXIcons.Plus, contentDescription = null)
            Text(stringResource(R.string.multi_action_empty_steps), color = colors.onSurfaceDim, fontSize = 13.sp)
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            ) {
                Text(stringResource(R.string.multi_action_add_step), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MultiStepCard(
    index: Int,
    step: MultiActionStep,
    onEdit: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = LocalEdgeXColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(EdgeXRadius.md),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (index + 1).toString(),
                    color = colors.onAccentSoft,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                )
            }
            EdgeXIcon(iconForStep(step), contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(step.label, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(step.code, color = colors.onSurfaceDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            EdgeXIconButton(onClick = onMore) {
                EdgeXIcon(EdgeXIcons.More, contentDescription = null, tint = colors.onSurfaceDim)
            }
        }
    }
}

@Composable
private fun StepOptionsSheet(
    step: MultiActionStep?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDismiss: () -> Unit,
    onEditAction: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onExecute: () -> Unit,
    onDelete: () -> Unit,
) {
    EdgeXBottomSheet(
        open = step != null,
        title = step?.label.orEmpty(),
        onDismissRequest = onDismiss,
    ) {
        EdgeXListGroup {
            val rows = listOf(
                StepOptionRow(R.string.action_edit, EdgeXIcons.Edit, true, onEditAction),
                StepOptionRow(R.string.multi_action_step_edit_icon_name, EdgeXIcons.Info, true, onRename),
                StepOptionRow(R.string.multi_action_step_duplicate, EdgeXIcons.Duplicate, true, onCopy),
                StepOptionRow(R.string.multi_action_step_move_up, EdgeXIcons.MoveUp, canMoveUp, onMoveUp),
                StepOptionRow(R.string.multi_action_step_move_down, EdgeXIcons.MoveDown, canMoveDown, onMoveDown),
                StepOptionRow(R.string.action_execute, EdgeXIcons.Execute, true, onExecute),
                StepOptionRow(R.string.action_delete, EdgeXIcons.ClearBackground, true, onDelete),
            )
            rows.forEachIndexed { index, row ->
                StepOptionEdgeRow(row)
                if (index != rows.lastIndex) EdgeXDivider()
            }
        }
    }
}

private data class StepOptionRow(
    val titleRes: Int,
    val icon: Int,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun StepOptionEdgeRow(row: StepOptionRow) {
    val colors = LocalEdgeXColors.current
    EdgeXRow(
        title = stringResource(row.titleRes),
        icon = row.icon,
        onClick = if (row.enabled) row.onClick else null,
    ) {
        if (!row.enabled) {
            Text(
                text = "-",
                color = colors.onSurfaceDim,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StepActionSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    onAction: (MultiStepAction) -> Unit,
) {
    val colors = LocalEdgeXColors.current
    var searchQuery by remember { mutableStateOf("") }
    EdgeXBottomSheet(
        open = open,
        title = stringResource(R.string.header_action_selection),
        onDismissRequest = {
            searchQuery = ""
            onDismiss()
        },
    ) {
        val query = searchQuery.trim()
        val filtered = if (query.isBlank()) multiStepActions else multiStepActions.filter { action ->
            val label = stringResource(action.labelRes)
            label.contains(query, ignoreCase = true) || action.code.contains(query, ignoreCase = true)
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            placeholder = { Text(stringResource(R.string.compose_search_actions_hint), color = colors.onSurfaceDim) },
            singleLine = true,
            shape = RoundedCornerShape(EdgeXRadius.sm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.outline,
                cursorColor = colors.accent,
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            EdgeXListGroup {
                filtered.forEachIndexed { index, action ->
                    EdgeXRow(
                        title = stringResource(action.labelRes),
                        subtitle = if (action.needsDetail) null else action.code,
                        icon = action.icon,
                        onClick = { onAction(action) },
                    ) {
                        if (action.needsDetail) {
                            EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceDim)
                        }
                    }
                    if (index != filtered.lastIndex) EdgeXDivider()
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(
    open: Boolean,
    title: String,
    initial: String,
    hint: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    if (!open) return
    var value by remember(initial, open) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(hint) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim()) }) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun UnsavedDialog(
    open: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    if (!open) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.multi_action_unsaved_title)) },
        text = { Text(stringResource(R.string.multi_action_unsaved_message)) },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.multi_action_discard)) }
        },
    )
}

@Composable
private fun ShellCommandDialog(
    open: Boolean,
    existing: String?,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit,
) {
    if (!open) return
    val parsed = remember(existing, open) { parseShell(existing.orEmpty()) }
    var command by remember(existing, open) { mutableStateOf(parsed.first) }
    var runAsRoot by remember(existing, open) { mutableStateOf(parsed.second) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.header_shell_command)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text(stringResource(R.string.label_shell_command)) },
                    placeholder = { Text(stringResource(R.string.hint_shell_command)) },
                    minLines = 2,
                )
                EdgeXSwitchRow(
                    title = stringResource(R.string.label_run_as_root),
                    subtitle = stringResource(R.string.desc_run_as_root),
                    checked = runAsRoot,
                    onCheckedChange = { runAsRoot = it },
                    icon = EdgeXIcons.Terminal,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(command.trim(), runAsRoot) }) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun MusicPickerSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    onPick: (String, String) -> Unit,
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
                EdgeXRow(title = label, icon = option.third, onClick = { onPick(option.first, label) })
                if (index != options.lastIndex) EdgeXDivider()
            }
        }
    }
}

@Composable
private fun MultiActionPickerSheet(
    open: Boolean,
    currentId: String,
    onDismiss: () -> Unit,
    onPick: (MultiAction) -> Unit,
) {
    val context = LocalContext.current
    EdgeXBottomSheet(open = open, title = stringResource(R.string.action_multi_action), onDismissRequest = onDismiss) {
        val items = remember(open) { MultiActionStore.getAll(context.configPrefs()).filter { it.id != currentId } }
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.compose_empty_multi_title),
                color = LocalEdgeXColors.current.onSurfaceDim,
                modifier = Modifier.padding(18.dp),
            )
        } else {
            EdgeXListGroup {
                items.forEachIndexed { index, item ->
                    EdgeXRow(
                        title = item.name,
                        subtitle = stringResource(R.string.compose_step_count, item.steps.size),
                        icon = EdgeXIcons.Multi,
                        onClick = { onPick(item) },
                    )
                    if (index != items.lastIndex) EdgeXDivider()
                }
            }
        }
    }
}

@Composable
private fun AppPickerSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    onPick: (MultiAppItem) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalEdgeXColors.current
    var apps by remember { mutableStateOf(emptyList<MultiAppItem>()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(open) {
        if (open && apps.isEmpty()) {
            apps = withContext(Dispatchers.IO) { context.loadLaunchableApps() }
        }
        if (!open) query = ""
    }
    EdgeXBottomSheet(open = open, title = stringResource(R.string.action_launch_app), onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            placeholder = { Text(stringResource(R.string.hint_search_apps), color = colors.onSurfaceDim) },
            singleLine = true,
            shape = RoundedCornerShape(EdgeXRadius.sm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.outline,
                cursorColor = colors.accent,
            ),
        )
        val filtered = remember(apps, query) {
            val q = query.trim()
            if (q.isBlank()) apps else apps.filter {
                it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            EdgeXListGroup {
                filtered.forEachIndexed { index, app ->
                    EdgeXRow(
                        title = app.label,
                        subtitle = app.packageName,
                        icon = EdgeXIcons.LaunchApp,
                        onClick = { onPick(app) },
                    )
                    if (index != filtered.lastIndex) EdgeXDivider()
                }
            }
        }
    }
}

private fun parseShell(code: String): Pair<String, Boolean> {
    if (!code.startsWith("shell:")) return "" to false
    val parts = code.removePrefix("shell:").split(":", limit = 2)
    return if (parts.size == 2) parts[1] to (parts[0] == "true") else "" to false
}

private fun Context.loadLaunchableApps(): List<MultiAppItem> {
    val pm = packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map {
            MultiAppItem(
                packageName = it.activityInfo.packageName,
                label = it.loadLabel(pm).toString(),
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun Context.startLegacyStepDetail(code: String, current: MultiActionStep?) {
    val tempKey = MultiActionStore.tempStepKey()
    putConfig(tempKey, current?.code.orEmpty())
    putConfig("${tempKey}_label", current?.label.orEmpty())
    val intent = when (code) {
        "app_shortcut" -> Intent(this, ShortcutSelectionActivity::class.java)
            .putExtra("pref_key", tempKey)
        "condition" -> Intent(this, ConditionActionActivity::class.java)
            .putExtra("pref_key", tempKey)
            .putExtra("title", getString(R.string.header_condition_if))
        "sub_gesture" -> {
            putConfigsSync(
                tempKey to "sub_gesture",
                "${tempKey}_label" to getString(R.string.action_sub_gesture),
            )
            Intent(this, SubGestureActivity::class.java)
                .putExtra("pref_key", tempKey)
                .putExtra("title", getString(R.string.header_sub_gesture))
        }
        else -> return
    }
    startActivity(intent)
}

private fun iconForStep(step: MultiActionStep): Int = when {
    step.code == "back" -> R.drawable.ic_arrow_back
    step.code == "home" -> R.drawable.ic_home
    step.code == "recents" || step.code == "recent" -> R.drawable.ic_recents
    step.code == "expand_notifications" -> R.drawable.ic_notifications
    step.code == "screenshot" -> R.drawable.ic_camera
    step.code == AppConfig.PARTIAL_SCREENSHOT_ACTION -> R.drawable.ic_partial_screenshot
    step.code == "lock_screen" -> R.drawable.ic_power
    step.code == "kill_app" -> R.drawable.ic_kill_app
    step.code == "clear_background" -> R.drawable.ic_clear_recent
    step.code == "freezer_drawer" -> R.drawable.ic_freezer
    step.code == "refreeze" -> R.drawable.ic_refreeze
    step.code == "clipboard" -> R.drawable.ic_paste
    step.code == "universal_copy" -> R.drawable.ic_content_copy
    step.code == "brightness_up" -> R.drawable.ic_brightness_up
    step.code == "brightness_down" -> R.drawable.ic_brightness_down
    step.code == "volume_up" -> R.drawable.ic_volume_up
    step.code == "volume_down" -> R.drawable.ic_volume_down
    step.code.startsWith("music_control:") -> R.drawable.ic_music
    step.code.startsWith("launch_app:") -> R.drawable.ic_launch_app
    step.code.startsWith("app_shortcut:") -> R.drawable.ic_app_shortcut
    step.code.startsWith("shell:") -> R.drawable.ic_terminal
    step.code.startsWith("multi_action:") -> R.drawable.ic_multi_action
    step.code.startsWith("condition:") -> R.drawable.ic_condition
    else -> R.drawable.ic_action_dot
}

@Composable
fun ThemeScreen(
    onBack: () -> Unit,
    onThemeChanged: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var accent by remember { mutableStateOf(EdgeXAccent.fromId(context.getConfigString(AppConfig.UI_ACCENT, EdgeXAccent.Default.id))) }
    var darkSetting by remember {
        val raw = context.getConfigString(AppConfig.UI_DARK_MODE, "system")
        val normalized = when (raw) {
            "true", "dark" -> "dark"
            "false", "light" -> "light"
            else -> "system"
        }
        mutableStateOf(DarkModeOption.fromId(normalized))
    }
    var customColor by remember {
        val savedHex = context.getConfigString(AppConfig.THEME_CUSTOM_COLOR, "#326D32")
        val colorInt = runCatching { android.graphics.Color.parseColor(savedHex) }.getOrDefault(android.graphics.Color.parseColor("#326D32"))
        mutableStateOf(Color(colorInt))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EdgeXTopBar(title = stringResource(R.string.header_theme), onBack = onBack)
        ThemeHero(accent)
        ThemeSectionLabel(stringResource(R.string.compose_theme_preset))
        AccentSwatches(
            selected = accent,
            onSelected = {
                accent = it
                customColor = it.lightAccent
                context.saveUiTheme(it, darkSetting.id)
                onThemeChanged()
                showToast(context.getString(R.string.compose_theme_saved))
            },
        )
        ThemeSectionLabel(stringResource(R.string.compose_theme_dark))
        EdgeXListGroup(modifier = Modifier.padding(16.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EdgeXIcon(EdgeXIcons.DarkMode, contentDescription = null, tint = LocalEdgeXColors.current.onSurfaceDim, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.compose_theme_dark_desc),
                        color = LocalEdgeXColors.current.onSurfaceDim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                EdgeXSegmentedControl(
                    options = DarkModeOption.entries,
                    selected = darkSetting,
                    label = { context.getString(it.labelRes) },
                    onSelected = { option ->
                        darkSetting = option
                        context.putConfig(AppConfig.UI_DARK_MODE, option.id)
                        onThemeChanged()
                    },
                    modifier = Modifier.testTag("theme_dark_mode"),
                )
            }
        }
        ThemeSectionLabel(stringResource(R.string.compose_theme_custom_rgb))
        EdgeXListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            EdgeXRow(
                title = "#%06X".format(customColor.toArgb() and 0xFFFFFF),
                subtitle = stringResource(R.string.compose_theme_custom_rgb),
                icon = EdgeXIcons.Theme,
                modifier = Modifier.testTag("theme_custom_apply"),
                onClick = {
                    ColorPickerDialog.show(
                        context = context,
                        title = context.getString(R.string.compose_theme_custom_rgb),
                        configKey = AppConfig.THEME_CUSTOM_COLOR,
                        defaultColor = "#%08X".format(customColor.toArgb().toLong() and 0xFFFFFFFFL),
                    ) { picked ->
                        customColor = Color(picked)
                        ThemeManager.saveCustomColor(context, picked)
                        context.putConfigsSync(AppConfig.UI_ACCENT to "custom")
                        accent = EdgeXAccent.Custom
                        onThemeChanged()
                        showToast(context.getString(R.string.compose_theme_custom_saved))
                    }
                },
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(customColor),
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun ThemeHero(accent: EdgeXAccent) {
    val themeAccentColor = if (accent == EdgeXAccent.Custom) {
        LocalEdgeXColors.current.accent
    } else {
        accent.lightAccent
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 20.dp),
        shape = RoundedCornerShape(EdgeXRadius.xl),
        colors = CardDefaults.cardColors(containerColor = themeAccentColor),
        border = BorderStroke(0.dp, Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .height(144.dp)
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(620f, -20f),
                        radius = 280f,
                    ),
                )
                .padding(24.dp),
        ) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(stringResource(R.string.compose_theme_current), color = Color.White.copy(alpha = 0.70f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(
                    accentName(accent),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AccentSwatches(selected: EdgeXAccent, onSelected: (EdgeXAccent) -> Unit) {
    val presets = remember { EdgeXAccent.entries.filter { it != EdgeXAccent.Custom } }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            presets.forEach { accent ->
                val isSelected = selected == accent
                Box(
                    modifier = Modifier
                        .testTag("theme_accent_${accent.id}")
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(accent.lightAccent)
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) LocalEdgeXColors.current.onSurface else Color.Transparent,
                            shape = RoundedCornerShape(18.dp),
                        )
                        .clickable { onSelected(accent) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            presets.forEach { accent ->
                Text(
                    text = accentName(accent),
                    color = LocalEdgeXColors.current.onSurfaceDim,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ThemeSectionLabel(label: String) {
    Text(
        text = label,
        color = LocalEdgeXColors.current.onSurfaceDim,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 10.dp),
    )
}

@Composable
private fun accentName(accent: EdgeXAccent): String =
    stringResource(
        when (accent) {
            EdgeXAccent.Default -> R.string.theme_preset_default
            EdgeXAccent.Classic -> R.string.theme_preset_classic
            EdgeXAccent.Cedar -> R.string.theme_preset_cedar
            EdgeXAccent.Ocean -> R.string.theme_preset_ocean
            EdgeXAccent.Ember -> R.string.theme_preset_ember
            EdgeXAccent.Custom -> R.string.theme_preset_custom
        },
    )

private fun Context.saveUiTheme(accent: EdgeXAccent, darkSetting: String) {
    putConfig(AppConfig.UI_ACCENT, accent.id)
    putConfig(AppConfig.UI_DARK_MODE, darkSetting)
    ThemeManager.saveCustomColor(this, accent.lightAccent.toArgb())
}

private enum class DarkModeOption(val id: String, val labelRes: Int) {
    System("system", R.string.compose_theme_dark_system),
    Light("light", R.string.compose_theme_dark_light),
    Dark("dark", R.string.compose_theme_dark_dark);
    
    companion object {
        fun fromId(id: String): DarkModeOption =
            entries.firstOrNull { it.id == id } ?: System
    }
}
