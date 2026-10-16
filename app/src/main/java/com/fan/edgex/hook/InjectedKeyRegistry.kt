package com.fan.edgex.hook

/**
 * A replay may pass through queueing, filtering and dispatching. Matching must
 * be non-destructive, otherwise the next hook mistakes that replay for hardware.
 * Full identity also avoids confusing unrelated keys sharing one millisecond.
 */
internal class InjectedKeyRegistry(
    private val now: () -> Long,
    private val ttlMillis: Long = 30_000,
    private val maxEntries: Int = 128,
) {
    data class Identity(
        val keyCode: Int,
        val action: Int,
        val downTime: Long,
        val eventTime: Long,
        val deviceId: Int,
        val repeatCount: Int,
    ) {
        // Android 15 InputDispatcher::injectInputEvent rewrites an injected
        // hardware event's device id to VIRTUAL_KEYBOARD_ID before queueing.
        fun asInjected(): Identity = copy(deviceId = -1)
    }

    private val entries = LinkedHashMap<Identity, Long>()

    @Synchronized
    fun mark(event: Identity) {
        prune()
        entries.remove(event)
        entries[event] = now()
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
    }

    @Synchronized
    fun contains(event: Identity): Boolean {
        prune()
        return entries.containsKey(event)
    }

    private fun prune() {
        val deadline = now() - ttlMillis
        entries.entries.removeAll { it.value <= deadline }
    }
}
