package com.fan.edgex.config

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

// UI-side config access. All writes go through here so the hook is always notified.
// Values are stored as strings so the ContentProvider never needs type hints.

fun Context.configPrefs(): SharedPreferences =
    getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)

fun Context.putConfig(key: String, value: String) {
    configPrefs().edit().putString(key, value).apply()
    notifyConfigChanged(mapOf(key to value))
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
        notifyConfigChanged(entries.toMap())
    }

    return committed
}

fun Context.broadcastFullConfigSnapshot() {
    HookConfigSnapshot.writeFromPreferences(this)
    val values = configPrefs().all
        .mapValues { (_, value) -> value?.toString() ?: "" }
        .filterKeys(HookConfigSnapshot::isHookRuntimeKey)
    sendConfigBroadcast(values, fullSnapshot = true)
}

// Reads for UI. Includes a legacy fallback for values previously stored as native booleans.
fun Context.getConfigString(key: String, default: String = ""): String =
    configPrefs().run { getString(key, null) ?: default }

fun Context.getConfigBool(key: String, default: Boolean = false): Boolean =
    configPrefs().run {
        runCatching { getString(key, null) }.getOrNull()?.toBooleanStrictOrNull()
            ?: runCatching { getBoolean(key, default) }.getOrDefault(default)
    }

private fun Context.notifyConfigChanged(changedValues: Map<String, String>) {
    HookConfigSnapshot.writeFromPreferences(this)

    changedValues.keys.forEach { key ->
        contentResolver.notifyChange(ConfigProvider.uriForKey(key), null)
    }
    contentResolver.notifyChange(ConfigProvider.CONTENT_URI, null)

    sendConfigBroadcast(changedValues, fullSnapshot = false)
}

private fun Context.sendConfigBroadcast(valuesByKey: Map<String, String>, fullSnapshot: Boolean) {
    val hookValues = valuesByKey.filterKeys(HookConfigSnapshot::isHookRuntimeKey)
    if (hookValues.isEmpty() && !fullSnapshot) return

    val keys = hookValues.keys.toTypedArray()
    val values = keys.map { hookValues.getValue(it) }.toTypedArray()
    sendBroadcast(Intent(HookConfigSnapshot.ACTION_CONFIG_CHANGED).apply {
        putExtra(HookConfigSnapshot.EXTRA_KEYS, keys)
        putExtra(HookConfigSnapshot.EXTRA_VALUES, values)
        putExtra(HookConfigSnapshot.EXTRA_FULL_SNAPSHOT, fullSnapshot)
    })
}
