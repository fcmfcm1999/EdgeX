package com.fan.edgex.hook

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import de.robv.android.xposed.XposedBridge

/**
 * KeyManager handles hardware key interception and action triggering.
 * Keys are trigger sources parallel to gestures.
 * 
 * Supported interaction modes:
 * - click (0): Quick press and release
 * - double_click (1): Two quick presses  
 * - long_press (2): Hold beyond threshold
 * 
 * State machine:
 * [IDLE] -> KEY_DOWN -> [PRESSED]
 *                          |
 *          +---------------+---------------+
 *          |               |               |
 *      timeout         KEY_UP          KEY_DOWN again
 *      (long_press)    (short)         (potential double)
 *          |               |               |
 *          v               v               v
 *     [LONG_PRESS]     [WAITING]     [DOUBLE_CLICK]
 *          |               |               |
 *          v           timeout              v
 *     execute          execute          execute
 *     long_press       click            double_click
 */
object KeyManager {

    private const val TAG = "EdgeX"

    // Interaction modes (matching Xposed Edge Pro)
    const val MODE_CLICK = 0
    const val MODE_DOUBLE_CLICK = 1
    const val MODE_LONG_PRESS = 2

    // Supported keys (keyCode -> config index)
    val SUPPORTED_KEYS = mapOf(
        KeyEvent.KEYCODE_BACK to 0,
        KeyEvent.KEYCODE_HOME to 1,
        KeyEvent.KEYCODE_APP_SWITCH to 2,
        KeyEvent.KEYCODE_MENU to 3,
        KeyEvent.KEYCODE_VOLUME_UP to 4,
        KeyEvent.KEYCODE_VOLUME_DOWN to 5
    )

    // State machine states
    private const val STATE_IDLE = 0
    private const val STATE_PRESSED = 1
    private const val STATE_WAITING_DOUBLE = 2

    // Current state per key
    private val keyStates = mutableMapOf<Int, Int>()
    
    // Track press times for timing calculations
    private val keyDownTimes = mutableMapOf<Int, Long>()
    
    // Store pending key events for forwarding if no action, or for double-tap timing
    private val pendingKeyDownEvents = mutableMapOf<Int, KeyEvent>()
    
    // Track if we consumed the key (should not forward)
    private val keyConsumed = mutableMapOf<Int, Boolean>()

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
        keysEnabled = configCache["keys_enabled"] == "true"
        
        for (keyCode in SUPPORTED_KEYS.keys) {
            keyEnabled[keyCode] = configCache["key_enabled_$keyCode"] == "true"
            keyActions["${keyCode}_$MODE_CLICK"] = configCache["key_${keyCode}_click"] ?: ""
            keyActions["${keyCode}_$MODE_DOUBLE_CLICK"] = configCache["key_${keyCode}_double_click"] ?: ""
            keyActions["${keyCode}_$MODE_LONG_PRESS"] = configCache["key_${keyCode}_long_press"] ?: ""
        }
        
        XposedBridge.log("$TAG: KeyManager config updated - keysEnabled=$keysEnabled")
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
     * Handle key event from filterInputEvent hook.
     * Returns true if event should be consumed (not forwarded to system).
     */
    fun handleKeyEvent(event: KeyEvent, context: Context, forwardEvent: () -> Unit): Boolean {
        val keyCode = event.keyCode
        
        // Check if keys feature is enabled
        if (!keysEnabled) return false
        
        // Check if this key is supported
        if (!SUPPORTED_KEYS.containsKey(keyCode)) return false
        
        // Check if this specific key is enabled
        if (keyEnabled[keyCode] != true) return false
        
        // Check if key has any action configured - if not, don't intercept
        if (!hasAnyAction(keyCode)) return false

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> handleKeyDown(keyCode, event, context, forwardEvent)
            KeyEvent.ACTION_UP -> handleKeyUp(keyCode, event, context, forwardEvent)
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
    private fun handleKeyDown(keyCode: Int, event: KeyEvent, context: Context, forwardEvent: () -> Unit): Boolean {
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
                return startNewPress(keyCode, event, context)
            }
            return false
        }
        
