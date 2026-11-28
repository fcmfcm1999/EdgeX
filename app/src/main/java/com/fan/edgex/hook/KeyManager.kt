package com.fan.edgex.hook

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.ViewConfiguration
import com.fan.edgex.config.AppConfig
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Method

internal enum class KeyWakeLockAction {
    NONE,
    ACQUIRE_RECOGNITION,
    ACQUIRE_DISPATCH_GRACE,
    RELEASE,
}

/**
 * Pure key interception state machine for hardware keys (Volume Up/Down, Power).
 * Extracted from [KeyManager] so all state transitions, timeouts, passthrough,
 * trigger conditions, wake-lock transitions, and loop-prevention logic can be
 * unit-tested on the JVM without Android framework hooks.
 */
internal class KeyInterceptStateMachine<E>(
    private val scheduleDelay: (delayMs: Long, runnable: Runnable) -> Unit,
    private val cancelDelay: (runnable: Runnable) -> Unit,
    private val onExecuteAction: (action: String, keyCode: Int) -> Unit,
    private val onForwardEvents: (events: List<E>) -> Unit,
    private val onWakeLockAction: (KeyWakeLockAction) -> Unit = {},
    private val rebaseEvent: (event: E, downTime: Long, eventTime: Long) -> E = { event, _, _ -> event },
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val uptimeMs: () -> Long = nowMs,
    private val log: (String) -> Unit = {},
) {
    data class EventInfo(
        val keyCode: Int,
        val action: Int,
        val repeatCount: Int,
        val downTime: Long,
        val eventTime: Long,
        val policyFlags: Int,
    )

    private data class InjectedEventKey(
        val keyCode: Int,
        val action: Int,
        val eventTime: Long,
    )

    companion object {
        const val MODE_CLICK = 0
        const val MODE_DOUBLE_CLICK = 1
        const val MODE_LONG_PRESS = 2

        const val KEYCODE_VOLUME_UP = KeyEvent.KEYCODE_VOLUME_UP
        const val KEYCODE_VOLUME_DOWN = KeyEvent.KEYCODE_VOLUME_DOWN
        const val KEYCODE_POWER = KeyEvent.KEYCODE_POWER

        // WindowManagerPolicy.FLAG_FILTERED (0x04000000) — marks events re-injected by InputFilter
        const val POLICY_FLAG_FILTERED = 0x04000000

        // WindowManagerPolicy.FLAG_INTERACTIVE (0x20000000) — set when screen is awake/interactive
        const val POLICY_FLAG_INTERACTIVE = 0x20000000

        private const val STATE_IDLE = 0
        private const val STATE_PRESSED = 1
        private const val STATE_WAITING_DOUBLE = 2
        private const val STATE_PASSTHROUGH = 3

        private const val VOLUME_PASSTHROUGH_DURATION = 3000L
        private const val MAX_INJECTED_ENTRIES = 32

        val SUPPORTED_KEYS = mapOf(
            KEYCODE_VOLUME_UP to 0,
            KEYCODE_VOLUME_DOWN to 1,
            KEYCODE_POWER to 2,
        )
    }

    private val lock = Any()

    private val keyStates = mutableMapOf<Int, Int>()
    private val activeKeyPaths = mutableMapOf<Int, KeyInterceptionPath>()
    private val keyDownTimes = mutableMapOf<Int, Long>()
    private val passthroughDownTimes = mutableMapOf<Int, Long>()
    private val pendingDownEvents = mutableMapOf<Int, E>()
    private val pendingUpEvents = mutableMapOf<Int, E>()
    private val pendingUpEventTimes = mutableMapOf<Int, Long>()
    private val keyConsumed = mutableMapOf<Int, Boolean>()
    private val injectedEvents = ArrayDeque<InjectedEventKey>()

    private var volumePassthroughUntil = 0L

    @Volatile
    var longPressTimeout: Long = 500L

    @Volatile
    var doubleTapTimeout: Long = 300L

    private val longPressRunnables = mutableMapOf<Int, Runnable>()
    private val doubleTapRunnables = mutableMapOf<Int, Runnable>()

    private var keysEnabled = false
    private var allowScreenOff = true
    private var allowLocked = true
    private val keyEnabled = mutableMapOf<Int, Boolean>()
    private val keyActions = mutableMapOf<String, String>()

    private fun isVolumeKey(keyCode: Int): Boolean =
        keyCode == KEYCODE_VOLUME_UP || keyCode == KEYCODE_VOLUME_DOWN

    private fun needsScreenOffWakeLock(keyCode: Int, path: KeyInterceptionPath?): Boolean =
        path == KeyInterceptionPath.QUEUEING && keyCode != KEYCODE_POWER

    fun updateConfig(configCache: Map<String, String>) {
        synchronized(lock) {
            keysEnabled = configCache[AppConfig.KEYS_ENABLED] == "true"
            allowScreenOff = TriggerConditionPolicy.parseTriggerEnabled(
                configCache[AppConfig.KEYS_TRIGGER_SCREEN_OFF],
            )
            allowLocked = allowScreenOff || TriggerConditionPolicy.parseTriggerEnabled(
                configCache[AppConfig.KEYS_TRIGGER_LOCKED],
            )

            for (keyCode in SUPPORTED_KEYS.keys) {
                keyActions["${keyCode}_$MODE_CLICK"] = configCache[AppConfig.keyAction(keyCode, "click")] ?: ""
                keyActions["${keyCode}_$MODE_DOUBLE_CLICK"] = configCache[AppConfig.keyAction(keyCode, "double_click")] ?: ""
                keyActions["${keyCode}_$MODE_LONG_PRESS"] = configCache[AppConfig.keyAction(keyCode, "long_press")] ?: ""
                val enabledValue = configCache[AppConfig.keyEnabled(keyCode)]
                keyEnabled[keyCode] = if (enabledValue != null) {
                    enabledValue == "true"
                } else {
                    hasAnyActionLocked(keyCode)
                }
            }
        }
    }

    fun markInjectedEvent(keyCode: Int, action: Int, eventTime: Long) {
        synchronized(lock) {
            if (injectedEvents.size >= MAX_INJECTED_ENTRIES) {
                injectedEvents.removeFirst()
            }
            injectedEvents.addLast(InjectedEventKey(keyCode, action, eventTime))
        }
    }

    fun handleKeyEvent(
        info: EventInfo,
        copyEvent: () -> E,
        fromBeforeQueueing: Boolean = false,
        source: KeyInterceptionPath = if (fromBeforeQueueing) KeyInterceptionPath.QUEUEING else KeyInterceptionPath.DISPATCH,
        isScreenInteractive: Boolean? = null,
        isKeyguardLocked: Boolean = false,
    ): Boolean {
        var actionToExecute: Pair<String, Int>? = null
        var eventsToForward: List<E>? = null
        var wakeLockAction = KeyWakeLockAction.NONE
        val consumed: Boolean

        synchronized(lock) {
            val keyCode = info.keyCode

            if (info.policyFlags and POLICY_FLAG_FILTERED != 0) return false

            val injectedKey = InjectedEventKey(keyCode, info.action, info.eventTime)
            if (injectedEvents.remove(injectedKey)) {
                return false
            }

            if (!SUPPORTED_KEYS.containsKey(keyCode)) return false

            val activePath = activeKeyPaths[keyCode]
            if (activePath == null) {
                if (!keysEnabled) return false
                if (keyEnabled[keyCode] != true) return false
                if (!hasAnyActionLocked(keyCode)) return false
            }

            val effectiveInteractive = when {
                keyCode == KEYCODE_POWER -> {
                    val policyInteractive = (info.policyFlags and POLICY_FLAG_INTERACTIVE) != 0
                    (isScreenInteractive ?: true) && policyInteractive
                }
                isScreenInteractive != null -> isScreenInteractive
                source == KeyInterceptionPath.QUEUEING -> (info.policyFlags and POLICY_FLAG_INTERACTIVE) != 0
                else -> true
            }

            val accepted = if (keyCode == KEYCODE_POWER) {
                if (activePath != null) {
                    activePath == source
                } else {
                    // Power key is handled exclusively in interceptKeyBeforeQueueing.
                    // Never intercept while screen is off (non-interactive) so the power button
                    // always wakes the device.
                    source == KeyInterceptionPath.QUEUEING &&
                        effectiveInteractive &&
                        TriggerConditionPolicy.shouldTriggerKey(
                            isScreenInteractive = true,
                            isKeyguardLocked = isKeyguardLocked,
                            allowScreenOff = allowScreenOff,
                            allowLocked = allowLocked,
                        )
                }
            } else {
                TriggerConditionPolicy.canAcceptKeyEvent(
                    source = source,
                    activePath = activePath,
                    isScreenInteractive = effectiveInteractive,
                    isKeyguardLocked = isKeyguardLocked,
                    allowScreenOff = allowScreenOff,
                    allowLocked = allowLocked,
                )
            }
            if (!accepted) return false

            if (activePath == null &&
                effectiveInteractive &&
                isVolumeKey(keyCode) &&
                (keyStates[keyCode] ?: STATE_IDLE) == STATE_IDLE &&
                nowMs() < volumePassthroughUntil
            ) {
                volumePassthroughUntil = nowMs() + VOLUME_PASSTHROUGH_DURATION
                return false
            }

            val wasScreenOffQueueing = needsScreenOffWakeLock(keyCode, activePath)
            consumed = when (info.action) {
                KeyEvent.ACTION_DOWN -> handleKeyDownLocked(
                    info = info,
                    source = source,
                    copyEvent = copyEvent,
                    setAction = { actionToExecute = it },
                    setForward = { eventsToForward = it },
                )
                KeyEvent.ACTION_UP -> handleKeyUpLocked(
                    info = info,
                    copyEvent = copyEvent,
                    setAction = { actionToExecute = it },
                    setForward = { eventsToForward = it },
                )
                else -> false
            }
            wakeLockAction = computeWakeLockActionLocked(
                wasScreenOffQueueing = wasScreenOffQueueing,
                dispatchedAction = actionToExecute != null,
            )
        }

        eventsToForward?.let { forwardList ->
            if (forwardList.isNotEmpty()) {
                onForwardEvents(forwardList)
            }
        }
        actionToExecute?.let { (action, keyCode) ->
            dispatchAction(action, keyCode)
        }
        if (wakeLockAction != KeyWakeLockAction.NONE) {
            onWakeLockAction(wakeLockAction)
        }
        return consumed
    }

    fun reset() {
        val runnablesToCancel: List<Runnable>
        synchronized(lock) {
            runnablesToCancel = buildList {
                addAll(longPressRunnables.values)
                addAll(doubleTapRunnables.values)
            }
            longPressRunnables.clear()
            doubleTapRunnables.clear()
            keyStates.clear()
            activeKeyPaths.clear()
            keyDownTimes.clear()
            passthroughDownTimes.clear()
            pendingDownEvents.clear()
            pendingUpEvents.clear()
            pendingUpEventTimes.clear()
            keyConsumed.clear()
            injectedEvents.clear()
            volumePassthroughUntil = 0L
        }
        runnablesToCancel.forEach(cancelDelay)
        onWakeLockAction(KeyWakeLockAction.RELEASE)
    }

    private fun hasAnyActionLocked(keyCode: Int): Boolean =
        hasActionLocked(keyCode, MODE_CLICK) ||
            hasActionLocked(keyCode, MODE_DOUBLE_CLICK) ||
            hasActionLocked(keyCode, MODE_LONG_PRESS)

    private fun getActionLocked(keyCode: Int, mode: Int): String =
        keyActions["${keyCode}_$mode"] ?: ""

    private fun hasActionLocked(keyCode: Int, mode: Int): Boolean {
        val action = getActionLocked(keyCode, mode)
        return action.isNotEmpty() && action != "none"
    }

    private fun handleKeyDownLocked(
        info: EventInfo,
        source: KeyInterceptionPath,
        copyEvent: () -> E,
        setAction: (Pair<String, Int>) -> Unit,
        setForward: (List<E>) -> Unit,
    ): Boolean {
        val keyCode = info.keyCode
        val currentState = keyStates[keyCode] ?: STATE_IDLE

        if (currentState == STATE_PASSTHROUGH) {
            // Keep consuming repeats so they don't race ahead of the replayed DOWN;
            // matching UP will be serialized through onForwardEvents.
            return true
        }

        if (info.repeatCount > 0) {
            if (currentState == STATE_PRESSED) {
                return true
            }
            if (currentState == STATE_IDLE) {
                return startNewPressLocked(info, source, copyEvent)
            }
            return false
        }

        return when (currentState) {
            STATE_IDLE -> startNewPressLocked(info, source, copyEvent)
            STATE_WAITING_DOUBLE -> {
                cancelDoubleTapTimeoutLocked(keyCode)

                val firstUpTime = pendingUpEventTimes[keyCode]
                val timeDiff = if (firstUpTime != null) info.eventTime - firstUpTime else Long.MAX_VALUE

                if (timeDiff < doubleTapTimeout && hasActionLocked(keyCode, MODE_DOUBLE_CLICK)) {
                    keyStates[keyCode] = STATE_PRESSED
                    keyConsumed[keyCode] = true
                    clearPendingLocked(keyCode)

                    val action = getActionLocked(keyCode, MODE_DOUBLE_CLICK)
                    log("Key $keyCode double-click -> $action")
                    setAction(action to keyCode)
                    true
                } else {
                    setForward(drainPendingForForwardLocked(keyCode))
                    startNewPressLocked(info, source, copyEvent)
                }
            }
            STATE_PRESSED -> true
            else -> keyConsumed[keyCode] == true
        }
    }

    private fun startNewPressLocked(
        info: EventInfo,
        source: KeyInterceptionPath,
        copyEvent: () -> E,
    ): Boolean {
        val keyCode = info.keyCode
        keyStates[keyCode] = STATE_PRESSED
        activeKeyPaths[keyCode] = source
        keyDownTimes[keyCode] = info.downTime
        pendingDownEvents[keyCode] = copyEvent()
        pendingUpEvents.remove(keyCode)
        pendingUpEventTimes.remove(keyCode)
        keyConsumed[keyCode] = false

        // Always start the long-press timer: if MODE_LONG_PRESS is configured it fires
        // the configured action; otherwise it hands the held key back to the system
        // (e.g., native power menu / assistant or continuous volume adjustment).
        startLongPressTimeoutLocked(keyCode)
        return true
    }

    private fun handleKeyUpLocked(
        info: EventInfo,
        copyEvent: () -> E,
        setAction: (Pair<String, Int>) -> Unit,
        setForward: (List<E>) -> Unit,
    ): Boolean {
        val keyCode = info.keyCode
        val currentState = keyStates[keyCode] ?: STATE_IDLE

        if (currentState == STATE_PASSTHROUGH) {
            clearKeyStateLocked(keyCode)
            val rebasedDownTime = passthroughDownTimes.remove(keyCode)
            val upEvent = if (rebasedDownTime != null) {
                val nowUptime = maxOf(uptimeMs(), rebasedDownTime)
                rebaseEvent(copyEvent(), rebasedDownTime, nowUptime)
            } else {
                copyEvent()
            }
            // Serialize the release through the same FIFO forward path as the replayed DOWN
            // so the physical UP can never overtake the injected DOWN in PhoneWindowManager.
            setForward(listOf(upEvent))
            return true
        }

        if (currentState != STATE_PRESSED) {
            return keyConsumed[keyCode] == true
        }

        cancelLongPressTimeoutLocked(keyCode)

        val downTime = keyDownTimes[keyCode] ?: info.eventTime
        val pressDuration = info.eventTime - downTime

        if (keyConsumed[keyCode] == true) {
            clearKeyStateLocked(keyCode)
            return true
        }

        if (pressDuration < longPressTimeout) {
            if (hasActionLocked(keyCode, MODE_DOUBLE_CLICK)) {
                keyStates[keyCode] = STATE_WAITING_DOUBLE
                pendingUpEvents[keyCode] = copyEvent()
                pendingUpEventTimes[keyCode] = info.eventTime
                startDoubleTapTimeoutLocked(keyCode)
                return true
            }

            if (hasActionLocked(keyCode, MODE_CLICK)) {
                clearKeyStateLocked(keyCode)

                val action = getActionLocked(keyCode, MODE_CLICK)
                log("Key $keyCode click -> $action")
                setAction(action to keyCode)
                return true
            }

            pendingUpEvents[keyCode] = copyEvent()
            pendingUpEventTimes[keyCode] = info.eventTime
            setForward(drainPendingForForwardLocked(keyCode))
            clearKeyStateLocked(keyCode)
            return true
        } else {
            // Held past long-press threshold without timeout consuming it
            if (hasActionLocked(keyCode, MODE_LONG_PRESS)) {
                clearKeyStateLocked(keyCode)

                val action = getActionLocked(keyCode, MODE_LONG_PRESS)
                log("Key $keyCode long-press (on release) -> $action")
                setAction(action to keyCode)
                return true
            }

            pendingUpEvents[keyCode] = copyEvent()
            pendingUpEventTimes[keyCode] = info.eventTime
            setForward(drainPendingForForwardLocked(keyCode))
            clearKeyStateLocked(keyCode)
            return true
        }
    }

    private fun startLongPressTimeoutLocked(keyCode: Int) {
        cancelLongPressTimeoutLocked(keyCode)

        lateinit var runnable: Runnable
        runnable = Runnable {
            var actionToExecute: Pair<String, Int>? = null
            var eventToForward: List<E>? = null
            var wakeLockAction = KeyWakeLockAction.NONE

            synchronized(lock) {
                if (longPressRunnables[keyCode] !== runnable) return@Runnable
                longPressRunnables.remove(keyCode)

                val wasScreenOffQueueing = needsScreenOffWakeLock(keyCode, activeKeyPaths[keyCode])
                if (keyStates[keyCode] == STATE_PRESSED && keyConsumed[keyCode] != true) {
                    if (hasActionLocked(keyCode, MODE_LONG_PRESS)) {
                        keyConsumed[keyCode] = true
                        clearPendingLocked(keyCode)

                        val action = getActionLocked(keyCode, MODE_LONG_PRESS)
                        log("Key $keyCode long-press -> $action")
                        actionToExecute = action to keyCode
                    } else {
                        val downEvent = pendingDownEvents.remove(keyCode)
                        pendingUpEvents.remove(keyCode)
                        pendingUpEventTimes.remove(keyCode)
                        keyStates[keyCode] = STATE_PASSTHROUGH
                        if (downEvent != null) {
                            if (isVolumeKey(keyCode)) {
                                volumePassthroughUntil = nowMs() + VOLUME_PASSTHROUGH_DURATION
                            }
                            val handoffTime = uptimeMs()
                            passthroughDownTimes[keyCode] = handoffTime
                            eventToForward = listOf(rebaseEvent(downEvent, handoffTime, handoffTime))
                        }
                    }
                    wakeLockAction = computeWakeLockActionLocked(
                        wasScreenOffQueueing = wasScreenOffQueueing,
                        dispatchedAction = actionToExecute != null,
                    )
                }
            }

            eventToForward?.let(onForwardEvents)
            actionToExecute?.let { (action, code) -> dispatchAction(action, code) }
            if (wakeLockAction != KeyWakeLockAction.NONE) {
                onWakeLockAction(wakeLockAction)
            }
        }
        longPressRunnables[keyCode] = runnable
        scheduleDelay(longPressTimeout, runnable)
    }

    private fun cancelLongPressTimeoutLocked(keyCode: Int) {
        longPressRunnables.remove(keyCode)?.let(cancelDelay)
    }

    private fun startDoubleTapTimeoutLocked(keyCode: Int) {
        cancelDoubleTapTimeoutLocked(keyCode)

        lateinit var runnable: Runnable
        runnable = Runnable {
            var actionToExecute: Pair<String, Int>? = null
            var eventsToForward: List<E>? = null
            var wakeLockAction = KeyWakeLockAction.NONE

            synchronized(lock) {
                if (doubleTapRunnables[keyCode] !== runnable) return@Runnable
                doubleTapRunnables.remove(keyCode)

                if (keyStates[keyCode] == STATE_WAITING_DOUBLE) {
                    val wasScreenOffQueueing = needsScreenOffWakeLock(keyCode, activeKeyPaths[keyCode])
                    if (hasActionLocked(keyCode, MODE_CLICK)) {
                        clearKeyStateLocked(keyCode)
                        val action = getActionLocked(keyCode, MODE_CLICK)
                        log("Key $keyCode click (after double-tap timeout) -> $action")
                        actionToExecute = action to keyCode
                    } else {
                        eventsToForward = drainPendingForForwardLocked(keyCode)
                        clearKeyStateLocked(keyCode)
                    }
                    wakeLockAction = computeWakeLockActionLocked(
                        wasScreenOffQueueing = wasScreenOffQueueing,
                        dispatchedAction = actionToExecute != null,
                    )
                }
            }

            eventsToForward?.let { list ->
                if (list.isNotEmpty()) onForwardEvents(list)
            }
            actionToExecute?.let { (action, code) -> dispatchAction(action, code) }
            if (wakeLockAction != KeyWakeLockAction.NONE) {
                onWakeLockAction(wakeLockAction)
            }
        }
        doubleTapRunnables[keyCode] = runnable
        scheduleDelay(doubleTapTimeout, runnable)
    }

    private fun cancelDoubleTapTimeoutLocked(keyCode: Int) {
        doubleTapRunnables.remove(keyCode)?.let(cancelDelay)
    }

    private fun drainPendingForForwardLocked(keyCode: Int): List<E> {
        val list = mutableListOf<E>()
        pendingDownEvents.remove(keyCode)?.let(list::add)
        pendingUpEvents.remove(keyCode)?.let(list::add)
        pendingUpEventTimes.remove(keyCode)
        if (list.isNotEmpty() && isVolumeKey(keyCode)) {
            volumePassthroughUntil = nowMs() + VOLUME_PASSTHROUGH_DURATION
        }
        return list
    }

    private fun clearPendingLocked(keyCode: Int) {
        pendingDownEvents.remove(keyCode)
        pendingUpEvents.remove(keyCode)
        pendingUpEventTimes.remove(keyCode)
    }

    private fun clearKeyStateLocked(keyCode: Int) {
        keyStates[keyCode] = STATE_IDLE
        activeKeyPaths.remove(keyCode)
        keyDownTimes.remove(keyCode)
        keyConsumed[keyCode] = false
        clearPendingLocked(keyCode)
    }

    private fun computeWakeLockActionLocked(
        wasScreenOffQueueing: Boolean,
        dispatchedAction: Boolean,
    ): KeyWakeLockAction {
        if (activeKeyPaths.entries.any { (code, path) -> needsScreenOffWakeLock(code, path) }) {
            return KeyWakeLockAction.ACQUIRE_RECOGNITION
        }
        if (!wasScreenOffQueueing) return KeyWakeLockAction.NONE
        return if (dispatchedAction) KeyWakeLockAction.ACQUIRE_DISPATCH_GRACE else KeyWakeLockAction.RELEASE
    }

    private fun dispatchAction(action: String, keyCode: Int) {
        if (action.isEmpty() || action == "none") return
        onExecuteAction(action, keyCode)
    }
}

