package com.fan.edgex.hook

import android.view.KeyEvent
import com.fan.edgex.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeyManagerTest {

    private data class ScheduledTask(
        val runAtMs: Long,
        val runnable: Runnable,
    )

    private var currentTimeMs = 1_000L
    private val scheduledTasks = mutableListOf<ScheduledTask>()
    private val executedActions = mutableListOf<Pair<String, Int>>()
    private val forwardedEvents = mutableListOf<KeyInterceptStateMachine.EventInfo>()

    private lateinit var machine: KeyInterceptStateMachine<KeyInterceptStateMachine.EventInfo>

    @Before
    fun setUp() {
        currentTimeMs = 1_000L
        scheduledTasks.clear()
        executedActions.clear()
        forwardedEvents.clear()

        machine = KeyInterceptStateMachine(
            scheduleDelay = { delayMs, runnable ->
                scheduledTasks.add(ScheduledTask(currentTimeMs + delayMs, runnable))
            },
            cancelDelay = { runnable ->
                scheduledTasks.removeAll { it.runnable === runnable }
            },
            onExecuteAction = { action, keyCode ->
                executedActions.add(action to keyCode)
            },
            onForwardEvents = { events ->
                events.forEach { event ->
                    machine.markInjectedEvent(event.keyCode, event.action, event.eventTime)
                    forwardedEvents.add(event)
                }
            },
            rebaseEvent = { event, downTime, eventTime ->
                event.copy(downTime = downTime, eventTime = eventTime)
            },
            nowMs = { currentTimeMs },
            uptimeMs = { currentTimeMs },
        )
        machine.longPressTimeout = 500L
        machine.doubleTapTimeout = 300L
    }

    private fun advanceTimeBy(deltaMs: Long) {
        currentTimeMs += deltaMs
        while (true) {
            val due = scheduledTasks.filter { it.runAtMs <= currentTimeMs }
                .minByOrNull { it.runAtMs } ?: break
            scheduledTasks.remove(due)
            due.runnable.run()
        }
    }

    private fun configurePower(
        click: String = "",
        doubleClick: String = "",
        longPress: String = "",
        keysEnabled: Boolean = true,
        powerEnabled: Boolean = true,
    ) {
        machine.updateConfig(
            mapOf(
                AppConfig.KEYS_ENABLED to keysEnabled.toString(),
                AppConfig.keyEnabled(KeyEvent.KEYCODE_POWER) to powerEnabled.toString(),
                AppConfig.keyAction(KeyEvent.KEYCODE_POWER, "click") to click,
                AppConfig.keyAction(KeyEvent.KEYCODE_POWER, "double_click") to doubleClick,
                AppConfig.keyAction(KeyEvent.KEYCODE_POWER, "long_press") to longPress,
            ),
        )
    }

    private fun powerEvent(
        action: Int,
        downTime: Long,
        eventTime: Long,
        repeatCount: Int = 0,
        interactive: Boolean = true,
    ): KeyInterceptStateMachine.EventInfo =
        KeyInterceptStateMachine.EventInfo(
            keyCode = KeyEvent.KEYCODE_POWER,
            action = action,
            repeatCount = repeatCount,
            downTime = downTime,
            eventTime = eventTime,
            policyFlags = if (interactive) KeyInterceptStateMachine.POLICY_FLAG_INTERACTIVE else 0,
        )

    private fun sendPower(event: KeyInterceptStateMachine.EventInfo): Boolean =
        machine.handleKeyEvent(event, copyEvent = { event }, fromBeforeQueueing = true)

    @Test
    fun powerSingleClickTriggersImmediatelyWhenNoDoubleClickConfigured() {
        configurePower(click = "back")

        assertTrue(sendPower(powerEvent(KeyEvent.ACTION_DOWN, downTime = 1000L, eventTime = 1000L)))
        assertTrue(sendPower(powerEvent(KeyEvent.ACTION_UP, downTime = 1000L, eventTime = 1080L)))

        assertEquals(listOf("back" to KeyEvent.KEYCODE_POWER), executedActions)
        assertTrue(forwardedEvents.isEmpty())
    }

    @Test
    fun powerDoubleClickTriggersAndDoesNotFireSingleClick() {
        configurePower(click = "back", doubleClick = "flashlight")

        assertTrue(sendPower(powerEvent(KeyEvent.ACTION_DOWN, downTime = 1000L, eventTime = 1000L)))
        assertTrue(sendPower(powerEvent(KeyEvent.ACTION_UP, downTime = 1000L, eventTime = 1060L)))
        assertTrue(executedActions.isEmpty())

        advanceTimeBy(120L)
        assertTrue(sendPower(powerEvent(KeyEvent.ACTION_DOWN, downTime = 1180L, eventTime = 1180L)))
        assertTrue(sendPower(powerEvent(KeyEvent.ACTION_UP, downTime = 1180L, eventTime = 1240L)))

        assertEquals(listOf("flashlight" to KeyEvent.KEYCODE_POWER), executedActions)
        advanceTimeBy(500L)
        assertEquals(1, executedActions.size)
        assertTrue(forwardedEvents.isEmpty())
    }

    @Test
    fun powerSingleClickWaitsForDoubleTapTimeoutWhenDoubleClickAlsoConfigured() {
        configurePower(click = "back", doubleClick = "flashlight")

        assertTrue(sendPower(powerEvent(KeyEvent.ACTION_DOWN, downTime = 1000L, eventTime = 1000L)))
        assertTrue(sendPower(powerEvent(KeyEvent.ACTION_UP, downTime = 1000L, eventTime = 1060L)))
        assertTrue(executedActions.isEmpty())

        advanceTimeBy(300L)
        assertEquals(listOf("back" to KeyEvent.KEYCODE_POWER), executedActions)
        assertTrue(forwardedEvents.isEmpty())
    }

    @Test
    fun powerLongPressTriggersAtTimeoutAndConsumesRelease() {
        configurePower(click = "back", longPress = "screenshot")

        assertTrue(sendPower(powerEvent(KeyEvent.ACTION_DOWN, downTime = 1000L, eventTime = 1000L)))
        assertTrue(sendPower(powerEvent(KeyEvent.ACTION_DOWN, downTime = 1000L, eventTime = 1300L, repeatCount = 1)))
        advanceTimeBy(500L)

        assertEquals(listOf("screenshot" to KeyEvent.KEYCODE_POWER), executedActions)

        assertTrue(sendPower(powerEvent(KeyEvent.ACTION_UP, downTime = 1000L, eventTime = 1600L)))
        assertEquals(1, executedActions.size)
        assertTrue(forwardedEvents.isEmpty())

        // Subsequent stray ACTION_UP in idle state must not be swallowed
        assertFalse(sendPower(powerEvent(KeyEvent.ACTION_UP, downTime = 1700L, eventTime = 1750L)))
    }

    @Test
    fun powerKeyPassesThroughWhenScreenIsOffSoDeviceAlwaysWakes() {
        configurePower(click = "back", doubleClick = "flashlight", longPress = "screenshot")

        // Screen off -> non-interactive DOWN must not be consumed
        assertFalse(sendPower(powerEvent(KeyEvent.ACTION_DOWN, downTime = 1000L, eventTime = 1000L, interactive = false)))
        // Screen wakes on DOWN, so matching UP arrives with interactive = true while state is IDLE
        assertFalse(sendPower(powerEvent(KeyEvent.ACTION_UP, downTime = 1000L, eventTime = 1080L, interactive = true)))

        assertTrue(executedActions.isEmpty())
        assertTrue(forwardedEvents.isEmpty())
    }

    @Test
    fun unmappedPowerClickForwardsToSystemAndSkipsReinterceptionEvenWithSameEventTime() {
        configurePower(longPress = "screenshot")

        val down = powerEvent(KeyEvent.ACTION_DOWN, downTime = 1000L, eventTime = 1000L)
        val up = powerEvent(KeyEvent.ACTION_UP, downTime = 1000L, eventTime = 1000L)

        assertTrue(sendPower(down))
        assertTrue(sendPower(up))

        assertTrue(executedActions.isEmpty())
        assertEquals(listOf(down, up), forwardedEvents)

        // When injected events re-enter interceptKeyBeforeQueueing, both must pass through
        assertFalse(sendPower(down))
        assertFalse(sendPower(up))
    }

    @Test
    fun unmappedPowerLongPressHandsBackToSystemWithSerializedRebasedTimestamps() {
        configurePower(click = "back")

        val down = powerEvent(KeyEvent.ACTION_DOWN, downTime = 1000L, eventTime = 1000L)
        assertTrue(sendPower(down))

        advanceTimeBy(500L) // currentTimeMs == 1500L
        assertTrue(executedActions.isEmpty())
        assertEquals(1, forwardedEvents.size)
        assertEquals(KeyEvent.ACTION_DOWN, forwardedEvents[0].action)
        assertEquals(1500L, forwardedEvents[0].downTime)
        assertEquals(1500L, forwardedEvents[0].eventTime)

        // Repeat consumed while in passthrough; release serialized with matching rebased downTime
        assertTrue(sendPower(powerEvent(KeyEvent.ACTION_DOWN, downTime = 1000L, eventTime = 1550L, repeatCount = 2)))
        advanceTimeBy(300L) // currentTimeMs == 1800L
        assertTrue(sendPower(powerEvent(KeyEvent.ACTION_UP, downTime = 1000L, eventTime = 1800L)))

        assertEquals(2, forwardedEvents.size)
        assertEquals(KeyEvent.ACTION_UP, forwardedEvents[1].action)
        assertEquals(1500L, forwardedEvents[1].downTime)
        assertEquals(1800L, forwardedEvents[1].eventTime)
        assertTrue(executedActions.isEmpty())

        // Replayed events pass straight through without re-interception
        assertFalse(sendPower(forwardedEvents[0]))
        assertFalse(sendPower(forwardedEvents[1]))
    }

    @Test
    fun unmappedVolumeLongPressCleansUpPassthroughStateBeforeNextMappedPress() {
        machine.updateConfig(
            mapOf(
                AppConfig.KEYS_ENABLED to "true",
                AppConfig.keyEnabled(KeyEvent.KEYCODE_VOLUME_UP) to "true",
                AppConfig.keyAction(KeyEvent.KEYCODE_VOLUME_UP, "click") to "back",
            ),
        )

        fun volumeEvent(action: Int, downTime: Long, eventTime: Long) =
            KeyInterceptStateMachine.EventInfo(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = action,
                repeatCount = 0,
                downTime = downTime,
                eventTime = eventTime,
                policyFlags = 0,
            )

        val down = volumeEvent(KeyEvent.ACTION_DOWN, downTime = 1000L, eventTime = 1000L)
        assertTrue(machine.handleKeyEvent(down, copyEvent = { down }))

        // Trigger unmapped long-press fallback (sets STATE_PASSTHROUGH and 3s volume passthrough)
        advanceTimeBy(500L) // currentTimeMs == 1500L
        assertEquals(1, forwardedEvents.size)

        // Release within the 3s volume passthrough window must still clear STATE_PASSTHROUGH
        advanceTimeBy(200L) // currentTimeMs == 1700L
        val up = volumeEvent(KeyEvent.ACTION_UP, downTime = 1000L, eventTime = 1700L)
        assertTrue(machine.handleKeyEvent(up, copyEvent = { up }))
        assertEquals(2, forwardedEvents.size)

        // After the 3s passthrough window expires, a normal click must fire its mapped action
        advanceTimeBy(4000L) // currentTimeMs == 5700L
        val nextDown = volumeEvent(KeyEvent.ACTION_DOWN, downTime = 5700L, eventTime = 5700L)
        val nextUp = volumeEvent(KeyEvent.ACTION_UP, downTime = 5700L, eventTime = 5760L)
        assertTrue(machine.handleKeyEvent(nextDown, copyEvent = { nextDown }))
        assertTrue(machine.handleKeyEvent(nextUp, copyEvent = { nextUp }))
        assertEquals(listOf("back" to KeyEvent.KEYCODE_VOLUME_UP), executedActions)
    }

    @Test
    fun powerKeyRespectsLockscreenTriggerConditionWhenScreenIsOn() {
        machine.updateConfig(
            mapOf(
                AppConfig.KEYS_ENABLED to "true",
                AppConfig.KEYS_TRIGGER_SCREEN_OFF to "false",
                AppConfig.KEYS_TRIGGER_LOCKED to "false",
                AppConfig.keyEnabled(KeyEvent.KEYCODE_POWER) to "true",
                AppConfig.keyAction(KeyEvent.KEYCODE_POWER, "click") to "back",
            ),
        )

        val lockedDown = powerEvent(KeyEvent.ACTION_DOWN, downTime = 1000L, eventTime = 1000L, interactive = true)
        assertFalse(
            machine.handleKeyEvent(
                info = lockedDown,
                copyEvent = { lockedDown },
                source = KeyInterceptionPath.QUEUEING,
                isScreenInteractive = true,
                isKeyguardLocked = true,
            ),
        )
        assertTrue(executedActions.isEmpty())

        // Unlocked screen-on power click still triggers
        val unlockedDown = powerEvent(KeyEvent.ACTION_DOWN, downTime = 2000L, eventTime = 2000L, interactive = true)
        val unlockedUp = powerEvent(KeyEvent.ACTION_UP, downTime = 2000L, eventTime = 2060L, interactive = true)
        assertTrue(
            machine.handleKeyEvent(
                info = unlockedDown,
                copyEvent = { unlockedDown },
                source = KeyInterceptionPath.QUEUEING,
                isScreenInteractive = true,
                isKeyguardLocked = false,
            ),
        )
        assertTrue(
            machine.handleKeyEvent(
                info = unlockedUp,
                copyEvent = { unlockedUp },
                source = KeyInterceptionPath.QUEUEING,
                isScreenInteractive = true,
                isKeyguardLocked = false,
            ),
        )
        assertEquals(listOf("back" to KeyEvent.KEYCODE_POWER), executedActions)
    }

    @Test
    fun screenOffVolumeKeyUsesQueueingPathAndSurvivesMidPressWake() {
        machine.updateConfig(
            mapOf(
                AppConfig.KEYS_ENABLED to "true",
                AppConfig.KEYS_TRIGGER_SCREEN_OFF to "true",
                AppConfig.keyEnabled(KeyEvent.KEYCODE_VOLUME_DOWN) to "true",
                AppConfig.keyAction(KeyEvent.KEYCODE_VOLUME_DOWN, "click") to "flashlight",
            ),
        )

        val down = KeyInterceptStateMachine.EventInfo(
            keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
            action = KeyEvent.ACTION_DOWN,
            repeatCount = 0,
            downTime = 1000L,
            eventTime = 1000L,
            policyFlags = 0,
        )
        val up = down.copy(action = KeyEvent.ACTION_UP, eventTime = 1080L)

        assertTrue(
            machine.handleKeyEvent(
                info = down,
                copyEvent = { down },
                source = KeyInterceptionPath.QUEUEING,
                isScreenInteractive = false,
                isKeyguardLocked = true,
            ),
        )
        // DISPATCH must not steal the active QUEUEING press even if screen wakes
        assertFalse(
            machine.handleKeyEvent(
                info = up,
                copyEvent = { up },
                source = KeyInterceptionPath.DISPATCH,
                isScreenInteractive = true,
                isKeyguardLocked = true,
            ),
        )
        // QUEUEING completes the press and executes the action
        assertTrue(
            machine.handleKeyEvent(
                info = up,
                copyEvent = { up },
                source = KeyInterceptionPath.QUEUEING,
                isScreenInteractive = true,
                isKeyguardLocked = true,
            ),
        )
        assertEquals(listOf("flashlight" to KeyEvent.KEYCODE_VOLUME_DOWN), executedActions)
    }
}
