package com.fan.edgex.service

import android.app.Notification
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
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

    // Keys of notifications we've already shown Edge Lighting for.
    // Cleared when the notification is dismissed, so re-posting after removal triggers again.
    // Using key alone (not postTime) because apps like LSPosed re-post with a fresh postTime on
    // rotation, which would defeat any (key, postTime) comparison.
    private val seenNotificationKeys = HashSet<String>()

    // Repeating pulse for incoming calls (CATEGORY_CALL).
    // The system only calls onNotificationPosted once per call, so we drive repetition ourselves.
    // Pulse fires every RINGING_PULSE_INTERVAL_MS, restarting the Edge Lighting animation.
    // Stopped when the call notification is removed (answered / rejected / ended).
    private val handler = Handler(Looper.getMainLooper())
    private var ringingKey: String? = null
    private var ringingColor: Int = 0
    private val ringingPulse = object : Runnable {
        override fun run() {
            fireLighting(ringingColor, CALL_DURATION_MS.toInt())
            handler.postDelayed(this, RINGING_PULSE_INTERVAL_MS)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!getConfigBool(AppConfig.EDGE_LIGHTING_ENABLED)) return
        if (!isScreenInteractive()) return
        if (!isAllowedPackage(sbn.packageName)) return

        val isCall = sbn.notification.category == Notification.CATEGORY_CALL

        if (isCall) {
            // Start (or restart with updated color) the ringing pulse loop.
            // We use CALL_DURATION_MS per trigger so the animation runs continuously
            // without restarting (and disrupting directional effects like comet) for
            // the typical ringing period. The pulse loop is a fallback for unusually
            // long-ringing calls only.
            val color = resolveColor(sbn)
            seenNotificationKeys.add(sbn.key)
            ringingKey = sbn.key
            ringingColor = color
            handler.removeCallbacks(ringingPulse)
            handler.post(ringingPulse)
            return
        }

        // Non-call notification: fire once, deduplicate rotation re-posts.
        if (sbn.key == ringingKey) stopRingingPulse()
        if (!seenNotificationKeys.add(sbn.key)) return
        fireLighting(resolveColor(sbn))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        seenNotificationKeys.remove(sbn.key)
        if (sbn.key == ringingKey) stopRingingPulse()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        iconColorCache.clear()
        seenNotificationKeys.clear()
        stopRingingPulse()
    }

    private fun stopRingingPulse() {
        handler.removeCallbacks(ringingPulse)
        ringingKey = null
        sendBroadcast(Intent(HookConfigSnapshot.ACTION_EDGE_LIGHTING_DISMISS))
    }

    private fun fireLighting(color: Int, durationMs: Int = getConfigString(AppConfig.EDGE_LIGHTING_DURATION_MS, "3000").toIntOrNull() ?: 3000) {
        sendBroadcast(Intent(HookConfigSnapshot.ACTION_EDGE_LIGHTING).apply {
            putExtra(HookConfigSnapshot.EXTRA_EDGE_LIGHTING_COLOR, color)
            putExtra(HookConfigSnapshot.EXTRA_EDGE_LIGHTING_DURATION_MS, durationMs)
        })
    }

    private fun resolveColor(sbn: StatusBarNotification): Int {
        val fallbackColor = parseColor(getConfigString(AppConfig.EDGE_LIGHTING_COLOR, DEFAULT_COLOR))
        return if (getConfigBool(AppConfig.EDGE_LIGHTING_AUTO_COLOR, true)) {
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
        // Each ringing pulse sends a 30-second animation so directional effects (comet, flow, etc.)
        // run continuously without restarting. 29 s interval ensures a fresh pulse fires before
        // the current animation fades out, acting as a fallback for calls that ring unusually long.
        const val CALL_DURATION_MS = 30_000L
        const val RINGING_PULSE_INTERVAL_MS = 29_000L
    }
}
