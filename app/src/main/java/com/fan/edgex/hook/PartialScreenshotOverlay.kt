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
import androidx.core.graphics.toColorInt
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.HookConfigSnapshot
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*

internal object PartialScreenshotOverlay {

    private const val TAG = "EdgeX:PartialSS"
    private enum class PanelPos { BOTTOM, TOP }

    private var overlayRef: WeakReference<View>? = null
    private var wmRef: WeakReference<WindowManager>? = null
    private val handler = Handler(Looper.getMainLooper())

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

    // ---- Overlay ----

    private fun buildRoot(context: Context, bitmap: Bitmap, wm: WindowManager): FrameLayout {
        val dp  = context.resources.displayMetrics.density
        val mp  = ViewGroup.LayoutParams.MATCH_PARENT
        val wc  = ViewGroup.LayoutParams.WRAP_CONTENT
        val accentColor = readAccentColor(context)
        val combinedView = CombinedView(context, bitmap)

        // ── Shared state ──────────────────────────────────────────────────
        val brushColors = listOf(
            Color.BLACK, Color.RED, Color.YELLOW, Color.GREEN,
            Color.BLUE, Color.parseColor("#E040FB"), Color.WHITE
        )
        var currentMode = CombinedView.Mode.SELECT
        var hasSelection = false
        combinedView.setBrushColor(brushColors[1])
        combinedView.setMode(CombinedView.Mode.SELECT)

        val circleSize = (52 * dp).toInt()

        // ── View factories (views can't be shared between two parents) ────
        fun makeCancel() = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.argb(220, 40, 40, 46))
            }
            addView(TextView(context).apply {
                text = "✕"; textSize = 19f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(circleSize, circleSize)
            })
            setOnClickListener { combinedView.release(); dismiss() }
        }

        fun makeSave() = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(accentColor)
            }
            addView(object : View(context) {
                private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE; style = Paint.Style.STROKE
                    strokeWidth = 2.5f * dp; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                }
                override fun onDraw(canvas: Canvas) {
                    val w = width.toFloat(); val h = height.toFloat()
                    val path = Path()
                    path.moveTo(w * 0.28f, h * 0.52f)
                    path.lineTo(w * 0.44f, h * 0.67f)
                    path.lineTo(w * 0.72f, h * 0.36f)
                    canvas.drawPath(path, p)
                }
            }.apply { layoutParams = FrameLayout.LayoutParams(circleSize, circleSize) })
            setOnClickListener {
                val finalBitmap = combinedView.getFinalBitmap()
                combinedView.release(); dismiss()
                Thread {
                    try { saveToGallery(context, finalBitmap); finalBitmap.recycle() }
                    catch (t: Throwable) {
                        XposedBridge.log("$TAG save failed: ${t.message}")
                        showToast(context, ModuleRes.getString(R.string.partial_screenshot_failed))
                    }
                }.start()
            }
        }

        // ── Color circles (h-panel only) ──────────────────────────────────
        val colorCircles = brushColors.mapIndexed { i, color ->
            View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(color)
                    val sc = if (color == Color.WHITE || color == Color.BLACK)
                        Color.argb(100, 200, 200, 200) else Color.argb(40, 255, 255, 255)
                    setStroke((2 * dp).toInt(), if (i == 1) Color.WHITE else sc)
                }
                layoutParams = LinearLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt()).apply {
                    setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                }
            }
        }

        // ── Color pill ────────────────────────────────────────────────────
        val colorCirclesLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            colorCircles.forEach { addView(it) }
        }
        val colorPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 28, 28, 34)); cornerRadius = 40 * dp
            }
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            addView(TextView(context).apply {
                text = "✏"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(wc, wc).apply { setMargins(0, 0, (8 * dp).toInt(), 0) }
            })
            addView(colorCirclesLayout)
        }
        val colorPillWrapper = FrameLayout(context).apply {
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            addView(colorPill, FrameLayout.LayoutParams(wc, wc).apply { gravity = Gravity.CENTER_HORIZONTAL })
            visibility = View.GONE
        }
        colorCircles.forEachIndexed { i, v ->
            v.setOnClickListener {
                if (!hasSelection) return@setOnClickListener
                combinedView.setBrushColor(brushColors[i])
                colorCircles.forEachIndexed { j, cv ->
                    val c = brushColors[j]
                    val sc = when {
                        j == i -> Color.WHITE
                        c == Color.WHITE || c == Color.BLACK -> Color.argb(100, 200, 200, 200)
                        else -> Color.argb(40, 255, 255, 255)
                    }
                    (cv.background as? GradientDrawable)?.setStroke((2 * dp).toInt(), sc)
                }
                if (currentMode != CombinedView.Mode.BRUSH) {
                    currentMode = CombinedView.Mode.BRUSH
                    combinedView.setMode(CombinedView.Mode.BRUSH)
                }
            }
        }

        // ── Tab factory ───────────────────────────────────────────────────
        fun makeTab(label: String, size: Float, padH: Int, padV: Int) = TextView(context).apply {
            text = label; textSize = size; gravity = Gravity.CENTER
            setTextColor(Color.argb(140, 255, 255, 255))
            setPadding(padH, padV, padH, padV)
        }

        // ── h-panel tabs ──────────────────────────────────────────────────
        val hSelTab = makeTab(ModuleRes.getString(R.string.partial_screenshot_tool_select), 15f, (20*dp).toInt(), (12*dp).toInt())
        val hBrushTab = makeTab(ModuleRes.getString(R.string.partial_screenshot_tool_brush), 15f, (20*dp).toInt(), (12*dp).toInt())
        val hMosaicTab = makeTab(ModuleRes.getString(R.string.partial_screenshot_tool_mosaic), 15f, (20*dp).toInt(), (12*dp).toInt())

        // ── Center-row var (assigned after panel construction) ───────────
        var hCenterRow: LinearLayout? = null

        // ── Shared style updater ──────────────────────────────────────────
        fun tabColor(isActive: Boolean, avail: Boolean) = when {
            isActive -> Color.WHITE
            avail    -> Color.argb(180, 255, 255, 255)
            else     -> Color.argb(70, 255, 255, 255)
        }
        fun updateStyles() {
            val isBrush  = currentMode == CombinedView.Mode.BRUSH
            val isMosaic = currentMode == CombinedView.Mode.MOSAIC
            val isSel    = currentMode == CombinedView.Mode.SELECT
            hSelTab.setTextColor(tabColor(isSel, true))
            hBrushTab.setTextColor(tabColor(isBrush, hasSelection))
            hMosaicTab.setTextColor(tabColor(isMosaic, hasSelection))
            colorPillWrapper.visibility =
                if (hasSelection && isBrush) View.VISIBLE else View.GONE
            hCenterRow?.visibility = if (isBrush || isMosaic) View.VISIBLE else View.INVISIBLE
        }
        updateStyles()

        // ── Mode switch ───────────────────────────────────────────────────
        fun switchMode(m: CombinedView.Mode) {
            if (m != CombinedView.Mode.SELECT && !hasSelection) return
            currentMode = m; combinedView.setMode(m); updateStyles()
        }
        hSelTab.setOnClickListener   { switchMode(CombinedView.Mode.SELECT) }
        hBrushTab.setOnClickListener { switchMode(CombinedView.Mode.BRUSH)  }
        hMosaicTab.setOnClickListener{ switchMode(CombinedView.Mode.MOSAIC) }

        combinedView.onSelectionChanged = { sel ->
            hasSelection = sel
            if (!sel && currentMode != CombinedView.Mode.SELECT) switchMode(CombinedView.Mode.SELECT)
            else updateStyles()
        }

        // ── h-panel center row ────────────────────────────────────────────
        val undoBtn = TextView(context).apply {
            text = "↩"; textSize = 33f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setPadding((12*dp).toInt(), (10*dp).toInt(), (12*dp).toInt(), (10*dp).toInt())
            setOnClickListener { combinedView.undo() }
        }
        val redoBtn = TextView(context).apply {
            text = "↪"; textSize = 33f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setPadding((12*dp).toInt(), (10*dp).toInt(), (12*dp).toInt(), (10*dp).toInt())
            setOnClickListener { combinedView.redo() }
        }

        fun updateUndoRedoState() {
            val canUndo = combinedView.canUndo()
            val canRedo = combinedView.canRedo()
            undoBtn.alpha = if (canUndo) 1f else 0.35f
            undoBtn.isClickable = canUndo
            redoBtn.alpha = if (canRedo) 1f else 0.35f
            redoBtn.isClickable = canRedo
        }
        updateUndoRedoState()
        combinedView.onStackChanged = { updateUndoRedoState() }

        hCenterRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            visibility = View.INVISIBLE
            addView(undoBtn)
            addView(TextView(context).apply {
                text = "Reset"; textSize = 17f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                setPadding((6*dp).toInt(), 0, (6*dp).toInt(), 0)
                setOnClickListener { combinedView.resetAnnotations() }
            })
            addView(redoBtn)
        }

        // ── Horizontal panel ──────────────────────────────────────────────
        val hPanelBg = GradientDrawable().apply {
            setColor(Color.argb(230, 18, 18, 20))
            cornerRadii = floatArrayOf(20*dp, 20*dp, 20*dp, 20*dp, 0f, 0f, 0f, 0f)
        }
        val hPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; background = hPanelBg
            addView(colorPillWrapper, LinearLayout.LayoutParams(mp, wc))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                setPadding(0, (10*dp).toInt(), 0, (2*dp).toInt())
                addView(hSelTab); addView(hBrushTab); addView(hMosaicTab)
            }, LinearLayout.LayoutParams(mp, wc))
            addView(View(context).apply { setBackgroundColor(Color.argb(35, 255, 255, 255)) },
                LinearLayout.LayoutParams(mp, 1))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding((24*dp).toInt(), (8*dp).toInt(), (24*dp).toInt(), (24*dp).toInt())
                addView(makeCancel().apply { layoutParams = LinearLayout.LayoutParams(circleSize, circleSize) })
                addView(hCenterRow!!, LinearLayout.LayoutParams(0, wc, 1f))
                addView(makeSave().apply { layoutParams = LinearLayout.LayoutParams(circleSize, circleSize) })
            }, LinearLayout.LayoutParams(mp, wc))
        }

        // ── Root ──────────────────────────────────────────────────────────
        val hPanelLp = FrameLayout.LayoutParams(mp, wc, Gravity.BOTTOM)
        val root = object : FrameLayout(context) {
            init {
                addView(combinedView, LayoutParams(mp, mp))
                addView(hPanel, hPanelLp)
            }
        }

        // ── Panel repositioning ───────────────────────────────────────────
        var currentPos = PanelPos.BOTTOM
        val topInset = wm.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.statusBars() or
                android.view.WindowInsets.Type.displayCutout()
            ).top

        fun applyPos(pos: PanelPos) {
            if (pos == currentPos) return
            currentPos = pos
            when (pos) {
                PanelPos.BOTTOM -> {
                    root.updateViewLayout(hPanel, hPanelLp.also { it.gravity = Gravity.BOTTOM })
                    hPanelBg.cornerRadii = floatArrayOf(20*dp, 20*dp, 20*dp, 20*dp, 0f, 0f, 0f, 0f)
                    hPanel.setPadding(0, 0, 0, 0)
                    // color pill above tool row
                    hPanel.removeView(colorPillWrapper)
                    hPanel.addView(colorPillWrapper, 0)
                }
                PanelPos.TOP -> {
                    root.updateViewLayout(hPanel, hPanelLp.also { it.gravity = Gravity.TOP })
                    hPanelBg.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, 20*dp, 20*dp, 20*dp, 20*dp)
                    hPanel.setPadding(0, topInset, 0, 0)
                    // color pill below tool row (expands downward, away from screen edge)
                    hPanel.removeView(colorPillWrapper)
                    hPanel.addView(colorPillWrapper, 1)
                }
            }
        }

        fun reposition(selRect: RectF?) {
            if (!hasSelection || selRect == null) { applyPos(PanelPos.BOTTOM); return }
            val screenH = root.height.toFloat().takeIf { it > 0 }
                ?: wm.currentWindowMetrics.bounds.height().toFloat()
            val panelH  = hPanel.height.toFloat().takeIf { it > 0 } ?: (130 * dp)
            val margin  = 32 * dp
            val pos = if (selRect.bottom < screenH - panelH - margin) PanelPos.BOTTOM else PanelPos.TOP
            applyPos(pos)
        }

        combinedView.onSelectionUpdated = { rect -> reposition(rect) }

        return root
    }

    // ---- Theme ----

    private fun readAccentColor(context: Context): Int {
        val snapshot = HookConfigSnapshot.readFromHookFile()
        val presetId = snapshot[AppConfig.THEME_PRESET] ?: ""
        return when (presetId) {
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

    // ---- Screen capture ----

    private fun captureDisplayBitmap(context: Context): Bitmap? {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bounds = wm.currentWindowMetrics.bounds
        val w = bounds.width(); val h = bounds.height()

        val scClass = runCatching { Class.forName("android.window.ScreenCapture") }.getOrNull() ?: return null

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
                XposedBridge.log("$TAG capture timed out"); return null
            }
            return captureResultObj?.let { hwBufToBitmap(it) }
        }.onFailure { XposedBridge.log("$TAG Android16 capture failed: ${it.message}") }

        val token = resolveDisplayToken(context) ?: return null
        runCatching {
            val bc = Class.forName("android.window.ScreenCapture\$DisplayCaptureArgs\$Builder")
            val builder = bc.getConstructor(android.os.IBinder::class.java).newInstance(token)
            XposedHelpers.callMethod(builder, "setSize", w, h)
            val args = XposedHelpers.callMethod(builder, "build")
            return hwBufToBitmap(XposedHelpers.callStaticMethod(scClass, "captureDisplay", args) ?: return null)
        }.onFailure { XposedBridge.log("$TAG captureDisplay(IBinder) failed: ${it.message}") }

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
        val hwBuf = XposedHelpers.callMethod(screenshotHwBuf, "getHardwareBuffer") as? HardwareBuffer ?: return null
        val colorSpace = runCatching {
            XposedHelpers.callMethod(screenshotHwBuf, "getColorSpace") as? ColorSpace
        }.getOrNull()
        val hw = Bitmap.wrapHardwareBuffer(hwBuf, colorSpace)
        hwBuf.close(); hw ?: return null
        val soft = hw.copy(Bitmap.Config.ARGB_8888, false)
        hw.recycle(); return soft
    }

    private fun resolveDisplayToken(context: Context): android.os.IBinder? {
        runCatching {
            val dmg = Class.forName("android.hardware.display.DisplayManagerGlobal")
            val instance = XposedHelpers.callStaticMethod(dmg, "getInstance")
            val info = XposedHelpers.callMethod(instance, "getDisplayInfo", android.view.Display.DEFAULT_DISPLAY)
            if (info != null)
                (XposedHelpers.getObjectField(info, "displayToken") as? android.os.IBinder)?.let { return it }
        }
        runCatching {
            val sc = Class.forName("android.view.SurfaceControl")
            val ids = XposedHelpers.callStaticMethod(sc, "getPhysicalDisplayIds") as? LongArray
            if (ids != null && ids.isNotEmpty())
                return XposedHelpers.callStaticMethod(sc, "getPhysicalDisplayToken", ids[0]) as? android.os.IBinder
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
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Screenshot_$timestamp.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        }
        try {
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
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

    // ---- Combined view ----

    private class CombinedView(context: Context, sourceBitmap: Bitmap) : View(context) {

        enum class Mode { SELECT, BRUSH, MOSAIC }

        private var mode = Mode.SELECT
        private var brushColor = Color.RED

        var onSelectionChanged: ((Boolean) -> Unit)? = null
        var onSelectionUpdated: ((RectF?) -> Unit)? = null
        var onStackChanged: (() -> Unit)? = null

        fun canUndo() = undoStack.isNotEmpty()
        fun canRedo() = redoStack.isNotEmpty()

        private val originalBitmap: Bitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
        private val editBitmap: Bitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        private val editCanvas = Canvas(editBitmap)

        private val displayMatrix = Matrix()
        private val inverseMatrix = Matrix()
        private var displayScale = 1f

        private val undoStack = ArrayDeque<Bitmap>()
        private val redoStack = ArrayDeque<Bitmap>()
        private val MAX_UNDO = 5

        private enum class TouchMode { NONE, DRAW, MOVE }
        private var touchMode = TouchMode.NONE
        private var startX = 0f; private var startY = 0f
        private var endX   = 0f; private var endY   = 0f
        private var hasSelection = false
            set(value) {
                if (field != value) { field = value; onSelectionChanged?.invoke(value) }
            }
        private var dragAnchorX = 0f;    private var dragAnchorY = 0f
        private var moveBaseStartX = 0f; private var moveBaseStartY = 0f
        private var moveBaseEndX   = 0f; private var moveBaseEndY   = 0f

        private var currentPath: Path? = null
        private var lastBitmapX = 0f; private var lastBitmapY = 0f

        private val brushStrokeWidthBitmap get() = (8f * resources.displayMetrics.density) / displayScale

        private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val darkPaint   = Paint().apply { color = Color.argb(155, 0, 0, 0); style = Paint.Style.FILL }
        private val borderPaint = Paint().apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true
        }
        private val handlePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
        private val brushPaint  = Paint().apply {
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

        private fun pushToUndo() {
            if (undoStack.size >= MAX_UNDO) undoStack.removeFirst().recycle()
            undoStack.addLast(editBitmap.copy(Bitmap.Config.ARGB_8888, false))
        }

        private fun clearRedo() {
            redoStack.forEach { if (!it.isRecycled) it.recycle() }
            redoStack.clear()
        }

        private fun pushUndo() { pushToUndo(); clearRedo(); onStackChanged?.invoke() }

        fun undo() {
            if (undoStack.isEmpty()) return
            if (redoStack.size >= MAX_UNDO) redoStack.removeFirst().recycle()
            redoStack.addLast(editBitmap.copy(Bitmap.Config.ARGB_8888, false))
            val prev = undoStack.removeLast()
            editCanvas.drawBitmap(prev, 0f, 0f,
                Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) })
            if (!prev.isRecycled) prev.recycle()
            invalidate()
            onStackChanged?.invoke()
        }

        fun redo() {
            if (redoStack.isEmpty()) return
            pushToUndo()
            val next = redoStack.removeLast()
            editCanvas.drawBitmap(next, 0f, 0f,
                Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) })
            if (!next.isRecycled) next.recycle()
            invalidate()
            onStackChanged?.invoke()
        }

        fun resetAnnotations() {
            clearRedo()
            undoStack.forEach { if (!it.isRecycled) it.recycle() }
            undoStack.clear()
            editCanvas.drawBitmap(originalBitmap, 0f, 0f,
                Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) })
            invalidate()
            onStackChanged?.invoke()
        }

        fun release() {
            if (!editBitmap.isRecycled) editBitmap.recycle()
            if (!originalBitmap.isRecycled) originalBitmap.recycle()
            undoStack.forEach { if (!it.isRecycled) it.recycle() }
            undoStack.clear(); clearRedo()
        }

        fun getFinalBitmap(): Bitmap {
            commitBrushPath()
            val sel = selectionBitmapRect() ?: return editBitmap.copy(Bitmap.Config.ARGB_8888, false)
            val w = sel.width().toInt().coerceAtLeast(1)
            val h = sel.height().toInt().coerceAtLeast(1)
            return Bitmap.createBitmap(editBitmap, sel.left.toInt(), sel.top.toInt(), w, h)
        }

        private fun normalizedRect() = RectF(
            minOf(startX, endX), minOf(startY, endY),
            maxOf(startX, endX), maxOf(startY, endY)
        )

        private fun selectionBitmapRect(): RectF? {
            if (!hasSelection) return null
            val vr = normalizedRect()
            val pts = floatArrayOf(vr.left, vr.top, vr.right, vr.bottom)
            inverseMatrix.mapPoints(pts)
            return RectF(
                pts[0].coerceIn(0f, editBitmap.width.toFloat()),
                pts[1].coerceIn(0f, editBitmap.height.toFloat()),
                pts[2].coerceIn(0f, editBitmap.width.toFloat()),
                pts[3].coerceIn(0f, editBitmap.height.toFloat())
            )
        }

        private fun viewToBitmap(vx: Float, vy: Float): FloatArray {
            val pts = floatArrayOf(vx, vy)
            inverseMatrix.mapPoints(pts)
            return pts
        }

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
                        dragAnchorX    = event.x; dragAnchorY    = event.y
                        moveBaseStartX = startX;  moveBaseStartY = startY
                        moveBaseEndX   = endX;    moveBaseEndY   = endY
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
                    onSelectionUpdated?.invoke(if (hasSelection) RectF(normalizedRect()) else null)
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
            val sel = selectionBitmapRect()
            brushPaint.color = brushColor
            brushPaint.strokeWidth = brushStrokeWidthBitmap
            editCanvas.save()
            if (sel != null) editCanvas.clipRect(sel)
            editCanvas.drawPath(path, brushPaint)
            editCanvas.restore()
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
            val sel = selectionBitmapRect() ?: return
            val pts = viewToBitmap(vx, vy)
            val bx = pts[0].toInt(); val by = pts[1].toInt()
            val blockSize = (20f / displayScale).toInt().coerceAtLeast(4)
            val halfBrush = (30f / displayScale).toInt().coerceAtLeast(4)
            val left   = (bx - halfBrush).toFloat().coerceAtLeast(sel.left).toInt().coerceIn(0, editBitmap.width)
            val top    = (by - halfBrush).toFloat().coerceAtLeast(sel.top).toInt().coerceIn(0, editBitmap.height)
            val right  = (bx + halfBrush).toFloat().coerceAtMost(sel.right).toInt().coerceIn(0, editBitmap.width)
            val bottom = (by + halfBrush).toFloat().coerceAtMost(sel.bottom).toInt().coerceIn(0, editBitmap.height)
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
            small.recycle(); return result
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawBitmap(editBitmap, displayMatrix, bitmapPaint)

            val path = currentPath
            if (path != null && mode == Mode.BRUSH) {
                val previewPath = Path(path).also { it.transform(displayMatrix) }
                canvas.save()
                if (hasSelection) canvas.clipRect(normalizedRect())
                canvas.drawPath(previewPath, Paint(brushPaint).apply {
                    color = brushColor; strokeWidth = brushStrokeWidthBitmap * displayScale
                })
                canvas.restore()
            }

            if (!hasSelection) {
                canvas.drawColor(Color.argb(155, 0, 0, 0))
            } else {
                selRect.set(normalizedRect())
                val vw = width.toFloat(); val vh = height.toFloat()
                val l = selRect.left; val t = selRect.top; val r = selRect.right; val b = selRect.bottom
                dimRect.set(0f, 0f, vw, t);  canvas.drawRect(dimRect, darkPaint)
                dimRect.set(0f, b, vw, vh);  canvas.drawRect(dimRect, darkPaint)
                dimRect.set(0f, t, l,  b);   canvas.drawRect(dimRect, darkPaint)
                dimRect.set(r, t, vw,  b);   canvas.drawRect(dimRect, darkPaint)
            }

            if (hasSelection) {
                selRect.set(normalizedRect())
                val l = selRect.left; val t = selRect.top; val r = selRect.right; val b = selRect.bottom
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
