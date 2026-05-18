package com.fan.edgex.service

import android.app.Notification
import android.content.Intent
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.toColorInt
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.HookConfigSnapshot
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import org.json.JSONArray

class NotificationEdgeService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!getConfigBool(AppConfig.EDGE_LIGHTING_ENABLED)) return
        if (!isScreenInteractive()) return
        if (!isAllowedPackage(sbn.packageName)) return

        val fallbackColor = parseColor(getConfigString(AppConfig.EDGE_LIGHTING_COLOR, DEFAULT_COLOR))
        val notificationColor = sbn.notification.color
        val color = if (
            getConfigBool(AppConfig.EDGE_LIGHTING_AUTO_COLOR, true) &&
            notificationColor != Notification.COLOR_DEFAULT
        ) {
            notificationColor
        } else {
            fallbackColor
        }

        sendBroadcast(Intent(HookConfigSnapshot.ACTION_EDGE_LIGHTING).apply {
            putExtra(HookConfigSnapshot.EXTRA_EDGE_LIGHTING_COLOR, color)
            putExtra(
                HookConfigSnapshot.EXTRA_EDGE_LIGHTING_DURATION_MS,
                getConfigString(AppConfig.EDGE_LIGHTING_DURATION_MS, "3000").toIntOrNull() ?: 3000,
            )
        })
    }

    private fun isScreenInteractive(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java)
        return powerManager?.isInteractive == true
    }

    private fun isAllowedPackage(packageName: String): Boolean {
        val selected = parsePackageList(getConfigString(AppConfig.EDGE_LIGHTING_APP_LIST))
        return selected.isEmpty() || packageName in selected
    }

    private fun parsePackageList(value: String): Set<String> {
        if (value.isBlank()) return emptySet()
        return runCatching {
            val array = JSONArray(value)
            buildSet {
                for (index in 0 until array.length()) {
                    val packageName = array.optString(index).trim()
                    if (packageName.isNotEmpty()) add(packageName)
                }
            }
        }.getOrElse {
            value.split(",").mapNotNullTo(mutableSetOf()) { it.trim().takeIf(String::isNotEmpty) }
        }
    }

    private fun parseColor(value: String): Int =
        runCatching { value.toColorInt() }.getOrElse { DEFAULT_COLOR.toColorInt() }

    private companion object {
        const val DEFAULT_COLOR = "#00FFFF"
    }
}
