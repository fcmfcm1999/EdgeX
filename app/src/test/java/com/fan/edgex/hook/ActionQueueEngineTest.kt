package com.fan.edgex.hook

import com.fan.edgex.config.ActionFlowStore
import com.fan.edgex.config.ConditionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionQueueEngineTest {

    private class FakeClock(var nowMs: Long = 1000L) : ActionClock {
        override fun uptimeMillis(): Long = nowMs
    }

    private data class ScheduledTask(
        val runAtMs: Long,
        val isLong: Boolean,
        val task: () -> Unit,
        var cancelled: Boolean = false,
    )

    private class FakeScheduler(private val clock: FakeClock) : ActionQueueScheduler {
        val tasks = mutableListOf<ScheduledTask>()

        override fun scheduleShort(delayMs: Long, task: () -> Unit): ActionCancellable {
            val entry = ScheduledTask(clock.nowMs + delayMs, isLong = false, task = task)
            tasks.add(entry)
            return object : ActionCancellable {
                override fun cancel() {
                    entry.cancelled = true
                }
            }
        }

        override fun scheduleLong(delayMs: Long, task: () -> Unit): ActionCancellable {
            val entry = ScheduledTask(clock.nowMs + delayMs, isLong = true, task = task)
            tasks.add(entry)
            return object : ActionCancellable {
                override fun cancel() {
                    entry.cancelled = true
                }
            }
        }

        fun runNextDue(): Boolean {
            val next = tasks.filter { !it.cancelled }.minByOrNull { it.runAtMs } ?: return false
            tasks.remove(next)
            clock.nowMs = maxOf(clock.nowMs, next.runAtMs)
            next.task()
            return true
        }

        fun drainAll(maxSteps: Int = 500) {
            var count = 0
            while (count < maxSteps && runNextDue()) {
                count++
            }
        }
    }

    private class TrackingWakeLock : ActionWakeLock {
        var held = false
        var acquireCount = 0
        var releaseCount = 0

        override fun acquire() {
            held = true
            acquireCount++
        }

        override fun release() {
            held = false
            releaseCount++
        }
    }

    @Test
    fun `executes multi action with explicit delay between steps`() {
        val clock = FakeClock(100L)
        val scheduler = FakeScheduler(clock)
        val wakeLock = TrackingWakeLock()
        val executed = mutableListOf<Pair<Long, String>>()
        val multiActions = mapOf(
            "m1" to listOf("back", "delay:2000", "home"),
        )
        val coordinator = ActionQueueCoordinator(
            clock = clock,
            scheduler = scheduler,
            wakeLockFactory = { wakeLock },
            cursorFactory = ActionCursorFactory(
                resolveConfig = { "" },
                evaluateCondition = { false },
                resolveMultiActionSteps = { multiActions[it].orEmpty() },
            ),
            dispatchLeaf = { executed += clock.nowMs to it },
        )

        coordinator.start("multi_action:m1")
        scheduler.drainAll()

        assertEquals(listOf(100L to "back", 2100L to "home"), executed)
        assertFalse(wakeLock.held)
        assertEquals(0, coordinator.activeRunCount())
    }

    @Test
    fun `repeat re-evaluates condition on every iteration and stops on abort`() {
        val clock = FakeClock(0L)
        val scheduler = FakeScheduler(clock)
        val executed = mutableListOf<String>()
        var counter = 0
        val config = mapOf(
            // repeat 5 times: multi_action m_loop
            ActionFlowStore.repeatCountKey("r1") to "5",
            ActionFlowStore.repeatActionKey("r1") to "multi_action:m_loop",
            // c1: if (counter >= 2) then abort else none
            ConditionStore.condIfKey("c1") to "custom_check",
            ConditionStore.condThenKey("c1") to ActionFlowStore.ACTION_ABORT,
            ConditionStore.condElseKey("c1") to "none",
        )
        val multiActions = mapOf(
            "m_loop" to listOf("condition:c1", "volume_up", "delay:500"),
        )
        val coordinator = ActionQueueCoordinator(
            clock = clock,
            scheduler = scheduler,
            cursorFactory = ActionCursorFactory(
                resolveConfig = { config[it].orEmpty() },
                evaluateCondition = { counter >= 2 },
                resolveMultiActionSteps = { multiActions[it].orEmpty() },
            ),
            dispatchLeaf = { action ->
                executed += action
                if (action == "volume_up") counter++
            },
        )

        coordinator.start("repeat:r1")
        scheduler.drainAll()

        assertEquals(listOf("volume_up", "volume_up"), executed)
        assertEquals(0, coordinator.activeRunCount())
    }

    @Test
    fun `stopAll cancels all currently active delayed queues`() {
        val clock = FakeClock(1000L)
        val scheduler = FakeScheduler(clock)
        val wakeLocks = mutableListOf<TrackingWakeLock>()
        val executed = mutableListOf<String>()
        val multiActions = mapOf(
            "m1" to listOf("a1", "delay:5000", "a2"),
            "m2" to listOf("b1", "delay:5000", "b2"),
        )
        val coordinator = ActionQueueCoordinator(
            clock = clock,
            scheduler = scheduler,
            wakeLockFactory = { TrackingWakeLock().also { wakeLocks += it } },
            cursorFactory = ActionCursorFactory(
                resolveConfig = { "" },
                evaluateCondition = { false },
                resolveMultiActionSteps = { multiActions[it].orEmpty() },
            ),
            dispatchLeaf = { executed += it },
        )

        coordinator.start("multi_action:m1")
        coordinator.start("multi_action:m2")
        assertEquals(listOf("a1", "b1"), executed)
        assertEquals(2, coordinator.activeRunCount())

        clock.nowMs = 2000L
        coordinator.stopAll()
        scheduler.drainAll()

        assertEquals(listOf("a1", "b1"), executed)
        assertEquals(0, coordinator.activeRunCount())
        assertTrue(wakeLocks.all { !it.held })
    }

    @Test
    fun `long delay uses alarm scheduler and releases wakelock while sleeping`() {
        val clock = FakeClock(0L)
        val scheduler = FakeScheduler(clock)
        val wakeLock = TrackingWakeLock()
        val executed = mutableListOf<Pair<Long, String>>()
        val multiActions = mapOf(
            "m1" to listOf("step1", "delay:45000", "step2"),
        )
        val coordinator = ActionQueueCoordinator(
            clock = clock,
            scheduler = scheduler,
            wakeLockFactory = { wakeLock },
            cursorFactory = ActionCursorFactory(
                resolveConfig = { "" },
                evaluateCondition = { false },
                resolveMultiActionSteps = { multiActions[it].orEmpty() },
            ),
            dispatchLeaf = { executed += clock.nowMs to it },
        )

        coordinator.start("multi_action:m1")
        // step1 ran; next is delay:45000 scheduled at 0ms (short), then step2 scheduled at 45000ms (long)
        assertTrue(scheduler.runNextDue()) // runs delay:45000
        assertEquals(1, scheduler.tasks.size)
        assertTrue(scheduler.tasks.first().isLong)
        assertFalse(wakeLock.held) // released while waiting on AlarmManager

        scheduler.drainAll()
        assertEquals(listOf(0L to "step1", 45_000L to "step2"), executed)
        assertFalse(wakeLock.held)
    }

    @Test
    fun `nested multi actions execute inline and guard against cycles`() {
        val clock = FakeClock(0L)
        val scheduler = FakeScheduler(clock)
        val executed = mutableListOf<String>()
        val multiActions = mapOf(
            "outer" to listOf("start", "multi_action:inner", "end"),
            "inner" to listOf("mid1", "mid2"),
            // Cyclic self-reference should terminate safely via depth limit
            "cycle" to listOf("tick", "multi_action:cycle"),
        )
        val coordinator = ActionQueueCoordinator(
            clock = clock,
            scheduler = scheduler,
            cursorFactory = ActionCursorFactory(
                resolveConfig = { "" },
                evaluateCondition = { false },
                resolveMultiActionSteps = { multiActions[it].orEmpty() },
                maxDepth = 3,
            ),
            dispatchLeaf = { executed += it },
        )

        coordinator.start("multi_action:outer")
        scheduler.drainAll()
        assertEquals(listOf("start", "mid1", "mid2", "end"), executed)

        executed.clear()
        coordinator.start("multi_action:cycle")
        scheduler.drainAll()
        // maxDepth = 3 -> tick runs at child depths 1, 2, 3, then depth 4 returns EmptyCursor
        assertEquals(listOf("tick", "tick", "tick"), executed)
    }

    @Test
    fun `degenerate empty repeat terminates without hanging`() {
        val clock = FakeClock(0L)
        val scheduler = FakeScheduler(clock)
        val executed = mutableListOf<String>()
        val config = mapOf(
            ActionFlowStore.repeatCountKey("empty_loop") to "999999",
            ActionFlowStore.repeatActionKey("empty_loop") to "multi_action:missing",
        )
        val coordinator = ActionQueueCoordinator(
            clock = clock,
            scheduler = scheduler,
            cursorFactory = ActionCursorFactory(
                resolveConfig = { config[it].orEmpty() },
                evaluateCondition = { false },
                resolveMultiActionSteps = { emptyList() },
            ),
            dispatchLeaf = { executed += it },
        )

        coordinator.start("repeat:empty_loop")
        scheduler.drainAll()

        assertTrue(executed.isEmpty())
        assertEquals(0, coordinator.activeRunCount())
    }
}
