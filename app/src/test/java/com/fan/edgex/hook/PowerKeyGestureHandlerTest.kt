package com.fan.edgex.hook

import org.junit.Assert.*
import org.junit.Test

class PowerKeyGestureHandlerTest {
    private class Clock : PowerKeyGestureHandler.Scheduler {
        var time = 0L
        val tasks = mutableMapOf<Runnable, Long>()
        override fun now() = time
        override fun postDelayed(task: Runnable, delayMillis: Long) { tasks[task] = time + delayMillis }
        override fun removeCallbacks(task: Runnable) { tasks.remove(task) }
        fun advance(to: Long) {
            while (tasks.values.any { it <= to }) {
                val next = tasks.minBy { it.value }
                time = next.value
                tasks.remove(next.key)
                next.key.run()
            }
            time = to
        }
    }

    private class Fixture(window: Long = 300L) {
        val clock = Clock()
        val forwarded = mutableListOf<String>()
        val actions = mutableListOf<String>()
        var config = PowerKeyGestureHandler.Config(enabled = true, doubleClick = "wallet")
        val detector = PowerKeyGestureHandler<String>(clock, forwarded::add, actions::add, 500, window)
        fun down(time: Long, repeat: Int = 0, downTime: Long = time): Boolean {
            clock.time = time
            return detector.handle(PowerKeyGestureHandler.Event("down:$time:$repeat", downTime, time, true, repeat), config)
        }
        fun up(time: Long, downTime: Long = 0, canceled: Boolean = false): Boolean {
            clock.time = time
            return detector.handle(PowerKeyGestureHandler.Event("up:$time", downTime, time, false, canceled = canceled), config)
        }
    }

    @Test fun doublePressRunsWalletOnceWithoutNativeLock() {
        val f = Fixture()
        assertTrue(f.down(0)); assertTrue(f.up(50))
        assertTrue(f.down(150)); assertTrue(f.up(200, 150))
        f.clock.advance(2000)
        assertEquals(listOf("wallet"), f.actions)
        assertTrue(f.forwarded.isEmpty())
        assertTrue(f.clock.tasks.isEmpty())
    }

    @Test fun unmappedSingleReplaysExactlyOneBalancedPairAfterWindow() {
        val f = Fixture()
        f.down(0); f.up(50)
        f.clock.advance(349)
        assertTrue(f.forwarded.isEmpty())
        f.clock.advance(350)
        assertEquals(listOf("down:0:0", "up:50"), f.forwarded)
        assertTrue(f.actions.isEmpty())
        f.clock.advance(2000)
        assertEquals(2, f.forwarded.size)
    }

    @Test fun disabledKeysPassThrough() {
        val f = Fixture()
        f.config = f.config.copy(enabled = false)
        assertFalse(f.down(0)); assertFalse(f.up(50))
        assertTrue(f.clock.tasks.isEmpty())
    }

    @Test fun emptyAndNoneMappingsPassThrough() {
        val f = Fixture()
        for (action in listOf("", "none")) {
            f.config = PowerKeyGestureHandler.Config(true, action, action, action)
            assertFalse(f.down(0)); assertFalse(f.up(50))
        }
    }

    @Test fun orphanRepeatAndReleasePassThrough() {
        val f = Fixture()
        assertFalse(f.down(0, repeat = 1)); assertFalse(f.up(50))
        assertTrue(f.clock.tasks.isEmpty())
    }

    @Test fun clickMappingRunsImmediatelyWithoutDoubleMapping() {
        val f = Fixture()
        f.config = PowerKeyGestureHandler.Config(true, click = "screenshot")
        f.down(0); f.up(50)
        assertEquals(listOf("screenshot"), f.actions)
        assertTrue(f.forwarded.isEmpty())
        assertTrue(f.clock.tasks.isEmpty())
    }

    @Test fun mappedSingleWaitsWhenDoubleMappingExists() {
        val f = Fixture()
        f.config = f.config.copy(click = "lock")
        f.down(0); f.up(50)
        assertTrue(f.actions.isEmpty())
        f.clock.advance(350)
        assertEquals(listOf("lock"), f.actions)
        assertTrue(f.forwarded.isEmpty())
    }

