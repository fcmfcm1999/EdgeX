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
    private val handler = Handler(Looper.getMainLooper())

    // Capture first, then show a single unified overlay.
    // No dismiss-before-capture needed because the overlay isn't shown yet.
    fun show(context: Context) {
        if (overlayRef?.get() != null) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wmRef = WeakReference(wm)
        Thread {
            val bitmap = captureDisplayBitmap(context)
            if (bitmap == null) {
                XposedBridge.log("$TAG captureDisplayBitmap returned null")
                showToast(context, ModuleRes.getString(R.string.partial_screenshot_failed))
                wmRef = null
                return@Thread
            }
            handler.post {
                val root = buildRoot(context, bitmap, wm)
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
        }.start()
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

    // ---- Single unified overlay (select + annotate in one place) ----

    private fun buildRoot(context: Context, bitmap: Bitmap, wm: WindowManager): FrameLayout {
        val dp = context.resources.displayMetrics.density
        val combinedView = CombinedView(context, bitmap)

        val brushColors = listOf(Color.RED, Color.parseColor("#FF9800"), Color.BLUE, Color.BLACK, Color.WHITE)
        var currentMode = CombinedView.Mode.SELECT
        combinedView.setBrushColor(brushColors[0])
        combinedView.setMode(CombinedView.Mode.SELECT)

        // Tool buttons
        fun makeToolBtn(label: String) = TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, (4 * dp).toInt(), 0) }
        }
        val selectBtn = makeToolBtn(ModuleRes.getString(R.string.partial_screenshot_tool_select))
        val brushBtn  = makeToolBtn(ModuleRes.getString(R.string.partial_screenshot_tool_brush))
        val mosaicBtn = makeToolBtn(ModuleRes.getString(R.string.partial_screenshot_tool_mosaic))

        // Color circles (always visible; dimmed when not in BRUSH mode)
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

        fun activeBg() = GradientDrawable().apply {
            setColor(Color.argb(80, 255, 255, 255)); cornerRadius = 10 * dp
        }

        fun updateHighlight() {
            selectBtn.background = if (currentMode == CombinedView.Mode.SELECT) activeBg() else null
            brushBtn.background  = if (currentMode == CombinedView.Mode.BRUSH)  activeBg() else null
            mosaicBtn.background = if (currentMode == CombinedView.Mode.MOSAIC) activeBg() else null
            colorViews.forEach { it.alpha = if (currentMode == CombinedView.Mode.BRUSH) 1f else 0.4f }
        }
        updateHighlight()

        colorViews.forEachIndexed { i, v ->
            v.setOnClickListener {
                combinedView.setBrushColor(brushColors[i])
                if (currentMode != CombinedView.Mode.BRUSH) {
                    currentMode = CombinedView.Mode.BRUSH
                    combinedView.setMode(CombinedView.Mode.BRUSH)
                }
                colorViews.forEachIndexed { j, cv ->
                    (cv.background as? GradientDrawable)?.setStroke(
                        (2 * dp).toInt(),
                        if (j == i) Color.WHITE else Color.argb(80, 255, 255, 255)
                    )
                }
                updateHighlight()
            }
        }

        selectBtn.setOnClickListener {
            currentMode = CombinedView.Mode.SELECT
            combinedView.setMode(CombinedView.Mode.SELECT)
            updateHighlight()
        }
        brushBtn.setOnClickListener {
            currentMode = CombinedView.Mode.BRUSH
            combinedView.setMode(CombinedView.Mode.BRUSH)
            updateHighlight()
        }
        mosaicBtn.setOnClickListener {
            currentMode = CombinedView.Mode.MOSAIC
            combinedView.setMode(CombinedView.Mode.MOSAIC)
            updateHighlight()
        }

        // Single bottom bar: [Select][Brush][Mosaic][|][colors][spacer][Undo][Cancel][Save]
        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(210, 20, 20, 20))
            setPadding(0, (12 * dp).toInt(), 0, (12 * dp).toInt())
            gravity = Gravity.CENTER_VERTICAL

            addView(selectBtn)
            addView(brushBtn)
            addView(mosaicBtn)
            addView(View(context).apply {
                setBackgroundColor(Color.argb(60, 255, 255, 255))
                layoutParams = LinearLayout.LayoutParams(1, (24 * dp).toInt()).apply {
                    setMargins((6 * dp).toInt(), 0, (6 * dp).toInt(), 0)
                }
            })
            colorViews.forEach { addView(it) }
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))

            val undoBtn   = buildTextButton(context, dp, ModuleRes.getString(R.string.partial_screenshot_undo), false)
            val cancelBtn = buildTextButton(context, dp, ModuleRes.getString(R.string.partial_screenshot_cancel), false)
            val saveBtn   = buildTextButton(context, dp, ModuleRes.getString(R.string.partial_screenshot_save), true)

            undoBtn.setOnClickListener { combinedView.undo() }
            cancelBtn.setOnClickListener { combinedView.release(); dismiss() }
            saveBtn.setOnClickListener {
                val finalBitmap = combinedView.getFinalBitmap()
                combinedView.release()
                dismiss()
                Thread {
                    try { saveToGallery(context, finalBitmap); finalBitmap.recycle() }
                    catch (t: Throwable) {
                        XposedBridge.log("$TAG save failed: ${t.message}")
                        showToast(context, ModuleRes.getString(R.string.partial_screenshot_failed))
                    }
                }.start()
            }

            addView(undoBtn)
            addView(cancelBtn)
            addView(saveBtn)
        }

        return object : FrameLayout(context) {
            init {
                addView(combinedView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                addView(bottomBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
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

    // ---- Combined view: SELECT / BRUSH / MOSAIC in one view ----

    private class CombinedView(context: Context, sourceBitmap: Bitmap) : View(context) {

        enum class Mode { SELECT, BRUSH, MOSAIC }

        private var mode = Mode.SELECT
        private var brushColor = Color.RED

        // Mutable bitmap for annotations
        private val editBitmap: Bitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        private val editCanvas = Canvas(editBitmap)

        // Fit-center display transform
        private val displayMatrix = Matrix()
        private val inverseMatrix = Matrix()
        private var displayScale = 1f

        // Undo (up to 5 snapshots)
        private val undoStack = ArrayDeque<Bitmap>()
        private val MAX_UNDO = 5

        // --- SELECT state ---
        private enum class TouchMode { NONE, DRAW, MOVE }
        private var touchMode = TouchMode.NONE
        private var startX = 0f; private var startY = 0f
        private var endX   = 0f; private var endY   = 0f
        private var hasSelection = false
        private var dragAnchorX = 0f;   private var dragAnchorY = 0f
        private var moveBaseStartX = 0f; private var moveBaseStartY = 0f
        private var moveBaseEndX   = 0f; private var moveBaseEndY   = 0f

        // --- BRUSH state ---
        private var currentPath: Path? = null
        private var lastBitmapX = 0f; private var lastBitmapY = 0f

        // Stroke width: 8dp apparent on screen, converted to bitmap pixels
        private val brushStrokeWidthBitmap get() = (8f * resources.displayMetrics.density) / displayScale

        // Paints
        private val bitmapPaint  = Paint(Paint.FILTER_BITMAP_FLAG)
        private val darkPaint    = Paint().apply { color = Color.argb(155, 0, 0, 0); style = Paint.Style.FILL }
        private val borderPaint  = Paint().apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true
        }
        private val handlePaint  = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
        private val brushPaint   = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val dimRect = RectF()
        private val selRect = RectF()

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w <= 0 || h <= 0) return
            displayScale = minOf(w / editBitmap.width.toFloat(), h / editBitmap.height.toFloat())
                .coerceAtLeast(0.001f)
            val dx = (w - editBitmap.width  * displayScale) / 2f
            val dy = (h - editBitmap.height * displayScale) / 2f
            displayMatrix.reset()
            displayMatrix.postScale(displayScale, displayScale)
            displayMatrix.postTranslate(dx, dy)
            displayMatrix.invert(inverseMatrix)
        }

        fun setMode(m: Mode) { mode = m; invalidate() }
        fun setBrushColor(color: Int) { brushColor = color }

        fun undo() {
            if (undoStack.isEmpty()) return
            val prev = undoStack.removeLast()
            editCanvas.drawBitmap(prev, 0f, 0f,
                Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) })
            if (!prev.isRecycled) prev.recycle()
            invalidate()
        }

        fun release() {
            if (!editBitmap.isRecycled) editBitmap.recycle()
            undoStack.forEach { if (!it.isRecycled) it.recycle() }
            undoStack.clear()
        }

        // Returns cropped bitmap if there's a selection, else full annotated bitmap.
        fun getFinalBitmap(): Bitmap {
            commitBrushPath()
            if (!hasSelection) return editBitmap.copy(Bitmap.Config.ARGB_8888, false)
            val sel = normalizedRect()
            val pts = floatArrayOf(sel.left, sel.top, sel.right, sel.bottom)
            inverseMatrix.mapPoints(pts)
            val left   = pts[0].toInt().coerceIn(0, editBitmap.width)
            val top    = pts[1].toInt().coerceIn(0, editBitmap.height)
            val right  = pts[2].toInt().coerceIn(0, editBitmap.width)
            val bottom = pts[3].toInt().coerceIn(0, editBitmap.height)
            val w = (right - left).coerceAtLeast(1)
            val h = (bottom - top).coerceAtLeast(1)
            return Bitmap.createBitmap(editBitmap, left, top, w, h)
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

        private fun normalizedRect() = RectF(
            minOf(startX, endX), minOf(startY, endY),
            maxOf(startX, endX), maxOf(startY, endY)
        )

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (mode) {
                Mode.SELECT -> handleSelectTouch(event)
                Mode.BRUSH  -> handleBrushTouch(event)
                Mode.MOSAIC -> handleMosaicTouch(event)
            }
            return true
        }

        private fun handleSelectTouch(event: MotionEvent) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (hasSelection && normalizedRect().contains(event.x, event.y)) {
                        touchMode = TouchMode.MOVE
                        dragAnchorX = event.x; dragAnchorY = event.y
                        moveBaseStartX = startX; moveBaseStartY = startY
                        moveBaseEndX   = endX;   moveBaseEndY   = endY
                    } else {
                        touchMode = TouchMode.DRAW
                        startX = event.x; startY = event.y
                        endX   = event.x; endY   = event.y
                        hasSelection = false
                    }
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    when (touchMode) {
                        TouchMode.MOVE -> {
                            val dx = event.x - dragAnchorX; val dy = event.y - dragAnchorY
                            startX = moveBaseStartX + dx; startY = moveBaseStartY + dy
                            endX   = moveBaseEndX   + dx; endY   = moveBaseEndY   + dy
                        }
                        TouchMode.DRAW -> { endX = event.x; endY = event.y; hasSelection = true }
                        TouchMode.NONE -> {}
                    }
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    when (touchMode) {
                        TouchMode.MOVE -> {
                            val dx = event.x - dragAnchorX; val dy = event.y - dragAnchorY
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
                    val midX = (bx + lastBitmapX) / 2f; val midY = (by + lastBitmapY) / 2f
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
            val bx = pts[0].toInt(); val by = pts[1].toInt()
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
            val smallW = (src.width  / blockSize).coerceAtLeast(1)
            val smallH = (src.height / blockSize).coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(src, smallW, smallH, false)
            val result = Bitmap.createScaledBitmap(small, src.width, src.height, false)
            small.recycle()
            return result
        }

        override fun onDraw(canvas: Canvas) {
            // 1. Annotated bitmap
            canvas.drawBitmap(editBitmap, displayMatrix, bitmapPaint)

            // 2. In-progress brush stroke preview
            val path = currentPath
            if (path != null && mode == Mode.BRUSH) {
                val previewPath = Path(path).also { it.transform(displayMatrix) }
                canvas.drawPath(previewPath, Paint(brushPaint).apply {
                    color = brushColor
                    strokeWidth = brushStrokeWidthBitmap * displayScale
                })
            }

            // 3. SELECT mode: dim overlay + selection rect/handles
            if (mode == Mode.SELECT) {
                if (!hasSelection) {
                    canvas.drawColor(Color.argb(155, 0, 0, 0))
                } else {
                    selRect.set(normalizedRect())
                    val vw = width.toFloat(); val vh = height.toFloat()
                    val l = selRect.left; val t = selRect.top
                    val r = selRect.right; val b = selRect.bottom
                    dimRect.set(0f, 0f, vw, t);  canvas.drawRect(dimRect, darkPaint)
                    dimRect.set(0f, b, vw, vh);  canvas.drawRect(dimRect, darkPaint)
                    dimRect.set(0f, t, l, b);    canvas.drawRect(dimRect, darkPaint)
                    dimRect.set(r, t, vw, b);    canvas.drawRect(dimRect, darkPaint)
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
        }
    }
}
