package com.fan.edgex.service

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NotificationLifecycleManagerTest {

    private class TestClock : Clock {
        var currentTime: Long = 0L
        override fun currentTimeMillis(): Long = currentTime
    }

    private lateinit var clock: TestClock
    private lateinit var manager: NotificationLifecycleManager

    @Before
    fun setUp() {
        clock = TestClock()
        clock.currentTime = 1000L
        manager = NotificationLifecycleManager(clock)
        NotificationLifecycleManager.clearState()
    }

    @Test
    fun testBrandNewNotificationTriggers() {
        val decision = manager.onNotificationPosted(
            key = "key1",
            isOngoing = false,
            isGroupSummary = false,
            groupAlertBehavior = 0,
            postTime = 1000L,
            lastAudiblyAlertedMillis = 1000L,
            shouldAlert = true
        )
        assertEquals(NotificationLifecycleManager.Decision.TRIGGER, decision)
    }

    @Test
    fun testShouldAlertFalseIgnored() {
        val decision = manager.onNotificationPosted(
            key = "key1",
            isOngoing = false,
            isGroupSummary = false,
            groupAlertBehavior = 0,
            postTime = 1000L,
            lastAudiblyAlertedMillis = 1000L,
            shouldAlert = false
        )
        assertEquals(NotificationLifecycleManager.Decision.IGNORE, decision)
    }

    @Test
    fun testInPlaceSilentUpdateIgnored() {
        // First post triggers
        manager.onNotificationPosted(
            key = "key1",
            isOngoing = false,
            isGroupSummary = false,
            groupAlertBehavior = 0,
            postTime = 1000L,
            lastAudiblyAlertedMillis = 1000L,
            shouldAlert = true
        )

        // Silent update (same alert time) is ignored
        clock.currentTime = 1500L
        val decision = manager.onNotificationPosted(
            key = "key1",
            isOngoing = false,
            isGroupSummary = false,
            groupAlertBehavior = 0,
            postTime = 1500L,
            lastAudiblyAlertedMillis = 1000L,
            shouldAlert = true
        )
        assertEquals(NotificationLifecycleManager.Decision.IGNORE, decision)
    }

    @Test
    fun testInPlaceAlertUpdateTriggers() {
        // First post triggers
        manager.onNotificationPosted(
            key = "key1",
            isOngoing = false,
            isGroupSummary = false,
            groupAlertBehavior = 0,
            postTime = 1000L,
            lastAudiblyAlertedMillis = 1000L,
            shouldAlert = true
        )

        // Update with new alert timestamp (e.g. subsequent message) triggers again
        clock.currentTime = 2000L
        val decision = manager.onNotificationPosted(
            key = "key1",
            isOngoing = false,
            isGroupSummary = false,
            groupAlertBehavior = 0,
            postTime = 2000L,
            lastAudiblyAlertedMillis = 2000L,
            shouldAlert = true
        )
        assertEquals(NotificationLifecycleManager.Decision.TRIGGER, decision)
    }

    @Test
    fun testGroupSummaryBehavior() {
        // Group summary with GROUP_ALERT_ALL (0) should be ignored
        val decision1 = manager.onNotificationPosted(
            key = "key1",
            isOngoing = false,
            isGroupSummary = true,
            groupAlertBehavior = 0,
            postTime = 1000L,
            lastAudiblyAlertedMillis = 1000L,
            shouldAlert = true
        )
        assertEquals(NotificationLifecycleManager.Decision.IGNORE, decision1)

        // Group summary with GROUP_ALERT_SUMMARY (1) should be triggered
        val decision2 = manager.onNotificationPosted(
            key = "key2",
            isOngoing = false,
            isGroupSummary = true,
            groupAlertBehavior = 1,
            postTime = 1000L,
            lastAudiblyAlertedMillis = 1000L,
            shouldAlert = true
        )
        assertEquals(NotificationLifecycleManager.Decision.TRIGGER, decision2)
    }

    @Test
    fun testOngoingRebuildIgnored() {
        // Post first
        manager.onNotificationPosted(
            key = "key1",
            isOngoing = true,
            isGroupSummary = false,
            groupAlertBehavior = 0,
            postTime = 1000L,
            lastAudiblyAlertedMillis = 1000L,
            shouldAlert = true
        )

        // Remove
        clock.currentTime = 2000L
        manager.onNotificationRemoved("key1", 2) // User removed

        // Repost ongoing within 10s (rebuild) -> Ignored
        clock.currentTime = 5000L
        val decision = manager.onNotificationPosted(
            key = "key1",
            isOngoing = true,
            isGroupSummary = false,
            groupAlertBehavior = 0,
            postTime = 5000L,
            lastAudiblyAlertedMillis = 1000L,
            shouldAlert = true
        )
        assertEquals(NotificationLifecycleManager.Decision.IGNORE, decision)
    }

    @Test
    fun testNonOngoingUserDismissalTriggers() {
        // Post first
        manager.onNotificationPosted(
            key = "key1",
            isOngoing = false,
            isGroupSummary = false,
            groupAlertBehavior = 0,
            postTime = 1000L,
            lastAudiblyAlertedMillis = 1000L,
            shouldAlert = true
        )

        // User removes (swipe: REASON_CANCEL = 2)
        clock.currentTime = 2000L
        manager.onNotificationRemoved("key1", 2)

        // Repost non-ongoing within 10s -> Triggers (as it is user action)
        clock.currentTime = 5000L
        val decision = manager.onNotificationPosted(
            key = "key1",
            isOngoing = false,
            isGroupSummary = false,
            groupAlertBehavior = 0,
            postTime = 5000L,
            lastAudiblyAlertedMillis = 1000L,
            shouldAlert = true
        )
        assertEquals(NotificationLifecycleManager.Decision.TRIGGER, decision)
    }

    @Test
    fun testNonOngoingSystemDismissalIgnored() {
        // Post first
        manager.onNotificationPosted(
            key = "key1",
            isOngoing = false,
            isGroupSummary = false,
            groupAlertBehavior = 0,
            postTime = 1000L,
            lastAudiblyAlertedMillis = 1000L,
            shouldAlert = true
        )

        // System removes (REASON_APP_CANCEL = 8)
        clock.currentTime = 2000L
        manager.onNotificationRemoved("key1", 8)

        // Repost non-ongoing within 10s -> Ignored (not user action, no new alert)
        clock.currentTime = 5000L
        val decision = manager.onNotificationPosted(
            key = "key1",
            isOngoing = false,
            isGroupSummary = false,
            groupAlertBehavior = 0,
            postTime = 5000L,
            lastAudiblyAlertedMillis = 1000L,
            shouldAlert = true
        )
        assertEquals(NotificationLifecycleManager.Decision.IGNORE, decision)
    }

    @Test
    fun testRepostAfterTimeoutTriggers() {
        // Post first
        manager.onNotificationPosted(
            key = "key1",
            isOngoing = false,
            isGroupSummary = false,
            groupAlertBehavior = 0,
            postTime = 1000L,
            lastAudiblyAlertedMillis = 1000L,
            shouldAlert = true
        )

        // System removes
        clock.currentTime = 2000L
        manager.onNotificationRemoved("key1", 8)

        // Repost after 10 seconds -> Triggers (expired timeout)
        clock.currentTime = 13000L
        val decision = manager.onNotificationPosted(
            key = "key1",
            isOngoing = false,
            isGroupSummary = false,
            groupAlertBehavior = 0,
            postTime = 13000L,
            lastAudiblyAlertedMillis = 1000L,
            shouldAlert = true
        )
        assertEquals(NotificationLifecycleManager.Decision.TRIGGER, decision)
    }

    @Test
    fun testPrewarmActiveNotifications() {
        // Prewarm active notification
        clock.currentTime = 1000L
        manager.prewarmNotification("key1", 1000L)

        // Same post triggers ignore because it is pre-warmed
        val decision = manager.onNotificationPosted(
            key = "key1",
            isOngoing = false,
            isGroupSummary = false,
            groupAlertBehavior = 0,
            postTime = 1000L,
            lastAudiblyAlertedMillis = 1000L,
            shouldAlert = true
        )
        assertEquals(NotificationLifecycleManager.Decision.IGNORE, decision)
    }
}
