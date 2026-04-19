package com.fan.edgex.hook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import de.robv.android.xposed.XposedBridge

/**
 * Google Lens-style text selection overlay.
 * Highlights text blocks in-place on the current screen.
 * User taps blocks to select, then copies selected text.
 */
object TextSelectionOverlay {

    private const val TAG = "EdgeX"
    private const val AUTO_DISMISS_MS = 30000L

    private val handler = Handler(Looper.getMainLooper())
    private var currentOverlay: View? = null
    private var dismissRunnable: Runnable? = null

    private var hintView: TextView? = null
    private var copyButton: TextView? = null
    private var selectAllButton: TextView? = null

    private class SelectableBlock(
        val text: String,
        val bounds: Rect,
        var selected: Boolean = false
    )

    fun show(context: Context, blocks: List<UniversalCopyManager.TextBlock>) {
        handler.post {
            dismiss()
            try {
                val selectable = blocks.map { SelectableBlock(it.text, Rect(it.bounds)) }
                addOverlay(context, selectable)
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: TextSelectionOverlay show failed: ${t.message}")
            }
        }
    }

    fun dismiss() {
        val runnable = dismissRunnable
        if (runnable != null) {
            handler.removeCallbacks(runnable)
            dismissRunnable = null
        }
        hintView = null
        copyButton = null
        selectAllButton = null
        val overlay = currentOverlay ?: return
        try {
            val wm = overlay.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeViewImmediate(overlay)
            currentOverlay = null
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: TextSelectionOverlay dismiss failed, retrying: ${t.message}")
            handler.postDelayed({ dismiss() }, 500)
        }
    }

    private fun addOverlay(context: Context, blocks: List<SelectableBlock>) {
        val density = context.resources.displayMetrics.density
        val dp = { value: Int -> (value * density + 0.5f).toInt() }
        val isChinese = context.resources.configuration.locales[0].language == "zh"
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

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
        }

        // Custom view for drawing and selecting text blocks
        val blocksView = TextBlocksView(context, blocks, density,
            onEmptyTap = { dismiss() },
            onSelectionChanged = { updateToolbarState(blocks, isChinese) }
        )
        root.addView(blocksView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Bottom toolbar
        val toolbar = createToolbar(context, blocks, density, isChinese)
        val toolbarParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(48)
        }
        root.addView(toolbar, toolbarParams)

        @Suppress("DEPRECATION")
        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        wm.addView(root, windowParams)
        currentOverlay = root

