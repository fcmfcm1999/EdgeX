package com.fan.edgex.service

import android.app.Notification
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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

    // Cache extracted icon colors per package; null means no colorful color was found
    private val iconColorCache = HashMap<String, Int?>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!getConfigBool(AppConfig.EDGE_LIGHTING_ENABLED)) return
        if (!isScreenInteractive()) return
        if (!isAllowedPackage(sbn.packageName)) return

        val fallbackColor = parseColor(getConfigString(AppConfig.EDGE_LIGHTING_COLOR, DEFAULT_COLOR))
        val color = if (getConfigBool(AppConfig.EDGE_LIGHTING_AUTO_COLOR, true)) {
            val rawColor = sbn.notification.color
            if (rawColor != Notification.COLOR_DEFAULT) {
                // Force full alpha: some apps omit the alpha byte (0x07C160 instead of 0xFF07C160),
                // which makes the color transparent and the edge lighting invisible.
                rawColor or (0xFF shl 24)
            } else {
                // App didn't call setColor() — extract a representative color from its launcher icon.
                iconColorCache.getOrPut(sbn.packageName) { extractIconColor(sbn.packageName) }
                    ?: fallbackColor
            }
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

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        iconColorCache.clear()
    }

    /**
     * Draws the app's launcher icon to a small bitmap and returns the most common
     * non-transparent, non-near-white, non-near-black pixel color.
     * Returns null if the icon has no suitable colorful pixels.
     */
    private fun extractIconColor(packageName: String): Int? {
        return try {
            val drawable = packageManager.getApplicationIcon(packageName)
            val size = 64
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(bitmap))

            val pixels = IntArray(size * size)
            bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
            bitmap.recycle()

            // Quantize to 8 levels per channel and count occurrences
            val counts = HashMap<Int, Int>()
            for (pixel in pixels) {
                if (Color.alpha(pixel) < 128) continue
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                if (r > 220 && g > 220 && b > 220) continue  // near-white
                if (r < 40 && g < 40 && b < 40) continue     // near-black
                val q = Color.rgb((r / 32) * 32, (g / 32) * 32, (b / 32) * 32)
                counts[q] = (counts[q] ?: 0) + 1
            }

            counts.maxByOrNull { it.value }?.key?.let { it or (0xFF shl 24) }
        } catch (_: Exception) {
            null
        }
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
