package com.fan.edgex.hook

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.ViewConfiguration
import com.fan.edgex.config.AppConfig
import de.robv.android.xposed.XposedBridge

/**
 * KeyManager handles hardware key interception and action triggering.
 * Keys are trigger sources parallel to gestures.
 *
 * Supported interaction modes:
 * - click (0): Quick press and release
 * - double_click (1): Two quick presses
 * - long_press (2): Hold beyond threshold
 */
object KeyManager {

    private const val TAG = "EdgeX"

    // Interaction modes (matching Xposed Edge Pro)
    const val MODE_CLICK = 0
    const val MODE_DOUBLE_CLICK = 1
    const val MODE_LONG_PRESS = 2

    // Supported keys (keyCode -> config index)
    val SUPPORTED_KEYS = mapOf(
        KeyEvent.KEYCODE_VOLUME_UP to 0,
        KeyEvent.KEYCODE_VOLUME_DOWN to 1,
        KeyEvent.KEYCODE_POWER to 2
    )

    // State machine states
    private const val STATE_IDLE = 0
    private const val STATE_PRESSED = 1
    private const val STATE_WAITING_DOUBLE = 2

    // WindowManagerPolicyConstants.FLAG_FILTERED (0x04000000)
    private const val POLICY_FLAG_FILTERED = 0x04000000
    private const val MAX_TRACKED_INJECTED_EVENTS = 32
    private const val VOLUME_PASSTHROUGH_DURATION = 3000L
    private const val WAKE_LOCK_EXTRA_MS = 2000L
    private const val WAKE_LOCK_DISPATCH_GRACE_MS = 500L

    private data class KeyConfig(
        val keysEnabled: Boolean = false,
        val keyEnabled: Map<Int, Boolean> = emptyMap(),
        val keyActions: Map<String, String> = emptyMap(),
        val allowScreenOff: Boolean = true,
        val allowLocked: Boolean = true,
    ) {
        fun getAction(keyCode: Int, mode: Int): String =
            keyActions["${keyCode}_$mode"] ?: ""

        fun hasAction(keyCode: Int, mode: Int): Boolean {
            val action = getAction(keyCode, mode)
            return action.isNotEmpty() && action != "none"
        }

        fun hasAnyAction(keyCode: Int): Boolean =
            hasAction(keyCode, MODE_CLICK) ||
                hasAction(keyCode, MODE_DOUBLE_CLICK) ||
                hasAction(keyCode, MODE_LONG_PRESS)
    }

    private enum class WakeLockAction {
        NONE,
        ACQUIRE_RECOGNITION,
        ACQUIRE_DISPATCH_GRACE,
        RELEASE,
    }

    private data class StepEffects(
        val consumed: Boolean = false,
        val eventsToInject: List<KeyEvent> = emptyList(),
        val actionToExecute: String? = null,
        val wakeLockAction: WakeLockAction = WakeLockAction.NONE,
    )

    // Atomically published immutable config snapshot (written on config/broadcast threads, read on input/main threads)
    @Volatile
    private var config = KeyConfig()

    // Guards all mutable runtime state below
    private val stateLock = Any()

    // Current state and owning interception path per key
    private val keyStates = mutableMapOf<Int, Int>()
    private val activeKeyPaths = mutableMapOf<Int, KeyInterceptionPath>()

    // Track press times for timing calculations
    private val keyDownTimes = mutableMapOf<Int, Long>()

    // Store pending KeyEvents for forwarding
    private val pendingDownEvents = mutableMapOf<Int, KeyEvent>()
    private val pendingUpEvents = mutableMapOf<Int, KeyEvent>()

    // Track if we consumed the key (should not forward)
    private val keyConsumed = mutableMapOf<Int, Boolean>()

    // Track injected events to avoid infinite loop (ordered set for bounded eviction)
    private val injectedEventTimes = LinkedHashSet<Long>()

    // Volume key passthrough while volume UI is active
    private var volumePassthroughUntil = 0L

    // Timeouts
    @Volatile
    private var longPressTimeout = 500L
    @Volatile
    private var doubleTapTimeout = 300L