/**
 * KeyManager handles hardware key interception and action triggering in system_server.
 * Keys are trigger sources parallel to gestures.
 *
 * - Power key (`KEYCODE_POWER`): intercepted in `InputManagerService.interceptKeyBeforeQueueing`
 *   while interactive because `PhoneWindowManager` strips `ACTION_PASS_TO_USER` there.
 * - Volume keys (`KEYCODE_VOLUME_UP` / `KEYCODE_VOLUME_DOWN`): intercepted in
 *   `interceptKeyBeforeQueueing` while screen-off (when enabled) and in
 *   `filterInputEvent` / `interceptKeyBeforeDispatching` while screen-on.
 */
object KeyManager {

    private const val TAG = "EdgeX"
    private const val WAKE_LOCK_EXTRA_MS = 2000L
    private const val WAKE_LOCK_DISPATCH_GRACE_MS = 500L

    const val MODE_CLICK = KeyInterceptStateMachine.MODE_CLICK
    const val MODE_DOUBLE_CLICK = KeyInterceptStateMachine.MODE_DOUBLE_CLICK
    const val MODE_LONG_PRESS = KeyInterceptStateMachine.MODE_LONG_PRESS

    val SUPPORTED_KEYS = KeyInterceptStateMachine.SUPPORTED_KEYS

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var lastContext: Context? = null

