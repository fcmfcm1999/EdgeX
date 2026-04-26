package com.fan.edgex.hook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.fan.edgex.BuildConfig
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
    private var lastSnapshotRequest = 0L
    private var systemContext: Context? = null

    private val configExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "EdgeX-Config").apply { isDaemon = true }
    }

    fun attachSystemContext(context: Context) {
        systemContext = context
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
                log("Config snapshot unavailable; requesting EdgeX to publish hook config")
            }
            requestSnapshotFromApp()
            return false
        }

        missingSnapshotLogged = false
        configCache.clear()
        configCache.putAll(snapshot)
        updateKeyConfig(configCache)
        log("Config snapshot loaded: ${snapshot.size} keys")
        return true
    }

    private fun requestSnapshotFromApp() {
        val now = System.currentTimeMillis()
        if (now - lastSnapshotRequest < SNAPSHOT_REQUEST_THROTTLE) return
        lastSnapshotRequest = now

        val context = systemContext ?: return
        try {
            context.sendBroadcast(Intent(HookConfigSnapshot.ACTION_CONFIG_SNAPSHOT_REQUEST).apply {
                component = ComponentName(
                    BuildConfig.APPLICATION_ID,
                    "${BuildConfig.APPLICATION_ID}.config.ConfigSnapshotReceiver",
                )
                setPackage(BuildConfig.APPLICATION_ID)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            })
        } catch (e: Exception) {
            log("Failed to request config snapshot: ${e.message}")
        }
    }

    private companion object {
        const val CONFIG_CACHE_TTL = 2000L
        const val SNAPSHOT_REQUEST_THROTTLE = 30_000L
    }
}