        val runnable = Runnable { dismiss() }
        dismissRunnable = runnable
        handler.postDelayed(runnable, AUTO_DISMISS_MS)
    }

    private fun createToolbar(
        context: Context,
        blocks: List<SelectableBlock>,
        density: Float,
        isChinese: Boolean
    ): LinearLayout {
        val dp = { value: Int -> (value * density + 0.5f).toInt() }

        val toolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E8222222"))
                cornerRadius = dp(28).toFloat()
            }
            setPadding(dp(16), dp(10), dp(8), dp(10))
            elevation = dp(8).toFloat()
            // Consume touches so they don't reach the blocks view
            setOnTouchListener { _, _ -> true }
        }

        // Hint / selected count
        val hint = TextView(context).apply {
            text = if (isChinese) "点按文字选择" else "Tap to select"
            setTextColor(Color.parseColor("#AAAAAA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        hintView = hint
        toolbar.addView(hint, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginEnd = dp(12) })

        // Select All button
        val selectAll = createToolbarButton(context, density,
            if (isChinese) "全选" else "All",
            Color.parseColor("#3A3A3A"),
            Color.parseColor("#CCCCCC")
        ) {
            val allSelected = blocks.all { it.selected }
            blocks.forEach { it.selected = !allSelected }
            (currentOverlay as? ViewGroup)?.getChildAt(0)?.invalidate()
            updateToolbarState(blocks, isChinese)
        }
        selectAllButton = selectAll
        toolbar.addView(selectAll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)
        ).apply { marginEnd = dp(6) })

        // Copy button
        val copy = createToolbarButton(context, density,
            if (isChinese) "复制" else "Copy",
            Color.parseColor("#009688"),
            Color.WHITE
        ) {
            val selected = blocks.filter { it.selected }
            if (selected.isNotEmpty()) {
                val text = selected.joinToString("\n") { it.text }
                copyToClipboard(context, text)
                val msg = if (isChinese) "已复制 ${selected.size} 项" else "${selected.size} copied"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
        copy.alpha = 0.4f
        copy.isEnabled = false
        copyButton = copy
        toolbar.addView(copy, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)
        ).apply { marginEnd = dp(6) })

        // Close button
        val close = createToolbarButton(context, density,
            "×",
            Color.parseColor("#3A3A3A"),
            Color.parseColor("#CCCCCC")
        ) { dismiss() }
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        toolbar.addView(close, LinearLayout.LayoutParams(dp(34), dp(34)))

        return toolbar
    }

    private fun createToolbarButton(
        context: Context,
        density: Float,
        label: String,
        bgColor: Int,
        textColor: Int,
        onClick: () -> Unit
    ): TextView {
        val dp = { value: Int -> (value * density + 0.5f).toInt() }
        return TextView(context).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(17).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun updateToolbarState(blocks: List<SelectableBlock>, isChinese: Boolean) {
        val count = blocks.count { it.selected }
        hintView?.text = if (count > 0) {
            if (isChinese) "已选 $count 项" else "$count selected"
        } else {
            if (isChinese) "点按文字选择" else "Tap to select"
        }
        copyButton?.apply {
            alpha = if (count > 0) 1f else 0.4f
            isEnabled = count > 0
        }
        selectAllButton?.text = if (blocks.all { it.selected }) {
            if (isChinese) "取消全选" else "Deselect"
        } else {
            if (isChinese) "全选" else "All"
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            cm.setPrimaryClip(ClipData.newPlainText("EdgeX", text))
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Clipboard copy failed: ${t.message}")
        }
    }

    /**
     * Custom view that draws highlight boxes at text positions and handles tap selection.
     */
    private class TextBlocksView(
        context: Context,
        private val blocks: List<SelectableBlock>,
        private val density: Float,
        private val onEmptyTap: () -> Unit,
        private val onSelectionChanged: () -> Unit
    ) : View(context) {

        private val cornerRadius = 4f * density
        private val tapPadding = (10 * density).toInt()
        private val tapSlopSq = (24 * density * 24 * density)

        private val scrimPaint = Paint().apply {
            color = Color.parseColor("#28000000")
            style = Paint.Style.FILL
        }

        private val normalFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#18FFFFFF")
            style = Paint.Style.FILL
        }

        private val normalStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }

        private val selectedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#404FC3F7")
            style = Paint.Style.FILL
        }

        private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DD4FC3F7")
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }

        private var downX = 0f
        private var downY = 0f
        private var downBlock: SelectableBlock? = null
        private val tmpRect = RectF()

        // Offset between view-local coords and screen coords
        private var viewOffsetX = 0
        private var viewOffsetY = 0
        private val locationOnScreen = IntArray(2)

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            getLocationOnScreen(locationOnScreen)
            viewOffsetX = locationOnScreen[0]
            viewOffsetY = locationOnScreen[1]
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
            for (block in blocks) {
                // Convert screen coords → view-local coords for drawing
                tmpRect.set(
                    block.bounds.left.toFloat() - viewOffsetX,
                    block.bounds.top.toFloat() - viewOffsetY,
                    block.bounds.right.toFloat() - viewOffsetX,
                    block.bounds.bottom.toFloat() - viewOffsetY
                )
                if (block.selected) {
                    canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, selectedFillPaint)
                    canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, selectedStrokePaint)
                } else {
                    canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, normalFillPaint)
                    canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, normalStrokePaint)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downBlock = findBlockAt(event.rawX, event.rawY)
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (dx * dx + dy * dy < tapSlopSq) {
                        val upBlock = findBlockAt(event.rawX, event.rawY)
                        if (upBlock != null && upBlock === downBlock) {
                            upBlock.selected = !upBlock.selected
                            invalidate()
                            onSelectionChanged()
                        } else if (upBlock == null && downBlock == null) {
                            onEmptyTap()
                        }
                    }
                    downBlock = null
                }
            }
            return true
        }

        private fun findBlockAt(x: Float, y: Float): SelectableBlock? {
            val ix = x.toInt()
            val iy = y.toInt()
            var best: SelectableBlock? = null
            var bestArea = Int.MAX_VALUE
            for (block in blocks) {
                val b = block.bounds
                if (ix >= b.left - tapPadding && ix <= b.right + tapPadding &&
                    iy >= b.top - tapPadding && iy <= b.bottom + tapPadding
                ) {
                    val area = b.width() * b.height()
                    if (area < bestArea) {
                        best = block
                        bestArea = area
                    }
                }
            }
            return best
        }
    }
}