    @Test fun longHoldWithNoMappingForwardsDownWhileStillHeld() {
        val f = Fixture()
        f.down(0)
        f.clock.advance(499)
        assertTrue(f.forwarded.isEmpty())
        f.clock.advance(500)
        assertEquals(listOf("down:0:0"), f.forwarded)
        assertTrue(f.down(600, repeat = 1, downTime = 0))
        assertTrue(f.up(900))
        assertEquals(listOf("down:0:0", "down:600:1", "up:900"), f.forwarded)
        assertTrue(f.actions.isEmpty())
    }

    @Test fun mappedLongHoldRunsOnceAndNeverLocksOnRelease() {
        val f = Fixture()
        f.config = f.config.copy(longPress = "assistant")
        f.down(0); f.clock.advance(500)
        f.down(600, repeat = 1, downTime = 0); f.up(900)
        f.clock.advance(2000)
        assertEquals(listOf("assistant"), f.actions)
        assertTrue(f.forwarded.isEmpty())
    }

    @Test fun heldReleaseResolvesEvenIfMainLooperTimeoutDidNotRun() {
        val f = Fixture()
        f.down(0); f.up(800)
        assertEquals(listOf("down:0:0", "up:800"), f.forwarded)
        f.clock.advance(2000)
        assertEquals(2, f.forwarded.size)
        assertTrue(f.actions.isEmpty())
    }

    @Test fun heldReleaseRunsMappedLongInsteadOfClickOnStalledLooper() {
        val f = Fixture()
        f.config = f.config.copy(click = "lock", longPress = "assistant")
        f.down(0); f.up(800)
        assertEquals(listOf("assistant"), f.actions)
        assertTrue(f.forwarded.isEmpty())
        assertTrue(f.clock.tasks.isEmpty())
    }

    @Test fun exactDoubleDeadlineIsNotDoubleEvenWhenTimerHasNotRun() {
        val f = Fixture()
        f.down(0); f.up(50); f.down(350); f.up(400, 350)
        assertTrue(f.actions.isEmpty())
        assertEquals(listOf("down:0:0", "up:50"), f.forwarded)
        f.clock.advance(700)
        assertEquals(listOf("down:0:0", "up:50", "down:350:0", "up:400"), f.forwarded)
    }

    @Test fun justBeforeDeadlineIsDouble() {
        val f = Fixture()
        f.down(0); f.up(50); f.down(349); f.up(400, 349)
        assertEquals(listOf("wallet"), f.actions)
        assertTrue(f.forwarded.isEmpty())
    }

    @Test fun repeatDoesNotStartSecondPressOrExecuteExtraAction() {
        val f = Fixture()
        f.down(0); f.down(25, repeat = 1, downTime = 0); f.up(50)
        f.down(100); f.down(125, repeat = 1, downTime = 100); f.up(150, 100)
        assertEquals(listOf("wallet"), f.actions)
        assertTrue(f.forwarded.isEmpty())
    }

    @Test fun configChangeDoesNotStrandAnOwnedUpOrChangeCurrentAction() {
        val f = Fixture()
        f.down(0); f.up(50)
        f.config = PowerKeyGestureHandler.Config(enabled = false)
        assertTrue(f.down(100)); assertTrue(f.up(150, 100))
        assertEquals(listOf("wallet"), f.actions)
        assertFalse(f.down(200)); assertFalse(f.up(250, 200))
    }

    @Test fun gameModeOrDisableDuringNativeHoldStillForwardsRelease() {
        val f = Fixture()
        f.down(0); f.clock.advance(500)
        f.config = f.config.copy(enabled = false)
        assertTrue(f.up(900))
        assertEquals(listOf("down:0:0", "up:900"), f.forwarded)
        assertFalse(f.down(1000))
    }

    @Test fun canceledPendingPressDoesNotBecomeSingleOrDouble() {
        val f = Fixture()
        f.down(0); f.up(50, canceled = true); f.clock.advance(1000)
        assertTrue(f.forwarded.isEmpty()); assertTrue(f.actions.isEmpty())
        assertTrue(f.clock.tasks.isEmpty())
        assertTrue(f.down(1100)); assertTrue(f.up(1150, 1100))
        f.clock.advance(1450)
        assertEquals(listOf("down:1100:0", "up:1150"), f.forwarded)
    }

    @Test fun canceledNativeReleaseIsStillForwarded() {
        val f = Fixture()
        f.down(0); f.clock.advance(500); f.up(600, canceled = true)
        assertEquals(listOf("down:0:0", "up:600"), f.forwarded)
    }

