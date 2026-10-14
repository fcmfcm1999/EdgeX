package com.fan.edgex.hook

import org.junit.Assert.*
import org.junit.Test

class HardwareKeyDetectorTest {
    private val detector = HardwareKeyDetector()
    private fun press(code: Int = 1082, device: Int = 1, down: Boolean = true,
        repeat: Int = 0, canceled: Boolean = false, deadline: Long = 31_000L, now: Long = 1_000L) =
        detector.handle(code, device, down, repeat, canceled, deadline, now)

    @Test fun capturesVendorButtonOnlyAfterCompletePressAndOnlyOnce() {
        assertEquals(HardwareKeyDetector.Result(true), press())
        assertEquals(HardwareKeyDetector.Result(true), press(repeat = 1))
        assertFalse(press(code = 24).consumed)
        assertFalse(press(device = 2, down = false).consumed)
        assertEquals(HardwareKeyDetector.Result(true, 1082, 31_000L), press(down = false))
        assertFalse(press().consumed)
        assertTrue(press(deadline = 32_000L, now = 2_000L).consumed)
    }

    @Test fun ignoresVirtualUnknownUnpairedAndInactiveEvents() {
        assertFalse(press(device = -1).consumed)
        assertFalse(press(code = 0).consumed)
        assertFalse(press(down = false).consumed)
        assertFalse(press(repeat = 1).consumed)
        assertFalse(press(deadline = 0L).consumed)
        assertFalse(press(deadline = 32_000L).consumed)
        assertFalse(press(now = 31_001L).consumed)
    }

    @Test fun expiredSessionConsumesPairedReleaseWithoutSaving() {
        press()
        assertEquals(HardwareKeyDetector.Result(true), press(down = false, now = 31_001L))
        assertFalse(press(now = 31_002L).consumed)
    }

    @Test fun dismissalDoesNotLeakReleaseOrSaveKey() {
        press()
        assertEquals(HardwareKeyDetector.Result(true), press(down = false, deadline = 0L))
    }

    @Test fun canceledReleaseDoesNotSaveKey() {
        press()
        assertEquals(HardwareKeyDetector.Result(true), press(down = false, canceled = true))
        assertFalse(press().consumed)
    }

    @Test fun missingReleaseCannotCaptureButtonForever() {
        press()
        assertFalse(press(now = 41_001L).consumed)
    }
}
