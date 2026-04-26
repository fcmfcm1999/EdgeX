package com.fan.edgex.hook

import android.os.Handler
import android.os.Looper
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.HookConfigSnapshot
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

internal class HookConfigRepository(
    private val updateKeyConfig: (Map<String, String>) -> Unit,
    private val log: (String) -> Unit,
) {
    private val configCache = ConcurrentHashMap<String, String>()
    private var lastConfigLoad = 0L
    private var missingSnapshotLogged = false

    private val configExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "EdgeX-Config").apply { isDaemon = true }
    }

    fun invalidate() {
        lastConfigLoad = 0L
    }

    fun updateFromBroadcast(keys: Array<String>, values: Array<String>, fullSnapshot: Boolean) {
        if (keys.size != values.size) {
            log("Ignoring malformed config broadcast: keys=${keys.size} values=${values.size}")
            return
        }

        if (fullSnapshot) {
            configCache.clear()
        }
        var appliedCount = 0
        keys.forEachIndexed { index, key ->
            if (HookConfigSnapshot.isHookRuntimeKey(key)) {
                configCache[key] = values[index]
                appliedCount++
            }
        }
        updateKeyConfig(configCache)
        HookConfigSnapshot.writeForHook(configCache)
        lastConfigLoad = System.currentTimeMillis()
        log("Config broadcast applied: $appliedCount/${keys.size} keys full=$fullSnapshot")
    }

    fun reloadAsync(onLoaded: (() -> Unit)? = null) {
        if (System.currentTimeMillis() - lastConfigLoad < CONFIG_CACHE_TTL) return
        configExecutor.execute {
            try {
                loadSnapshot()
                lastConfigLoad = System.currentTimeMillis()
                onLoaded?.let { Handler(Looper.getMainLooper()).post(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isGesturesEnabled(): Boolean =
        get(AppConfig.GESTURES_ENABLED) == "true"

    fun isZoneEnabled(zone: String): Boolean =
        get(AppConfig.zoneEnabled(zone)) == "true"

    fun get(key: String, defValue: String = ""): String =
        configCache[key] ?: defValue

    private fun loadSnapshot(): Boolean {
        val snapshot = HookConfigSnapshot.readFromHookFile()
        if (snapshot.isEmpty()) {
            if (!missingSnapshotLogged) {
                missingSnapshotLogged = true
                log("Config snapshot unavailable; open EdgeX once after upgrade to publish hook config")
            }
            return false
        }

        missingSnapshotLogged = false
        configCache.clear()
        configCache.putAll(snapshot)
        updateKeyConfig(configCache)
        log("Config snapshot loaded: ${snapshot.size} keys")
        return true
    }

    private companion object {
        const val CONFIG_CACHE_TTL = 2000L
    }
}
