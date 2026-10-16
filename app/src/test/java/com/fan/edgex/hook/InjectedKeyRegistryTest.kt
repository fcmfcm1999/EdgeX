package com.fan.edgex.hook

import org.junit.Assert.*
import org.junit.Test

class InjectedKeyRegistryTest {
    private var time = 0L
    private val event = InjectedKeyRegistry.Identity(26, 0, 10, 20, 1, 0)

    @Test fun replayBypassesEveryPipelineStageWithoutRemovingMarker() {
        val registry = InjectedKeyRegistry({ time })
        registry.mark(event)
        repeat(3) { assertTrue(registry.contains(event)) }
    }

    @Test fun sameMillisecondDoesNotMatchOtherKeyActionDeviceOrPress() {
        val registry = InjectedKeyRegistry({ time })
        registry.mark(event)
        for (other in listOf(event.copy(keyCode = 24), event.copy(action = 1),
            event.copy(downTime = 11), event.copy(eventTime = 21),
            event.copy(deviceId = 2), event.copy(repeatCount = 1))) {
            assertFalse(registry.contains(other))
        }
    }

    @Test fun replayMatchesNativeDeviceNormalizationInsteadOfPhysicalDevice() {
        val registry = InjectedKeyRegistry({ time })
        registry.mark(event.asInjected())
        val nativeReplay = event.copy(deviceId = -1)
        repeat(3) { assertTrue(registry.contains(nativeReplay)) }
        assertFalse(registry.contains(event))
    }

    @Test fun staleReplayMarkersExpire() {
        val registry = InjectedKeyRegistry({ time }, ttlMillis = 100)
        registry.mark(event)
        time = 99; assertTrue(registry.contains(event))
        time = 100; assertFalse(registry.contains(event))
    }

    @Test fun registryIsBoundedAndEvictsOldest() {
        val registry = InjectedKeyRegistry({ time }, maxEntries = 2)
        registry.mark(event)
        registry.mark(event.copy(eventTime = 21))
        registry.mark(event.copy(eventTime = 22))
        assertFalse(registry.contains(event))
        assertTrue(registry.contains(event.copy(eventTime = 21)))
        assertTrue(registry.contains(event.copy(eventTime = 22)))
    }

    @Test fun markingAgainRefreshesExpiryAndEvictionOrder() {
        val registry = InjectedKeyRegistry({ time }, ttlMillis = 100, maxEntries = 2)
        registry.mark(event)
        registry.mark(event.copy(eventTime = 21))
        time = 50; registry.mark(event)
        registry.mark(event.copy(eventTime = 22))
        time = 100
        assertTrue(registry.contains(event))
        assertFalse(registry.contains(event.copy(eventTime = 21)))
    }
}
