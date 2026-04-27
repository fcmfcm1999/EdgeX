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
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.HookConfigSnapshot
import de.robv.android.xposed.XposedBridge
import java.lang.ref.WeakReference

/**
 * Clipboard history overlay — styled to match DrawerWindow.
 * ClipboardHook feeds new entries; up to MAX_HISTORY are kept (deduped, most-recent first).
 * Tap an item → paste; tap × → delete that entry; "清空" → clear all.
 */
object ClipboardOverlay {

    private const val TAG = "EdgeX"
    private const val AUTO_DISMISS_MS = 30_000L
    private const val MAX_HISTORY = 50

    private val handler = Handler(Looper.getMainLooper())
    private var overlayRef: WeakReference<View>? = null
    private var autoDismissRunnable: Runnable? = null

    // ── History ────────────────────────────────────────────────────────────────

    private val history = mutableListOf<String>()

    @Synchronized
    fun onClipboardChanged(text: String?) {
        if (text.isNullOrEmpty()) return
        history.remove(text)
        history.add(0, text)
        if (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
    }

    @Synchronized private fun historySnapshot() = ArrayList(history)
    @Synchronized private fun deleteEntry(text: String) { history.remove(text) }
    @Synchronized private fun clearAll() { history.clear() }

    // ── Show / dismiss ─────────────────────────────────────────────────────────

    fun isShowing(): Boolean = overlayRef?.get() != null

    fun show(context: Context) {
        handler.post {
            dismiss()
            val items = historySnapshot()
            if (items.isEmpty()) {
                showToast(context, ModuleRes.getString(R.string.clipboard_empty))
                return@post
            }
            try {
                addOverlay(context, items)
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

    // ── Theme (mirrors DrawerWindow) ───────────────────────────────────────────

    private fun isDark(context: Context) =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

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

    // ── Overlay construction ───────────────────────────────────────────────────

    private fun addOverlay(context: Context, items: List<String>) {
        val dp = context.resources.displayMetrics.density
        val screenH = context.resources.displayMetrics.heightPixels
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val accent = readAccentColor()
        val dark = isDark(context)

        // Color tokens — identical to DrawerWindow.setupModernLayout
        val surfaceBg   = if (dark) Color.argb(238, 20, 19, 30)   else Color.argb(238, 250, 248, 255)
        val textPrimary = if (dark) Color.WHITE                    else "#1C1B1F".toColorInt()
        val textMuted   = if (dark) "#9A97AA".toColorInt()         else "#6B6880".toColorInt()
        val divider     = if (dark) Color.argb(35, 255, 255, 255)  else Color.argb(40, 0, 0, 0)
        val itemBg      = if (dark) Color.argb(160, 42, 40, 58)    else Color.argb(170, 230, 226, 244)
        val cornerRad   = 28f * dp

        var sheetTop = 0  // populated after first layout; used by dispatchTouchEvent

        val root = object : FrameLayout(context) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                // Single-tap anywhere above the sheet → dismiss immediately on ACTION_DOWN
                if (ev.action == MotionEvent.ACTION_DOWN && sheetTop > 0 && ev.y < sheetTop) {
                    dismiss()
                    return true
                }
                return super.dispatchTouchEvent(ev)
            }
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismiss()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val sheet = buildSheet(
            context, items, dp, screenH,
            surfaceBg, textPrimary, textMuted, divider, itemBg, cornerRad, accent
        )
        root.addView(sheet, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM })

        // Capture sheet top after layout so dispatchTouchEvent can compare y
        sheet.addOnLayoutChangeListener { _, _, top, _, _, _, _, _, _ ->
            sheetTop = top
        }

        @Suppress("DEPRECATION")
        wm.addView(root, WindowManager.LayoutParams().apply {
            type   = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
            format = PixelFormat.TRANSLUCENT
            width  = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            flags  = WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                     WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            dimAmount       = 0.25f
            blurBehindRadius = 36
        })
        overlayRef = WeakReference(root)

        autoDismissRunnable = Runnable { dismiss() }.also {
            handler.postDelayed(it, AUTO_DISMISS_MS)
        }
    }

    private fun buildSheet(
        context: Context,
        initialItems: List<String>,
        dp: Float,
        screenH: Int,
        surfaceBg: Int,
        textPrimary: Int,
        textMuted: Int,
        dividerColor: Int,
        itemBg: Int,
        cornerRad: Float,
        accent: Int
    ): View {
        val dpi = { v: Int -> (v * dp + 0.5f).toInt() }

        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(surfaceBg)
                cornerRadii = floatArrayOf(
                    cornerRad, cornerRad, cornerRad, cornerRad, 0f, 0f, 0f, 0f
                )
            }
            elevation = 20f * dp
            isClickable = true   // block touches from falling through to root behind
        }

        // ── Handle ──
        sheet.addView(View(context).apply {
            background = GradientDrawable().apply {
                setColor(dividerColor)
                cornerRadius = dpi(2).toFloat()
            }
        }, LinearLayout.LayoutParams(dpi(32), dpi(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dpi(12)
            bottomMargin = dpi(6)
        })

        // ── Header — mirrors DrawerWindow header style ──
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20), dpi(12), dpi(16), dpi(14))
        }

        // Top row: title + clear-all
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(context).apply {
            text = ModuleRes.getString(R.string.clipboard_overlay_title)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = -0.02f
            setTextColor(textPrimary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val clearAllBtn = TextView(context).apply {
            text = ModuleRes.getString(R.string.clipboard_clear_all)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
            gravity = Gravity.CENTER
            setPadding(dpi(12), dpi(8), dpi(4), dpi(8))
            setOnClickListener { clearAll(); dismiss() }
        }
        titleRow.addView(clearAllBtn)
        header.addView(titleRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Subtitle: "N 条记录"
        val countView = TextView(context).apply {
            text = ModuleRes.getString(R.string.clipboard_count, initialItems.size)
            textSize = 12.5f
            setTextColor(textMuted)
            setPadding(0, dpi(4), 0, 0)
        }
        header.addView(countView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        sheet.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // ── Divider ──
        sheet.addView(View(context).apply { setBackgroundColor(dividerColor) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

        // ── Scrollable list ──
        val listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(listContainer, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        sheet.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        sheet.addView(View(context), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dpi(16)
        ))

        // ── Populate / rebuild helper ──
        fun rebuildList(items: List<String>) {
            listContainer.removeAllViews()
            countView.text = ModuleRes.getString(R.string.clipboard_count, items.size)

            items.forEachIndexed { index, text ->
                listContainer.addView(
                    buildItemRow(context, text, dp, textPrimary, textMuted, itemBg,
                        onPaste = {
                            dismiss()
                            // Inject text directly as key events — no clipboard write,
                            // so the system clipboard-change notification never appears.
                            handler.postDelayed({ injectText(context, text) }, 150)
                        },
                        onDelete = {
                            deleteEntry(text)
                            val updated = historySnapshot()
                            if (updated.isEmpty()) dismiss() else rebuildList(updated)
                        }
                    ),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                if (index < items.lastIndex) {
                    listContainer.addView(View(context).apply { setBackgroundColor(dividerColor) },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                            marginStart = dpi(20); marginEnd = dpi(20)
                        })
                }
            }

            // Clamp scroll height to 65% of screen after items are laid out
            scrollView.post {
                val maxH = (screenH * 0.65f).toInt()
                if (listContainer.height > maxH) {
                    scrollView.layoutParams = (scrollView.layoutParams as LinearLayout.LayoutParams)
                        .also { it.height = maxH }
                    scrollView.requestLayout()
                }
            }
        }

        rebuildList(initialItems)
        return sheet
    }

    private fun buildItemRow(
        context: Context,
        text: String,
        dp: Float,
        textPrimary: Int,
        textMuted: Int,
        itemBg: Int,
        onPaste: () -> Unit,
        onDelete: () -> Unit
    ): View {
        val dpi = { v: Int -> (v * dp + 0.5f).toInt() }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(20), dpi(12), dpi(8), dpi(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { onPaste() }
        }

        row.addView(TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(textPrimary)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing((2 * dp), 1f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dpi(4)
        })

        // × delete button
        row.addView(TextView(context).apply {
            this.text = "×"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textMuted)
            gravity = Gravity.CENTER
            setPadding(dpi(12), dpi(4), dpi(12), dpi(4))
            setOnClickListener { onDelete() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        return row
    }

    /**
     * Inject [text] directly into the focused input field via key events,
     * mirroring XPE's approach (y0.i0 / KeyCharacterMap.getEvents).
     *
     * For ASCII / mappable characters: KeyCharacterMap converts each char to
     * DOWN+UP key events and we inject them one by one.
     * For Unicode / non-ASCII that can't be mapped: fall back to the synthetic
     * ACTION_MULTIPLE + KEYCODE_UNKNOWN "characters" KeyEvent, which Android's
     * InputDispatcher forwards as commitText on the active input connection.
     *
     * Neither path writes to ClipboardManager, so the system clipboard-change
     * notification (bottom-left toast) is never triggered.
     */
    private fun injectText(context: Context, text: String) {
        if (text.isEmpty()) return
        try {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE) ?: return
            val injectMethod = inputManager.javaClass.getMethod(
                "injectInputEvent",
                android.view.InputEvent::class.java,
                Int::class.javaPrimitiveType
            )

            val charMap = android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD)
            val events = charMap.getEvents(text.toCharArray())

            if (events != null) {
                // ASCII / fully mappable — inject each DOWN+UP key event
                for (event in events) {
                    val timed = android.view.KeyEvent.changeTimeRepeat(
                        event, android.os.SystemClock.uptimeMillis(), 0
                    )
                    injectMethod.invoke(inputManager, timed, 0)
                }
            } else {
                // Unicode / non-ASCII — synthetic "characters" event
                // (deprecated API but still processed by InputDispatcher on Android 15)
                @Suppress("DEPRECATION")
                val charEvent = android.view.KeyEvent(
                    android.os.SystemClock.uptimeMillis(),
                    text,
                    android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,
                    0
                )
                injectMethod.invoke(inputManager, charEvent, 0)
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: injectText failed: ${t.message}")
        }
    }

    private fun showToast(context: Context, text: String) {
        try {
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) { }
    }
}
