package com.fan.edgex.config

import android.content.Context
import android.view.KeyEvent
import com.fan.edgex.R

fun Context.hardwareKeyName(code: Int): String = when (code) {
    KeyEvent.KEYCODE_VOLUME_UP -> getString(R.string.key_volume_up)
    KeyEvent.KEYCODE_VOLUME_DOWN -> getString(R.string.key_volume_down)
    KeyEvent.KEYCODE_POWER -> getString(R.string.key_power)
    else -> {
        // Use the ROM's name, including vendor constants absent from the public SDK.
        val name = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
        if (name.toIntOrNull() != null || name == "UNKNOWN") getString(R.string.key_custom_name, code)
        else name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }
    }
}

fun Context.hardwareKeyCodes(): List<Int> = HardwareKeyConfig.configuredCodes(
    configPrefs().all.mapValues { (_, value) -> value?.toString().orEmpty() }
)

fun Context.rememberHardwareKey(code: Int) {
    if (code <= 0 || code in HardwareKeyConfig.defaultCodes) return
    val codes = HardwareKeyConfig.parseCodes(getConfigString(HardwareKeyConfig.CUSTOM_CODES))
    putConfig(HardwareKeyConfig.CUSTOM_CODES, (codes + code).distinct().joinToString(","))
}
