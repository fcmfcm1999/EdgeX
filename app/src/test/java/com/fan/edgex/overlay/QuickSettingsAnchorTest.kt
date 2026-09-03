package com.fan.edgex.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSettingsAnchorTest {
    @Test
    fun zoneWinsOverTouchCoordinates() {
        assertEquals(
            QuickSettingsAnchor(QuickSettingsHorizontalAnchor.END, QuickSettingsVerticalAnchor.TOP),
            QuickSettingsAnchor.resolve("right_top", 10f, 900f, 1000f, 1000f),
        )
        assertEquals(
            QuickSettingsAnchor(QuickSettingsHorizontalAnchor.START, QuickSettingsVerticalAnchor.BOTTOM),
            QuickSettingsAnchor.resolve("bottom_left", 900f, 10f, 1000f, 1000f),
        )
    }

    @Test
    fun fullEdgesAnchorToTheirCenter() {
        assertEquals(
            QuickSettingsAnchor(QuickSettingsHorizontalAnchor.START, QuickSettingsVerticalAnchor.CENTER),
            QuickSettingsAnchor.resolve("left", 0f, 0f, 1000f, 1000f),
        )
        assertEquals(
            QuickSettingsAnchor(QuickSettingsHorizontalAnchor.CENTER, QuickSettingsVerticalAnchor.TOP),
            QuickSettingsAnchor.resolve("top", 0f, 0f, 1000f, 1000f),
        )
    }

    @Test
    fun missingZoneFallsBackToTouchThirds() {
        assertEquals(
            QuickSettingsAnchor(QuickSettingsHorizontalAnchor.START, QuickSettingsVerticalAnchor.BOTTOM),
            QuickSettingsAnchor.resolve(null, 100f, 900f, 1000f, 1000f),
        )
    }

    @Test
    fun keyInvocationDefaultsToTopEnd() {
        assertEquals(
            QuickSettingsAnchor(QuickSettingsHorizontalAnchor.END, QuickSettingsVerticalAnchor.TOP),
            QuickSettingsAnchor.resolve(null, 0f, 0f, 1000f, 1000f),
        )
    }
}
