package com.fan.edgex.hook

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.HookConfigSnapshot
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

internal class HookConfigRepository(
    private val contentUri: Uri,
    private val supportedKeysProvider: () -> Set<Int>,
    private val keyTriggersProvider: () -> List<String>,
    private val updateKeyConfig: (Map<String, String>) -> Unit,
    private val log: (String) -> Unit,
) {
    private val configCache = ConcurrentHashMap<String, String>()
    private var observerRegistered = false
    private var lastConfigLoad = 0L
    private var systemContext: Context? = null
    private var systemUiContext: Context? = null

    private val configExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "EdgeX-Config").apply { isDaemon = true }
    }

    fun attachSystemContext(context: Context) {
        systemContext = context
    }

    fun attachSystemUiContext(context: Context) {
        systemUiContext = context
    }

    fun ensureObserverRegistered(context: Context, onChanged: () -> Unit) {
        if (observerRegistered) return
        observerRegistered = true
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                log("Config change observed: $uri")
                invalidate()
                onChanged()
            }
        }
        try {
            context.contentResolver.registerContentObserver(contentUri, true, observer)
            log("Config ContentObserver registered in process")
        } catch (e: Exception) {
            observerRegistered = false
            log("Failed to register config observer: ${e.message}")
        }
    }

    fun invalidate() {
        lastConfigLoad = 0L
    }

    fun updateFromBroadcast(keys: Array<String>, values: Array<String>) {
        if (keys.size != values.size) {
            log("Ignoring malformed config broadcast: keys=${keys.size} values=${values.size}")
            return
        }

        keys.forEachIndexed { index, key ->
            configCache[key] = values[index]
        }
        updateKeyConfig(configCache)
        lastConfigLoad = System.currentTimeMillis()
        log("Config broadcast applied: ${keys.joinToString()}")
    }

    fun reloadAsync(onLoaded: (() -> Unit)? = null) {
        if (System.currentTimeMillis() - lastConfigLoad < CONFIG_CACHE_TTL) return
        configExecutor.execute {
            try {
                if (!loadSnapshot()) {
                    loadConfigKeys()
                }
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

    private fun loadConfigKeys() {
        fun query(key: String) {
            configCache[key] = queryConfigProvider(key)
        }

        query(AppConfig.GESTURES_ENABLED)
        query(AppConfig.DEBUG_MATRIX)
        query(AppConfig.KEYS_ENABLED)
        query(AppConfig.FREEZER_ARC_DRAWER)
        query(AppConfig.FREEZER_APP_LIST)
        query(AppConfig.THEME_PRESET)
        query(AppConfig.THEME_CUSTOM_COLOR)

        for (zone in AppConfig.ZONES) {
            query(AppConfig.zoneEnabled(zone))
            for (gesture in AppConfig.GESTURES) {
                val parentKey = AppConfig.gestureAction(zone, gesture)
                query(parentKey)
                if (configCache[parentKey] == AppConfig.SUB_GESTURE_ACTION) {
                    for (direction in AppConfig.SUB_GESTURE_DIRECTIONS) {
                        query(AppConfig.subGestureChildKey(parentKey, direction))
                    }
                }
            }
        }

        for (keyCode in supportedKeysProvider()) {
            query(AppConfig.keyEnabled(keyCode))
            for (trigger in keyTriggersProvider()) {
                query(AppConfig.keyAction(keyCode, trigger))
            }
        }

        updateKeyConfig(configCache)
    }

    private fun loadSnapshot(): Boolean {
        val snapshot = HookConfigSnapshot.readFromHookFile()
        if (snapshot.isEmpty()) return false

        configCache.clear()
        configCache.putAll(snapshot)
        updateKeyConfig(configCache)
        log("Config snapshot loaded: ${snapshot.size} keys")
        return true
    }

    private fun queryConfigProvider(key: String): String {
        val context = systemContext ?: systemUiContext ?: return ""
        return try {
            context.contentResolver.query(
                Uri.withAppendedPath(contentUri, key),
                null,
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else ""
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private companion object {
        const val CONFIG_CACHE_TTL = 2000L
    }
}
