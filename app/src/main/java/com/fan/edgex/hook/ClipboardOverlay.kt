package com.fan.edgex.hook

import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.HookConfigSnapshot
import de.robv.android.xposed.XposedBridge
import java.lang.ref.WeakReference

/**
 * Shows current clipboard text in a floating card.
 * Tap "Paste" to inject clipboard content into the focused field,
 * tap outside or "Close" to dismiss.
 */
object ClipboardOverlay {

    private const val TAG = "EdgeX"
    private const val AUTO_DISMISS_MS = 15_000L

    private val handler = Handler(Looper.getMainLooper())
    private var overlayRef: WeakReference<View>? = null
    private var autoDismissRunnable: Runnable? = null

    fun isShowing(): Boolean = overlayRef?.get() != null

    fun show(context: Context) {
        handler.post {
            dismiss()
            val text = readClipboardText(context)
            if (text == null) {
                showToast(context, ModuleRes.getString(R.string.clipboard_empty))
                return@post
            }
            try {
                addOverlay(context, text)
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: ClipboardOverlay show failed: ${t.message}")
            }
        }
    }

    fun dismiss() {
        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        autoDismissRunnable = null
        val overlay = overlayRef?.get() ?: return
        overlayRef = null
        try {
            val wm = overlay.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeViewImmediate(overlay)
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: ClipboardOverlay dismiss failed: ${t.message}")
        }
    }

    // ── Clipboard read ─────────────────────────────────────────────────────────

    private fun readClipboardText(context: Context): String? {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim()
            if (text.isNullOrEmpty()) null else text
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: readClipboard failed: ${t.message}")
            null
        }
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    private fun readAccentColor(): Int {
        val snapshot = HookConfigSnapshot.readFromHookFile()
        return when (snapshot[AppConfig.THEME_PRESET]) {
            "custom" -> runCatching {
                (snapshot[AppConfig.THEME_CUSTOM_COLOR] ?: "").toColorInt()
            }.getOrElse { "#326D32".toColorInt() }
            "classic" -> "#00796B".toColorInt()
            "cedar"   -> "#496B3D".toColorInt()
            "ocean"   -> "#2F6F8F".toColorInt()
            "ember"   -> "#C56B2A".toColorInt()
            else      -> "#326D32".toColorInt()
        }
    }

    private fun onColor(bg: Int): Int =
        if (ColorUtils.calculateLuminance(bg) > 0.45) "#000000".toColorInt()
        else "#FFFFFF".toColorInt()

    // ── Overlay construction ───────────────────────────────────────────────────

    private fun addOverlay(context: Context, clipText: String) {
        val density = context.resources.displayMetrics.density
        val dp = { v: Int -> (v * density + 0.5f).toInt() }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val accent = readAccentColor()

        // Full-screen root: scrim layer + card
        val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismiss()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isFocusableInTouchMode = true
            isFocusable = true
            setBackgroundColor("#80000000".toColorInt())
            setOnClickListener { dismiss() }  // tap scrim → dismiss
        }

        val card = buildCard(context, clipText, density, accent)
        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            leftMargin  = dp(16)
            rightMargin = dp(16)
            bottomMargin = dp(80)
        }
        root.addView(card, cardParams)

        @Suppress("DEPRECATION")
        wm.addView(root, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ))
        overlayRef = WeakReference(root)

        autoDismissRunnable = Runnable { dismiss() }.also {
            handler.postDelayed(it, AUTO_DISMISS_MS)
        }
    }

    private fun buildCard(
        context: Context,
        clipText: String,
        density: Float,
        accent: Int
    ): View {
        val dp = { v: Int -> (v * density + 0.5f).toInt() }

        val surface     = "#1C1B1F".toColorInt()
        val onSurf      = "#E6E1E5".toColorInt()
        val onSurfVar   = "#CAC4D0".toColorInt()
        val surfOutline = "#49454F".toColorInt()
        val onAccent    = onColor(accent)

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(20).toFloat()
            }
            elevation = dp(8).toFloat()
            setPadding(dp(20), dp(16), dp(20), dp(16))
            // Consume touches so they don't fall through to scrim
            setOnClickListener { /* consume */ }
        }

        // Title row: icon label + label
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleText = TextView(context).apply {
            text = ModuleRes.getString(R.string.clipboard_overlay_title)
            setTextColor(onSurfVar)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
        }
        titleRow.addView(titleText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        card.addView(titleRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // Content area — scrollable for long text, max 6 lines visible
        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
        }
        val contentText = TextView(context).apply {
            text = clipText
            setTextColor(onSurf)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxLines = 6
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        scrollView.addView(contentText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        card.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(14) })

        // Divider
        card.addView(View(context).apply {
            setBackgroundColor(ColorUtils.setAlphaComponent(surfOutline, 100))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
            bottomMargin = dp(14)
        })

        // Action row
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Close button — text style
        val closeBtn = TextView(context).apply {
            text = ModuleRes.getString(R.string.clipboard_close)
            setTextColor(onSurfVar)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply {
                setColor(ColorUtils.setAlphaComponent(surfOutline, 60))
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener { dismiss() }
        }
        actionRow.addView(closeBtn, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginEnd = dp(10)
        })

        // Paste button — filled accent
        val pasteBtn = TextView(context).apply {
            text = ModuleRes.getString(R.string.clipboard_paste)
            setTextColor(onAccent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply {
                setColor(accent)
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener {
                dismiss()
                // Give the overlay 150 ms to vanish so focus returns to the text field
                handler.postDelayed({
                    try {
                        GlobalActionHelper.performGlobalAction(
                            it.context, GlobalActionHelper.GLOBAL_ACTION_PASTE
                        )
                    } catch (t: Throwable) {
                        XposedBridge.log("$TAG: paste failed: ${t.message}")
                    }
                }, 150)
            }
        }
        actionRow.addView(pasteBtn, LinearLayout.LayoutParams(0, dp(44), 1f))

        card.addView(actionRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return card
    }

    private fun showToast(context: Context, text: String) {
        try {
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) { }
    }
}