    private val handler = Handler(Looper.getMainLooper())

    // Runnables for timeouts (guarded by stateLock)
    private val longPressRunnables = mutableMapOf<Int, Runnable>()
    private val doubleTapRunnables = mutableMapOf<Int, Runnable>()

    // Partial wake lock for screen-off deferred recognition (long-press / double-tap)
    @Volatile
    private var recognitionWakeLock: PowerManager.WakeLock? = null
    private val wakeLockMonitor = Any()

    private fun isVolumeKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    /**
     * Initialize timeouts and wake lock from system configuration.
     */
    fun init(context: Context) {
        longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
        ensureWakeLock(context)
    }

    /**
     * Update configuration from cache atomically.
     */
    fun updateConfig(configCache: Map<String, String>) {
        val actions = mutableMapOf<String, String>()
        val enabledMap = mutableMapOf<Int, Boolean>()

        for (keyCode in SUPPORTED_KEYS.keys) {
            val click = configCache[AppConfig.keyAction(keyCode, "click")] ?: ""
            val doubleClick = configCache[AppConfig.keyAction(keyCode, "double_click")] ?: ""
            val longPress = configCache[AppConfig.keyAction(keyCode, "long_press")] ?: ""
            actions["${keyCode}_$MODE_CLICK"] = click
            actions["${keyCode}_$MODE_DOUBLE_CLICK"] = doubleClick
            actions["${keyCode}_$MODE_LONG_PRESS"] = longPress

            val enabledValue = configCache[AppConfig.keyEnabled(keyCode)]
            enabledMap[keyCode] = if (enabledValue != null) {
                enabledValue == "true"
            } else {
                AppConfig.isActiveActionValue(click) ||
                    AppConfig.isActiveActionValue(doubleClick) ||
                    AppConfig.isActiveActionValue(longPress)
            }
        }

        val allowScreenOff = TriggerConditionPolicy.parseTriggerEnabled(configCache[AppConfig.KEYS_TRIGGER_SCREEN_OFF])
        val allowLocked = allowScreenOff || TriggerConditionPolicy.parseTriggerEnabled(configCache[AppConfig.KEYS_TRIGGER_LOCKED])

        config = KeyConfig(
            keysEnabled = configCache[AppConfig.KEYS_ENABLED] == "true",
            keyEnabled = enabledMap,
            keyActions = actions,
            allowScreenOff = allowScreenOff,
            allowLocked = allowLocked,
        )
    }

    private fun copyKeyEvent(event: KeyEvent): KeyEvent = KeyEvent(event)

    fun markInjectedEvent(event: KeyEvent) {
        synchronized(stateLock) {
            markInjectedEventTimeLocked(event.eventTime)
        }
    }

