package com.fan.edgex.hook

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.HookConfigSnapshot
import de.robv.android.xposed.XposedBridge
import java.lang.ref.WeakReference

/**
 * Material 3-style text selection overlay for universal copy.
 * Shows highlighted text blocks in-place; user taps to select, then copies.
 */
object TextSelectionOverlay {

    private const val TAG = "EdgeX"
    private const val AUTO_DISMISS_MS = 30_000L

    private val handler = Handler(Looper.getMainLooper())
    private var overlayRef: WeakReference<View>? = null
    private var sheetRef: WeakReference<View>? = null
    private var hintView: WeakReference<TextView>? = null
    private var copyButton: WeakReference<TextView>? = null
    private var selectAllButton: WeakReference<TextView>? = null
    private var autoDismissRunnable: Runnable? = null

    fun isShowing(): Boolean = overlayRef?.get() != null

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
        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        autoDismissRunnable = null
        hintView = null
        copyButton = null
        selectAllButton = null

        val overlay = overlayRef?.get() ?: return
        overlayRef = null
        sheetRef = null

        // Animate out then remove from WM
        ObjectAnimator.ofFloat(overlay, "alpha", overlay.alpha, 0f).apply {
            duration = 180
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    try {
                        val wm = overlay.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                        wm.removeViewImmediate(overlay)
                    } catch (t: Throwable) {
                        XposedBridge.log("$TAG: removeView failed: ${t.message}")
                    }
                }
            })
            start()
        }
    }

    // ── Color helpers ──────────────────────────────────────────────────────────

    private fun readAccentColor(context: Context): Int {
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
        if (ColorUtils.calculateLuminance(bg) > 0.45) Color.BLACK else Color.WHITE

    // ── Overlay construction ───────────────────────────────────────────────────

    private fun addOverlay(context: Context, blocks: List<SelectableBlock>) {
        val density = context.resources.displayMetrics.density
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val accent = readAccentColor(context)

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
            alpha = 0f
        }

        // Text blocks canvas layer
        val blocksView = TextBlocksView(context, blocks, density, accent,
            onEmptyTap = { dismiss() },
            onSelectionChanged = { updateToolbarState(blocks) }
        )
        root.addView(blocksView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Material 3 bottom sheet
        val sheet = buildBottomSheet(context, blocks, density, accent)
        val sheetParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM }
        root.addView(sheet, sheetParams)
        sheetRef = WeakReference(sheet)

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

        // Animate in after first layout
        sheet.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    sheet.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val slideDistance = sheet.height.toFloat().coerceAtLeast(300f) + 40f
                    sheet.translationY = slideDistance
                    ObjectAnimator.ofFloat(root, "alpha", 0f, 1f).apply {
                        duration = 200
                        start()
                    }
                    ObjectAnimator.ofFloat(sheet, "translationY", slideDistance, 0f).apply {
                        duration = 380
                        interpolator = OvershootInterpolatorCompat(0.65f)
                        start()
                    }
                }
            }
        )

        autoDismissRunnable = Runnable { dismiss() }.also {
            handler.postDelayed(it, AUTO_DISMISS_MS)
        }
    }

    // ── Bottom sheet (Material 3 style) ────────────────────────────────────────

    private fun buildBottomSheet(
        context: Context,
        blocks: List<SelectableBlock>,
        density: Float,
        accent: Int
    ): View {
        val dp = { v: Int -> (v * density + 0.5f).toInt() }

        // M3 dark surface tokens
        val surface      = "#1C1B1F".toColorInt()
        val surfaceVar   = "#49454F".toColorInt()   // outline-variant for divider/handle
        val onSurf       = "#E6E1E5".toColorInt()
        val onSurfVar    = "#CAC4D0".toColorInt()
        val tonalSurf    = ColorUtils.blendARGB(surface, accent, 0.12f)
        val onAccent     = onColor(accent)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(surface)
                // Rounded top corners only
                cornerRadii = floatArrayOf(
                    dp(28).toFloat(), dp(28).toFloat(),
                    dp(28).toFloat(), dp(28).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
            elevation = dp(6).toFloat()
            setPadding(dp(24), dp(12), dp(24), dp(28))
            setOnTouchListener { _, _ -> true }  // consume touches
        }

        // Handle pill
        container.addView(View(context).apply {
            background = GradientDrawable().apply {
                setColor(surfaceVar)
                cornerRadius = dp(2).toFloat()
            }
        }, LinearLayout.LayoutParams(dp(32), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(20)
        })

        // Hint / selected-count label
        val hint = TextView(context).apply {
            text = ModuleRes.getString(R.string.copy_tap_to_select)
            setTextColor(onSurfVar)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            letterSpacing = 0.01f
        }
        hintView = WeakReference(hint)
        container.addView(hint, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) })

        // Thin divider
        container.addView(View(context).apply {
            setBackgroundColor(ColorUtils.setAlphaComponent(surfaceVar, 80))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
            bottomMargin = dp(16)
        })

        // Action row
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // "Select All" — tonal button
        val selectAllBtn = makeButton(
            context, density,
            text = ModuleRes.getString(R.string.copy_select_all),
            bgColor = tonalSurf,
            textColor = onSurf
        ) {
            val allSelected = blocks.all { it.selected }
            blocks.forEach { it.selected = !allSelected }
            (overlayRef?.get() as? ViewGroup)?.getChildAt(0)?.invalidate()
            updateToolbarState(blocks)
        }
        selectAllButton = WeakReference(selectAllBtn)
        row.addView(selectAllBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginEnd = dp(12)
        })

        // "Copy" — filled button (accent), disabled initially
        val copyBtn = makeButton(
            context, density,
            text = ModuleRes.getString(R.string.copy_copy),
            bgColor = accent,
            textColor = onAccent
        ) {
            val selected = blocks.filter { it.selected }
            if (selected.isNotEmpty()) {
                val text = selected.joinToString("\n") { it.text }
                copyToClipboard(context, text)
                Toast.makeText(
                    context,
                    ModuleRes.getString(R.string.copy_copied_count, selected.size),
                    Toast.LENGTH_SHORT
                ).show()
                dismiss()
            }
        }
        copyBtn.alpha = 0.38f
        copyBtn.isEnabled = false
        copyButton = WeakReference(copyBtn)
        row.addView(copyBtn, LinearLayout.LayoutParams(0, dp(48), 1f))

        container.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return container
    }

    private fun makeButton(
        context: Context,
        density: Float,
        text: String,
        bgColor: Int,
        textColor: Int,
        onClick: () -> Unit
    ): TextView {
        val dp = { v: Int -> (v * density + 0.5f).toInt() }
        return TextView(context).apply {
            this.text = text
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.01f
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun updateToolbarState(blocks: List<SelectableBlock>) {
        val count = blocks.count { it.selected }
        hintView?.get()?.text = if (count > 0) {
            ModuleRes.getString(R.string.copy_selected_count, count)
        } else {
            ModuleRes.getString(R.string.copy_tap_to_select)
        }
        copyButton?.get()?.apply {
            val enabled = count > 0
            isEnabled = enabled
            animate().alpha(if (enabled) 1f else 0.38f).setDuration(120).start()
            text = if (enabled) {
                "${ModuleRes.getString(R.string.copy_copy)}  ·  $count"
            } else {
                ModuleRes.getString(R.string.copy_copy)
            }
        }
        selectAllButton?.get()?.text = if (blocks.all { it.selected }) {
            ModuleRes.getString(R.string.copy_deselect)
        } else {
            ModuleRes.getString(R.string.copy_select_all)
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

    // ── Block canvas view ──────────────────────────────────────────────────────

    private class TextBlocksView(
        context: Context,
        private val blocks: List<SelectableBlock>,
        private val density: Float,
        private val accent: Int,
        private val onEmptyTap: () -> Unit,
        private val onSelectionChanged: () -> Unit
    ) : View(context) {

        private val cornerRadius = 6f * density
        private val tapPadding   = (10 * density).toInt()
        private val tapSlopSq    = 24 * density * 24 * density

        private val scrimPaint = Paint().apply {
            color = "#40000000".toColorInt()
            style = Paint.Style.FILL
        }
        private val normalFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#18FFFFFF".toColorInt()
            style = Paint.Style.FILL
        }
        private val normalStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#44FFFFFF".toColorInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        private val selFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(accent, 90)
            style = Paint.Style.FILL
        }
        private val selStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(accent, 220)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }

        private var rippleBlock: SelectableBlock? = null
        private var rippleAlpha = 0f

        private var downX = 0f
        private var downY = 0f
        private var downBlock: SelectableBlock? = null
        private val tmpRect = RectF()
        private val screenPos = IntArray(2)

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            getLocationOnScreen(screenPos)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
            for (block in blocks) {
                tmpRect.set(
                    block.bounds.left.toFloat()  - screenPos[0],
                    block.bounds.top.toFloat()   - screenPos[1],
                    block.bounds.right.toFloat() - screenPos[0],
                    block.bounds.bottom.toFloat()- screenPos[1]
                )
                if (block.selected) {
                    canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, selFill)
                    canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, selStroke)
                } else {
                    canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, normalFill)
                    canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, normalStroke)
                    // Ripple flash on tap
                    if (block === rippleBlock && rippleAlpha > 0f) {
                        val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = ColorUtils.setAlphaComponent(Color.WHITE, (rippleAlpha * 64).toInt())
                            style = Paint.Style.FILL
                        }
                        canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, ripplePaint)
                    }
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
                        when {
                            upBlock != null && upBlock === downBlock -> {
                                upBlock.selected = !upBlock.selected
                                playRipple(upBlock)
                                onSelectionChanged()
                            }
                            upBlock == null && downBlock == null -> onEmptyTap()
                        }
                    }
                    downBlock = null
                }
            }
            return true
        }

        private fun playRipple(block: SelectableBlock) {
            rippleBlock = block
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 280
                addUpdateListener {
                    rippleAlpha = it.animatedValue as Float
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        rippleBlock = null
                        invalidate()
                    }
                })
                start()
            }
            invalidate()
        }

        private fun findBlockAt(x: Float, y: Float): SelectableBlock? {
            val ix = x.toInt()
            val iy = y.toInt()
            var best: SelectableBlock? = null
            var bestArea = Int.MAX_VALUE
            for (block in blocks) {
                val b = block.bounds
                if (ix in (b.left - tapPadding)..(b.right + tapPadding) &&
                    iy in (b.top - tapPadding)..(b.bottom + tapPadding)) {
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

    // ── Interpolator ──────────────────────────────────────────────────────────

    private class OvershootInterpolatorCompat(private val tension: Float) :
        android.view.animation.Interpolator {
        override fun getInterpolation(t: Float): Float {
            // Classic overshoot: f(t) = (tension+1)*t^3 - tension*t^2
            val s = tension
            val t1 = t - 1f
            return t1 * t1 * ((s + 1f) * t1 + s) + 1f
        }
    }
}
