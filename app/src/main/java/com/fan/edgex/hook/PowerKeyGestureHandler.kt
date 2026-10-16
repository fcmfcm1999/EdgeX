package com.fan.edgex.hook

/**
 * Owns a power-key sequence BEFORE PhoneWindowManager sees its first DOWN.
 * Android-free so timing, replay and native fallback can be tested on the JVM.
 * E is a copied platform event; replay preserves its original timestamps and flags.
 */
internal class PowerKeyGestureHandler<E>(
    private val scheduler: Scheduler,
    private val forwardEvent: (E) -> Unit,
    private val executeAction: (String) -> Unit,
    private val longPressTimeout: Long,
    private val doubleTapTimeout: Long,
) {
    interface Scheduler {
        fun now(): Long
        fun postDelayed(task: Runnable, delayMillis: Long)
        fun removeCallbacks(task: Runnable)
    }

    data class Config(
        val enabled: Boolean = false,
        val click: String = "",
        val doubleClick: String = "",
        val longPress: String = "",
    ) {
        val active: Boolean get() = enabled && listOf(click, doubleClick, longPress).any(::isAction)
    }

    data class Event<E>(
        val original: E,
        val downTime: Long,
        val eventTime: Long,
        val isDown: Boolean,
        val repeatCount: Int = 0,
        val canceled: Boolean = false,
    )

    private enum class State { IDLE, PRESSED, WAITING_DOUBLE, CONSUMED, NATIVE_DOWN }

    private var state = State.IDLE
    private var config = Config()
    private var down: Event<E>? = null
    private var up: Event<E>? = null
    private var longTask: Runnable? = null
    private var doubleTask: Runnable? = null
    private var generation = 0L

    /** Configuration changes apply to the next sequence, never strand an owned key-up. */
    @Synchronized
    fun handle(event: Event<E>, currentConfig: Config): Boolean {
        if (state == State.IDLE) {
            // Do not steal an orphan repeat/release from the system, including at boot.
            if (!currentConfig.active || !event.isDown || event.repeatCount != 0) return false
            config = currentConfig
            startPress(event)
            return true
        }

        if (state == State.NATIVE_DOWN) {
            // The system already owns the replayed DOWN; always balance it, even if
            // EdgeX was disabled or the display changed state while the key was held.
            forwardEvent(event.original)
            if (!event.isDown) clear()
            return true
        }

        if (state == State.CONSUMED) {
            if (!event.isDown) clear()
            return true
        }

        if (state == State.WAITING_DOUBLE) {
            if (!event.isDown || event.repeatCount != 0) return true
            val gap = event.eventTime - checkNotNull(up).eventTime
            if (gap >= 0 && gap < doubleTapTimeout) {
                val action = config.doubleClick
                clear()
                state = State.CONSUMED
                executeAction(action)
            } else {
                // Input callbacks can beat a main-thread timeout. Resolve by event time,
                // not by which thread wins the race, then treat this as a fresh press.
                finishSingleClick()
                return handle(event, currentConfig)
            }
            return true
        }

        // PRESSED: repeats are swallowed while the initial DOWN is still pending.
        if (event.isDown) return true
        cancelLongTask()
        if (event.canceled) {
            clear()
            return true
        }
        val duration = event.eventTime - checkNotNull(down).downTime
        if (duration >= longPressTimeout) {
            // A stalled main looper must not turn a long hold into a click/double click.
            resolveLongPress()
            if (state == State.NATIVE_DOWN) forwardEvent(event.original)
            clear()
        } else if (isAction(config.doubleClick)) {
            up = event
            state = State.WAITING_DOUBLE
            scheduleDoubleTimeout(event.eventTime)
        } else {
            up = event
            finishSingleClick()
        }
        return true
    }

    /** A physical volume DOWN while power is held belongs to native key chords. */
    @Synchronized
    fun handoffToNative() {
        if (state != State.PRESSED) return
        ++generation
        cancelLongTask()
        val original = checkNotNull(down).original
        down = null
        state = State.NATIVE_DOWN
        forwardEvent(original)
    }

    private fun startPress(event: Event<E>) {
        state = State.PRESSED
        down = event
        up = null
        val token = ++generation
        val task = Runnable {
            synchronized(this) {
                if (token == generation && state == State.PRESSED) {
                    longTask = null
                    resolveLongPress()
                }
            }
        }
        longTask = task
        // Always schedule: an unmapped long hold must reach the native power menu
        // while held, rather than replaying DOWN+UP only after release.
        scheduler.postDelayed(task, (event.downTime + longPressTimeout - scheduler.now()).coerceAtLeast(0))
    }

    private fun resolveLongPress() {
        val original = checkNotNull(down).original
        down = null
        up = null
        if (isAction(config.longPress)) {
            state = State.CONSUMED
            executeAction(config.longPress)
        } else {
            state = State.NATIVE_DOWN
            forwardEvent(original)
        }
    }

    private fun scheduleDoubleTimeout(upTime: Long) {
        val token = ++generation
        val task = Runnable {
            synchronized(this) {
                if (token == generation && state == State.WAITING_DOUBLE) {
                    doubleTask = null
                    finishSingleClick()
                }
            }
        }
        doubleTask = task
        scheduler.postDelayed(task, (upTime + doubleTapTimeout - scheduler.now()).coerceAtLeast(0))
    }

    private fun finishSingleClick() {
        val action = config.click
        val first = down?.original
        val last = up?.original
        clear()
        if (isAction(action)) {
            executeAction(action)
        } else {
            first?.let(forwardEvent)
            last?.let(forwardEvent)
        }
    }

    private fun cancelLongTask() {
        longTask?.let(scheduler::removeCallbacks)
        longTask = null
    }

    private fun clear() {
        ++generation
        cancelLongTask()
        doubleTask?.let(scheduler::removeCallbacks)
        doubleTask = null
        down = null
        up = null
        state = State.IDLE
    }

    companion object {
        // Power-only window; volume keys retain the platform double-tap timeout.
        const val DOUBLE_TAP_TIMEOUT_MS = 200L

        private fun isAction(value: String): Boolean = value.isNotEmpty() && value != "none"
    }
}
