package com.fan.edgex.hook

import com.fan.edgex.config.HardwareKeyConfig

/** One complete physical press per session. No key history is retained. */
internal class HardwareKeyDetector {
    data class Result(val consumed: Boolean, val code: Int? = null, val session: Long = 0L)

    private var completedSession = 0L
    private var capturedCode: Int? = null
    private var capturedDevice = -1
    private var capturedSession = 0L

    fun handle(code: Int, device: Int, down: Boolean, repeat: Int, canceled: Boolean, deadline: Long, now: Long): Result {
        if (device < 0 || code <= 0) return Result(false)
        // A lost release must not leave a button permanently captured.
        if (capturedCode != null && now > capturedSession + 10_000L) capturedCode = null
        if (capturedCode == null) {
            if (deadline == completedSession || deadline - now !in 1..HardwareKeyConfig.DETECT_DURATION_MS) return Result(false)
            if (!down || repeat != 0) return Result(false)
            capturedCode = code
            capturedDevice = device
            capturedSession = deadline
        }
        if (code != capturedCode || device != capturedDevice) return Result(false)
        if (down) return Result(true)
        capturedCode = null
        completedSession = capturedSession
        // Cancellation/expiry still consumes the matching release, but never saves a key.
        return if (!canceled && now <= capturedSession && deadline == capturedSession) {
            Result(true, code, capturedSession)
        } else Result(true)
    }
}
