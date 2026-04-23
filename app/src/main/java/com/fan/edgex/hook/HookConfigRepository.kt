package com.fan.edgex.hook

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.fan.edgex.config.AppConfig
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

    fun reloadAsync(onLoaded: (() -> Unit)? = null) {
        if (System.currentTimeMillis() - lastConfigLoad < CONFIG_CACHE_TTL) return
        configExecutor.execute {
            try {
                loadConfigKeys()
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

        for (zone in AppConfig.ZONES) {
            query(AppConfig.zoneEnabled(zone))
            for (gesture in AppConfig.GESTURES) {
                query(AppConfig.gestureAction(zone, gesture))
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
