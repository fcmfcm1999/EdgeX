package com.fan.edgex.hook

enum class KeyInterceptionPath {
    QUEUEING,
    DISPATCH,
}

internal object TriggerConditionPolicy {

    fun parseTriggerEnabled(rawValue: String?): Boolean =
        rawValue != "false"

    fun shouldTriggerGesture(
        isKeyguardLocked: Boolean,
        allowLocked: Boolean,
    ): Boolean = !isKeyguardLocked || allowLocked

    fun shouldTriggerKey(
        isScreenInteractive: Boolean,
        isKeyguardLocked: Boolean,
        allowScreenOff: Boolean,
        allowLocked: Boolean,
    ): Boolean {
        if (!isScreenInteractive) {
            return allowScreenOff
        }
        if (isKeyguardLocked) {
            return allowLocked || allowScreenOff
        }
        return true
    }

    /**
     * Routing and trigger-condition decision for hardware keys:
     * - If a press is already active (`activePath != null`), only its owning path may process
     *   remaining events (even if screen interactive state changes mid-press).
     * - If no press is active (`activePath == null`), `QUEUEING` only claims screen-off presses
     *   and `DISPATCH` only claims screen-on presses, subject to [shouldTriggerKey].
     */
    fun canAcceptKeyEvent(
        source: KeyInterceptionPath,
        activePath: KeyInterceptionPath?,
        isScreenInteractive: Boolean,
        isKeyguardLocked: Boolean,
        allowScreenOff: Boolean,
        allowLocked: Boolean,
    ): Boolean {
        if (activePath != null) {
            return activePath == source
        }
        val pathMatchesScreenState = when (source) {
            KeyInterceptionPath.QUEUEING -> !isScreenInteractive
            KeyInterceptionPath.DISPATCH -> isScreenInteractive
        }
        if (!pathMatchesScreenState) return false

        return shouldTriggerKey(
            isScreenInteractive = isScreenInteractive,
            isKeyguardLocked = isKeyguardLocked,
            allowScreenOff = allowScreenOff,
            allowLocked = allowLocked,
        )
    }
}
