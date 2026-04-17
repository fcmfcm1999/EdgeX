package com.fan.edgex.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.BulletSpan
import android.text.style.ClickableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.fan.edgex.BuildConfig
import com.fan.edgex.R
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

    data class ReleaseInfo(
        val tagName: String,
        val versionName: String,
        val body: String,
        val htmlUrl: String
    )

    /**
     * Auto-check: respects 24h cooldown and skipped version.
     */
    fun checkOnLaunch(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return

        fetchLatestRelease { release ->
            if (release == null) return@fetchLatestRelease
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            val skipped = prefs.getString(KEY_SKIPPED_VERSION, null)
            if (release.tagName == skipped) return@fetchLatestRelease
            if (!isNewer(release.versionName, BuildConfig.VERSION_NAME)) return@fetchLatestRelease
            activity.runOnUiThread { showUpdateDialog(activity, release) }
        }
    }

    /**
     * Manual check: ignores cooldown and skipped version.
     */
    fun checkNow(activity: Activity) {
        fetchLatestRelease { release ->
            activity.runOnUiThread {
                if (release == null || !isNewer(release.versionName, BuildConfig.VERSION_NAME)) {
                    android.widget.Toast.makeText(
                        activity,
                        activity.getString(R.string.update_already_latest),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    showUpdateDialog(activity, release)
                }
            }
        }
    }

    private fun showUpdateDialog(activity: Activity, release: ReleaseInfo) {
        if (activity.isFinishing || activity.isDestroyed) return
        val body = release.body.ifBlank { activity.getString(R.string.update_no_changelog) }
        val styledBody = parseMarkdown(body)
        // Make URLs open in browser instead of crashing
        val clickableBody = makeLinksClickable(styledBody, activity)

        val dp = { value: Int -> (value * activity.resources.displayMetrics.density + 0.5f).toInt() }

        // Title
        val titleView = TextView(activity).apply {
            text = activity.getString(R.string.update_new_version_title, release.versionName)
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
            setPadding(dp(20), dp(20), dp(20), dp(8))
        }

        // Content
        val contentView = TextView(activity).apply {
            setText(clickableBody)
            setTextColor(0xFF333333.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            setPadding(dp(20), 0, dp(20), 0)
            movementMethod = LinkMovementMethod.getInstance()
            setLinkTextColor(0xFF009688.toInt())
        }

        val scrollView = ScrollView(activity).apply {
            addView(contentView, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // Skip button
        val skipButton = TextView(activity).apply {
            text = activity.getString(R.string.update_skip_version)
            setTextColor(0xFF009688.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val bg = GradientDrawable().apply { cornerRadius = dp(4).toFloat() }
            background = bg
        }

        val buttonBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(12), dp(8), dp(12), dp(16))
            addView(skipButton, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // Root layout
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(scrollView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buttonBar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val dialog = AlertDialog.Builder(activity).create()
        dialog.setView(root)
        dialog.window?.let { window ->
            val shape = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0xFFF5F5F5.toInt())
            }
            val inset = dp(24)
            window.setBackgroundDrawable(InsetDrawable(shape, inset, inset, inset, inset))
        }
        dialog.show()

        skipButton.setOnClickListener {
            activity.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE)
                .edit().putString(KEY_SKIPPED_VERSION, release.tagName).apply()
            dialog.dismiss()
        }
    }

    /** Replace URLSpans with ones that open browser via Intent */
    private fun makeLinksClickable(spannable: SpannableStringBuilder, activity: Activity): SpannableStringBuilder {
        val urls = spannable.getSpans(0, spannable.length, URLSpan::class.java)
        for (urlSpan in urls) {
            val start = spannable.getSpanStart(urlSpan)
            val end = spannable.getSpanEnd(urlSpan)
            val flags = spannable.getSpanFlags(urlSpan)
            val url = urlSpan.url
            spannable.removeSpan(urlSpan)
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: android.view.View) {
                    try {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(activity,
                            activity.getString(R.string.toast_cannot_open_browser),
                            android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }, start, end, flags)
        }
        return spannable
    }

    /**
     * Lightweight Markdown → SpannableString converter.
     * Supports: ## headings, **bold**, `code`, [links](url), - bullet lists
     */
    private fun parseMarkdown(md: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        val lines = md.replace("\r\n", "\n").split("\n")

        for ((i, rawLine) in lines.withIndex()) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) {
                if (sb.isNotEmpty() && sb[sb.length - 1] != '\n') sb.append("\n")
                continue
            }

            val start = sb.length

            // Heading: ## text
            val headingMatch = Regex("^(#{1,3})\\s+(.*)").matchEntire(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text = headingMatch.groupValues[2]
                appendInlineFormatted(sb, text)
                val size = when (level) { 1 -> 1.3f; 2 -> 1.15f; else -> 1.05f }
                sb.setSpan(RelativeSizeSpan(size), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append("\n")
                continue
            }

            // Bullet: - text  or  * text
            val bulletMatch = Regex("^\\s*[-*]\\s+(.*)").matchEntire(line)
            if (bulletMatch != null) {
                val text = bulletMatch.groupValues[1]
                appendInlineFormatted(sb, text)
                sb.setSpan(BulletSpan(16), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append("\n")
                continue
            }

            // Plain line
            appendInlineFormatted(sb, line)
            if (i < lines.lastIndex) sb.append("\n")
        }

        // Trim trailing newlines
        while (sb.isNotEmpty() && sb[sb.length - 1] == '\n') sb.delete(sb.length - 1, sb.length)
        return sb
    }

    /** Apply inline formatting: **bold**, `code`, [text](url) */
    private fun appendInlineFormatted(sb: SpannableStringBuilder, text: String) {
        val regex = Regex("""\*\*(.+?)\*\*|`(.+?)`|\[(.+?)]\((.+?)\)""")
        var cursor = 0
        for (match in regex.findAll(text)) {
            if (match.range.first > cursor) {
                sb.append(text, cursor, match.range.first)
            }
            val start = sb.length
            when {
                match.groupValues[1].isNotEmpty() -> { // **bold**
                    sb.append(match.groupValues[1])
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                match.groupValues[2].isNotEmpty() -> { // `code`
                    sb.append(match.groupValues[2])
                    sb.setSpan(TypefaceSpan("monospace"), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                match.groupValues[3].isNotEmpty() -> { // [text](url)
                    sb.append(match.groupValues[3])
                    sb.setSpan(URLSpan(match.groupValues[4]), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) sb.append(text, cursor, text.length)
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
