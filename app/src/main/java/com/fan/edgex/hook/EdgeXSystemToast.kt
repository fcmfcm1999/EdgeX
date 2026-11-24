package com.fan.edgex.hook

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.fan.edgex.R
import de.robv.android.xposed.XposedBridge

/**
 * Unified EdgeX toast rendered in system_server with the EdgeX app icon,
 * matching the in-app Compose [com.fan.edgex.ui.compose.components.EdgeXToast] style.
 */
object EdgeXSystemToast {
    private const val TAG = "EdgeXSystemToast"
    private const val ACCENT_COLOR = 0xFF326D32.toInt()
    private const val LIGHT_BG_COLOR = 0xEB1C1E1B.toInt()
    private const val LIGHT_TEXT_COLOR = 0xFFF5F5F2.toInt()
    private const val DARK_BG_COLOR = 0xEBEDEDEA.toInt()
    private const val DARK_TEXT_COLOR = 0xFF181A17.toInt()

    const val DURATION_SHORT_MS = 2000L
    const val DURATION_LONG_MS = 3500L

    private const val FADE_IN_MS = 150L
    private const val FADE_OUT_MS = 180L

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private var currentView: View? = null
    private var currentWindowManager: WindowManager? = null
    private var dismissRunnable: Runnable? = null

    fun show(
        context: Context,
        text: String,
        durationMs: Long = DURATION_SHORT_MS,
    ) {
        val message = normalizeMessage(text) ?: return
        mainHandler.post {
            showOnMainThread(context, message, durationMs)
        }
    }

    fun dismiss() {
        mainHandler.post {
            dismissCurrentLocked()
        }
    }

    internal fun normalizeMessage(text: String): String? =
        text.trim().takeIf { it.isNotEmpty() }

    private fun showOnMainThread(context: Context, message: String, durationMs: Long) {
        dismissCurrentLocked()
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = buildToastView(context, message)
            val density = context.resources.displayMetrics.density
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (72 * density).toInt()
                title = TAG
            }

            view.alpha = 0f
            wm.addView(view, params)
            currentView = view
            currentWindowManager = wm

            view.animate()
                .alpha(1f)
                .setDuration(FADE_IN_MS)
                .start()

            val hideTask = Runnable {
                if (currentView !== view) return@Runnable
                view.animate()
                    .alpha(0f)
                    .setDuration(FADE_OUT_MS)
                    .withEndAction {
                        if (currentView === view) {
                            dismissCurrentLocked()
                        }
                    }
                    .start()
            }
            dismissRunnable = hideTask
            mainHandler.postDelayed(hideTask, durationMs.coerceAtLeast(500L))
        } catch (t: Throwable) {
            XposedBridge.log("EdgeX: $TAG overlay failed, falling back to system Toast: ${t.message}")
            runCatching {
                val toastDuration = if (durationMs >= DURATION_LONG_MS) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                Toast.makeText(context, message, toastDuration).show()
            }
        }
    }

    private fun dismissCurrentLocked() {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null
        val view = currentView
        val wm = currentWindowManager
        currentView = null
        currentWindowManager = null
        if (view != null && wm != null) {
            runCatching { view.animate().cancel() }
            runCatching { wm.removeViewImmediate(view) }
        }
    }

    private fun buildToastView(context: Context, message: String): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isNight) DARK_BG_COLOR else LIGHT_BG_COLOR
        val textColor = if (isNight) DARK_TEXT_COLOR else LIGHT_TEXT_COLOR

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(16), dp(8))
            elevation = dp(6).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                setColor(bgColor)
            }
        }

        val badgeSize = dp(22)
        val iconBadge = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ACCENT_COLOR)
            }
            val iconView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                ModuleRes.getDrawable(R.drawable.ic_launcher_foreground, Color.WHITE)?.let {
                    setImageDrawable(it)
                }
            }
            addView(
                iconView,
                FrameLayout.LayoutParams(badgeSize, badgeSize, Gravity.CENTER),
            )
        }
        container.addView(
            iconBadge,
            LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                marginEnd = dp(8)
            },
        )

        val maxTextWidth = (context.resources.displayMetrics.widthPixels * 0.72f).toInt()
        val textView = TextView(context).apply {
            text = message
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = maxTextWidth
        }
        container.addView(
            textView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        return container
    }
}
