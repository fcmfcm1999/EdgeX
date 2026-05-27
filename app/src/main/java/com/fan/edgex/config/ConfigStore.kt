package com.fan.edgex.config

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit

// UI-side config access. All writes go through here so the hook is always notified.
// Values are stored as strings to keep the hook snapshot schema stable across releases.

fun Context.configPrefs(): SharedPreferences =
    getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)

fun Context.putConfig(key: String, value: String) {
    val changedValues = runtimeValuesAfterChange(mapOf(key to value))
    configPrefs().edit {
        changedValues.forEach { (changedKey, changedValue) ->
            putString(changedKey, changedValue)
        }
    }
    notifyConfigChanged(changedValues)
}

fun Context.putConfig(key: String, value: Boolean) = putConfig(key, value.toString())

fun Context.putConfigsSync(vararg entries: Pair<String, String>): Boolean {
    if (entries.isEmpty()) return true

    val changedValues = runtimeValuesAfterChange(entries.toMap())
    val committed = configPrefs().edit().apply {
        changedValues.forEach { (key, value) ->
            putString(key, value)
        }
    }.commit()

    if (committed) {
        notifyConfigChanged(changedValues)
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

fun Context.requestHookActionExecution(actionCode: String) {
    if (actionCode.isBlank() || actionCode == "none") return

    HookConfigSnapshot.writeFromPreferences(this)
    sendBroadcast(Intent(HookConfigSnapshot.ACTION_EXECUTE_ACTION).apply {
        putExtra(HookConfigSnapshot.EXTRA_ACTION_CODE, actionCode)
    })
}

// Reads for UI. Includes a legacy fallback for values previously stored as native booleans.
fun Context.getConfigString(key: String, default: String = ""): String =
    configPrefs().run { getString(key, null) ?: default }

fun Context.getConfigBool(key: String, default: Boolean = false): Boolean =
    configPrefs().run {
        runCatching { getString(key, null) }.getOrNull()?.toBooleanStrictOrNull()
            ?: runCatching { getBoolean(key, default) }.getOrDefault(default)
    }

fun Context.syncRuntimeEnableFlagsFromConfiguredActions(): Boolean {
    val prefs = configPrefs()
    val values = prefs.all.mapValues { (_, value) -> value?.toString() ?: "" }
    val derivedValues = mutableMapOf<String, String>()

    AppConfig.ZONES.forEach { zone ->
        val enabledKey = AppConfig.zoneEnabled(zone)
        if (!prefs.contains(enabledKey) && zoneHasConfiguredAction(zone, values)) {
            derivedValues[enabledKey] = true.toString()
        }
    }

    AppConfig.KEY_TRIGGERS.flatMap { trigger ->
        values.keys.mapNotNull { key ->
            AppConfig.keyActionParts(key)
                ?.takeIf { (_, parsedTrigger) -> parsedTrigger == trigger }
                ?.first
        }
    }.toSet().forEach { keyCode ->
        val enabledKey = AppConfig.keyEnabled(keyCode)
        if (!prefs.contains(enabledKey) && keyHasConfiguredAction(keyCode, values)) {
            derivedValues[enabledKey] = true.toString()
        }
    }

    if (derivedValues.isEmpty()) return false

    val committed = prefs.edit().apply {
        derivedValues.forEach { (key, value) -> putString(key, value) }
    }.commit()
    if (committed) notifyConfigChanged(derivedValues)
    return committed
}

private fun Context.notifyConfigChanged(changedValues: Map<String, String>) {
    HookConfigSnapshot.writeFromPreferences(this)
    sendConfigBroadcast(changedValues, fullSnapshot = false)
}

private fun Context.runtimeValuesAfterChange(changedValues: Map<String, String>): Map<String, String> {
    val prefs = configPrefs()
    val result = changedValues.toMutableMap()

    changedValues.keys.mapNotNull(AppConfig::gestureActionParts)
        .map { (zone, _) -> zone }
        .toSet()
        .forEach { zone ->
            val enabledKey = AppConfig.zoneEnabled(zone)
            if (!prefs.contains(enabledKey)) {
                result[enabledKey] = zoneHasConfiguredAction(zone, changedValues, prefs).toString()
            }
        }

    changedValues.keys.mapNotNull(AppConfig::keyActionParts)
        .map { (keyCode, _) -> keyCode }
        .toSet()
        .forEach { keyCode ->
            val enabledKey = AppConfig.keyEnabled(keyCode)
            if (!prefs.contains(enabledKey)) {
                result[enabledKey] = keyHasConfiguredAction(keyCode, changedValues, prefs).toString()
            }
        }

    return result
}

private fun zoneHasConfiguredAction(
    zone: String,
    changedValues: Map<String, String>,
    prefs: SharedPreferences,
): Boolean =
    AppConfig.GESTURES.any { gesture ->
        val key = AppConfig.gestureAction(zone, gesture)
        AppConfig.isActiveActionValue(changedValues[key] ?: prefs.getString(key, "").orEmpty())
    }

private fun zoneHasConfiguredAction(zone: String, values: Map<String, String>): Boolean =
    AppConfig.GESTURES.any { gesture ->
        AppConfig.isActiveActionValue(values[AppConfig.gestureAction(zone, gesture)].orEmpty())
    }

private fun keyHasConfiguredAction(
    keyCode: Int,
    changedValues: Map<String, String>,
    prefs: SharedPreferences,
): Boolean =
    AppConfig.KEY_TRIGGERS.any { trigger ->
        val key = AppConfig.keyAction(keyCode, trigger)
        AppConfig.isActiveActionValue(changedValues[key] ?: prefs.getString(key, "").orEmpty())
    }

private fun keyHasConfiguredAction(keyCode: Int, values: Map<String, String>): Boolean =
    AppConfig.KEY_TRIGGERS.any { trigger ->
        AppConfig.isActiveActionValue(values[AppConfig.keyAction(keyCode, trigger)].orEmpty())
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
