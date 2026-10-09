package com.fan.edgex.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessLevelConverterTest {
    @Test
    fun userLevelsRoundTripThroughDisplayBrightness() {
        listOf(0f, 0.1f, 0.5f, 0.9f, 1f).forEach { level ->
            val brightness = BrightnessLevelConverter.toDisplayBrightness(level, 0.02f, 1f)

            assertEquals(level, BrightnessLevelConverter.toUserLevel(brightness, 0.02f, 1f), 0.0001f)
        }
    }

    @Test
    fun actionAdjustmentUsesSmallConsistentUserLevelStep() {
        val initialLevel = 0.4f
        val current = BrightnessLevelConverter.toDisplayBrightness(initialLevel, 0f, 1f)
        val adjusted = BrightnessLevelConverter.adjust(current, 0f, 1f, increase = true)

        assertEquals(
            initialLevel + BrightnessLevelConverter.ACTION_STEP,
            BrightnessLevelConverter.toUserLevel(adjusted, 0f, 1f),
            0.0001f,
        )
        assertTrue(adjusted > current)
    }

    @Test
    fun continuousAdjustmentUsesTheExactUserLevelDelta() {
        val current = BrightnessLevelConverter.toDisplayBrightness(0.4f, 0f, 1f)
        val adjusted = BrightnessLevelConverter.adjustByUserLevel(current, 0f, 1f, 0.015f)

        assertEquals(0.415f, BrightnessLevelConverter.toUserLevel(adjusted, 0f, 1f), 0.0001f)
    }
}