    private fun markInjectedEventTimeLocked(eventTime: Long) {
        if (injectedEventTimes.size >= MAX_TRACKED_INJECTED_EVENTS) {
            val iterator = injectedEventTimes.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        injectedEventTimes.add(eventTime)
    }

    /**
     * Handle key event from interceptKeyBeforeQueueing (QUEUEING) or
     * filterInputEvent / interceptKeyBeforeDispatching (DISPATCH).
     * Returns true if event should be consumed (not forwarded to system).
     */
    fun handleKeyEvent(
        event: KeyEvent,
        context: Context,
        policyFlags: Int = 0,
        source: KeyInterceptionPath = KeyInterceptionPath.DISPATCH,
        isScreenInteractive: Boolean = true,
        isKeyguardLocked: Boolean = false,
    ): Boolean {
        val keyCode = event.keyCode
        val eventTime = event.eventTime

        if (source == KeyInterceptionPath.DISPATCH && (policyFlags and POLICY_FLAG_FILTERED) != 0) {
            return false
        }

        val currentConfig = config

        val effects = synchronized(stateLock) {
            if (injectedEventTimes.remove(eventTime)) {
                return@synchronized StepEffects(consumed = false)
            }

            if (!SUPPORTED_KEYS.containsKey(keyCode)) {
                return@synchronized StepEffects(consumed = false)
            }

            val activePath = activeKeyPaths[keyCode]
            if (activePath == null) {
                if (!currentConfig.keysEnabled) return@synchronized StepEffects(consumed = false)
                if (currentConfig.keyEnabled[keyCode] != true) return@synchronized StepEffects(consumed = false)
                if (!currentConfig.hasAnyAction(keyCode)) return@synchronized StepEffects(consumed = false)
            }

            if (!TriggerConditionPolicy.canAcceptKeyEvent(
                    source = source,
                    activePath = activePath,
                    isScreenInteractive = isScreenInteractive,
                    isKeyguardLocked = isKeyguardLocked,
                    allowScreenOff = currentConfig.allowScreenOff,
                    allowLocked = currentConfig.allowLocked,
                )
            ) {
                return@synchronized StepEffects(consumed = false)
            }

            if (activePath == null &&
                isScreenInteractive &&
                isVolumeKey(keyCode) &&
                System.currentTimeMillis() < volumePassthroughUntil
            ) {
                volumePassthroughUntil = System.currentTimeMillis() + VOLUME_PASSTHROUGH_DURATION
                return@synchronized StepEffects(consumed = false)
            }

            when (event.action) {
                KeyEvent.ACTION_DOWN -> handleKeyDownLocked(keyCode, event, context, source, currentConfig)
                KeyEvent.ACTION_UP -> handleKeyUpLocked(keyCode, event, context, currentConfig)
                else -> StepEffects(consumed = false)
            }
        }

        applyStepEffects(effects, context)
        return effects.consumed
    }

    private fun handleKeyDownLocked(
        keyCode: Int,
        event: KeyEvent,
        context: Context,
        source: KeyInterceptionPath,
        currentConfig: KeyConfig,
    ): StepEffects {
        val repeatCount = event.repeatCount
        val currentState = keyStates[keyCode] ?: STATE_IDLE

        if (repeatCount > 0) {
            if (currentState == STATE_PRESSED) {
                return StepEffects(consumed = true)
            }
            if (currentState == STATE_IDLE) {
                return startNewPressLocked(keyCode, event, context, source, currentConfig)
            }
            return StepEffects(consumed = false)
        }

        return when (currentState) {
            STATE_IDLE -> startNewPressLocked(keyCode, event, context, source, currentConfig)
            STATE_WAITING_DOUBLE -> {
                val wasQueueing = activeKeyPaths[keyCode] == KeyInterceptionPath.QUEUEING
                cancelDoubleTapTimeoutLocked(keyCode)

                val firstUpEvent = pendingUpEvents[keyCode]
                val timeDiff = if (firstUpEvent != null) event.eventTime - firstUpEvent.eventTime else Long.MAX_VALUE

                if (timeDiff < doubleTapTimeout && currentConfig.hasAction(keyCode, MODE_DOUBLE_CLICK)) {
                    keyStates[keyCode] = STATE_PRESSED
                    keyConsumed[keyCode] = true
                    pendingDownEvents.remove(keyCode)
                    pendingUpEvents.remove(keyCode)

                    val action = currentConfig.getAction(keyCode, MODE_DOUBLE_CLICK)
                    XposedBridge.log("$TAG: Key $keyCode double-click -> $action")
                    StepEffects(
                        consumed = true,
                        actionToExecute = action,
                        wakeLockAction = computeWakeLockActionLocked(wasQueueing, dispatchedAction = true),
                    )
                } else {
                    val forwarded = collectForwardEventsLocked(keyCode)
                    val nextPress = startNewPressLocked(keyCode, event, context, source, currentConfig)
                    nextPress.copy(eventsToInject = forwarded + nextPress.eventsToInject)
                }
            }
            STATE_PRESSED -> StepEffects(consumed = true)
            else -> StepEffects(consumed = keyConsumed[keyCode] == true)
        }
    }

    private fun startNewPressLocked(
        keyCode: Int,
        event: KeyEvent,
        context: Context,
        source: KeyInterceptionPath,
        currentConfig: KeyConfig,
    ): StepEffects {
        keyStates[keyCode] = STATE_PRESSED
        activeKeyPaths[keyCode] = source
        keyDownTimes[keyCode] = event.downTime
        pendingDownEvents[keyCode] = copyKeyEvent(event)
        keyConsumed[keyCode] = false

        if (currentConfig.hasAction(keyCode, MODE_LONG_PRESS)) {
            startLongPressTimeoutLocked(keyCode, context)
        }

        val wakeLockAction = if (source == KeyInterceptionPath.QUEUEING) {
            WakeLockAction.ACQUIRE_RECOGNITION
        } else {
            WakeLockAction.NONE
        }
        return StepEffects(consumed = true, wakeLockAction = wakeLockAction)
    }

    private fun handleKeyUpLocked(
        keyCode: Int,
        event: KeyEvent,
        context: Context,
        currentConfig: KeyConfig,
    ): StepEffects {
        val currentState = keyStates[keyCode] ?: STATE_IDLE
        val wasQueueing = activeKeyPaths[keyCode] == KeyInterceptionPath.QUEUEING

        if (currentState != STATE_PRESSED) {
            return StepEffects(consumed = keyConsumed[keyCode] == true)
        }

        cancelLongPressTimeoutLocked(keyCode)

        if ((event.flags and KeyEvent.FLAG_CANCELED) != 0) {
            clearKeyStateLocked(keyCode)
            return StepEffects(
                consumed = true,
                wakeLockAction = computeWakeLockActionLocked(wasQueueing, dispatchedAction = false),
            )
        }

        val downTime = keyDownTimes[keyCode] ?: event.eventTime
        val pressDuration = event.eventTime - downTime

        if (keyConsumed[keyCode] == true) {
            clearKeyStateLocked(keyCode)
            return StepEffects(
                consumed = true,
                wakeLockAction = computeWakeLockActionLocked(wasQueueing, dispatchedAction = false),
            )
        }

        if (pressDuration < longPressTimeout) {
            if (currentConfig.hasAction(keyCode, MODE_DOUBLE_CLICK)) {
                keyStates[keyCode] = STATE_WAITING_DOUBLE
                pendingUpEvents[keyCode] = copyKeyEvent(event)
                startDoubleTapTimeoutLocked(keyCode, context)
                return StepEffects(
                    consumed = true,
                    wakeLockAction = if (wasQueueing) WakeLockAction.ACQUIRE_RECOGNITION else WakeLockAction.NONE,
                )
            }

            if (currentConfig.hasAction(keyCode, MODE_CLICK)) {
                clearKeyStateLocked(keyCode)
                val action = currentConfig.getAction(keyCode, MODE_CLICK)
                XposedBridge.log("$TAG: Key $keyCode click -> $action")
                return StepEffects(
                    consumed = true,
                    actionToExecute = action,
                    wakeLockAction = computeWakeLockActionLocked(wasQueueing, dispatchedAction = true),
                )
            }

            pendingUpEvents[keyCode] = copyKeyEvent(event)
            val forwarded = collectForwardEventsLocked(keyCode)
            clearKeyStateLocked(keyCode)
            return StepEffects(
                consumed = true,
                eventsToInject = forwarded,
                wakeLockAction = computeWakeLockActionLocked(wasQueueing, dispatchedAction = false),
            )
        } else {
            if (currentConfig.hasAction(keyCode, MODE_CLICK) && pendingDownEvents.containsKey(keyCode)) {
                clearKeyStateLocked(keyCode)
                val action = currentConfig.getAction(keyCode, MODE_CLICK)
                return StepEffects(
                    consumed = true,
                    actionToExecute = action,
                    wakeLockAction = computeWakeLockActionLocked(wasQueueing, dispatchedAction = true),
                )
            }
            pendingUpEvents[keyCode] = copyKeyEvent(event)
            val forwarded = collectForwardEventsLocked(keyCode)
            clearKeyStateLocked(keyCode)
            return StepEffects(
                consumed = true,
                eventsToInject = forwarded,
                wakeLockAction = computeWakeLockActionLocked(wasQueueing, dispatchedAction = false),
            )
        }
    }

    private fun startLongPressTimeoutLocked(keyCode: Int, context: Context) {
        cancelLongPressTimeoutLocked(keyCode)

        val runnable = Runnable {
            val currentConfig = config
            val effects = synchronized(stateLock) {
                longPressRunnables.remove(keyCode)
                if (keyStates[keyCode] != STATE_PRESSED || keyConsumed[keyCode] == true) {
                    return@synchronized StepEffects()
                }
                val wasQueueing = activeKeyPaths[keyCode] == KeyInterceptionPath.QUEUEING
                if (currentConfig.hasAction(keyCode, MODE_LONG_PRESS)) {
                    keyConsumed[keyCode] = true
                    pendingDownEvents.remove(keyCode)

                    val action = currentConfig.getAction(keyCode, MODE_LONG_PRESS)
                    XposedBridge.log("$TAG: Key $keyCode long-press -> $action")
                    StepEffects(
                        actionToExecute = action,
                        wakeLockAction = computeWakeLockActionLocked(wasQueueing, dispatchedAction = true),
                    )
                } else {
                    val downEvent = pendingDownEvents.remove(keyCode)
                    val events = if (downEvent != null) {
                        markInjectedEventTimeLocked(downEvent.eventTime)
                        if (isVolumeKey(downEvent.keyCode)) {
                            volumePassthroughUntil = System.currentTimeMillis() + VOLUME_PASSTHROUGH_DURATION
                        }
                        listOf(downEvent)
                    } else {
                        emptyList()
                    }
                    StepEffects(eventsToInject = events)
                }
            }
            applyStepEffects(effects, context)
        }
        longPressRunnables[keyCode] = runnable
        handler.postDelayed(runnable, longPressTimeout)
    }

    private fun cancelLongPressTimeoutLocked(keyCode: Int) {
        longPressRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }
    }

