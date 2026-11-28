package com.fan.edgex.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezerAppLauncherTest {

    @Test
    fun unfreezeAndLaunchCore_enablesRetriesUntilIntentReadyAndLaunches() {
        val events = mutableListOf<String>()
        val scheduled = ArrayDeque<Pair<Long, () -> Unit>>()
        var resolveAttempts = 0
        var launched = false

        FreezerAppLauncher.unfreezeAndLaunchCore(
            runAsync = { block ->
                events.add("async")
                block()
            },
            enablePackage = { events.add("enable") },
            postToMain = { block ->
                events.add("main")
                block()
            },
            postDelayedToMain = { delayMs, block ->
                scheduled.addLast(delayMs to block)
            },
            resolveLaunchIntent = {
                resolveAttempts++
                if (resolveAttempts >= 3) "intent:com.example.frozen" else null
            },
            startActivity = { intent -> events.add("start:$intent") },
            onLaunched = { launched = true },
            onUnfreezeFailed = { events.add("unfreeze_failed") },
            onLaunchTimeout = { events.add("timeout") },
            onLaunchError = { events.add("error") },
        )

        while (scheduled.isNotEmpty()) {
            val (delayMs, task) = scheduled.removeFirst()
            assertEquals(FreezerAppLauncher.LAUNCH_RETRY_DELAY_MS, delayMs)
            task()
        }

        assertEquals(3, resolveAttempts)
        assertTrue(launched)
        assertEquals(
            listOf("async", "enable", "main", "start:intent:com.example.frozen"),
            events,
        )
    }

    @Test
    fun unfreezeAndLaunchCore_reportsFailureWhenEnableThrows() {
        var unfreezeFailed = false
        var launched = false

        FreezerAppLauncher.unfreezeAndLaunchCore<String>(
            runAsync = { it() },
            enablePackage = { throw SecurityException("denied") },
            postToMain = { it() },
            postDelayedToMain = { _, _ -> },
            resolveLaunchIntent = { "intent" },
            startActivity = { launched = true },
            onUnfreezeFailed = { unfreezeFailed = true },
            onLaunchTimeout = {},
            onLaunchError = {},
        )

        assertTrue(unfreezeFailed)
        assertFalse(launched)
    }

    @Test
    fun launchWithRetryCore_timesOutAfterMaxRetries() {
        val scheduled = ArrayDeque<() -> Unit>()
        var attempts = 0
        var timedOut = false

        FreezerAppLauncher.launchWithRetryCore<String>(
            postDelayedToMain = { _, block -> scheduled.addLast(block) },
            resolveLaunchIntent = {
                attempts++
                null
            },
            startActivity = {},
            onLaunchTimeout = { timedOut = true },
            onLaunchError = {},
            maxRetries = 4,
            retryDelayMs = 200L,
        )

        while (scheduled.isNotEmpty()) {
            scheduled.removeFirst().invoke()
        }

        assertEquals(4, attempts)
        assertTrue(timedOut)
    }
}