    @Test fun staleCanceledTimeoutCannotAffectNewSequence() {
        val f = Fixture()
        f.down(0)
        val staleLong = f.clock.tasks.keys.single()
        f.up(50)
        val staleDouble = f.clock.tasks.keys.single()
        f.down(100); f.up(150, 100)
        f.down(200)
        staleLong.run(); staleDouble.run()
        assertEquals(listOf("wallet"), f.actions)
        assertTrue(f.forwarded.isEmpty())
        f.up(250, 200); f.clock.advance(550)
        assertEquals(listOf("down:200:0", "up:250"), f.forwarded)
    }

    @Test fun backToBackDoublePressesEachFireOnce() {
        val f = Fixture()
        for (base in listOf(0L, 300L)) {
            f.down(base); f.up(base + 50, base)
            f.down(base + 100); f.up(base + 150, base + 100)
        }
        f.clock.advance(2000)
        assertEquals(listOf("wallet", "wallet"), f.actions)
        assertTrue(f.forwarded.isEmpty())
    }

    @Test fun nativeHoldTakesPrecedenceOverSingleMapping() {
        val f = Fixture()
        f.config = PowerKeyGestureHandler.Config(true, click = "screenshot")
        f.down(0); f.clock.advance(500); f.up(900)
        assertTrue(f.actions.isEmpty())
        assertEquals(listOf("down:0:0", "up:900"), f.forwarded)
    }

    @Test fun elapsedDownTimeShortensRemainingHoldTimeout() {
        val f = Fixture()
        f.down(200, downTime = 0)
        f.clock.advance(499); assertTrue(f.forwarded.isEmpty())
        f.clock.advance(500); assertEquals(listOf("down:200:0"), f.forwarded)
    }

    @Test fun nativeChordHandoffForwardsPowerDownBeforeVolumePolicyAndBalancesUp() {
        val f = Fixture()
        f.down(0)
        f.detector.handoffToNative()
        assertEquals(listOf("down:0:0"), f.forwarded)
        assertTrue(f.clock.tasks.isEmpty())
        assertTrue(f.up(200))
        assertEquals(listOf("down:0:0", "up:200"), f.forwarded)
        assertTrue(f.actions.isEmpty())
    }

    @Test fun nativeChordOverridesMappedLongPressWhileHeld() {
        val f = Fixture()
        f.config = f.config.copy(longPress = "assistant")
        f.down(0); f.detector.handoffToNative(); f.clock.advance(500); f.up(600)
        assertEquals(listOf("down:0:0", "up:600"), f.forwarded)
        assertTrue(f.actions.isEmpty())
    }

    @Test fun volumeAfterPowerWasReleasedDoesNotFlushWaitingDoublePress() {
        val f = Fixture()
        f.down(0); f.up(50); f.detector.handoffToNative()
        assertTrue(f.forwarded.isEmpty())
        f.down(100); f.up(150, 100)
        assertEquals(listOf("wallet"), f.actions)
    }

    @Test fun repeatedNativeHandoffDoesNotReplayDownTwice() {
        val f = Fixture()
        f.detector.handoffToNative()
        f.down(0); f.detector.handoffToNative(); f.detector.handoffToNative(); f.up(200)
        assertEquals(listOf("down:0:0", "up:200"), f.forwarded)
    }


    @Test fun productionPowerWindowIs200Milliseconds() {
        assertEquals(200L, PowerKeyGestureHandler.DOUBLE_TAP_TIMEOUT_MS)
    }

    @Test fun productionSingleFallbackRuns200MillisecondsAfterRelease() {
        val f = Fixture(PowerKeyGestureHandler.DOUBLE_TAP_TIMEOUT_MS)
        f.down(0); f.up(50)
        f.clock.advance(249)
        assertTrue(f.forwarded.isEmpty())
        f.clock.advance(250)
        assertEquals(listOf("down:0:0", "up:50"), f.forwarded)
    }

    @Test fun productionDoublePressAccepts199MillisecondsButNot200() {
        val within = Fixture(PowerKeyGestureHandler.DOUBLE_TAP_TIMEOUT_MS)
        within.down(0); within.up(50); within.down(249); within.up(280, 249)
        assertEquals(listOf("wallet"), within.actions)
        assertTrue(within.forwarded.isEmpty())
        val boundary = Fixture(PowerKeyGestureHandler.DOUBLE_TAP_TIMEOUT_MS)
        boundary.down(0); boundary.up(50); boundary.down(250); boundary.up(280, 250)
        assertTrue(boundary.actions.isEmpty())
        assertEquals(listOf("down:0:0", "up:50"), boundary.forwarded)
    }

}
