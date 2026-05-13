package com.fan.edgex.hook

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.HardwareBuffer
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.*
import android.widget.*
import com.fan.edgex.R
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*

internal object PartialScreenshotOverlay {

    private const val TAG = "EdgeX:PartialSS"

    private var overlayRef: WeakReference<View>? = null
    private var wmRef: WeakReference<WindowManager>? = null
    private var annotOverlayRef: WeakReference<View>? = null
    private var annotWmRef: WeakReference<WindowManager>? = null
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
        try { wm.removeView(view) } catch (t: Throwable) {
            XposedBridge.log("$TAG dismiss failed: ${t.message}")
        }
        overlayRef = null
        wmRef = null
    }

    private fun dismissAnnotation() {
        val wm = annotWmRef?.get() ?: return
        val view = annotOverlayRef?.get() ?: return
        try { wm.removeView(view) } catch (t: Throwable) {
            XposedBridge.log("$TAG dismissAnnotation failed: ${t.message}")
        }
        annotOverlayRef = null
        annotWmRef = null
    }

    // ---- Selection stage ----

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
                if (rect != null) captureRegion(context, rect, wm)
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
                background = GradientDrawable().apply {
                    cornerRadius = 20 * dp
                    setColor(Color.argb(220, 33, 150, 243))
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, (8 * dp).toInt(), 0) }
        }
    }

    private fun captureRegion(context: Context, selectionRect: RectF, wm: WindowManager) {
        dismiss()
        handler.postDelayed({
            Thread {
                try {
                    val full = captureDisplayBitmap(context) ?: run {
                        XposedBridge.log("$TAG captureDisplayBitmap returned null")
                        showToast(context, ModuleRes.getString(R.string.partial_screenshot_failed))
                        return@Thread
                    }
                    val metrics = context.resources.displayMetrics
                    val scaleX = full.width.toFloat() / metrics.widthPixels
                    val scaleY = full.height.toFloat() / metrics.heightPixels
                    val left   = (selectionRect.left   * scaleX).toInt().coerceIn(0, full.width)
                    val top    = (selectionRect.top    * scaleY).toInt().coerceIn(0, full.height)
                    val right  = (selectionRect.right  * scaleX).toInt().coerceIn(0, full.width)
                    val bottom = (selectionRect.bottom * scaleY).toInt().coerceIn(0, full.height)
                    val w = (right - left).coerceAtLeast(1)
                    val h = (bottom - top).coerceAtLeast(1)
                    val cropped = Bitmap.createBitmap(full, left, top, w, h)
                    full.recycle()
                    handler.post { showAnnotationStage(context, cropped, wm) }
                } catch (t: Throwable) {
                    XposedBridge.log("$TAG capture failed: ${t.message}")
                    showToast(context, ModuleRes.getString(R.string.partial_screenshot_failed))
                }
            }.start()
        }, 250)
    }

    // ---- Annotation stage ----

    private fun showAnnotationStage(context: Context, bitmap: Bitmap, wm: WindowManager) {
        if (annotOverlayRef?.get() != null) return
        annotWmRef = WeakReference(wm)
        val root = buildAnnotationRoot(context, bitmap)
        annotOverlayRef = WeakReference(root)
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

    private fun buildAnnotationRoot(context: Context, bitmap: Bitmap): FrameLayout {
        val dp = context.resources.displayMetrics.density
        val annotationView = AnnotationView(context, bitmap)

        val brushColors = listOf(Color.RED, Color.parseColor("#FF9800"), Color.BLUE, Color.BLACK, Color.WHITE)
        var currentTool = AnnotationView.Tool.BRUSH

        annotationView.setTool(AnnotationView.Tool.BRUSH)
        annotationView.setBrushColor(brushColors[0])

        // Tool toggle buttons
        val brushBtn = TextView(context).apply {
            text = ModuleRes.getString(R.string.partial_screenshot_tool_brush)
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, (4 * dp).toInt(), 0) }
        }
        val mosaicBtn = TextView(context).apply {
            text = ModuleRes.getString(R.string.partial_screenshot_tool_mosaic)
            textSize = 13f
            setTextColor(Color.argb(150, 255, 255, 255))
            setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, (4 * dp).toInt(), 0) }
        }

        // Color circles (always visible)
        val colorViews = brushColors.mapIndexed { i, color ->
            View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke((2 * dp).toInt(), if (i == 0) Color.WHITE else Color.argb(80, 255, 255, 255))
                }
                layoutParams = LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt()).apply {
                    setMargins((5 * dp).toInt(), 0, (5 * dp).toInt(), 0)
                }
            }
        }

        fun updateToolHighlight() {
            val activeBg: () -> GradientDrawable = {
                GradientDrawable().apply { setColor(Color.argb(80, 255, 255, 255)); cornerRadius = 10 * dp }
            }
            brushBtn.background  = if (currentTool == AnnotationView.Tool.BRUSH)  activeBg() else null
            mosaicBtn.background = if (currentTool == AnnotationView.Tool.MOSAIC) activeBg() else null
            brushBtn.setTextColor(Color.WHITE)
            mosaicBtn.setTextColor(Color.WHITE)
            // Dim colors when mosaic is active so user knows they apply to brush
            val alpha = if (currentTool == AnnotationView.Tool.BRUSH) 1f else 0.4f
            colorViews.forEach { it.alpha = alpha }
        }
        updateToolHighlight()

        // Tapping a color always activates brush first
        colorViews.forEachIndexed { i, v ->
            v.setOnClickListener {
                annotationView.setBrushColor(brushColors[i])
                if (currentTool != AnnotationView.Tool.BRUSH) {
                    currentTool = AnnotationView.Tool.BRUSH
                    annotationView.setTool(AnnotationView.Tool.BRUSH)
                }
                colorViews.forEachIndexed { j, cv ->
                    (cv.background as? GradientDrawable)?.setStroke(
                        (2 * dp).toInt(),
                        if (j == i) Color.WHITE else Color.argb(80, 255, 255, 255)
                    )
                }
                updateToolHighlight()
            }
        }

        brushBtn.setOnClickListener {
            currentTool = AnnotationView.Tool.BRUSH
            annotationView.setTool(AnnotationView.Tool.BRUSH)
            updateToolHighlight()
        }
        mosaicBtn.setOnClickListener {
            currentTool = AnnotationView.Tool.MOSAIC
            annotationView.setTool(AnnotationView.Tool.MOSAIC)
            updateToolHighlight()
        }

        // Row 1: tool toggles + thin divider + color circles
        val toolRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
            addView(brushBtn)
            addView(mosaicBtn)
            // vertical rule
            addView(View(context).apply {
                setBackgroundColor(Color.argb(60, 255, 255, 255))
                layoutParams = LinearLayout.LayoutParams((1).toInt(), (24 * dp).toInt()).apply {
                    setMargins((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
                }
            })
            colorViews.forEach { addView(it) }
        }

        // Row 2: undo (left) + cancel + save (right)
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())

            val undoBtn   = buildTextButton(context, dp, ModuleRes.getString(R.string.partial_screenshot_undo), false)
            val cancelBtn = buildTextButton(context, dp, ModuleRes.getString(R.string.partial_screenshot_cancel), false)
            val saveBtn   = buildTextButton(context, dp, ModuleRes.getString(R.string.partial_screenshot_save), true)

            undoBtn.setOnClickListener { annotationView.undo() }
            cancelBtn.setOnClickListener { annotationView.release(); dismissAnnotation() }
            saveBtn.setOnClickListener {
                val finalBitmap = annotationView.getFinalBitmap()
                annotationView.release()
                dismissAnnotation()
                Thread {
                    try { saveToGallery(context, finalBitmap); finalBitmap.recycle() }
                    catch (t: Throwable) {
                        XposedBridge.log("$TAG save failed: ${t.message}")
                        showToast(context, ModuleRes.getString(R.string.partial_screenshot_failed))
                    }
                }.start()
            }

            addView(undoBtn)
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(cancelBtn)
            addView(saveBtn)
        }

        // Single card panel with rounded top corners
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(230, 20, 20, 20))
                cornerRadii = floatArrayOf(16 * dp, 16 * dp, 16 * dp, 16 * dp, 0f, 0f, 0f, 0f)
            }
            addView(toolRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(
                View(context).apply { setBackgroundColor(Color.argb(40, 255, 255, 255)) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            )
            addView(actionRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        return object : FrameLayout(context) {
            init {
                setBackgroundColor(Color.BLACK)
                addView(annotationView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM
                })
            }
        }
    }

    // ---- Screen capture ----

    // Android 16+: ScreenCapture.capture(ScreenCaptureParams(displayId), Executor, OutcomeReceiver)
    // Android 12–15: ScreenCapture.captureDisplay(DisplayCaptureArgs(IBinder token))
    private fun captureDisplayBitmap(context: Context): Bitmap? {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bounds = wm.currentWindowMetrics.bounds
        val w = bounds.width()
        val h = bounds.height()

        val scClass = runCatching { Class.forName("android.window.ScreenCapture") }.getOrNull()
            ?: return null

        // Android 16+: ScreenCaptureParams.Builder(int displayId)
        runCatching {
            val paramsClass = scClass.declaredClasses.firstOrNull { "ScreenCaptureParams" in it.simpleName }
                ?: Class.forName("android.window.ScreenCaptureParams")
            val builderClass = paramsClass.declaredClasses.firstOrNull { "Builder" in it.simpleName }!!
            val builder = builderClass.getConstructor(Int::class.javaPrimitiveType)
                .newInstance(android.view.Display.DEFAULT_DISPLAY)
            val params = XposedHelpers.callMethod(builder, "build")

            val latch = java.util.concurrent.CountDownLatch(1)
            var captureResultObj: Any? = null
            val receiver = java.lang.reflect.Proxy.newProxyInstance(
                scClass.classLoader, arrayOf(Class.forName("android.os.OutcomeReceiver"))
            ) { _, method, args ->
                when (method.name) {
                    "onResult" -> { captureResultObj = args?.get(0); latch.countDown() }
                    "onError"  -> { XposedBridge.log("$TAG onError: ${args?.get(0)}"); latch.countDown() }
                }
                null
            }
            XposedHelpers.callStaticMethod(scClass, "capture", params,
                java.util.concurrent.Executors.newSingleThreadExecutor(), receiver)
            if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                XposedBridge.log("$TAG capture timed out")
                return null
            }
            return captureResultObj?.let { hwBufToBitmap(it) }
        }.onFailure { XposedBridge.log("$TAG Android16 capture failed: ${it.message}") }

        // Android 12–15: DisplayCaptureArgs(IBinder token)
        val token = resolveDisplayToken(context) ?: return null
        runCatching {
            val bc = Class.forName("android.window.ScreenCapture\$DisplayCaptureArgs\$Builder")
            val builder = bc.getConstructor(android.os.IBinder::class.java).newInstance(token)
            XposedHelpers.callMethod(builder, "setSize", w, h)
            val args = XposedHelpers.callMethod(builder, "build")
            return hwBufToBitmap(XposedHelpers.callStaticMethod(scClass, "captureDisplay", args) ?: return null)
        }.onFailure { XposedBridge.log("$TAG captureDisplay(IBinder) failed: ${it.message}") }

        // android.view.SurfaceControl fallback (Android 12–13)
        runCatching {
            val sc2 = Class.forName("android.view.SurfaceControl")
            val bc = Class.forName("android.view.SurfaceControl\$DisplayCaptureArgs\$Builder")
            val builder = bc.getConstructor(android.os.IBinder::class.java).newInstance(token)
            XposedHelpers.callMethod(builder, "setSize", w, h)
            val args = XposedHelpers.callMethod(builder, "build")
            return hwBufToBitmap(XposedHelpers.callStaticMethod(sc2, "captureDisplay", args) ?: return null)
        }.onFailure { XposedBridge.log("$TAG SurfaceControl fallback failed: ${it.message}") }

        return null
    }

    private fun hwBufToBitmap(screenshotHwBuf: Any): Bitmap? {
        val hwBuf = XposedHelpers.callMethod(screenshotHwBuf, "getHardwareBuffer") as? HardwareBuffer
            ?: return null
        val colorSpace = runCatching {
            XposedHelpers.callMethod(screenshotHwBuf, "getColorSpace") as? ColorSpace
        }.getOrNull()
        val hw = Bitmap.wrapHardwareBuffer(hwBuf, colorSpace)
        hwBuf.close()
        hw ?: return null
        val soft = hw.copy(Bitmap.Config.ARGB_8888, false)
        hw.recycle()
        return soft
    }

    private fun resolveDisplayToken(context: Context): android.os.IBinder? {
        runCatching {
            val dmg = Class.forName("android.hardware.display.DisplayManagerGlobal")
            val instance = XposedHelpers.callStaticMethod(dmg, "getInstance")
            val info = XposedHelpers.callMethod(instance, "getDisplayInfo", android.view.Display.DEFAULT_DISPLAY)
            if (info != null) {
                (XposedHelpers.getObjectField(info, "displayToken") as? android.os.IBinder)?.let { return it }
            }
        }
        runCatching {
            val sc = Class.forName("android.view.SurfaceControl")
            val ids = XposedHelpers.callStaticMethod(sc, "getPhysicalDisplayIds") as? LongArray
            if (ids != null && ids.isNotEmpty()) {
                return XposedHelpers.callStaticMethod(sc, "getPhysicalDisplayToken", ids[0]) as? android.os.IBinder
            }
        }
        runCatching {
            return XposedHelpers.callStaticMethod(
                Class.forName("android.view.SurfaceControl"), "getInternalDisplayToken"
            ) as? android.os.IBinder
        }
        runCatching {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            return XposedHelpers.callMethod(
                dm.getDisplay(android.view.Display.DEFAULT_DISPLAY), "getDisplayToken"
            ) as? android.os.IBinder
        }
        return null
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
            try { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
        }
    }

    // ---- Selection view ----

    private class SelectionView(context: Context) : View(context) {

        private enum class TouchMode { NONE, DRAW, MOVE }
        private var touchMode = TouchMode.NONE

        private var startX = 0f
        private var startY = 0f
        private var endX = 0f
        private var endY = 0f
        private var hasSelection = false

        // Drag-move bookkeeping
        private var dragAnchorX = 0f
        private var dragAnchorY = 0f
        private var moveBaseStartX = 0f
        private var moveBaseStartY = 0f
        private var moveBaseEndX = 0f
        private var moveBaseEndY = 0f

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
                    if (hasSelection && normalizedRect().contains(event.x, event.y)) {
                        touchMode = TouchMode.MOVE
                        dragAnchorX = event.x
                        dragAnchorY = event.y
                        moveBaseStartX = startX
                        moveBaseStartY = startY
                        moveBaseEndX = endX
                        moveBaseEndY = endY
                    } else {
                        touchMode = TouchMode.DRAW
                        startX = event.x; startY = event.y
                        endX = event.x;   endY = event.y
                        hasSelection = false
                    }
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    when (touchMode) {
                        TouchMode.MOVE -> {
                            val dx = event.x - dragAnchorX
                            val dy = event.y - dragAnchorY
                            startX = moveBaseStartX + dx; startY = moveBaseStartY + dy
                            endX   = moveBaseEndX   + dx; endY   = moveBaseEndY   + dy
                        }
                        TouchMode.DRAW -> {
                            endX = event.x; endY = event.y
                            hasSelection = true
                        }
                        TouchMode.NONE -> {}
                    }
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    when (touchMode) {
                        TouchMode.MOVE -> {
                            val dx = event.x - dragAnchorX
                            val dy = event.y - dragAnchorY
                            startX = moveBaseStartX + dx; startY = moveBaseStartY + dy
                            endX   = moveBaseEndX   + dx; endY   = moveBaseEndY   + dy
                        }
                        TouchMode.DRAW -> {
                            endX = event.x; endY = event.y
                            hasSelection = normalizedRect().let { it.width() > 10f && it.height() > 10f }
                        }
                        TouchMode.NONE -> {}
                    }
                    touchMode = TouchMode.NONE
                    invalidate()
                }
            }
            return true
        }

        fun getSelectionRect(): RectF? {
            if (!hasSelection) return null
            return normalizedRect()
        }

        private fun normalizedRect() = RectF(
            minOf(startX, endX), minOf(startY, endY),
            maxOf(startX, endX), maxOf(startY, endY)
        )

        override fun onDraw(canvas: Canvas) {
            if (!hasSelection) {
                canvas.drawColor(Color.argb(155, 0, 0, 0))
                return
            }

            selRect.set(normalizedRect())
            val w = width.toFloat(); val h = height.toFloat()
            val l = selRect.left; val t = selRect.top
            val r = selRect.right; val b = selRect.bottom

            dimRect.set(0f, 0f, w, t);  canvas.drawRect(dimRect, darkPaint)
            dimRect.set(0f, b, w, h);   canvas.drawRect(dimRect, darkPaint)
            dimRect.set(0f, t, l, b);   canvas.drawRect(dimRect, darkPaint)
            dimRect.set(r, t, w, b);    canvas.drawRect(dimRect, darkPaint)

            canvas.drawRect(selRect, borderPaint)

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

    // ---- Annotation view ----

    private class AnnotationView(context: Context, sourceBitmap: Bitmap) : View(context) {

        enum class Tool { BRUSH, MOSAIC }

        private var currentTool = Tool.BRUSH
        private var brushColor = Color.RED

        private val editBitmap: Bitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        private val editCanvas = Canvas(editBitmap)

        private val displayMatrix = Matrix()
        private val inverseMatrix = Matrix()
        private var displayScale = 1f

        private val undoStack = ArrayDeque<Bitmap>()
        private val MAX_UNDO = 5

        private var currentPath: Path? = null
        private var lastBitmapX = 0f
        private var lastBitmapY = 0f

        private val brushPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

        // Stroke width in bitmap pixels that looks like 8dp on screen
        private val brushStrokeWidthBitmap get() = (8f * resources.displayMetrics.density) / displayScale

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w <= 0 || h <= 0) return
            val scaleX = w / editBitmap.width.toFloat()
            val scaleY = h / editBitmap.height.toFloat()
            displayScale = minOf(scaleX, scaleY).coerceAtLeast(0.001f)
            val dx = (w - editBitmap.width * displayScale) / 2f
            val dy = (h - editBitmap.height * displayScale) / 2f
            displayMatrix.reset()
            displayMatrix.postScale(displayScale, displayScale)
            displayMatrix.postTranslate(dx, dy)
            displayMatrix.invert(inverseMatrix)
        }

        fun setTool(tool: Tool) { currentTool = tool }
        fun setBrushColor(color: Int) { brushColor = color }

        fun getFinalBitmap(): Bitmap = editBitmap.copy(Bitmap.Config.ARGB_8888, false)

        fun release() {
            if (!editBitmap.isRecycled) editBitmap.recycle()
            undoStack.forEach { if (!it.isRecycled) it.recycle() }
            undoStack.clear()
        }

        fun undo() {
            if (undoStack.isEmpty()) return
            val prev = undoStack.removeLast()
            val srcPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
            editCanvas.drawBitmap(prev, 0f, 0f, srcPaint)
            if (!prev.isRecycled) prev.recycle()
            invalidate()
        }

        private fun pushUndo() {
            if (undoStack.size >= MAX_UNDO) undoStack.removeFirst().recycle()
            undoStack.addLast(editBitmap.copy(Bitmap.Config.ARGB_8888, false))
        }

        private fun viewToBitmap(vx: Float, vy: Float): FloatArray {
            val pts = floatArrayOf(vx, vy)
            inverseMatrix.mapPoints(pts)
            return pts
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (currentTool) {
                Tool.BRUSH  -> handleBrushTouch(event)
                Tool.MOSAIC -> handleMosaicTouch(event)
            }
            return true
        }

        private fun handleBrushTouch(event: MotionEvent) {
            val pts = viewToBitmap(event.x, event.y)
            val bx = pts[0].coerceIn(0f, editBitmap.width.toFloat())
            val by = pts[1].coerceIn(0f, editBitmap.height.toFloat())
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    pushUndo()
                    currentPath = Path().apply { moveTo(bx, by) }
                    lastBitmapX = bx; lastBitmapY = by
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    val midX = (bx + lastBitmapX) / 2f
                    val midY = (by + lastBitmapY) / 2f
                    currentPath?.quadTo(lastBitmapX, lastBitmapY, midX, midY)
                    lastBitmapX = bx; lastBitmapY = by
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    currentPath?.lineTo(bx, by)
                    commitBrushPath()
                    invalidate()
                }
            }
        }

        private fun commitBrushPath() {
            val path = currentPath ?: return
            brushPaint.color = brushColor
            brushPaint.strokeWidth = brushStrokeWidthBitmap
            editCanvas.drawPath(path, brushPaint)
            currentPath = null
        }

        private fun handleMosaicTouch(event: MotionEvent) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { pushUndo(); applyMosaic(event.x, event.y) }
                MotionEvent.ACTION_MOVE -> applyMosaic(event.x, event.y)
                MotionEvent.ACTION_UP   -> invalidate()
            }
        }

        private fun applyMosaic(vx: Float, vy: Float) {
            val pts = viewToBitmap(vx, vy)
            val bx = pts[0].toInt()
            val by = pts[1].toInt()

            val blockSize = (20f / displayScale).toInt().coerceAtLeast(4)
            val halfBrush = (30f / displayScale).toInt().coerceAtLeast(4)

            val left   = (bx - halfBrush).coerceIn(0, editBitmap.width)
            val top    = (by - halfBrush).coerceIn(0, editBitmap.height)
            val right  = (bx + halfBrush).coerceIn(0, editBitmap.width)
            val bottom = (by + halfBrush).coerceIn(0, editBitmap.height)
            val rw = right - left; val rh = bottom - top
            if (rw <= 0 || rh <= 0) return

            val region = Bitmap.createBitmap(editBitmap, left, top, rw, rh)
            val pixelated = pixelateBitmap(region, blockSize)
            region.recycle()
            editCanvas.drawBitmap(pixelated, left.toFloat(), top.toFloat(), null)
            pixelated.recycle()
            invalidate()
        }

        private fun pixelateBitmap(src: Bitmap, blockSize: Int): Bitmap {
            val smallW = (src.width / blockSize).coerceAtLeast(1)
            val smallH = (src.height / blockSize).coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(src, smallW, smallH, false)
            val result = Bitmap.createScaledBitmap(small, src.width, src.height, false)
            small.recycle()
            return result
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawBitmap(editBitmap, displayMatrix, bitmapPaint)
            val path = currentPath ?: return
            // Draw in-progress brush stroke (transform bitmap-space path to view space)
            val previewPath = Path(path).also { it.transform(displayMatrix) }
            val previewPaint = Paint(brushPaint).apply {
                color = brushColor
                strokeWidth = brushStrokeWidthBitmap * displayScale
            }
            canvas.drawPath(previewPath, previewPaint)
        }
    }
}
