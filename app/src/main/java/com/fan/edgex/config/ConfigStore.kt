package com.fan.edgex.config

import android.content.Context
import android.content.SharedPreferences

// UI-side config access. All writes go through here so the hook is always notified.
// Values are stored as strings so the ContentProvider never needs type hints.

fun Context.configPrefs(): SharedPreferences =
    getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)

fun Context.putConfig(key: String, value: String) {
    configPrefs().edit().putString(key, value).apply()
    notifyConfigChanged(listOf(key))
}

fun Context.putConfig(key: String, value: Boolean) = putConfig(key, value.toString())

fun Context.putConfigsSync(vararg entries: Pair<String, String>): Boolean {
    if (entries.isEmpty()) return true

    val committed = configPrefs().edit().apply {
        entries.forEach { (key, value) ->
            putString(key, value)
        }
    }.commit()

    if (committed) {
        notifyConfigChanged(entries.map { it.first }.distinct())
    }

    return committed
}

// Reads for UI. Includes a legacy fallback for values previously stored as native booleans.
fun Context.getConfigString(key: String, default: String = ""): String =
    configPrefs().run { getString(key, null) ?: default }

fun Context.getConfigBool(key: String, default: Boolean = false): Boolean =
    configPrefs().run {
        runCatching { getString(key, null) }.getOrNull()?.toBooleanStrictOrNull()
            ?: runCatching { getBoolean(key, default) }.getOrDefault(default)
    }

private fun Context.notifyConfigChanged(keys: Collection<String>) {
    keys.forEach { key ->
        contentResolver.notifyChange(ConfigProvider.uriForKey(key), null)
    }
    contentResolver.notifyChange(ConfigProvider.CONTENT_URI, null)
}
