package com.fan.edgex.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerConditionPolicyTest {

    @Test
    fun parseTriggerEnabledDefaultsToTrueUnlessExplicitlyFalse() {
        assertTrue(TriggerConditionPolicy.parseTriggerEnabled(null))
        assertTrue(TriggerConditionPolicy.parseTriggerEnabled(""))
        assertTrue(TriggerConditionPolicy.parseTriggerEnabled("true"))
        assertFalse(TriggerConditionPolicy.parseTriggerEnabled("false"))
    }

    @Test
    fun gestureTriggerAllowsUnlockedAndGatesLockscreen() {
        assertTrue(TriggerConditionPolicy.shouldTriggerGesture(isKeyguardLocked = false, allowLocked = true))
        assertTrue(TriggerConditionPolicy.shouldTriggerGesture(isKeyguardLocked = false, allowLocked = false))
        assertTrue(TriggerConditionPolicy.shouldTriggerGesture(isKeyguardLocked = true, allowLocked = true))
        assertFalse(TriggerConditionPolicy.shouldTriggerGesture(isKeyguardLocked = true, allowLocked = false))
    }

    @Test
    fun keyTriggerScreenOffImpliesLockscreenAndUnlockedAlwaysAllowed() {
        // Screen off depends only on allowScreenOff
        assertTrue(
            TriggerConditionPolicy.shouldTriggerKey(
                isScreenInteractive = false,
                isKeyguardLocked = true,
                allowScreenOff = true,
                allowLocked = true,
            ),
        )
        assertTrue(
            TriggerConditionPolicy.shouldTriggerKey(
                isScreenInteractive = false,
                isKeyguardLocked = true,
                allowScreenOff = true,
                allowLocked = false,
            ),
        )
        assertFalse(
            TriggerConditionPolicy.shouldTriggerKey(
                isScreenInteractive = false,
                isKeyguardLocked = true,
                allowScreenOff = false,
                allowLocked = true,
            ),
        )

        // Screen on + locked: allowed if allowLocked OR allowScreenOff (screenOff implies locked)
        assertTrue(
            TriggerConditionPolicy.shouldTriggerKey(
                isScreenInteractive = true,
                isKeyguardLocked = true,
                allowScreenOff = true,
                allowLocked = false,
            ),
        )
        assertTrue(
            TriggerConditionPolicy.shouldTriggerKey(
                isScreenInteractive = true,
                isKeyguardLocked = true,
                allowScreenOff = false,
                allowLocked = true,
            ),
        )
        assertFalse(
            TriggerConditionPolicy.shouldTriggerKey(
                isScreenInteractive = true,
                isKeyguardLocked = true,
                allowScreenOff = false,
                allowLocked = false,
            ),
        )

        // Screen on + unlocked: always allowed when keys are enabled
        assertTrue(
            TriggerConditionPolicy.shouldTriggerKey(
                isScreenInteractive = true,
                isKeyguardLocked = false,
                allowScreenOff = false,
                allowLocked = false,
            ),
        )
    }

    @Test
    fun keyPathOwnershipSurvivesWakeBetweenDownAndUp() {
        // Screen-off DOWN claimed by QUEUEING
        assertTrue(
            TriggerConditionPolicy.canAcceptKeyEvent(
                source = KeyInterceptionPath.QUEUEING,
                activePath = null,
                isScreenInteractive = false,
                isKeyguardLocked = true,
                allowScreenOff = true,
                allowLocked = true,
            ),
        )
        // Screen wakes up before UP -> QUEUEING still owns UP
        assertTrue(
            TriggerConditionPolicy.canAcceptKeyEvent(
                source = KeyInterceptionPath.QUEUEING,
                activePath = KeyInterceptionPath.QUEUEING,
                isScreenInteractive = true,
                isKeyguardLocked = true,
                allowScreenOff = true,
                allowLocked = true,
            ),
        )
        // DISPATCH must not steal a press owned by QUEUEING
        assertFalse(
            TriggerConditionPolicy.canAcceptKeyEvent(
                source = KeyInterceptionPath.DISPATCH,
                activePath = KeyInterceptionPath.QUEUEING,
                isScreenInteractive = true,
                isKeyguardLocked = false,
                allowScreenOff = true,
                allowLocked = true,
            ),
        )
        // Fresh screen-on press is NOT claimed by QUEUEING; it goes to DISPATCH
        assertFalse(
            TriggerConditionPolicy.canAcceptKeyEvent(
                source = KeyInterceptionPath.QUEUEING,
                activePath = null,
                isScreenInteractive = true,
                isKeyguardLocked = false,
                allowScreenOff = true,
                allowLocked = true,
            ),
        )
        assertTrue(
            TriggerConditionPolicy.canAcceptKeyEvent(
                source = KeyInterceptionPath.DISPATCH,
                activePath = null,
                isScreenInteractive = true,
                isKeyguardLocked = false,
                allowScreenOff = false,
                allowLocked = false,
            ),
        )
    }
}
