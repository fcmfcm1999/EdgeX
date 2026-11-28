package com.fan.edgex.config

enum class ActionEntryPoint {
    GESTURE,
    KEY,
    PIE_SLOT,
    CUSTOM_PANEL_SLOT,
    SIDE_BAR_SLOT,
    MULTI_ACTION_EDITOR,
}

data class ActionPickerScope(
    val entryPoint: ActionEntryPoint,
    val insideMultiAction: Boolean = entryPoint == ActionEntryPoint.MULTI_ACTION_EDITOR,
    val insideRepeat: Boolean = false,
    val insideRepeatDirectChild: Boolean = false,
    val insideSubGesture: Boolean = false,
    val insideConditionBranch: Boolean = false,
) {
    val isDirectGestureTrigger: Boolean
        get() = entryPoint == ActionEntryPoint.GESTURE &&
            !insideMultiAction &&
            !insideRepeat &&
            !insideSubGesture &&
            !insideConditionBranch

    val allowsQueueFlowControl: Boolean
        get() = (insideMultiAction || (insideRepeat && insideConditionBranch)) &&
            !insideRepeatDirectChild

    fun enterSubGesture(): ActionPickerScope = copy(
        insideSubGesture = true,
        insideRepeatDirectChild = false,
    )

    fun enterConditionBranch(): ActionPickerScope = copy(
        insideConditionBranch = true,
        insideRepeatDirectChild = false,
    )

    fun enterRepeatChild(): ActionPickerScope = copy(
        insideRepeat = true,
        insideRepeatDirectChild = true,
    )
}

/**
 * Single source of truth for which actions appear in each [ActionPickerScope].
 */
object ActionVisibilityPolicy {
    private val TOUCH_GESTURE_ONLY_ACTIONS: Set<String> = setOf(
        AppConfig.PIE_ACTION,
        AppConfig.SUB_GESTURE_ACTION,
        AppConfig.GESTURE_POINTER_ACTION,
    )

    private val QUEUE_ONLY_FLOW_ACTIONS: Set<String> = setOf(
        ActionFlowStore.ACTION_DELAY_MENU,
        ActionFlowStore.ACTION_ABORT,
    )

    private val SIDE_BAR_ACTIONS: Set<String> = setOf(
        AppConfig.SIDE_BAR_LEFT_ACTION,
        AppConfig.SIDE_BAR_RIGHT_ACTION,
    )

    fun isVisible(actionCode: String, scope: ActionPickerScope): Boolean = when (actionCode) {
        "none" -> !scope.insideRepeatDirectChild &&
            !(scope.entryPoint == ActionEntryPoint.MULTI_ACTION_EDITOR && !scope.insideConditionBranch)

        "multi_action" -> !scope.insideMultiAction

        in QUEUE_ONLY_FLOW_ACTIONS -> scope.allowsQueueFlowControl

        in TOUCH_GESTURE_ONLY_ACTIONS -> scope.isDirectGestureTrigger

        AppConfig.CUSTOM_PANEL_ACTION -> scope.entryPoint != ActionEntryPoint.CUSTOM_PANEL_SLOT

        in SIDE_BAR_ACTIONS -> scope.entryPoint != ActionEntryPoint.SIDE_BAR_SLOT

        else -> true
    }

    fun excludedCodes(scope: ActionPickerScope): Set<String> = buildSet {
        if (scope.insideRepeatDirectChild ||
            (scope.entryPoint == ActionEntryPoint.MULTI_ACTION_EDITOR && !scope.insideConditionBranch)
        ) {
            add("none")
        }
        if (scope.insideMultiAction) {
            add("multi_action")
        }
        if (!scope.allowsQueueFlowControl) {
            addAll(QUEUE_ONLY_FLOW_ACTIONS)
        }
        if (!scope.isDirectGestureTrigger) {
            addAll(TOUCH_GESTURE_ONLY_ACTIONS)
        }
        if (scope.entryPoint == ActionEntryPoint.CUSTOM_PANEL_SLOT) {
            add(AppConfig.CUSTOM_PANEL_ACTION)
        }
        if (scope.entryPoint == ActionEntryPoint.SIDE_BAR_SLOT) {
            addAll(SIDE_BAR_ACTIONS)
        }
    }
}
