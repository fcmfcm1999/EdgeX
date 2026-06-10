package com.fan.edgex.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.fan.edgex.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val API_URL = "https://api.github.com/repos/fcmfcm1999/EdgeX/releases/latest"
    private const val PREF_NAME = "update_checker"
    private const val KEY_LAST_CHECK = "last_check_time"
    private const val KEY_SKIPPED_VERSION = "skipped_version"
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
    private val MARKDOWN_HEADING_REGEX = Regex("^\\s{0,3}(#{1,3})\\s+(.*)")

    data class ReleaseInfo(
        val tagName: String,
        val versionName: String,
        val body: String,
        val htmlUrl: String
    )

    /**
     * Auto-check: respects 24h cooldown and skipped version.
     */
    fun checkOnLaunch(activity: Activity, onUpdateAvailable: (ReleaseInfo) -> Unit) {
        val prefs = activity.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return

        fetchLatestRelease { release ->
            if (release == null) return@fetchLatestRelease
            prefs.edit { putLong(KEY_LAST_CHECK, System.currentTimeMillis()) }
            val skipped = prefs.getString(KEY_SKIPPED_VERSION, null)
            if (release.tagName == skipped) return@fetchLatestRelease
            if (!isNewer(release.versionName, BuildConfig.VERSION_NAME)) return@fetchLatestRelease
            activity.runOnUiThread { onUpdateAvailable(release) }
        }
    }

    /**
     * Manual check: ignores cooldown and skipped version.
     */
    fun checkNow(activity: Activity, onResult: (ReleaseInfo?) -> Unit) {
        fetchLatestRelease { release ->
            activity.runOnUiThread {
                onResult(release?.takeIf { isNewer(it.versionName, BuildConfig.VERSION_NAME) })
            }
        }
    }

    internal fun parseMarkdownHeading(line: String): Pair<Int, String>? {
        val match = MARKDOWN_HEADING_REGEX.matchEntire(line) ?: return null
        return match.groupValues[1].length to match.groupValues[2]
    }

    fun skipVersion(context: Context, release: ReleaseInfo) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_SKIPPED_VERSION, release.tagName) }
    }

    /**
     * Extracts the localized section from a bilingual release body.
     *
     * Format expected in the GitHub release body:
     *   <!-- EN -->
     *   English content...
     *   <!-- ZH -->
     *   中文内容...
     *
     * The markers can appear in either order. If no markers are found the full
     * body is returned as-is (backwards compatible with single-language releases).
     */
    fun extractLocalizedBody(body: String, context: Context): String {
        val enMarker = "<!-- EN -->"
        val zhMarker = "<!-- ZH -->"
        val enIdx = body.indexOf(enMarker)
        val zhIdx = body.indexOf(zhMarker)
        if (enIdx == -1 && zhIdx == -1) return body

        @Suppress("DEPRECATION")
        val isZh = context.resources.configuration.locales[0].language == "zh"
        return if (isZh) {
            if (zhIdx == -1) return body
            val start = zhIdx + zhMarker.length
            val end = if (enIdx != -1 && enIdx > zhIdx) enIdx else body.length
            body.substring(start, end).trim()
        } else {
            if (enIdx == -1) return body
            val start = enIdx + enMarker.length
            val end = if (zhIdx != -1 && zhIdx > enIdx) zhIdx else body.length
            body.substring(start, end).trim()
        }
    }

    private fun fetchLatestRelease(callback: (ReleaseInfo?) -> Unit) {
        Thread {
            try {
                val conn = URL(API_URL).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                if (conn.responseCode != 200) {
                    Log.w(TAG, "GitHub API returned ${conn.responseCode}")
                    callback(null)
                    return@Thread
                }
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val tagName = json.optString("tag_name", "")
                val body = json.optString("body", "")
                val htmlUrl = json.optString("html_url", "")
                val versionName = tagName.removePrefix("v")

                callback(ReleaseInfo(tagName, versionName, body, htmlUrl))
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                callback(null)
            }
        }.start()
    }

    /**
     * Compare two version strings (e.g. "1.2.3" vs "1.1").
     * Returns true if [remote] is strictly newer than [local].
     */
    internal fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(r.size, l.size)
        for (i in 0 until len) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }
}
