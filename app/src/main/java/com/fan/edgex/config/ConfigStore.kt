package com.fan.edgex.config

import android.content.Context
import android.content.SharedPreferences

// UI-side config access. All writes go through here so the hook is always notified.
// Values are stored as strings so the ContentProvider never needs type hints.

fun Context.configPrefs(): SharedPreferences =
    getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)

fun Context.putConfig(key: String, value: String) {
    configPrefs().edit().putString(key, value).apply()
    contentResolver.notifyChange(ConfigProvider.CONTENT_URI, null)
}

fun Context.putConfig(key: String, value: Boolean) = putConfig(key, value.toString())

// Reads for UI. Includes a legacy fallback for values previously stored as native booleans.
fun Context.getConfigString(key: String, default: String = ""): String =
    configPrefs().run { getString(key, null) ?: default }

fun Context.getConfigBool(key: String, default: Boolean = false): Boolean =
    configPrefs().run {
        getString(key, null)?.toBooleanStrictOrNull()
            ?: runCatching { getBoolean(key, default) }.getOrDefault(default)
    }