    @Volatile
    private var cachedInputManagerInject: Method? = null

    @Volatile
    private var cachedGlobalInject: Pair<Any, Method>? = null

    // Partial wake lock for screen-off deferred recognition (long-press / double-tap)
    @Volatile
    private var recognitionWakeLock: PowerManager.WakeLock? = null
    private val wakeLockMonitor = Any()

    private val stateMachine = KeyInterceptStateMachine<KeyEvent>(
        scheduleDelay = { delayMs, runnable -> handler.postDelayed(runnable, delayMs) },
        cancelDelay = { runnable -> handler.removeCallbacks(runnable) },
        onExecuteAction = { action, _ ->
            val ctx = lastContext ?: return@KeyInterceptStateMachine
            GestureManager.executeKeyAction(action, ctx)
        },
        onForwardEvents = { events ->
            val ctx = lastContext ?: return@KeyInterceptStateMachine
            // Mark injected events synchronously before posting so any immediate
            // dispatch is recognized, and perform reflection/JNI injection on main thread
            // off the InputDispatcher thread in strict FIFO order.
            events.forEach(::markInjectedEvent)
            if (Looper.myLooper() == Looper.getMainLooper()) {
                events.forEach { injectKeyEvent(it, ctx) }
            } else {
                handler.post {
                    events.forEach { injectKeyEvent(it, ctx) }
                }
            }
        },
        onWakeLockAction = { action ->
            applyWakeLock(lastContext, action)
        },
        rebaseEvent = { event, downTime, eventTime ->
            KeyEvent(
                downTime,
                eventTime,
                event.action,
                event.keyCode,
                event.repeatCount,
                event.metaState,
                event.deviceId,
                event.scanCode,
                event.flags,
                event.source,
            )
        },
        uptimeMs = { android.os.SystemClock.uptimeMillis() },
        log = { message -> XposedBridge.log("$TAG: $message") },
    )

