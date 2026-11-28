package com.fan.edgex.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionVisibilityPolicyTest {

    @Test
    fun `gesture entry allows touch actions, repeat, stop_all, multi_action, but hides delay and abort`() {
        val scope = ActionPickerScope(ActionEntryPoint.GESTURE)

        assertTrue(ActionVisibilityPolicy.isVisible("none", scope))
        assertTrue(ActionVisibilityPolicy.isVisible("back", scope))
        assertTrue(ActionVisibilityPolicy.isVisible("multi_action", scope))
        assertTrue(ActionVisibilityPolicy.isVisible("condition", scope))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_REPEAT_MENU, scope))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_STOP_ALL, scope))
        assertTrue(ActionVisibilityPolicy.isVisible(AppConfig.PIE_ACTION, scope))
        assertTrue(ActionVisibilityPolicy.isVisible(AppConfig.SUB_GESTURE_ACTION, scope))
        assertTrue(ActionVisibilityPolicy.isVisible(AppConfig.GESTURE_POINTER_ACTION, scope))

        assertFalse(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_DELAY_MENU, scope))
        assertFalse(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_ABORT, scope))
    }

    @Test
    fun `gesture condition branch hides touch-session actions and queue-only actions`() {
        val scope = ActionPickerScope(ActionEntryPoint.GESTURE).enterConditionBranch()

        assertTrue(ActionVisibilityPolicy.isVisible("none", scope))
        assertTrue(ActionVisibilityPolicy.isVisible("multi_action", scope))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_REPEAT_MENU, scope))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_STOP_ALL, scope))

        assertFalse(ActionVisibilityPolicy.isVisible(AppConfig.PIE_ACTION, scope))
        assertFalse(ActionVisibilityPolicy.isVisible(AppConfig.SUB_GESTURE_ACTION, scope))
        assertFalse(ActionVisibilityPolicy.isVisible(AppConfig.GESTURE_POINTER_ACTION, scope))
        assertFalse(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_DELAY_MENU, scope))
        assertFalse(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_ABORT, scope))
    }

    @Test
    fun `key, pie, custom panel, side bar, and sub-gesture hide touch-only actions and enforce self-recursion guards`() {
        val keyScope = ActionPickerScope(ActionEntryPoint.KEY)
        assertFalse(ActionVisibilityPolicy.isVisible(AppConfig.PIE_ACTION, keyScope))
        assertFalse(ActionVisibilityPolicy.isVisible(AppConfig.SUB_GESTURE_ACTION, keyScope))
        assertFalse(ActionVisibilityPolicy.isVisible(AppConfig.GESTURE_POINTER_ACTION, keyScope))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_REPEAT_MENU, keyScope))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_STOP_ALL, keyScope))
        assertFalse(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_DELAY_MENU, keyScope))
        assertFalse(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_ABORT, keyScope))

        val panelScope = ActionPickerScope(ActionEntryPoint.CUSTOM_PANEL_SLOT)
        assertFalse(ActionVisibilityPolicy.isVisible(AppConfig.CUSTOM_PANEL_ACTION, panelScope))
        assertTrue(ActionVisibilityPolicy.isVisible(AppConfig.SIDE_BAR_LEFT_ACTION, panelScope))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_STOP_ALL, panelScope))

        val sideBarScope = ActionPickerScope(ActionEntryPoint.SIDE_BAR_SLOT)
        assertFalse(ActionVisibilityPolicy.isVisible(AppConfig.SIDE_BAR_LEFT_ACTION, sideBarScope))
        assertFalse(ActionVisibilityPolicy.isVisible(AppConfig.SIDE_BAR_RIGHT_ACTION, sideBarScope))
        assertTrue(ActionVisibilityPolicy.isVisible(AppConfig.CUSTOM_PANEL_ACTION, sideBarScope))

        val subGestureScope = ActionPickerScope(ActionEntryPoint.GESTURE).enterSubGesture()
        assertFalse(ActionVisibilityPolicy.isVisible(AppConfig.SUB_GESTURE_ACTION, subGestureScope))
        assertFalse(ActionVisibilityPolicy.isVisible(AppConfig.PIE_ACTION, subGestureScope))
        assertFalse(ActionVisibilityPolicy.isVisible(AppConfig.GESTURE_POINTER_ACTION, subGestureScope))
    }

    @Test
    fun `multi-action editor shows all 4 flow controls and excludes multi_action and none`() {
        val scope = ActionPickerScope(ActionEntryPoint.MULTI_ACTION_EDITOR)

        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_DELAY_MENU, scope))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_REPEAT_MENU, scope))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_ABORT, scope))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_STOP_ALL, scope))
        assertTrue(ActionVisibilityPolicy.isVisible("condition", scope))

        assertFalse(ActionVisibilityPolicy.isVisible("multi_action", scope))
        assertFalse(ActionVisibilityPolicy.isVisible("none", scope))
        assertFalse(ActionVisibilityPolicy.isVisible(AppConfig.PIE_ACTION, scope))
    }

    @Test
    fun `condition branch inside multi-action allows none, delay, abort and still excludes multi_action`() {
        val scope = ActionPickerScope(ActionEntryPoint.MULTI_ACTION_EDITOR).enterConditionBranch()

        assertTrue(ActionVisibilityPolicy.isVisible("none", scope))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_DELAY_MENU, scope))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_ABORT, scope))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_REPEAT_MENU, scope))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_STOP_ALL, scope))
        assertFalse(ActionVisibilityPolicy.isVisible("multi_action", scope))
    }

    @Test
    fun `repeat direct child excludes delay abort none and condition branch enables delay and abort`() {
        val topLevelRepeat = ActionPickerScope(ActionEntryPoint.GESTURE).enterRepeatChild()
        assertFalse(ActionVisibilityPolicy.isVisible("none", topLevelRepeat))
        assertFalse(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_DELAY_MENU, topLevelRepeat))
        assertFalse(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_ABORT, topLevelRepeat))
        assertTrue(ActionVisibilityPolicy.isVisible("multi_action", topLevelRepeat))
        assertTrue(ActionVisibilityPolicy.isVisible("condition", topLevelRepeat))

        val repeatConditionBranch = topLevelRepeat.enterConditionBranch()
        assertTrue(ActionVisibilityPolicy.isVisible("none", repeatConditionBranch))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_DELAY_MENU, repeatConditionBranch))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_ABORT, repeatConditionBranch))
        assertTrue(ActionVisibilityPolicy.isVisible("multi_action", repeatConditionBranch))

        val multiRepeat = ActionPickerScope(ActionEntryPoint.MULTI_ACTION_EDITOR).enterRepeatChild()
        assertFalse(ActionVisibilityPolicy.isVisible("multi_action", multiRepeat))
        assertFalse(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_DELAY_MENU, multiRepeat))
        assertFalse(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_ABORT, multiRepeat))

        val multiRepeatCond = multiRepeat.enterConditionBranch()
        assertFalse(ActionVisibilityPolicy.isVisible("multi_action", multiRepeatCond))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_DELAY_MENU, multiRepeatCond))
        assertTrue(ActionVisibilityPolicy.isVisible(ActionFlowStore.ACTION_ABORT, multiRepeatCond))
    }

    @Test
    fun `excludedCodes matches isVisible for all representative action codes`() {
        val sampleCodes = listOf(
            "none", "back", "multi_action", "condition",
            ActionFlowStore.ACTION_DELAY_MENU,
            ActionFlowStore.ACTION_REPEAT_MENU,
            ActionFlowStore.ACTION_ABORT,
            ActionFlowStore.ACTION_STOP_ALL,
            AppConfig.PIE_ACTION,
            AppConfig.SUB_GESTURE_ACTION,
            AppConfig.GESTURE_POINTER_ACTION,
            AppConfig.CUSTOM_PANEL_ACTION,
            AppConfig.SIDE_BAR_LEFT_ACTION,
            AppConfig.SIDE_BAR_RIGHT_ACTION,
        )
        val scopes = listOf(
            ActionPickerScope(ActionEntryPoint.GESTURE),
            ActionPickerScope(ActionEntryPoint.GESTURE).enterConditionBranch(),
            ActionPickerScope(ActionEntryPoint.GESTURE).enterRepeatChild(),
            ActionPickerScope(ActionEntryPoint.GESTURE).enterRepeatChild().enterConditionBranch(),
            ActionPickerScope(ActionEntryPoint.GESTURE).enterSubGesture(),
            ActionPickerScope(ActionEntryPoint.KEY),
            ActionPickerScope(ActionEntryPoint.PIE_SLOT),
            ActionPickerScope(ActionEntryPoint.CUSTOM_PANEL_SLOT),
            ActionPickerScope(ActionEntryPoint.SIDE_BAR_SLOT),
            ActionPickerScope(ActionEntryPoint.MULTI_ACTION_EDITOR),
            ActionPickerScope(ActionEntryPoint.MULTI_ACTION_EDITOR).enterConditionBranch(),
            ActionPickerScope(ActionEntryPoint.MULTI_ACTION_EDITOR).enterRepeatChild(),
        )
        scopes.forEach { scope ->
            val excluded = ActionVisibilityPolicy.excludedCodes(scope)
            sampleCodes.forEach { code ->
                assertTrue(
                    "Mismatch for code=$code in scope=$scope",
                    ActionVisibilityPolicy.isVisible(code, scope) == (code !in excluded),
                )
            }
        }
    }
}
