package com.fan.edgex.hook

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
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
 * Clipboard history overlay.
 * ClipboardHook feeds new entries via onClipboardChanged(); this object
 * keeps up to MAX_HISTORY entries (most-recent first, deduped) and shows
 * them in a scrollable bottom sheet. Tap an item to paste it; tap × to delete.
 */
object ClipboardOverlay {

    private const val TAG = "EdgeX"
    private const val AUTO_DISMISS_MS = 30_000L
    private const val MAX_HISTORY = 50

    private val handler = Handler(Looper.getMainLooper())
    private var overlayRef: WeakReference<View>? = null
    private var autoDismissRunnable: Runnable? = null

    // ── History storage ────────────────────────────────────────────────────────

    private val history = mutableListOf<String>()   // index 0 = most-recent

    @Synchronized
    fun onClipboardChanged(text: String?) {
        if (text.isNullOrEmpty()) return
        history.remove(text)           // deduplicate: move existing to top
        history.add(0, text)
        if (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
    }

    @Synchronized
    private fun historySnapshot() = ArrayList(history)

    @Synchronized
    private fun deleteEntry(text: String) {
        history.remove(text)
    }

    @Synchronized
    private fun clearAll() {
        history.clear()
    }

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

    // ── Theme ──────────────────────────────────────────────────────────────────

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

    private fun addOverlay(context: Context, items: List<String>) {
        val density = context.resources.displayMetrics.density
        val screenH = context.resources.displayMetrics.heightPixels
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val accent = readAccentColor()

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
            setOnClickListener { dismiss() }
        }

        val sheet = buildSheet(context, items, density, screenH, accent)
        root.addView(sheet, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM })

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

    private fun buildSheet(
        context: Context,
        initialItems: List<String>,
        density: Float,
        screenH: Int,
        accent: Int
    ): View {
        val dp = { v: Int -> (v * density + 0.5f).toInt() }

        val surface    = "#1C1B1F".toColorInt()
        val surfaceVar = "#2B2930".toColorInt()
        val onSurf     = "#E6E1E5".toColorInt()
        val onSurfVar  = "#CAC4D0".toColorInt()
        val outline    = "#49454F".toColorInt()
        val onAccent   = onColor(accent)

        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(surface)
                cornerRadii = floatArrayOf(
                    dp(24).toFloat(), dp(24).toFloat(),
                    dp(24).toFloat(), dp(24).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
            elevation = dp(8).toFloat()
            setOnClickListener { /* consume */ }
        }

        // ── Handle ──
        sheet.addView(View(context).apply {
            background = GradientDrawable().apply {
                setColor(outline)
                cornerRadius = dp(2).toFloat()
            }
        }, LinearLayout.LayoutParams(dp(32), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(12)
            bottomMargin = dp(4)
        })

        // ── Header: title + count + clear-all ──
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(12), dp(12))
        }

        val titleView = TextView(context).apply {
            text = ModuleRes.getString(R.string.clipboard_overlay_title)
            setTextColor(onSurf)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val countView = TextView(context).apply {
            text = "${initialItems.size}"
            setTextColor(onSurfVar)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        header.addView(countView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(12) })

        val clearAllBtn = TextView(context).apply {
            text = ModuleRes.getString(R.string.clipboard_clear_all)
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        header.addView(clearAllBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        sheet.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // ── Divider ──
        sheet.addView(View(context).apply {
            setBackgroundColor(ColorUtils.setAlphaComponent(outline, 80))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        // ── Scrollable list ──
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(listContainer, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        // Max height: 65% of screen
        val maxListH = (screenH * 0.65f).toInt()
        sheet.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { height = LinearLayout.LayoutParams.WRAP_CONTENT }.also {
            it.height = minOf(maxListH, LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        // Bottom padding inside list
        sheet.addView(View(context), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(16)
        ))

        // ── Populate list items ──
        fun rebuildList(items: List<String>) {
            listContainer.removeAllViews()
            countView.text = "${items.size}"
            items.forEachIndexed { index, text ->
                val row = buildItemRow(
                    context, text, density, accent, onAccent, onSurf, onSurfVar, surfaceVar, outline,
                    onPaste = {
                        dismiss()
                        handler.postDelayed({
                            try {
                                GlobalActionHelper.performGlobalAction(
                                    context, GlobalActionHelper.GLOBAL_ACTION_PASTE
                                )
                            } catch (t: Throwable) {
                                XposedBridge.log("$TAG: paste failed: ${t.message}")
                            }
                        }, 150)
                    },
                    onDelete = {
                        deleteEntry(text)
                        val updated = historySnapshot()
                        if (updated.isEmpty()) {
                            dismiss()
                        } else {
                            rebuildList(updated)
                        }
                    }
                )
                listContainer.addView(row, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ))
                // Thin separator between items (not after last)
                if (index < items.lastIndex) {
                    listContainer.addView(View(context).apply {
                        setBackgroundColor(ColorUtils.setAlphaComponent(outline, 40))
                    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                        marginStart = dp(20)
                        marginEnd = dp(20)
                    })
                }
            }
        }

        rebuildList(initialItems)

        clearAllBtn.setOnClickListener {
            clearAll()
            dismiss()
        }

        // Clamp ScrollView height after measure
        scrollView.post {
            val measuredH = listContainer.height
            if (measuredH > maxListH) {
                scrollView.layoutParams = (scrollView.layoutParams as LinearLayout.LayoutParams).also {
                    it.height = maxListH
                }
                scrollView.requestLayout()
            }
        }

        return sheet
    }

    private fun buildItemRow(
        context: Context,
        text: String,
        density: Float,
        accent: Int,
        onAccent: Int,
        onSurf: Int,
        onSurfVar: Int,
        surfaceVar: Int,
        outline: Int,
        onPaste: () -> Unit,
        onDelete: () -> Unit
    ): View {
        val dp = { v: Int -> (v * density + 0.5f).toInt() }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(8), dp(14))
            // Ripple-like press feedback via state
            setOnClickListener { onPaste() }
        }

        // Text preview (2 lines max)
        val textView = TextView(context).apply {
            this.text = text
            setTextColor(onSurf)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        row.addView(textView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(8)
        })

        // Delete button
        val deleteBtn = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            val size = dp(40)
            background = GradientDrawable().apply {
                setColor("#00000000".toColorInt())
                cornerRadius = dp(20).toFloat()
            }
            setOnClickListener { onDelete() }
        }
        val deleteIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_delete)
            imageTintList = android.content.res.ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(onSurfVar, 160)
            )
        }
        deleteBtn.addView(deleteIcon, LinearLayout.LayoutParams(dp(20), dp(20)))
        row.addView(deleteBtn, LinearLayout.LayoutParams(dp(40), dp(40)))

        return row
    }

    private fun showToast(context: Context, text: String) {
        try {
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) { }
    }
}
