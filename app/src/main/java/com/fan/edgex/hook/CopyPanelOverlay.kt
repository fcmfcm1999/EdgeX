package com.fan.edgex.hook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import de.robv.android.xposed.XposedBridge

/**
 * Floating panel overlay for universal copy.
 * Shows all text items from the current page; user taps one to copy it.
 * Runs in system_server process where system window types are available.
 */
object CopyPanelOverlay {

    private const val TAG = "EdgeX"
    private const val AUTO_DISMISS_MS = 15000L

    private val handler = Handler(Looper.getMainLooper())
    private var currentPanel: View? = null
    private var dismissRunnable: Runnable? = null

    fun show(context: Context, texts: List<String>) {
        handler.post {
            dismiss()
            try {
                addPanel(context, texts)
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: CopyPanel show failed: ${t.message}")
            }
        }
    }

    fun dismiss() {
        val runnable = dismissRunnable
        if (runnable != null) {
            handler.removeCallbacks(runnable)
            dismissRunnable = null
        }
        val panel = currentPanel ?: return
        try {
            val wm = panel.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeViewImmediate(panel)
            currentPanel = null
        } catch (t: Throwable) {
            // Removal failed — keep reference and schedule retry so window doesn't orphan
            XposedBridge.log("$TAG: CopyPanel dismiss failed, retrying: ${t.message}")
            handler.postDelayed({ dismiss() }, 500)
        }
    }

    private fun addPanel(context: Context, texts: List<String>) {
        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        val panelWidth = (screenWidth * 0.92).toInt()
        val maxListHeight = (screenHeight * 0.55).toInt()
        val dp = { value: Int -> (value * density + 0.5f).toInt() }
        val isChinese = context.resources.configuration.locales[0].language == "zh"

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Root — semi-transparent scrim, tap outside to dismiss
        val root = object : FrameLayout(context) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    dismiss()
                }
                return true
            }
        }
        root.setBackgroundColor(Color.parseColor("#66000000"))

        // Card container
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F0222222"))
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(16), dp(14), dp(16), dp(10))
            elevation = dp(12).toFloat()
            // Consume touches so they don't reach the root scrim
            setOnTouchListener { _, _ -> true }
        }

        // Header row: title + count
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            text = if (isChinese) "全局复制" else "Global Copy"
            setTextColor(Color.parseColor("#CCCCCC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val count = TextView(context).apply {
            text = if (isChinese) "${texts.size} 项" else "${texts.size} items"
            setTextColor(Color.parseColor("#888888"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        header.addView(count)
        card.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })

        // Hint
        val hint = TextView(context).apply {
            text = if (isChinese) "点击文字即可复制" else "Tap text to copy"
            setTextColor(Color.parseColor("#777777"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        card.addView(hint, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        // Scrollable list of text items
        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
        }
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        for ((index, text) in texts.withIndex()) {
            val itemView = createTextItem(context, text, density, isChinese)
            listContainer.addView(itemView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index < texts.size - 1) bottomMargin = dp(4)
            })
        }

        scrollView.addView(listContainer)
        card.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // Cap scroll height
        scrollView.post {
            if (scrollView.height > maxListHeight) {
                scrollView.layoutParams = scrollView.layoutParams.apply { height = maxListHeight }
            }
        }

        // Bottom button row
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        // "Copy All" button
        val copyAllBtn = createButton(
            context,
            if (isChinese) "复制全部" else "Copy All",
            density,
            highlight = true
        ) {
            val allText = texts.joinToString("\n")
            if (copyToClipboard(context, allText)) {
                Toast.makeText(
                    context,
                    if (isChinese) "已复制全部文本" else "All text copied",
                    Toast.LENGTH_SHORT
                ).show()
            }
            dismiss()
        }
        buttonRow.addView(copyAllBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)
        ).apply { marginStart = dp(8) })

        // Close button
        val closeBtn = createButton(
            context,
            if (isChinese) "关闭" else "Close",
            density
        ) { dismiss() }
        buttonRow.addView(closeBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)
        ).apply { marginStart = dp(10) })

        card.addView(buttonRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Position card
        val cardParams = FrameLayout.LayoutParams(panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        }
        root.addView(card, cardParams)

        @Suppress("DEPRECATION")
        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        wm.addView(root, windowParams)
        currentPanel = root

        val runnable = Runnable { dismiss() }
        dismissRunnable = runnable
        handler.postDelayed(runnable, AUTO_DISMISS_MS)
    }

    private fun createTextItem(
        context: Context,
        text: String,
        density: Float,
        isChinese: Boolean
    ): View {
        val dp = { value: Int -> (value * density + 0.5f).toInt() }
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#E0E0E0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            maxLines = 6
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.15f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2A2A"))
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener {
                if (copyToClipboard(context, text)) {
                    Toast.makeText(
                        context,
                        if (isChinese) "已复制" else "Copied",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                dismiss()
            }
        }
    }

    private fun createButton(
        context: Context,
        label: String,
        density: Float,
        highlight: Boolean = false,
        onClick: () -> Unit
    ): TextView {
        val dp = { value: Int -> (value * density + 0.5f).toInt() }
        return TextView(context).apply {
            text = label
            setTextColor(if (highlight) Color.BLACK else Color.parseColor("#CCCCCC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply {
                setColor(if (highlight) Color.parseColor("#4FC3F7") else Color.parseColor("#3A3A3A"))
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun copyToClipboard(context: Context, text: String): Boolean {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false
            cm.setPrimaryClip(ClipData.newPlainText("EdgeX", text))
            true
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Clipboard copy failed: ${t.message}")
            false
        }
    }
}
