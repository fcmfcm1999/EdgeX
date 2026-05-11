package com.fan.edgex.hook

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.*
import android.widget.*
import com.fan.edgex.R
import de.robv.android.xposed.XposedBridge
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*

internal object PartialScreenshotOverlay {

    private const val TAG = "EdgeX:PartialSS"

    private var overlayRef: WeakReference<View>? = null
    private var wmRef: WeakReference<WindowManager>? = null
    private val handler = Handler(Looper.getMainLooper())

    fun show(context: Context) {
        if (overlayRef?.get() != null) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wmRef = WeakReference(wm)

        val root = buildRoot(context, wm)
        overlayRef = WeakReference(root)

        @Suppress("DEPRECATION")
        wm.addView(root, WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        })
    }

    private fun dismiss() {
        val wm = wmRef?.get() ?: return
        val view = overlayRef?.get() ?: return
        try {
            wm.removeView(view)
        } catch (t: Throwable) {
            XposedBridge.log("$TAG dismiss failed: ${t.message}")
        }
        overlayRef = null
        wmRef = null
    }

    private fun buildRoot(context: Context, wm: WindowManager): FrameLayout {
        val dp = context.resources.displayMetrics.density

        val selectionView = SelectionView(context)

        val btnBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(210, 20, 20, 20))
            setPadding(0, (12 * dp).toInt(), 0, (12 * dp).toInt())

            val hintView = TextView(context).apply {
                text = ModuleRes.getString(R.string.partial_screenshot_hint)
                textSize = 13f
                setTextColor(Color.argb(200, 255, 255, 255))
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * dp).toInt(), 0, 0, 0)
            }
            addView(hintView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val cancelBtn = buildTextButton(context, dp, ModuleRes.getString(R.string.partial_screenshot_cancel), false)
            val captureBtn = buildTextButton(context, dp, ModuleRes.getString(R.string.partial_screenshot_capture), true)

            cancelBtn.setOnClickListener { dismiss() }
            captureBtn.setOnClickListener {
                val rect = selectionView.getSelectionRect()
                if (rect != null) {
                    captureRegion(context, rect, wm)
                }
            }
            addView(cancelBtn)
            addView(captureBtn)
        }

        return object : FrameLayout(context) {
            init {
                addView(selectionView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                addView(btnBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM
                })
            }
        }
    }

    private fun buildTextButton(context: Context, dp: Float, label: String, primary: Boolean): TextView {
        return TextView(context).apply {
            text = label
            textSize = 14f
            setTextColor(if (primary) Color.WHITE else Color.argb(200, 255, 255, 255))
            setTypeface(null, if (primary) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())

            if (primary) {
                val bg = GradientDrawable().apply {
                    cornerRadius = 20 * dp
                    setColor(Color.argb(220, 33, 150, 243))
                }
                background = bg
            }

            val margin = (8 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, margin, 0) }
        }
    }

    private fun captureRegion(context: Context, selectionRect: RectF, wm: WindowManager) {
        dismiss()
        handler.postDelayed({
            Thread {
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("screencap", "-p"))
                    val bitmap = BitmapFactory.decodeStream(process.inputStream)
                    process.waitFor()

                    if (bitmap == null) {
                        showToast(context, ModuleRes.getString(R.string.partial_screenshot_failed))
                        return@Thread
                    }

                    val metrics = context.resources.displayMetrics
                    val scaleX = bitmap.width.toFloat() / metrics.widthPixels
                    val scaleY = bitmap.height.toFloat() / metrics.heightPixels

                    val left = (selectionRect.left * scaleX).toInt().coerceIn(0, bitmap.width)
                    val top = (selectionRect.top * scaleY).toInt().coerceIn(0, bitmap.height)
                    val right = (selectionRect.right * scaleX).toInt().coerceIn(0, bitmap.width)
                    val bottom = (selectionRect.bottom * scaleY).toInt().coerceIn(0, bitmap.height)
                    val w = (right - left).coerceAtLeast(1)
                    val h = (bottom - top).coerceAtLeast(1)

                    val cropped = Bitmap.createBitmap(bitmap, left, top, w, h)
                    bitmap.recycle()

                    saveToGallery(context, cropped)
                    cropped.recycle()
                } catch (t: Throwable) {
                    XposedBridge.log("$TAG capture failed: ${t.message}")
                    showToast(context, ModuleRes.getString(R.string.partial_screenshot_failed))
                }
            }.start()
        }, 250)
    }

    private fun saveToGallery(context: Context, bitmap: Bitmap) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "Screenshot_$timestamp.png"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        }

        try {
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                showToast(context, ModuleRes.getString(R.string.partial_screenshot_saved))
            } else {
                showToast(context, ModuleRes.getString(R.string.partial_screenshot_failed))
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG saveToGallery failed: ${t.message}")
            showToast(context, ModuleRes.getString(R.string.partial_screenshot_failed))
        }
    }

    private fun showToast(context: Context, text: String) {
        handler.post {
            try {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {
            }
        }
    }

    // ------- Selection view -------

    private class SelectionView(context: Context) : View(context) {

        private var startX = 0f
        private var startY = 0f
        private var endX = 0f
        private var endY = 0f
        private var hasSelection = false

        private val darkPaint = Paint().apply {
            color = Color.argb(155, 0, 0, 0)
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        private val handlePaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val dimRect = RectF()
        private val selRect = RectF()

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    endX = event.x
                    endY = event.y
                    hasSelection = false
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    endX = event.x
                    endY = event.y
                    hasSelection = true
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    endX = event.x
                    endY = event.y
                    hasSelection = normalizedRect().let { it.width() > 10f && it.height() > 10f }
                    invalidate()
                }
            }
            return true
        }

        fun getSelectionRect(): RectF? {
            if (!hasSelection) return null
            return normalizedRect()
        }

        private fun normalizedRect(): RectF {
            return RectF(
                minOf(startX, endX),
                minOf(startY, endY),
                maxOf(startX, endX),
                maxOf(startY, endY)
            )
        }

        override fun onDraw(canvas: Canvas) {
            if (!hasSelection) {
                canvas.drawColor(Color.argb(155, 0, 0, 0))
                return
            }

            selRect.set(normalizedRect())
            val w = width.toFloat()
            val h = height.toFloat()
            val l = selRect.left
            val t = selRect.top
            val r = selRect.right
            val b = selRect.bottom

            // Four dark rects surrounding the selection
            dimRect.set(0f, 0f, w, t);       canvas.drawRect(dimRect, darkPaint)
            dimRect.set(0f, b, w, h);        canvas.drawRect(dimRect, darkPaint)
            dimRect.set(0f, t, l, b);        canvas.drawRect(dimRect, darkPaint)
            dimRect.set(r, t, w, b);         canvas.drawRect(dimRect, darkPaint)

            // Selection border
            canvas.drawRect(selRect, borderPaint)

            // Corner handles
            val hs = 18f
            canvas.drawRect(l - 2f, t - 2f, l + hs, t + 2f, handlePaint)
            canvas.drawRect(l - 2f, t - 2f, l + 2f, t + hs, handlePaint)
            canvas.drawRect(r - hs, t - 2f, r + 2f, t + 2f, handlePaint)
            canvas.drawRect(r - 2f, t - 2f, r + 2f, t + hs, handlePaint)
            canvas.drawRect(l - 2f, b - 2f, l + hs, b + 2f, handlePaint)
            canvas.drawRect(l - 2f, b - hs, l + 2f, b + 2f, handlePaint)
            canvas.drawRect(r - hs, b - 2f, r + 2f, b + 2f, handlePaint)
            canvas.drawRect(r - 2f, b - hs, r + 2f, b + 2f, handlePaint)
        }
    }
}
