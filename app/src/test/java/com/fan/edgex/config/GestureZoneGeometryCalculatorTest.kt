package com.fan.edgex.config

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureZoneGeometryCalculatorTest {

    @Test
    fun testClampingDefaults() {
        val calculator = GestureZoneGeometryCalculator { _, def -> def }
        
        // Default splits
        val splits = calculator.getSplits("left")
        assertEquals(33, splits.first)
        assertEquals(66, splits.second)

        // Default thickness
        val thickness = calculator.getThicknessDp("left_top")
        assertEquals(16, thickness)
        
        // Fallback zone thickness should be 16
        val fallbackThickness = calculator.getThicknessDp("left")
        assertEquals(16, fallbackThickness)
    }

    @Test
    fun testThicknessClamping() {
        // Underflow thickness (less than 8)
        val calcUnder = GestureZoneGeometryCalculator { _, _ -> "2" }
        assertEquals(8, calcUnder.getThicknessDp("left_top"))

        // Overflow thickness (greater than 32)
        val calcOver = GestureZoneGeometryCalculator { _, _ -> "40" }
        assertEquals(32, calcOver.getThicknessDp("left_top"))

        // Within range
        val calcOk = GestureZoneGeometryCalculator { _, _ -> "15" }
        assertEquals(15, calcOk.getThicknessDp("left_top"))
    }

    @Test
    fun testSplitAdjustments() {
        // Adjust first split: if it pushes close to second, it pushes second split
        val (f1, s1) = GestureZoneGeometryCalculator.adjustFirst(50, 45)
        assertEquals(50, f1)
        assertEquals(60, s1) // second is pushed to first + 10

        // If first is adjusted within limits without pushing second
        val (f2, s2) = GestureZoneGeometryCalculator.adjustFirst(20, 66)
        assertEquals(20, f2)
        assertEquals(66, s2)

        // Adjust second split: if it pushes close to first, it pushes first split down
        val (f3, s3) = GestureZoneGeometryCalculator.adjustSecond(45, 40)
        assertEquals(30, f3) // first is pushed down to second - 10
        assertEquals(40, s3)
    }

    @Test
    fun testSegmentResolution() {
        // Segment resolution along the edge
        // With splits (33, 66)
        assertEquals(0, GestureZoneGeometryCalculator.resolveSegment(10f, 100f, 33, 66))
        assertEquals(1, GestureZoneGeometryCalculator.resolveSegment(50f, 100f, 33, 66))
        assertEquals(2, GestureZoneGeometryCalculator.resolveSegment(90f, 100f, 33, 66))

        // With offset (avoiding corners)
        // startOffset = 10, endOffset = 90 (active length = 80)
        // splits (33, 66): p1 = 10 + 80 * 0.33 = 36.4, p2 = 10 + 80 * 0.66 = 62.8
        assertEquals(0, GestureZoneGeometryCalculator.resolveSegmentWithOffset(30f, 10f, 90f, 33, 66))
        assertEquals(1, GestureZoneGeometryCalculator.resolveSegmentWithOffset(50f, 10f, 90f, 33, 66))
        assertEquals(2, GestureZoneGeometryCalculator.resolveSegmentWithOffset(70f, 10f, 90f, 33, 66))
    }

    @Test
    fun testAdjustMiddleHeight() {
        // Normal adjustment within bounds, no shifting needed
        // Midpoint of 33 and 66 is 49.5. New height is 40.
        // f = 49.5 - 20 = 29.5 -> rounded to 30. s = 30 + 40 = 70.
        // Wait, let's double check round(49.5 - 20) = round(29.5) = 30.0 -> 30.
        val (f1, s1) = GestureZoneGeometryCalculator.adjustMiddleHeight(40, 33, 66)
        assertEquals(30, f1)
        assertEquals(70, s1)

        // Shifting left when hitting the right boundary (MAX_PERCENT = 90)
        // Midpoint is 70. New height is 50.
        // f = 70 - 25 = 45. s = 45 + 50 = 95. Since 95 > 90:
        // s = 90, f = 90 - 50 = 40.
        val (f2, s2) = GestureZoneGeometryCalculator.adjustMiddleHeight(50, 60, 80)
        assertEquals(40, f2)
        assertEquals(90, s2)

        // Shifting right when hitting the left boundary (MIN_PERCENT = 10)
        // Midpoint is 25. New height is 40.
        // f = 25 - 20 = 5. Since 5 < 10:
        // f = 10, s = 10 + 40 = 50.
        val (f3, s3) = GestureZoneGeometryCalculator.adjustMiddleHeight(40, 20, 30)
        assertEquals(10, f3)
        assertEquals(50, s3)
    }
}