    private fun startDoubleTapTimeoutLocked(keyCode: Int, context: Context) {
        cancelDoubleTapTimeoutLocked(keyCode)

        val runnable = Runnable {
            val currentConfig = config
            val effects = synchronized(stateLock) {
                doubleTapRunnables.remove(keyCode)
                if (keyStates[keyCode] != STATE_WAITING_DOUBLE) {
                    return@synchronized StepEffects()
                }
                val wasQueueing = activeKeyPaths[keyCode] == KeyInterceptionPath.QUEUEING
                if (currentConfig.hasAction(keyCode, MODE_CLICK)) {
                    clearKeyStateLocked(keyCode)
                    val action = currentConfig.getAction(keyCode, MODE_CLICK)
                    XposedBridge.log("$TAG: Key $keyCode click (after double-tap timeout) -> $action")
                    StepEffects(
                        actionToExecute = action,
                        wakeLockAction = computeWakeLockActionLocked(wasQueueing, dispatchedAction = true),
                    )
                } else {
                    val forwarded = collectForwardEventsLocked(keyCode)
                    clearKeyStateLocked(keyCode)
                    StepEffects(
                        eventsToInject = forwarded,
                        wakeLockAction = computeWakeLockActionLocked(wasQueueing, dispatchedAction = false),
                    )
                }
            }
            applyStepEffects(effects, context)
        }
        doubleTapRunnables[keyCode] = runnable
        handler.postDelayed(runnable, doubleTapTimeout)
    }

