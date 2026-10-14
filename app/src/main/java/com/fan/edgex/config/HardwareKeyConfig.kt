package com.fan.edgex.config

/** Shared by the settings UI and system_server; vendor key codes need not be in the SDK. */
object HardwareKeyConfig {
    val defaultCodes = listOf(24, 25, 26)
    const val CUSTOM_CODES = "hardware_key_codes"
    const val DETECT_UNTIL = "hardware_key_detect_until"
    const val ACTION_DETECTED = "com.fan.edgex.HARDWARE_KEY_DETECTED"
    const val EXTRA_CODE = "key_code"
    const val EXTRA_SESSION = "session"
    const val DETECT_DURATION_MS = 30_000L

    fun parseCodes(value: String): List<Int> = value.split(',')
        .mapNotNull { it.trim().toIntOrNull()?.takeIf { code -> code > 0 } }
        .distinct()

    fun configuredCodes(values: Map<String, String>): List<Int> =
        (defaultCodes + parseCodes(values[CUSTOM_CODES].orEmpty()) + values.keys.mapNotNull { key ->
            AppConfig.keyActionParts(key)?.first
                ?: key.takeIf { it.startsWith("key_enabled_") }?.removePrefix("key_enabled_")?.toIntOrNull()
        }).filter { it > 0 }.distinct()
}
