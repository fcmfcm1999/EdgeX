package com.fan.edgex.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockscreenActionPolicyTest {
    @Test
    fun immediateActionsStayAvailableOnLockscreen() {
        assertFalse(LockscreenActionPolicy.requiresUnlock("lock_screen"))
        assertFalse(LockscreenActionPolicy.requiresUnlock("toggle_flashlight"))
        assertFalse(LockscreenActionPolicy.requiresUnlock("app_shortcut:com.example:id"))
        assertFalse(LockscreenActionPolicy.requiresUnlock("screenshot"))
        assertFalse(LockscreenActionPolicy.requiresUnlock("music_control:play_pause"))
        assertFalse(LockscreenActionPolicy.requiresUnlock("multi_action:123"))
        assertFalse(LockscreenActionPolicy.requiresUnlock("condition:123"))
    }

    @Test
    fun foregroundActionsWaitForUnlock() {
        assertTrue(LockscreenActionPolicy.requiresUnlock("home"))
        assertTrue(LockscreenActionPolicy.requiresUnlock("recents"))
        assertTrue(LockscreenActionPolicy.requiresUnlock("launch_app:com.example"))
        assertTrue(LockscreenActionPolicy.requiresUnlock("prev_app"))
        assertTrue(LockscreenActionPolicy.requiresUnlock("fast_scroll:to_top"))
        assertTrue(LockscreenActionPolicy.requiresUnlock("clipboard"))
    }
}
