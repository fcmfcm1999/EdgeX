package com.fan.edgex.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigContractTest {
    @Test
    fun edgeZonesStayCompatibleWithHookSnapshotKeys() {
        assertEquals(16, AppConfig.ZONES.size)
        assertEquals("left", AppConfig.fallbackEdgeZone("left_mid"))
        assertEquals("right", AppConfig.fallbackEdgeZone("right_bottom"))
        assertEquals("top", AppConfig.fallbackEdgeZone("top_mid"))
        assertEquals("bottom", AppConfig.fallbackEdgeZone("bottom_right"))
    }

    @Test
    fun uiThemeKeysAreUiOnlyPreferences() {
        assertEquals("ui_accent", AppConfig.UI_ACCENT)
        assertEquals("ui_dark_mode", AppConfig.UI_DARK_MODE)
        assertEquals("ui_density", AppConfig.UI_DENSITY)
        assertTrue(AppConfig.GESTURES.contains("swipe_left"))
        assertTrue(AppConfig.GESTURES.contains("swipe_right"))
    }

    @Test
    fun actionKeysCanBeMappedBackToRuntimeEnableFlags() {
        assertEquals("right_mid" to "swipe_left", AppConfig.gestureActionParts("right_mid_swipe_left"))
        assertEquals(24 to "double_click", AppConfig.keyActionParts("key_24_double_click"))
        assertTrue(AppConfig.isActiveActionValue("back"))
        assertEquals(null, AppConfig.gestureActionParts("right_mid_swipe_left_label"))
        assertEquals(null, AppConfig.keyActionParts("key_24_double_click_label"))
    }
}