    fun init(context: Context) {
        lastContext = context
        stateMachine.longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        stateMachine.doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
        ensureWakeLock(context)
    }

    fun updateConfig(configCache: Map<String, String>) {
        stateMachine.updateConfig(configCache)
    }

    fun markInjectedEvent(event: KeyEvent) {
        stateMachine.markInjectedEvent(event.keyCode, event.action, event.eventTime)
    }

    fun handleKeyEvent(
        event: KeyEvent,
        context: Context,
        policyFlags: Int = 0,
        source: KeyInterceptionPath = KeyInterceptionPath.DISPATCH,
        isScreenInteractive: Boolean = true,
        isKeyguardLocked: Boolean = false,
    ): Boolean {
        lastContext = context
        val info = KeyInterceptStateMachine.EventInfo(
            keyCode = event.keyCode,
            action = event.action,
            repeatCount = event.repeatCount,
            downTime = event.downTime,
            eventTime = event.eventTime,
            policyFlags = policyFlags,
        )
        return stateMachine.handleKeyEvent(
            info = info,
            copyEvent = { KeyEvent(event) },
            source = source,
            isScreenInteractive = isScreenInteractive,
            isKeyguardLocked = isKeyguardLocked,
        )
    }

    fun reset() {
        stateMachine.reset()
    }

