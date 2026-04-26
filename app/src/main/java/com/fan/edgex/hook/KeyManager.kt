package com.fan.edgex.hook

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import com.fan.edgex.config.AppConfig
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * KeyManager handles hardware key interception and action triggering.
 * Keys are trigger sources parallel to gestures.
 * 
 * Following Xposed Edge Pro's approach:
 * - Save MethodHookParam to forward events later if needed
 * - Use g(param) to invoke original method when we don't want to consume
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
    
    // Android 10+ requires copying KeyEvent (like Xposed Edge Pro's z.E flag)
    private val needsCopyEvent = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q

    // Current state per key
    private val keyStates = mutableMapOf<Int, Int>()
    
    // Track press times for timing calculations
    private val keyDownTimes = mutableMapOf<Int, Long>()
    
    // Store pending KeyEvents for forwarding (like Xposed Edge Pro's H and I)
    // H = DOWN event, I = UP event (for double-tap waiting)
    private val pendingDownEvents = mutableMapOf<Int, KeyEvent>()
    private val pendingUpEvents = mutableMapOf<Int, KeyEvent>()
    
    // Track if we consumed the key (should not forward)
    private val keyConsumed = mutableMapOf<Int, Boolean>()
    
    // Track injected events to avoid infinite loop
    // We store (downTime, eventTime) pairs of events we injected
    private val injectedEventTimes = mutableSetOf<Long>()

    // Volume key passthrough: after our action fires for a volume key,
    // the system volume panel is already showing (handled earlier in pipeline).
    // Subsequent volume presses should pass through for normal volume control.
    private var volumePassthroughUntil = 0L
    private const val VOLUME_PASSTHROUGH_DURATION = 3000L  // matches volume panel auto-hide

    private fun isVolumeKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
    }

    // Timeouts
    private var longPressTimeout = 500L
    private var doubleTapTimeout = 300L

    private val handler = Handler(Looper.getMainLooper())
    
    // Runnables for timeouts
    private val longPressRunnables = mutableMapOf<Int, Runnable>()
    private val doubleTapRunnables = mutableMapOf<Int, Runnable>()

    // Config cache
    private var keysEnabled = false
    private val keyEnabled = mutableMapOf<Int, Boolean>()
    private val keyActions = mutableMapOf<String, String>() // "keyCode_mode" -> action

    /**
     * Initialize timeouts from system configuration.
     */
    fun init(context: Context) {
        longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
        XposedBridge.log("$TAG: KeyManager init - longPress=${longPressTimeout}ms, doubleTap=${doubleTapTimeout}ms")
    }

    /**
     * Update configuration from cache.
     */
    fun updateConfig(configCache: Map<String, String>) {
        keysEnabled = configCache[AppConfig.KEYS_ENABLED] == "true"

        for (keyCode in SUPPORTED_KEYS.keys) {
            keyEnabled[keyCode] = configCache[AppConfig.keyEnabled(keyCode)] == "true"
            keyActions["${keyCode}_$MODE_CLICK"] = configCache[AppConfig.keyAction(keyCode, "click")] ?: ""
            keyActions["${keyCode}_$MODE_DOUBLE_CLICK"] = configCache[AppConfig.keyAction(keyCode, "double_click")] ?: ""
            keyActions["${keyCode}_$MODE_LONG_PRESS"] = configCache[AppConfig.keyAction(keyCode, "long_press")] ?: ""
        }
        
    }

    /**
     * Check if this key has any action configured.
     */
    private fun hasAnyAction(keyCode: Int): Boolean {
        return keyActions["${keyCode}_$MODE_CLICK"]?.isNotEmpty() == true ||
               keyActions["${keyCode}_$MODE_DOUBLE_CLICK"]?.isNotEmpty() == true ||
               keyActions["${keyCode}_$MODE_LONG_PRESS"]?.isNotEmpty() == true
    }

    /**
     * Get action for key and mode.
     */
    private fun getAction(keyCode: Int, mode: Int): String {
        return keyActions["${keyCode}_$mode"] ?: ""
    }

    /**
     * Check if key has action for specific mode.
     */
    private fun hasAction(keyCode: Int, mode: Int): Boolean {
        val action = getAction(keyCode, mode)
        return action.isNotEmpty() && action != "none"
    }

    /**
     * Copy KeyEvent for later forwarding (like Xposed Edge Pro's s() method).
     * On Android 10+, we need to copy the KeyEvent to avoid issues.
     */
    private fun copyKeyEvent(event: KeyEvent): KeyEvent {
        return if (needsCopyEvent) KeyEvent(event) else event
    }
    
    /**
     * Forward key events by injecting them via InputManager.
     * This is more reliable than invokeOriginalMethod, especially for delayed forwarding.
     * Like Xposed Edge Pro's approach using InputManager.injectInputEvent.
     */
    private fun injectKeyEvent(event: KeyEvent, context: Context) {
        // Mark this event as injected by us (using eventTime as identifier)
        markInjectedEvent(event)
        
        // Volume key passthrough: if we inject a volume key, it means we didn't consume it
        // and it's falling back to the system behavior (bringing up the volume UI).
        // We activate passthrough so subsequent presses control volume normally.
        if (isVolumeKey(event.keyCode)) {
            volumePassthroughUntil = System.currentTimeMillis() + VOLUME_PASSTHROUGH_DURATION
            XposedBridge.log("$TAG: KeyManager power/volume activity — entering passthrough for ${VOLUME_PASSTHROUGH_DURATION}ms")
        }
        
        // Clean up old entries (keep only last 10)
        
        try {
            // Try InputManager.getInstance()
            val inputManager = context.getSystemService(Context.INPUT_SERVICE)
            if (inputManager != null) {
                val injectMethod = inputManager.javaClass.getMethod(
                    "injectInputEvent",
                    Class.forName("android.view.InputEvent"),
                    Int::class.javaPrimitiveType
                )
                injectMethod.invoke(inputManager, event, 0) // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
                XposedBridge.log("$TAG: Injected key event via InputManager")
                return
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: InputManager inject failed: ${t.message}")
        }
        
        // Fallback: try InputManagerGlobal
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
            XposedBridge.log("$TAG: Injected key event via InputManagerGlobal")
            return
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: InputManagerGlobal inject failed: ${t.message}")
        }
        
        XposedBridge.log("$TAG: Failed to inject key event - no method worked")
    }

    fun markInjectedEvent(event: KeyEvent) {
        injectedEventTimes.add(event.eventTime)
    }
    
    /**
     * Forward saved key events by injecting them.
     * This replaces the old forwardParam() approach which didn't work reliably.
     */
    private fun forwardKeyEvents(keyCode: Int, context: Context) {
        val downEvent = pendingDownEvents[keyCode]
        val upEvent = pendingUpEvents[keyCode]
        
        
        if (downEvent != null) {
            injectKeyEvent(downEvent, context)
        }
        
        if (upEvent != null) {
            injectKeyEvent(upEvent, context)
        }
        
        pendingDownEvents.remove(keyCode)
        pendingUpEvents.remove(keyCode)
    }

    // Policy flag used to mark injected events (to avoid infinite loop)
    // Following Xposed Edge Pro's approach with 0x4000000
    private const val INJECTED_EVENT_FLAG = 0x4000000
    
    /**
     * Handle key event from interceptKeyBeforeDispatching hook.
     * Returns true if event should be consumed (not forwarded to system).
     */
    fun handleKeyEvent(event: KeyEvent, context: Context, param: XC_MethodHook.MethodHookParam, policyFlags: Int = 0): Boolean {
        val keyCode = event.keyCode
        val eventTime = event.eventTime
        
        
        // Skip events that we injected ourselves (to avoid infinite loop)
        // Method 1: Check policyFlags (if injection method supports it)
        if (policyFlags and INJECTED_EVENT_FLAG != 0) {
            XposedBridge.log("$TAG: Skipping injected event via policyFlags for keyCode=$keyCode")
            return false
        }
        
        // Method 2: Check if eventTime matches one we injected
        if (injectedEventTimes.contains(eventTime)) {
            XposedBridge.log("$TAG: Skipping injected event via eventTime for keyCode=$keyCode (time=$eventTime)")
            injectedEventTimes.remove(eventTime) // Clean up after recognition
            return false
        }
        
        // Check if keys feature is enabled
        if (!keysEnabled) {
            XposedBridge.log("$TAG: Keys feature disabled, not handling")
            return false
        }
        
        // Check if this key is supported
        if (!SUPPORTED_KEYS.containsKey(keyCode)) {
            return false
        }
        
        // Check if this specific key is enabled
        if (keyEnabled[keyCode] != true) {
            return false
        }
        
        // Check if key has any action configured - if not, don't intercept
        if (!hasAnyAction(keyCode)) {
            return false
        }

        // Volume key passthrough: if volume panel is likely showing
        // (we recently executed an action for a volume key), let
        // subsequent volume presses pass through for normal volume control.
        if (isVolumeKey(keyCode) && System.currentTimeMillis() < volumePassthroughUntil) {
            // Extend the passthrough window on each press (matching volume panel behavior)
            volumePassthroughUntil = System.currentTimeMillis() + VOLUME_PASSTHROUGH_DURATION
            XposedBridge.log("$TAG: Volume key $keyCode in passthrough window — letting through for volume control")
            return false
        }

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> handleKeyDown(keyCode, event, context, param)
            KeyEvent.ACTION_UP -> handleKeyUp(keyCode, event, context, param)
            else -> false
        }
    }

    /**
     * Handle KEY_DOWN event.
     * 
     * Key insight from Xposed Edge Pro:
     * - Use repeatCount to detect if this is a new press or a repeat
     * - repeatCount == 0 means first press
     * - repeatCount > 0 means key is being held down
     */
    private fun handleKeyDown(keyCode: Int, event: KeyEvent, context: Context, param: XC_MethodHook.MethodHookParam): Boolean {
        val repeatCount = event.repeatCount
        val currentState = keyStates[keyCode] ?: STATE_IDLE
        
        // If this is a repeat event (key held down)
        if (repeatCount > 0) {
            // If we're already tracking, keep consuming
            if (currentState == STATE_PRESSED) {
                return true
            }
            // If we're not tracking but this is the first event we see (missed repeat=0),
            // treat the first repeat as a new press
            if (currentState == STATE_IDLE) {
                return startNewPress(keyCode, event, context, param)
            }
            return false
        }
        
        // First press (repeatCount == 0)
        when (currentState) {
            STATE_IDLE -> {
                return startNewPress(keyCode, event, context, param)
            }
            STATE_WAITING_DOUBLE -> {
                // Second press within double-tap window
                cancelDoubleTapTimeout(keyCode)
                
                // Check if event times match double-tap timing
                val firstUpEvent = pendingUpEvents[keyCode]
                val timeDiff = if (firstUpEvent != null) event.eventTime - firstUpEvent.eventTime else Long.MAX_VALUE
                
                if (timeDiff < doubleTapTimeout && hasAction(keyCode, MODE_DOUBLE_CLICK)) {
                    // Execute double-click action
                    keyStates[keyCode] = STATE_PRESSED
                    keyConsumed[keyCode] = true
                    pendingDownEvents.remove(keyCode)
                    pendingUpEvents.remove(keyCode)
                    
                    val action = getAction(keyCode, MODE_DOUBLE_CLICK)
                    XposedBridge.log("$TAG: Key $keyCode double-click -> $action")
                    executeAction(action, context, keyCode)
                    return true
                } else {
                    // No double-click action or timing didn't match
                    // Forward the pending events via injection and start new press
                    forwardKeyEvents(keyCode, context)
                    return startNewPress(keyCode, event, context, param)
                }
            }
            STATE_PRESSED -> {
                // Still in pressed state, probably a duplicate event
                return true
            }
            else -> return keyConsumed[keyCode] == true
        }
    }

    /**
     * Start tracking a new key press.
     */
    private fun startNewPress(keyCode: Int, event: KeyEvent, context: Context, param: XC_MethodHook.MethodHookParam): Boolean {
        keyStates[keyCode] = STATE_PRESSED
        // Use downTime for more accurate timing - this is the time the key was originally pressed
        keyDownTimes[keyCode] = event.downTime
        // Save param for potential forwarding (like Xposed Edge Pro's H)
        pendingDownEvents[keyCode] = copyKeyEvent(event)
        keyConsumed[keyCode] = false

        // Start long-press timeout if long-press action exists
        if (hasAction(keyCode, MODE_LONG_PRESS)) {
            startLongPressTimeout(keyCode, context)
        }

        // Always intercept initially to detect the gesture type
        return true
    }

    /**
     * Handle KEY_UP event.
     * Following Xposed Edge Pro's v() method logic.
     */
    private fun handleKeyUp(keyCode: Int, event: KeyEvent, context: Context, param: XC_MethodHook.MethodHookParam): Boolean {
        val currentState = keyStates[keyCode] ?: STATE_IDLE
        
        if (currentState != STATE_PRESSED) {
            return keyConsumed[keyCode] == true
        }

        cancelLongPressTimeout(keyCode)

        val downTime = keyDownTimes[keyCode] ?: event.eventTime
        val pressDuration = event.eventTime - downTime

        // Check if it was a long press (timeout would have fired)
        if (keyConsumed[keyCode] == true) {
            // Long press already executed
            keyStates[keyCode] = STATE_IDLE
            pendingDownEvents.remove(keyCode)
            pendingUpEvents.remove(keyCode)
            return true
        }

        // Short press - check if we need to wait for double-tap
        if (pressDuration < longPressTimeout) {
            // Check if double-click action exists (like Xposed Edge Pro's D(keyCode).z != 0)
            if (hasAction(keyCode, MODE_DOUBLE_CLICK)) {
                // Wait for potential double-tap - save UP event param (like Xposed Edge Pro's I)
                keyStates[keyCode] = STATE_WAITING_DOUBLE
                pendingUpEvents[keyCode] = copyKeyEvent(event)
                startDoubleTapTimeout(keyCode, context)
                return true
            }
            
            // No double-click action - check for click action (like Xposed Edge Pro's q(keyCode, 0).z != 0)
            if (hasAction(keyCode, MODE_CLICK)) {
                // Execute click immediately
                keyStates[keyCode] = STATE_IDLE
                pendingDownEvents.remove(keyCode)
                pendingUpEvents.remove(keyCode)
                
                val action = getAction(keyCode, MODE_CLICK)
                XposedBridge.log("$TAG: Key $keyCode click -> $action")
                executeAction(action, context, keyCode)
                return true
            }
            
            // No click or double-click action - forward the events via InputManager injection
            XposedBridge.log("$TAG: Key $keyCode no click/double-click action -> injecting events")
            pendingUpEvents[keyCode] = copyKeyEvent(event)
            forwardKeyEvents(keyCode, context)
            keyStates[keyCode] = STATE_IDLE
            return true
        } else {
            // Key was held past long-press threshold but long-press timeout didn't consume
            // If the DOWN event was already injected (because no long press action),
            // we should NOT execute the click action on release to avoid stuck keys.
            if (hasAction(keyCode, MODE_CLICK) && pendingDownEvents.containsKey(keyCode)) {
                // Execute click on release
                keyStates[keyCode] = STATE_IDLE
                pendingDownEvents.remove(keyCode)
                pendingUpEvents.remove(keyCode)
                
                val action = getAction(keyCode, MODE_CLICK)
                executeAction(action, context, keyCode)
                return true
            }
            // Forward the events via InputManager injection
            XposedBridge.log("$TAG: Key $keyCode held but no long-press action -> injecting UP")
            pendingUpEvents[keyCode] = copyKeyEvent(event)
            forwardKeyEvents(keyCode, context)
            keyStates[keyCode] = STATE_IDLE
            return true
        }
    }

    /**
     * Start long-press timeout.
     */
    private fun startLongPressTimeout(keyCode: Int, context: Context) {
        cancelLongPressTimeout(keyCode)
        
        val runnable = Runnable {
            synchronized(this) {
                if (keyStates[keyCode] == STATE_PRESSED && keyConsumed[keyCode] != true) {
                    if (hasAction(keyCode, MODE_LONG_PRESS)) {
                        keyConsumed[keyCode] = true
                        pendingDownEvents.remove(keyCode)
                        
                        val action = getAction(keyCode, MODE_LONG_PRESS)
                        XposedBridge.log("$TAG: Key $keyCode long-press -> $action")
                        executeAction(action, context, keyCode)
                    } else {
                        // User held the key, but no custom long-press config.
                        // Forward the DOWN event now so system handles it natively (e.g. for volume auto-repeat).
                        XposedBridge.log("$TAG: Key $keyCode held but no action -> injecting DOWN to system")
                        val downEvent = pendingDownEvents.remove(keyCode)
                        if (downEvent != null) {
                            injectKeyEvent(downEvent, context)
                        }
                    }
                }
            }
        }
        longPressRunnables[keyCode] = runnable
        handler.postDelayed(runnable, longPressTimeout)
    }

    /**
     * Cancel long-press timeout.
     */
    private fun cancelLongPressTimeout(keyCode: Int) {
        longPressRunnables[keyCode]?.let { handler.removeCallbacks(it) }
        longPressRunnables.remove(keyCode)
    }

    /**
     * Start double-tap timeout.
     * Like Xposed Edge Pro's e() callback - when timeout fires, execute click or forward.
     */
    private fun startDoubleTapTimeout(keyCode: Int, context: Context) {
        cancelDoubleTapTimeout(keyCode)
        
        val runnable = Runnable {
            synchronized(this) {
                if (keyStates[keyCode] == STATE_WAITING_DOUBLE) {
                    keyStates[keyCode] = STATE_IDLE
                    
                    if (hasAction(keyCode, MODE_CLICK)) {
                        // Double-tap timed out, execute single click
                        pendingDownEvents.remove(keyCode)
                        pendingUpEvents.remove(keyCode)
                        
                        val action = getAction(keyCode, MODE_CLICK)
                        XposedBridge.log("$TAG: Key $keyCode click (after double-tap timeout) -> $action")
                        executeAction(action, context, keyCode)
                    } else {
                        // No click action, forward original events via InputManager injection
                        XposedBridge.log("$TAG: Key $keyCode double-tap timeout, no click action -> injecting events")
                        forwardKeyEvents(keyCode, context)
                    }
                }
            }
        }
        doubleTapRunnables[keyCode] = runnable
        handler.postDelayed(runnable, doubleTapTimeout)
    }

    /**
     * Cancel double-tap timeout.
     */
    private fun cancelDoubleTapTimeout(keyCode: Int) {
        doubleTapRunnables[keyCode]?.let { handler.removeCallbacks(it) }
        doubleTapRunnables.remove(keyCode)
    }

    /**
     * Execute action (delegate to GestureManager's action system).
     */
    private fun executeAction(action: String, context: Context, keyCode: Int = -1) {
        if (action.isEmpty() || action == "none") return

        GestureManager.executeKeyAction(action, context)
    }

    /**
     * Reset all state (e.g., on screen off or config change).
     * Ensures no stale key tracking survives a lock/unlock cycle.
     */
    fun reset() {
        for (keyCode in SUPPORTED_KEYS.keys) {
            cancelLongPressTimeout(keyCode)
            cancelDoubleTapTimeout(keyCode)
        }
        keyStates.clear()
        keyDownTimes.clear()
        pendingDownEvents.clear()
        pendingUpEvents.clear()
        keyConsumed.clear()
        injectedEventTimes.clear()
        volumePassthroughUntil = 0L
    }
}
