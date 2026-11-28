package com.fan.edgex.config

object ActionFlowStore {
    const val ACTION_DELAY_MENU = "delay"
    const val ACTION_DELAY_PREFIX = "delay:"
    const val ACTION_REPEAT_MENU = "repeat"
    const val ACTION_REPEAT_PREFIX = "repeat:"
    const val ACTION_ABORT = "abort"
    const val ACTION_STOP_ALL = "stop_all"

    const val DEFAULT_DELAY_MS = 1000L
    const val MAX_DELAY_MS = 86_400_000L // 24 hours
    const val DEFAULT_REPEAT_COUNT = 2
    const val MAX_REPEAT_COUNT = 999_999

    /** Actions that are only shown when configuring a multi-action. */
    val MULTI_ACTION_ONLY_FLOW_CODES: Set<String> = setOf(
        ACTION_DELAY_MENU,
        ACTION_REPEAT_MENU,
        ACTION_ABORT,
        ACTION_STOP_ALL,
    )

    fun buildDelayCode(durationMs: Long): String =
        "$ACTION_DELAY_PREFIX${durationMs.coerceIn(0L, MAX_DELAY_MS)}"

    fun parseDelayMs(actionCode: String): Long? {
        if (!actionCode.startsWith(ACTION_DELAY_PREFIX)) return null
        val raw = actionCode.removePrefix(ACTION_DELAY_PREFIX).trim()
        return raw.toLongOrNull()?.takeIf { it >= 0L }?.coerceAtMost(MAX_DELAY_MS)
    }

    fun isDelay(actionCode: String): Boolean = parseDelayMs(actionCode) != null

    fun formatDelayDuration(durationMs: Long): String {
        val clamped = durationMs.coerceAtLeast(0L)
        return when {
            clamped >= 1000L && clamped % 1000L == 0L -> "${clamped / 1000L}s"
            else -> "${clamped}ms"
        }
    }

    fun repeatCountKey(id: String) = "repeat_${id}_count"
    fun repeatActionKey(id: String) = "repeat_${id}_action"
    fun repeatActionLabelKey(id: String) = "repeat_${id}_action_label"

    fun buildRepeatCode(id: String) = "$ACTION_REPEAT_PREFIX$id"

    fun extractRepeatId(actionCode: String): String? {
        if (!actionCode.startsWith(ACTION_REPEAT_PREFIX)) return null
        return actionCode.removePrefix(ACTION_REPEAT_PREFIX).takeIf { it.isNotBlank() }
    }

    fun parseRepeatCount(raw: String): Int =
        raw.trim().toIntOrNull()?.coerceIn(0, MAX_REPEAT_COUNT) ?: 0
}
