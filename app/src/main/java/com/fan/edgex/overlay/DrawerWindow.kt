package com.fan.edgex.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.hook.ModuleRes

class DrawerWindow(
    private val context: Context,
    private val resolveConfig: (String) -> String,
    private val onDismiss: (() -> Unit)? = null,
) {

    private data class AppEntry(val resolveInfo: android.content.pm.ResolveInfo, val isFrozen: Boolean)

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: FrameLayout? = null
    private var drawerPanel: View? = null
    private var contentLeftBound = 0
    private var configReceiver: android.content.BroadcastReceiver? = null

    private val MOCK_MODE = false

    private val isDarkMode: Boolean
        get() = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    fun show() {
        if (rootView != null) return

        registerConfigReceiver()

        val useArcDrawer = resolveConfig(AppConfig.FREEZER_ARC_DRAWER).toBoolean()

        val displayMetrics = context.resources.displayMetrics

        rootView = object : FrameLayout(context) {
            override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
                if (ev.action == android.view.MotionEvent.ACTION_DOWN && ev.x < contentLeftBound) {
                    animateOut()
                    return true
                }
                return super.dispatchTouchEvent(ev)
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    animateOut()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val pm = context.packageManager
        val displayApps: List<AppEntry> = if (MOCK_MODE) {
            loadMockApps(pm)
        } else {
            loadConfiguredApps(pm)
        }

        if (useArcDrawer) {
            setupArcLayout(displayApps, pm, displayMetrics)
            contentLeftBound = displayMetrics.widthPixels - (200 * displayMetrics.density).toInt()
        } else {
            val isLandscape = context.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
            // Portrait needs a wider panel (70%) to comfortably fit 3 columns
            val panelFraction = if (isLandscape) 0.62f else 0.70f
            val panelWidth = (displayMetrics.widthPixels * panelFraction).toInt()
            contentLeftBound = displayMetrics.widthPixels - panelWidth
            setupModernLayout(displayApps, pm, panelWidth)
        }

        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            // FLAG_DIM_BEHIND (0x2) | FLAG_BLUR_BEHIND (0x4)
            flags = 0x00000002 or 0x00000004
            dimAmount = 0.25f
            blurBehindRadius = 36
        }

        try {
            windowManager.addView(rootView, params)
            if (!useArcDrawer) {
                drawerPanel?.let { panel ->
                    val panelWidth = (displayMetrics.widthPixels * 0.62f).toInt()
                    panel.translationX = panelWidth.toFloat()
                    ValueAnimator.ofFloat(panelWidth.toFloat(), 0f).apply {
                        duration = 340
                        interpolator = DecelerateInterpolator(2.2f)
                        addUpdateListener { panel.translationX = it.animatedValue as Float }
                        start()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupModernLayout(
        displayApps: List<AppEntry>,
        pm: android.content.pm.PackageManager,
        panelWidth: Int
    ) {
        val dp = context.resources.displayMetrics.density
        val dark = isDarkMode

        val surfaceBg     = if (dark) Color.argb(238, 20, 19, 30)    else Color.argb(238, 250, 248, 255)
        val textPrimary   = if (dark) Color.WHITE                     else Color.parseColor("#1C1B1F")
        val textMuted     = if (dark) Color.parseColor("#9A97AA")     else Color.parseColor("#6B6880")
        val cardBg        = if (dark) Color.argb(160, 42, 40, 58)    else Color.argb(170, 230, 226, 244)
        val dividerColor  = if (dark) Color.argb(35, 255, 255, 255)  else Color.argb(40, 0, 0, 0)
        val frozenBadgeBg = Color.argb(210, 12, 18, 52)
        val cornerRad     = 28f * dp

        val panel = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.END
            }
            background = GradientDrawable().apply {
                setColor(surfaceBg)
                // Only left corners rounded; right edge is off-screen
                cornerRadii = floatArrayOf(cornerRad, cornerRad, 0f, 0f, 0f, 0f, cornerRad, cornerRad)
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width + cornerRad.toInt(), view.height, cornerRad)
                }
            }
            clipToOutline = true
            elevation = 20f * dp
            isClickable = true
        }
        drawerPanel = panel

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Header
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (52 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            addView(TextView(context).apply {
                text = "Freezer"
                textSize = 22f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(textPrimary)
                letterSpacing = -0.02f
            })
            val frozenCount = displayApps.count { it.isFrozen }
            addView(TextView(context).apply {
                text = "${displayApps.size} apps · $frozenCount frozen"
                textSize = 12.5f
                setTextColor(textMuted)
                setPadding(0, (4 * dp).toInt(), 0, 0)
            })
        })

        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(dividerColor)
        })

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        if (displayApps.isEmpty()) {
            scrollView.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (240 * dp).toInt()
                )
                addView(TextView(context).apply {
                    text = ModuleRes.getString(R.string.label_empty_drawer)
                    textSize = 14f
                    setTextColor(textMuted)
                    gravity = Gravity.CENTER
                })
            })
        } else {
            val isLandscape = context.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val columns = if (isLandscape) 4 else 3
            val gap = (8 * dp).toInt()
            val hPad = (12 * dp).toInt()

            // Pre-compute card dimensions so content never overflows the card boundary
            val cardWidth = (panelWidth - 2 * hPad - columns * 2 * gap) / columns
            // Icon occupies at most 56% of card width, hard-capped at 52dp
            val iconSize = (cardWidth * 0.56f).toInt().coerceIn((32 * dp).toInt(), (52 * dp).toInt())
            // Equal horizontal padding so icon is centered with room to spare
            val innerPadH = ((cardWidth - iconSize) / 2).coerceAtLeast((4 * dp).toInt())
            // Vertical: fixed padding values, card height = icon + label row + padding
            val innerPadTop = (10 * dp).toInt()
            val innerPadBot = (8 * dp).toInt()
            val labelTopPad = (5 * dp).toInt()
            val labelHeightPx = (18 * dp).toInt()
            val cardHeight = iconSize + innerPadTop + labelTopPad + labelHeightPx + innerPadBot
            val cardCorner = (cardWidth * 0.22f).coerceAtMost(20f * dp)

            val gridContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(hPad, (8 * dp).toInt(), hPad, (28 * dp).toInt())
            }

            val grayscaleFilter = android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().apply { setSaturation(0f) }
            )
            val shownPackages = mutableSetOf<String>()
            var currentRow: LinearLayout? = null
            var col = 0

            for ((ri, frozen) in displayApps) {
                val pkg = ri.activityInfo.applicationInfo.packageName
                if (!shownPackages.add(pkg)) continue

                if (col % columns == 0) {
                    currentRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    gridContainer.addView(currentRow)
                }

                val card = FrameLayout(context).apply {
                    // Use explicit cardHeight so card is always square-ish regardless of orientation
                    layoutParams = LinearLayout.LayoutParams(0, cardHeight, 1f).apply {
                        setMargins(gap, gap, gap, gap)
                    }
                    background = GradientDrawable().apply {
                        setColor(cardBg)
                        cornerRadius = cardCorner
                    }
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, cardCorner)
                        }
                    }
                    clipToOutline = true
                    elevation = 3f * dp
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (frozen) threadUnfreeze(pkg, ri.loadLabel(pm).toString(), pm)
                        else launchApp(context, pm, pkg)
                    }
                }

                // inner fills the card and centers content vertically
                val inner = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(innerPadH, innerPadTop, innerPadH, innerPadBot)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val iconFrame = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                    }
                }
                iconFrame.addView(ImageView(context).apply {
                    setImageDrawable(ri.loadIcon(pm))
                    layoutParams = FrameLayout.LayoutParams(iconSize, iconSize)
                    if (frozen) { colorFilter = grayscaleFilter; alpha = 0.55f }
                })
                if (frozen) {
                    val badgeSize = (iconSize * 0.38f).toInt()
                    iconFrame.addView(TextView(context).apply {
                        text = "❄"
                        textSize = (badgeSize / dp * 0.62f)
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#90CAF9"))
                        background = GradientDrawable().apply {
                            setColor(frozenBadgeBg)
                            cornerRadius = badgeSize * 0.38f
                        }
                        setPadding((2 * dp).toInt(), (1 * dp).toInt(), (2 * dp).toInt(), (1 * dp).toInt())
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { gravity = Gravity.BOTTOM or Gravity.END }
                    })
                }

                inner.addView(iconFrame)
                inner.addView(TextView(context).apply {
                    text = ri.loadLabel(pm)
                    textSize = (iconSize / dp * 0.22f).coerceIn(9f, 12f)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(if (frozen) textMuted else textPrimary)
                    setPadding(0, labelTopPad, 0, 0)
                })

                card.addView(inner)
                currentRow?.addView(card)
                col++
            }

            // Pad trailing cells so last row aligns left
            val trailing = columns - (col % columns)
            if (trailing < columns) {
                repeat(trailing) {
                    currentRow?.addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, cardHeight, 1f).apply {
                            setMargins(gap, gap, gap, gap)
                        }
                    })
                }
            }

            scrollView.addView(gridContainer)
        }

        root.addView(scrollView)
        panel.addView(root)
        rootView?.addView(panel)
    }

    private fun setupArcLayout(
        displayApps: List<AppEntry>,
        pm: android.content.pm.PackageManager,
        displayMetrics: android.util.DisplayMetrics
    ) {
        val drawerContent = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { gravity = Gravity.RIGHT }
            isClickable = false
        }

        val arcLayout = ArcLayoutView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            onEmptySpaceClick = { animateOut() }
        }

        if (displayApps.isEmpty()) {
            drawerContent.addView(TextView(context).apply {
                text = ModuleRes.getString(R.string.label_empty_drawer)
                textSize = 16f
                setPadding(40, 40, 40, 40)
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
            })
        } else {
            val grayscaleFilter = android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().apply { setSaturation(0f) }
            )
            val shownPackages = mutableSetOf<String>()

            for ((ri, frozen) in displayApps) {
                val pkg = ri.activityInfo.applicationInfo.packageName
                if (!shownPackages.add(pkg)) continue

                try {
                    arcLayout.addItem(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(12, 12, 12, 12)
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#F0F0F0"))
                            cornerRadius = 24f
                        }
                        addView(ImageView(context).apply {
                            setImageDrawable(ri.loadIcon(pm))
                            layoutParams = LinearLayout.LayoutParams(55, 55)
                            if (frozen) { colorFilter = grayscaleFilter; alpha = 0.5f }
                        })
                        addView(TextView(context).apply {
                            text = ri.loadLabel(pm)
                            textSize = 9f
                            gravity = Gravity.CENTER
                            maxLines = 1
                            setTextColor(if (frozen) Color.GRAY else Color.DKGRAY)
                        })
                        setOnClickListener {
                            if (frozen) threadUnfreeze(pkg, ri.loadLabel(pm).toString(), pm)
                            else launchApp(context, pm, pkg)
                        }
                    })
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        drawerContent.addView(arcLayout)
        rootView?.addView(drawerContent)
    }

    private fun threadUnfreeze(packageName: String, label: String, pm: android.content.pm.PackageManager) {
        de.robv.android.xposed.XposedBridge.log("EdgeX: DrawerWindow.threadUnfreeze - packageName: $packageName")
        Thread {
            var success = false
            try {
                pm.setApplicationEnabledSetting(
                    packageName,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    0
                )
                success = true
                de.robv.android.xposed.XposedBridge.log("EdgeX: PM API unfreeze SUCCESS for $packageName")
            } catch (e: Exception) {
                de.robv.android.xposed.XposedBridge.log("EdgeX: PM API unfreeze FAILED for $packageName: ${e.message}")
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (success) launchApp(context, pm, packageName)
                else android.widget.Toast.makeText(
                    context, ModuleRes.getString(R.string.toast_unfreeze_failed_drawer),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    private fun launchApp(context: Context, pm: android.content.pm.PackageManager, packageName: String) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var retries = 0
        var task: Runnable? = null
        task = Runnable {
            try {
                val intent = pm.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    context.startActivity(intent)
                    dismiss()
                } else if (++retries < 10) {
                    handler.postDelayed(task!!, 200)
                } else {
                    android.widget.Toast.makeText(
                        context, ModuleRes.getString(R.string.toast_launch_timeout),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    context, ModuleRes.getString(R.string.toast_launch_error, e.message),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        task.run()
    }

    fun isShowing() = rootView != null

    fun forceDismiss() = dismiss()

    private fun animateOut() {
        val panel = drawerPanel
        if (panel != null) {
            val panelWidth = panel.width.takeIf { it > 0 }
                ?: (context.resources.displayMetrics.widthPixels * 0.62f).toInt()
            ValueAnimator.ofFloat(0f, panelWidth.toFloat()).apply {
                duration = 260
                interpolator = DecelerateInterpolator(1.8f)
                addUpdateListener { panel.translationX = it.animatedValue as Float }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { dismiss() }
                })
                start()
            }
        } else {
            dismiss()
        }
    }

    private fun loadMockApps(pm: android.content.pm.PackageManager): List<AppEntry> {
        return try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            pm.queryIntentActivities(mainIntent, 0)
                .sortedWith { a, b ->
                    a.loadLabel(pm).toString().compareTo(b.loadLabel(pm).toString(), ignoreCase = true)
                }
                .distinctBy { it.activityInfo.applicationInfo.packageName }
                .take(20)
                .mapIndexed { index, ri -> AppEntry(ri, isFrozen = index % 4 == 3) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadConfiguredApps(pm: android.content.pm.PackageManager): List<AppEntry> {
        return try {
            val configuredPackages = linkedSetOf<String>()
            try {
                context.contentResolver.query(
                    ConfigProvider.uriForKey(AppConfig.FREEZER_APP_LIST),
                    null, null, null, null
                )?.use {
                    if (it.moveToFirst()) {
                        val listStr = it.getString(0)
                        if (!listStr.isNullOrEmpty()) {
                            configuredPackages.addAll(
                                listStr.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                de.robv.android.xposed.XposedBridge.log("EdgeX: Failed to read config: ${e.message}")
            }

            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            pm.queryIntentActivities(mainIntent, android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)
                .filter { configuredPackages.contains(it.activityInfo.applicationInfo.packageName) }
                .sortedWith { a, b ->
                    a.loadLabel(pm).toString().compareTo(b.loadLabel(pm).toString(), ignoreCase = true)
                }
                .distinctBy { it.activityInfo.applicationInfo.packageName }
                .map { ri -> AppEntry(ri, isFrozen = !ri.activityInfo.applicationInfo.enabled) }
        } catch (e: Exception) {
            de.robv.android.xposed.XposedBridge.log("EdgeX: Failed to load apps: ${e.message}")
            emptyList()
        }
    }

    private fun registerConfigReceiver() {
        if (configReceiver != null) return
        configReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                    recreateOnRotation()
                }
            }
        }
        context.registerReceiver(
            configReceiver,
            android.content.IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED)
        )
    }

    private fun unregisterConfigReceiver() {
        configReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
            configReceiver = null
        }
    }

    /** Tear down the current window and rebuild with the new orientation config. */
    private fun recreateOnRotation() {
        // Remove the window silently — do NOT invoke onDismiss so DrawerManager
        // keeps its activeDrawer reference pointing at this instance.
        unregisterConfigReceiver()
        try { windowManager.removeView(rootView) } catch (_: Exception) {}
        rootView = null
        drawerPanel = null
        // Wait one frame for the system to finish applying the new configuration
        // so displayMetrics reflects the rotated dimensions when show() runs.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ show() }, 80)
    }

    private fun dismiss() {
        unregisterConfigReceiver()
        if (rootView != null) {
            try { windowManager.removeView(rootView) } catch (_: Exception) {}
            rootView = null
            drawerPanel = null
            onDismiss?.invoke()
        }
    }
}