        // First press (repeatCount == 0)
        when (currentState) {
            STATE_IDLE -> {
                return startNewPress(keyCode, event, context)
            }
            STATE_WAITING_DOUBLE -> {
                // Second press within double-tap window
                cancelDoubleTapTimeout(keyCode)
                
                // Check if event times match double-tap timing
                val firstUpEvent = pendingKeyDownEvents[keyCode]
                val timeDiff = if (firstUpEvent != null) event.eventTime - firstUpEvent.eventTime else Long.MAX_VALUE
                
                if (timeDiff < doubleTapTimeout && hasAction(keyCode, MODE_DOUBLE_CLICK)) {
                    // Execute double-click action
                    keyStates[keyCode] = STATE_PRESSED
                    keyConsumed[keyCode] = true
                    pendingKeyDownEvents.remove(keyCode)
                    
                    val action = getAction(keyCode, MODE_DOUBLE_CLICK)
                    XposedBridge.log("$TAG: Key $keyCode double-click -> $action")
                    executeAction(action, context)
                    return true
                } else {
                    // No double-click action or timing didn't match, execute pending single click if any
                    if (hasAction(keyCode, MODE_CLICK)) {
                        val action = getAction(keyCode, MODE_CLICK)
                        executeAction(action, context)
                    }
                    pendingKeyDownEvents.remove(keyCode)
                    return startNewPress(keyCode, event, context)
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
    private fun startNewPress(keyCode: Int, event: KeyEvent, context: Context): Boolean {
        keyStates[keyCode] = STATE_PRESSED
        // Use downTime for more accurate timing - this is the time the key was originally pressed
        keyDownTimes[keyCode] = event.downTime
        pendingKeyDownEvents[keyCode] = KeyEvent(event) // Copy for potential forwarding
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
     */
    private fun handleKeyUp(keyCode: Int, event: KeyEvent, context: Context, forwardEvent: () -> Unit): Boolean {
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
            pendingKeyDownEvents.remove(keyCode)
            return true
        }

        // Short press - check if we need to wait for double-tap
        if (pressDuration < longPressTimeout) {
            if (hasAction(keyCode, MODE_DOUBLE_CLICK)) {
                // Wait for potential double-tap - store the UP event time for double-tap detection
                keyStates[keyCode] = STATE_WAITING_DOUBLE
                // Store this event's info for double-tap timing
                pendingKeyDownEvents[keyCode] = KeyEvent(event)
                startDoubleTapTimeout(keyCode, context, forwardEvent)
                return true
            } else if (hasAction(keyCode, MODE_CLICK)) {
                // Execute click immediately
                keyStates[keyCode] = STATE_IDLE
                pendingKeyDownEvents.remove(keyCode)
                
                val action = getAction(keyCode, MODE_CLICK)
                XposedBridge.log("$TAG: Key $keyCode click -> $action")
                executeAction(action, context)
                return true
            } else {
                // No click or double-click action, forward the events
                keyStates[keyCode] = STATE_IDLE
                pendingKeyDownEvents.remove(keyCode)
                forwardEvent()
                return true
            }
        } else {
            // Long press but no long-press action (timeout didn't consume)
            // This means the key was held but long-press timeout fired and had no action
            if (hasAction(keyCode, MODE_CLICK)) {
                // Execute click on release
                keyStates[keyCode] = STATE_IDLE
                pendingKeyDownEvents.remove(keyCode)
                
                val action = getAction(keyCode, MODE_CLICK)
                executeAction(action, context)
                return true
            }
            // Forward the events
            keyStates[keyCode] = STATE_IDLE
            pendingKeyDownEvents.remove(keyCode)
            forwardEvent()
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
                    keyConsumed[keyCode] = true
                    pendingKeyDownEvents.remove(keyCode)
                    
                    val action = getAction(keyCode, MODE_LONG_PRESS)
                    XposedBridge.log("$TAG: Key $keyCode long-press -> $action")
                    executeAction(action, context)
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
     */
    private fun startDoubleTapTimeout(keyCode: Int, context: Context, forwardEvent: () -> Unit) {
        cancelDoubleTapTimeout(keyCode)
        
        val runnable = Runnable {
            synchronized(this) {
                if (keyStates[keyCode] == STATE_WAITING_DOUBLE) {
                    keyStates[keyCode] = STATE_IDLE
                    
                    if (hasAction(keyCode, MODE_CLICK)) {
                        // Double-tap timed out, execute single click
                        pendingKeyDownEvents.remove(keyCode)
                        
                        val action = getAction(keyCode, MODE_CLICK)
                        XposedBridge.log("$TAG: Key $keyCode click (after double-tap timeout) -> $action")
                        executeAction(action, context)
                    } else {
                        // No click action, forward original events
                        pendingKeyDownEvents.remove(keyCode)
                        forwardEvent()
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
    private fun executeAction(action: String, context: Context) {
        if (action.isEmpty() || action == "none") return
        GestureManager.executeKeyAction(action, context)
    }

    /**
     * Reset all state (e.g., on config change).
     */
    fun reset() {
        for (keyCode in SUPPORTED_KEYS.keys) {
            cancelLongPressTimeout(keyCode)
            cancelDoubleTapTimeout(keyCode)
        }
        keyStates.clear()
        keyDownTimes.clear()
        pendingKeyDownEvents.clear()
        keyConsumed.clear()
    }
}