    private fun ensureWakeLock(context: Context?): PowerManager.WakeLock? {
        recognitionWakeLock?.let { return it }
        val ctx = context ?: return null
        return synchronized(wakeLockMonitor) {
            recognitionWakeLock ?: runCatching {
                val pm = ctx.getSystemService(PowerManager::class.java) ?: return@runCatching null
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EdgeX:KeyRecognition").apply {
                    setReferenceCounted(false)
                }
            }.getOrNull()?.also { recognitionWakeLock = it }
        }
    }

    private fun applyWakeLock(context: Context?, action: KeyWakeLockAction) {
        if (action == KeyWakeLockAction.NONE) return
        val wl = ensureWakeLock(context) ?: return
        runCatching {
            synchronized(wakeLockMonitor) {
                when (action) {
                    KeyWakeLockAction.ACQUIRE_RECOGNITION -> {
                        val timeout = maxOf(
                            stateMachine.longPressTimeout,
                            stateMachine.doubleTapTimeout,
                        ) + WAKE_LOCK_EXTRA_MS
                        wl.acquire(timeout)
                    }
                    KeyWakeLockAction.ACQUIRE_DISPATCH_GRACE -> {
                        wl.acquire(WAKE_LOCK_DISPATCH_GRACE_MS)
                    }
                    KeyWakeLockAction.RELEASE -> {
                        if (wl.isHeld) wl.release()
                    }
                    KeyWakeLockAction.NONE -> Unit
                }
            }
        }
    }

    private fun injectKeyEvent(event: KeyEvent, context: Context) {
        try {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE)
            if (inputManager != null) {
                val injectMethod = cachedInputManagerInject ?: inputManager.javaClass.getMethod(
                    "injectInputEvent",
                    Class.forName("android.view.InputEvent"),
                    Int::class.javaPrimitiveType,
                ).also { cachedInputManagerInject = it }
                injectMethod.invoke(inputManager, event, 0) // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
                return
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: InputManager inject failed: ${t.message}")
        }

        try {
            val (global, injectMethod) = cachedGlobalInject ?: run {
                val globalCls = Class.forName("android.hardware.input.InputManagerGlobal")
                val globalObj = globalCls.getMethod("getInstance").invoke(null)
                    ?: throw IllegalStateException("InputManagerGlobal.getInstance() returned null")
                val method = globalCls.getMethod(
                    "injectInputEvent",
                    Class.forName("android.view.InputEvent"),
                    Int::class.javaPrimitiveType,
                )
                (globalObj to method).also { cachedGlobalInject = it }
            }
            injectMethod.invoke(global, event, 0)
            return
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: InputManagerGlobal inject failed: ${t.message}")
        }

        XposedBridge.log("$TAG: Failed to inject key event - no method worked")
    }
}