    private fun cancelDoubleTapTimeoutLocked(keyCode: Int) {
        doubleTapRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }
    }

    private fun collectForwardEventsLocked(keyCode: Int): List<KeyEvent> {
        val result = mutableListOf<KeyEvent>()
        pendingDownEvents.remove(keyCode)?.let { event ->
            markInjectedEventTimeLocked(event.eventTime)
            if (isVolumeKey(event.keyCode)) {
                volumePassthroughUntil = System.currentTimeMillis() + VOLUME_PASSTHROUGH_DURATION
            }
            result.add(event)
        }
        pendingUpEvents.remove(keyCode)?.let { event ->
            markInjectedEventTimeLocked(event.eventTime)
            if (isVolumeKey(event.keyCode)) {
                volumePassthroughUntil = System.currentTimeMillis() + VOLUME_PASSTHROUGH_DURATION
            }
            result.add(event)
        }
        return result
    }

    private fun clearKeyStateLocked(keyCode: Int) {
        keyStates.remove(keyCode)
        activeKeyPaths.remove(keyCode)
        keyDownTimes.remove(keyCode)
        pendingDownEvents.remove(keyCode)
        pendingUpEvents.remove(keyCode)
        keyConsumed.remove(keyCode)
    }

    private fun computeWakeLockActionLocked(wasQueueing: Boolean, dispatchedAction: Boolean): WakeLockAction {
        if (activeKeyPaths.values.any { it == KeyInterceptionPath.QUEUEING }) {
            return WakeLockAction.ACQUIRE_RECOGNITION
        }
        if (!wasQueueing) return WakeLockAction.NONE
        return if (dispatchedAction) WakeLockAction.ACQUIRE_DISPATCH_GRACE else WakeLockAction.RELEASE
    }

    private fun applyStepEffects(effects: StepEffects, context: Context) {
        for (event in effects.eventsToInject) {
            injectKeyEvent(event, context)
        }
        effects.actionToExecute?.let { action ->
            executeAction(action, context)
        }
        applyWakeLock(context, effects.wakeLockAction)
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

    private fun applyWakeLock(context: Context?, action: WakeLockAction) {
        if (action == WakeLockAction.NONE) return
        val wl = ensureWakeLock(context) ?: return
        runCatching {
            synchronized(wakeLockMonitor) {
                when (action) {
                    WakeLockAction.ACQUIRE_RECOGNITION -> {
                        val timeout = maxOf(longPressTimeout, doubleTapTimeout) + WAKE_LOCK_EXTRA_MS
                        wl.acquire(timeout)
                    }
                    WakeLockAction.ACQUIRE_DISPATCH_GRACE -> {
                        wl.acquire(WAKE_LOCK_DISPATCH_GRACE_MS)
                    }
                    WakeLockAction.RELEASE -> {
                        if (wl.isHeld) wl.release()
                    }
                    WakeLockAction.NONE -> Unit
                }
            }
        }
    }

    private fun injectKeyEvent(event: KeyEvent, context: Context) {
        try {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE)
            if (inputManager != null) {
                val injectMethod = inputManager.javaClass.getMethod(
                    "injectInputEvent",
                    Class.forName("android.view.InputEvent"),
                    Int::class.javaPrimitiveType
                )
                injectMethod.invoke(inputManager, event, 0)
                return
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: InputManager inject failed: ${t.message}")
        }

        try {
            val globalCls = Class.forName("android.hardware.input.InputManagerGlobal")
            val getInstance = globalCls.getMethod("getInstance")
            val global = getInstance.invoke(null)
            val injectMethod = globalCls.getMethod(
                "injectInputEvent",
                Class.forName("android.view.InputEvent"),
                Int::class.javaPrimitiveType
            )
            injectMethod.invoke(global, event, 0)
            return
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: InputManagerGlobal inject failed: ${t.message}")
        }

        XposedBridge.log("$TAG: Failed to inject key event - no method worked")
    }

    private fun executeAction(action: String, context: Context) {
        if (action.isEmpty() || action == "none") return
        GestureManager.executeKeyAction(action, context)
    }

    /**
     * Reset all state (e.g., on screen off or config change).
     * Ensures no stale key tracking survives a lock/unlock cycle.
     */
    fun reset() {
        synchronized(stateLock) {
            for (keyCode in SUPPORTED_KEYS.keys) {
                cancelLongPressTimeoutLocked(keyCode)
                cancelDoubleTapTimeoutLocked(keyCode)
            }
            keyStates.clear()
            activeKeyPaths.clear()
            keyDownTimes.clear()
            pendingDownEvents.clear()
            pendingUpEvents.clear()
            keyConsumed.clear()
            injectedEventTimes.clear()
            volumePassthroughUntil = 0L
        }
        applyWakeLock(null, WakeLockAction.RELEASE)
    }
}
