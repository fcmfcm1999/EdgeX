package com.fan.edgex.service

interface Clock {
    fun currentTimeMillis(): Long
}

class SystemTimeClock : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

data class NotificationTriggerState(
    val lastTriggeredAt: Long,
    val lastPostTime: Long,
    val removedAt: Long? = null,
    val removalReason: Int? = null,
)

class NotificationLifecycleManager(private val clock: Clock = SystemTimeClock()) {
    
    companion object {
        private val seenNotifications = HashMap<String, NotificationTriggerState>()
        
        const val REBUILD_THRESHOLD_MS = 10_000L
        const val CLEANUP_THRESHOLD_MS = 60_000L
        
        fun clearState() {
            seenNotifications.clear()
        }

        fun getTriggerState(key: String): NotificationTriggerState? {
            return seenNotifications[key]
        }
    }
    
    private fun cleanupExpired() {
        val now = clock.currentTimeMillis()
        val iterator = seenNotifications.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val removedAt = entry.value.removedAt
            if (removedAt != null && (now - removedAt) > CLEANUP_THRESHOLD_MS) {
                iterator.remove()
            }
        }
    }
    
    enum class Decision {
        TRIGGER,
        IGNORE
    }
    
    fun onNotificationPosted(
        key: String,
        isOngoing: Boolean,
        isGroupSummary: Boolean,
        groupAlertBehavior: Int,
        postTime: Long,
        lastAudiblyAlertedMillis: Long,
        shouldAlert: Boolean
    ): Decision {
        cleanupExpired()
        
        if (!shouldAlert) return Decision.IGNORE
        
        // Stage 2: Group Summary Filter
        if (isGroupSummary) {
            // Notification.GROUP_ALERT_SUMMARY is constant value 1
            if (groupAlertBehavior != 1) {
                return Decision.IGNORE
            }
        }
        
        val now = clock.currentTimeMillis()
        val state = seenNotifications[key]
        
        if (state == null) {
            seenNotifications[key] = NotificationTriggerState(
                lastTriggeredAt = now,
                lastPostTime = postTime
            )
            return Decision.TRIGGER
        }
        
        val removedAt = state.removedAt
        if (removedAt != null) {
            val durationSinceRemoval = now - removedAt
            if (durationSinceRemoval < REBUILD_THRESHOLD_MS) {
                // User actions are:
                // REASON_CLICK = 1
                // REASON_CANCEL = 2 (user swiped away, or tapped clear all, or clicked to dismiss)
                // REASON_CANCEL_ALL = 3 (user cleared all)
                val isUserAction = when (state.removalReason) {
                    1, 2, 3 -> true
                    else -> false
                }
                
                val newAlert = lastAudiblyAlertedMillis > state.lastTriggeredAt
                
                if (newAlert || (!isOngoing && isUserAction)) {
                    seenNotifications[key] = NotificationTriggerState(
                        lastTriggeredAt = now,
                        lastPostTime = postTime
                    )
                    return Decision.TRIGGER
                } else {
                    // System rebuild or non-user removal without new alert -> ignore
                    // Restore back to online (removedAt = null) so subsequent updates are handled
                    seenNotifications[key] = state.copy(removedAt = null, removalReason = null)
                    return Decision.IGNORE
                }
            } else {
                // Expired removal state -> treat as brand new notification
                seenNotifications[key] = NotificationTriggerState(
                    lastTriggeredAt = now,
                    lastPostTime = postTime
                )
                return Decision.TRIGGER
            }
        } else {
            // In-place update
            val newAlert = lastAudiblyAlertedMillis > state.lastTriggeredAt
            if (newAlert) {
                seenNotifications[key] = state.copy(
                    lastTriggeredAt = now,
                    lastPostTime = postTime
                )
                return Decision.TRIGGER
            } else {
                return Decision.IGNORE
            }
        }
    }
    
    fun onNotificationRemoved(key: String, reason: Int) {
        cleanupExpired()
        val state = seenNotifications[key]
        if (state != null) {
            seenNotifications[key] = state.copy(
                removedAt = clock.currentTimeMillis(),
                removalReason = reason
            )
        }
    }
    
    fun prewarmNotification(key: String, postTime: Long) {
        val now = clock.currentTimeMillis()
        seenNotifications[key] = NotificationTriggerState(
            lastTriggeredAt = now,
            lastPostTime = postTime
        )
    }
}
