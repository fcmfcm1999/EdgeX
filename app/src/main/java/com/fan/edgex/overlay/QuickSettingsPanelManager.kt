package com.fan.edgex.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.text.TextUtils
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.ThemeColorResolver
import com.fan.edgex.hook.ModuleRes
import kotlin.math.min
import kotlin.math.roundToInt

object QuickSettingsPanelManager {
    private var activeWindow: QuickSettingsPanelWindow? = null

    fun show(
        context: Context,
        resolveConfig: (String) -> String,
        zone: String?,
        touchX: Float,
        touchY: Float,
    ) {
        if (activeWindow?.isShowing() == true) return
        val window = QuickSettingsPanelWindow(context, resolveConfig, zone, touchX, touchY) {
            activeWindow = null
        }
        activeWindow = window
        window.show()
    }

    fun dismiss() {
        activeWindow?.forceDismiss()
        activeWindow = null
    }
}

private class QuickSettingsPanelWindow(
    private val context: Context,
    private val resolveConfig: (String) -> String,
    private val zone: String?,
    private val touchX: Float,
    private val touchY: Float,
    private val onDismiss: () -> Unit,
) {
    private data class TileBinding(val background: View, val icon: ImageView)

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val controller = QuickSettingsController(context)
    private val iconResolver = SystemQuickSettingsIconResolver(context)
    private val dp = context.resources.displayMetrics.density
    private val accent = ThemeColorResolver.resolveThemeColor(resolveConfig)
    private val onAccent = if (luminance(accent) > 0.58) Color.rgb(25, 25, 25) else Color.WHITE
    private val hapticsEnabled = resolveConfig(AppConfig.HAPTIC_FEEDBACK) == "true"
    private val tileBindings = mutableMapOf<QuickSettingsIcon, TileBinding>()
    private var brightnessSlider: VerticalLevelSlider? = null
    private var volumeSlider: VerticalLevelSlider? = null
    private var mediaArtworkView: ImageView? = null
    private var mediaTitleView: TextView? = null
    private var mediaPreviousButton: ImageView? = null
    private var mediaPlayPauseButton: ImageView? = null
    private var mediaNextButton: ImageView? = null
    private var rootView: FrameLayout? = null
    private var panelView: View? = null
    private var anchor: QuickSettingsAnchor? = null

    fun isShowing(): Boolean = rootView != null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (rootView != null) return
        val metrics = windowManager.currentWindowMetrics
        anchor = QuickSettingsAnchor.resolve(
            zone = zone,
            touchX = touchX,
            touchY = touchY,
            screenWidth = metrics.bounds.width().toFloat(),
            screenHeight = metrics.bounds.height().toFloat(),
        )

        val root = object : FrameLayout(context) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (ev.action == MotionEvent.ACTION_DOWN && isOutsidePanel(ev.rawX, ev.rawY)) {
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
        rootView = root

        val panel = buildPanel()
        panelView = panel
        root.addView(panel, panelLayoutParams(metrics))

        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            dimAmount = QUICK_SETTINGS_DIM
            blurBehindRadius = (28 * dp).toInt()
        }

        try {
            windowManager.addView(root, params)
            controller.start(::renderState)
            animateIn(panel)
        } catch (t: Throwable) {
            rootView = null
            panelView = null
            controller.stop()
            de.robv.android.xposed.XposedBridge.log("EdgeX: QuickSettingsPanel addView failed: ${t.message}")
        }
    }

    fun forceDismiss() {
        val root = rootView ?: return
        controller.stop()
        try {
            windowManager.removeView(root)
        } catch (_: Throwable) {
        } finally {
            rootView = null
            panelView = null
            tileBindings.clear()
            brightnessSlider = null
            volumeSlider = null
            mediaArtworkView = null
            mediaTitleView = null
            mediaPreviousButton = null
            mediaPlayPauseButton = null
            mediaNextButton = null
            onDismiss()
        }
    }

    private fun buildPanel(): View {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(12), px(12), px(14))
            background = roundedBackground(PANEL_COLOR, 26f, Color.argb(45, 255, 255, 255), 1f)
            elevation = px(18).toFloat()
        }

        panel.addView(View(context).apply {
            background = roundedBackground(Color.argb(120, 220, 222, 228), 3f)
        }, LinearLayout.LayoutParams(px(34), px(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = px(14)
        })

        panel.addView(buildMainControls(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            px(164),
        ))
        panel.addView(buildMediaRow(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            px(92),
        ).apply { topMargin = px(12) })
        panel.addView(buildUtilityRow(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            px(72),
        ).apply { topMargin = px(12) })
        return panel
    }

    private fun buildMainControls(): View {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(buildConnectivityGroup(), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        brightnessSlider = createSlider(
            QuickSettingsIcon.BRIGHTNESS,
            R.drawable.ic_brightness_up,
            ModuleRes.getString(R.string.quick_settings_brightness),
            controller::setBrightness,
        ).also { slider ->
            row.addView(slider, LinearLayout.LayoutParams(px(64), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                leftMargin = px(6)
            })
        }
        volumeSlider = createSlider(
            QuickSettingsIcon.VOLUME,
            R.drawable.ic_volume_up,
            ModuleRes.getString(R.string.quick_settings_volume),
            controller::setVolume,
        ).also { slider ->
            row.addView(slider, LinearLayout.LayoutParams(px(64), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                leftMargin = px(6)
            })
        }
        return row
    }

    private fun buildConnectivityGroup(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(6), px(6), px(6), px(6))
            background = roundedBackground(MODULE_COLOR, 22f)
            addView(buildTileRow(
                Triple(QuickSettingsIcon.WIFI, R.drawable.ic_wifi, controller::toggleWifi),
                Triple(QuickSettingsIcon.BLUETOOTH, R.drawable.ic_bluetooth, controller::toggleBluetooth),
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buildTileRow(
                Triple(QuickSettingsIcon.AIRPLANE, R.drawable.ic_airplane_mode, controller::toggleAirplaneMode),
                Triple(QuickSettingsIcon.HOTSPOT, R.drawable.ic_wifi, controller::toggleHotspot),
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = px(4)
            })
        }
    }

    private fun buildTileRow(
        first: Triple<QuickSettingsIcon, Int, () -> Unit>,
        second: Triple<QuickSettingsIcon, Int, () -> Unit>,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(createTile(first.first, first.second, contentDescription(first.first), first.third),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(createTile(second.first, second.second, contentDescription(second.first), second.third),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = px(4) })
    }

    private fun createTile(
        icon: QuickSettingsIcon,
        fallbackRes: Int,
        description: String,
        action: () -> Unit,
    ): View {
        val image = ImageView(context).apply {
            setImageDrawable(iconResolver.load(icon, fallbackRes))
            setColorFilter(Color.WHITE)
            contentDescription = description
            isClickable = true
            isFocusable = true
            setPadding(px(13), px(13), px(13), px(13))
            setOnClickListener {
                emitHaptic()
                action()
            }
        }
        val container = FrameLayout(context).apply {
            foregroundGravity = Gravity.CENTER
            addView(image, FrameLayout.LayoutParams(px(52), px(52), Gravity.CENTER))
        }
        tileBindings[icon] = TileBinding(image, image)
        updateTile(tileBindings.getValue(icon), false)
        return container
    }

    private fun buildMediaRow(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(10), px(8), px(10), px(8))
            background = roundedBackground(MODULE_COLOR, 20f)

            addView(createMediaArtwork(), LinearLayout.LayoutParams(px(56), px(56)).apply {
                rightMargin = px(8)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                mediaTitleView = TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.MARQUEE
                    marqueeRepeatLimit = -1
                    isSelected = true
                }.also { addView(it, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    px(24),
                )) }

                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    mediaPreviousButton = createMediaButton(R.drawable.ic_music_previous,
                        ModuleRes.getString(R.string.quick_settings_previous), controller::mediaPrevious)
                        .also { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)) }
                    mediaPlayPauseButton = createMediaButton(R.drawable.ic_music_play,
                        ModuleRes.getString(R.string.quick_settings_play_pause), controller::mediaPlayPause).also {
                        addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                    }
                    mediaNextButton = createMediaButton(R.drawable.ic_music_next,
                        ModuleRes.getString(R.string.quick_settings_next), controller::mediaNext)
                        .also { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)) }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun createMediaArtwork(): View = FrameLayout(context).apply {
        background = roundedBackground(withAlpha(accent, 220), 12f)
        clipToOutline = true
        mediaArtworkView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(ModuleRes.getDrawable(R.drawable.ic_music)?.mutate())
            setColorFilter(onAccent)
            setPadding(px(12), px(12), px(12), px(12))
        }.also {
            addView(it, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
    }

    private fun createMediaButton(
        iconRes: Int,
        description: String,
        action: () -> Unit,
    ): ImageView = ImageView(context).apply {
        setImageDrawable(ModuleRes.getDrawable(iconRes)?.mutate())
        setColorFilter(Color.WHITE)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = description
        isClickable = true
        isFocusable = true
        setPadding(px(13), px(13), px(13), px(13))
        setOnClickListener {
            emitHaptic()
            action()
        }
    }

    private fun buildUtilityRow(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(px(8), px(6), px(8), px(6))
            background = roundedBackground(MODULE_COLOR, 20f)
        }
        listOf(
            UtilitySpec(QuickSettingsIcon.DO_NOT_DISTURB, R.drawable.ic_notifications, controller::toggleDoNotDisturb),
            UtilitySpec(QuickSettingsIcon.FLASHLIGHT, R.drawable.ic_flashlight, controller::toggleFlashlight),
            UtilitySpec(QuickSettingsIcon.ROTATION_LOCK, R.drawable.ic_screen_rotation, controller::toggleRotationLock),
            UtilitySpec(QuickSettingsIcon.CAMERA, R.drawable.ic_camera) {
                animateOut()
                controller.launchCamera()
            },
        ).forEach { spec ->
            row.addView(createUtilityButton(spec), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        return row
    }

    private data class UtilitySpec(
        val icon: QuickSettingsIcon,
        val fallbackRes: Int,
        val action: () -> Unit,
    )

    private fun createUtilityButton(spec: UtilitySpec): View {
        val image = ImageView(context).apply {
            setImageDrawable(iconResolver.load(spec.icon, spec.fallbackRes))
            setColorFilter(Color.WHITE)
            contentDescription = contentDescription(spec.icon)
            isClickable = true
            isFocusable = true
            setPadding(px(13), px(13), px(13), px(13))
            setOnClickListener {
                emitHaptic()
                spec.action()
            }
        }
        val container = FrameLayout(context).apply {
            addView(image, FrameLayout.LayoutParams(px(52), px(52), Gravity.CENTER))
        }
        tileBindings[spec.icon] = TileBinding(image, image)
        updateTile(tileBindings.getValue(spec.icon), false)
        return container
    }

    private fun createSlider(
        icon: QuickSettingsIcon,
        fallbackRes: Int,
        description: String,
        onLevelChanged: (Float) -> Unit,
    ): VerticalLevelSlider = VerticalLevelSlider(
        context = context,
        accent = accent,
        onAccent = onAccent,
        icon = iconResolver.load(icon, fallbackRes),
        hapticsEnabled = hapticsEnabled,
        onLevelChanged = onLevelChanged,
    ).apply { contentDescription = description }

    private fun emitHaptic() {
        if (!hapticsEnabled) return
        runCatching {
            val effect = when (resolveConfig(AppConfig.HAPTIC_FEEDBACK_TYPE)) {
                AppConfig.HAPTIC_FEEDBACK_TYPE_TICK -> VibrationEffect.EFFECT_TICK
                AppConfig.HAPTIC_FEEDBACK_TYPE_HEAVY_CLICK -> VibrationEffect.EFFECT_HEAVY_CLICK
                AppConfig.HAPTIC_FEEDBACK_TYPE_DOUBLE_CLICK -> VibrationEffect.EFFECT_DOUBLE_CLICK
                else -> VibrationEffect.EFFECT_CLICK
            }
            context.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createPredefined(effect))
        }
    }

    private fun renderState(state: QuickSettingsState) {
        updateTile(QuickSettingsIcon.WIFI, state.wifi)
        updateTile(QuickSettingsIcon.BLUETOOTH, state.bluetooth)
        updateTile(QuickSettingsIcon.AIRPLANE, state.airplane)
        updateTile(QuickSettingsIcon.HOTSPOT, state.hotspot)
        updateTile(QuickSettingsIcon.DO_NOT_DISTURB, state.doNotDisturb)
        updateTile(QuickSettingsIcon.FLASHLIGHT, state.flashlight)
        updateTile(QuickSettingsIcon.ROTATION_LOCK, state.rotationLocked)
        updateTile(QuickSettingsIcon.CAMERA, false)
        brightnessSlider?.setLevelFromSystem(state.brightness)
        volumeSlider?.setLevelFromSystem(state.volume)
        renderMediaState(state)
    }

    private fun renderMediaState(state: QuickSettingsState) {
        mediaArtworkView?.apply {
            alpha = if (state.mediaAvailable) 1f else 0.52f
            if (state.mediaArtwork != null) {
                setPadding(0, 0, 0, 0)
                clearColorFilter()
                setImageBitmap(state.mediaArtwork)
            } else {
                setPadding(px(12), px(12), px(12), px(12))
                setColorFilter(onAccent)
                setImageDrawable(ModuleRes.getDrawable(R.drawable.ic_music)?.mutate())
            }
        }
        mediaPlayPauseButton?.setImageDrawable(ModuleRes.getDrawable(
            if (state.mediaPlaying) R.drawable.ic_music_pause else R.drawable.ic_music_play,
        )?.mutate())
        mediaTitleView?.apply {
            setTextColor(if (state.mediaAvailable) Color.WHITE else Color.argb(145, 255, 255, 255))
            text = when {
                !state.mediaAvailable -> ModuleRes.getString(R.string.quick_settings_no_media)
                !state.mediaTitle.isNullOrBlank() -> state.mediaTitle
                else -> ModuleRes.getString(R.string.quick_settings_media_unknown)
            }
            isSelected = state.mediaAvailable && !state.mediaTitle.isNullOrBlank()
        }
        listOf(mediaPreviousButton, mediaPlayPauseButton, mediaNextButton).forEach { button ->
            button?.apply {
                isEnabled = state.mediaAvailable
                alpha = if (state.mediaAvailable) 1f else 0.35f
            }
        }
    }

    private fun updateTile(icon: QuickSettingsIcon, active: Boolean) {
        tileBindings[icon]?.let { updateTile(it, active) }
    }

    private fun updateTile(binding: TileBinding, active: Boolean) {
        binding.background.background = roundedBackground(
            if (active) accent else Color.argb(210, 68, 72, 83),
            28f,
        )
        binding.icon.setColorFilter(if (active) onAccent else Color.WHITE)
        binding.background.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
    }

    private fun panelLayoutParams(metrics: android.view.WindowMetrics): FrameLayout.LayoutParams {
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )
        val availableWidth = metrics.bounds.width() - insets.left - insets.right
        val maxPanelWidth = (availableWidth - px(24)).coerceAtLeast(px(260))
        val panelWidth = min(px(320), maxPanelWidth)
        val resolved = anchor ?: QuickSettingsAnchor.resolve(null, 0f, 0f, 1f, 1f)
        val horizontalGravity = when (resolved.horizontal) {
            QuickSettingsHorizontalAnchor.START -> Gravity.START
            QuickSettingsHorizontalAnchor.CENTER -> Gravity.CENTER_HORIZONTAL
            QuickSettingsHorizontalAnchor.END -> Gravity.END
        }
        val verticalGravity = when (resolved.vertical) {
            QuickSettingsVerticalAnchor.TOP -> Gravity.TOP
            QuickSettingsVerticalAnchor.CENTER -> Gravity.CENTER_VERTICAL
            QuickSettingsVerticalAnchor.BOTTOM -> Gravity.BOTTOM
        }
        return FrameLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT, horizontalGravity or verticalGravity).apply {
            leftMargin = insets.left + px(8)
            rightMargin = insets.right + px(8)
            topMargin = insets.top + px(8)
            bottomMargin = insets.bottom + px(8)
        }
    }

    private fun animateIn(panel: View) {
        val resolved = anchor ?: return
        panel.alpha = 0f
        panel.scaleX = 0.96f
        panel.scaleY = 0.96f
        panel.translationX = when (resolved.horizontal) {
            QuickSettingsHorizontalAnchor.START -> -px(24).toFloat()
            QuickSettingsHorizontalAnchor.CENTER -> 0f
            QuickSettingsHorizontalAnchor.END -> px(24).toFloat()
        }
        panel.translationY = when (resolved.vertical) {
            QuickSettingsVerticalAnchor.TOP -> -px(24).toFloat()
            QuickSettingsVerticalAnchor.CENTER -> 0f
            QuickSettingsVerticalAnchor.BOTTOM -> px(24).toFloat()
        }
        panel.animate()
            .alpha(1f)
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .setDuration(190)
            .start()
    }

    private fun animateOut() {
        val panel = panelView ?: return forceDismiss()
        val resolved = anchor ?: return forceDismiss()
        val endX = when (resolved.horizontal) {
            QuickSettingsHorizontalAnchor.START -> -px(18).toFloat()
            QuickSettingsHorizontalAnchor.CENTER -> 0f
            QuickSettingsHorizontalAnchor.END -> px(18).toFloat()
        }
        val endY = when (resolved.vertical) {
            QuickSettingsVerticalAnchor.TOP -> -px(18).toFloat()
            QuickSettingsVerticalAnchor.CENTER -> 0f
            QuickSettingsVerticalAnchor.BOTTOM -> px(18).toFloat()
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 130
            addUpdateListener {
                val fraction = it.animatedFraction
                panel.alpha = 1f - fraction
                panel.translationX = endX * fraction
                panel.translationY = endY * fraction
            }
            doOnEnd(::forceDismiss)
            start()
        }
    }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
        })
    }

    private fun isOutsidePanel(x: Float, y: Float): Boolean {
        val panel = panelView ?: return false
        val location = IntArray(2)
        panel.getLocationOnScreen(location)
        return x < location[0] || x > location[0] + panel.width ||
            y < location[1] || y > location[1] + panel.height
    }

    private fun contentDescription(icon: QuickSettingsIcon): String = when (icon) {
        QuickSettingsIcon.WIFI -> ModuleRes.getString(R.string.quick_settings_wifi)
        QuickSettingsIcon.BLUETOOTH -> ModuleRes.getString(R.string.quick_settings_bluetooth)
        QuickSettingsIcon.AIRPLANE -> ModuleRes.getString(R.string.quick_settings_airplane)
        QuickSettingsIcon.HOTSPOT -> ModuleRes.getString(R.string.quick_settings_hotspot)
        QuickSettingsIcon.DO_NOT_DISTURB -> ModuleRes.getString(R.string.quick_settings_do_not_disturb)
        QuickSettingsIcon.FLASHLIGHT -> ModuleRes.getString(R.string.quick_settings_flashlight)
        QuickSettingsIcon.ROTATION_LOCK -> ModuleRes.getString(R.string.quick_settings_rotation_lock)
        QuickSettingsIcon.CAMERA -> ModuleRes.getString(R.string.quick_settings_camera)
        else -> ""
    }

    private fun roundedBackground(
        color: Int,
        radiusDp: Float,
        strokeColor: Int = Color.TRANSPARENT,
        strokeDp: Float = 0f,
    ): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * dp
        if (strokeDp > 0f) setStroke((strokeDp * dp).toInt().coerceAtLeast(1), strokeColor)
    }

    private fun px(value: Int): Int = (value * dp + 0.5f).toInt()

    private companion object {
        const val QUICK_SETTINGS_DIM = 0.28f
        val PANEL_COLOR = Color.argb(226, 43, 49, 62)
        val MODULE_COLOR = Color.argb(210, 13, 18, 28)

        fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )

        fun luminance(color: Int): Double {
            fun channel(value: Int): Double {
                val normalized = value / 255.0
                return if (normalized <= 0.03928) normalized / 12.92 else Math.pow((normalized + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(Color.red(color)) +
                0.7152 * channel(Color.green(color)) +
                0.0722 * channel(Color.blue(color))
        }
    }
}

private class VerticalLevelSlider(
    context: Context,
    private val accent: Int,
    private val onAccent: Int,
    private val icon: Drawable?,
    private val hapticsEnabled: Boolean,
    private val onLevelChanged: (Float) -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 8, 12, 20) }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val rect = RectF()
    private var level = 0.5f
    private var tracking = false
    private var lastHapticStep = -1

    init {
        isClickable = true
        isFocusable = true
        background = GradientDrawable().apply {
            setColor(Color.argb(205, 13, 18, 28))
            cornerRadius = 24f * density
        }
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    fun setLevelFromSystem(value: Float) {
        if (tracking) return
        level = value.coerceIn(0f, 1f)
        lastHapticStep = (level * HAPTIC_STEPS).roundToInt()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val right = (width - paddingRight).toFloat()
        val bottom = (height - paddingBottom).toFloat()
        val radius = (right - left) / 2f
        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, radius, radius, trackPaint)

        val fillHeight = (bottom - top) * level
        if (fillHeight > 0f) {
            rect.set(left, bottom - fillHeight, right, bottom)
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
        }

        icon?.let { drawable ->
            drawable.setTint(onAccent)
            val size = min(dp(25), (right - left).toInt())
            val iconLeft = ((left + right - size) / 2f).toInt()
            val iconTop = (bottom - dp(15) - size).toInt()
            drawable.bounds = android.graphics.Rect(iconLeft, iconTop, iconLeft + size, iconTop + size)
            drawable.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                tracking = true
                updateFromTouch(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateFromTouch(event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    updateFromTouch(event.y)
                    performClick()
                }
                tracking = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateFromTouch(y: Float) {
        val usable = (height - paddingTop - paddingBottom).coerceAtLeast(1)
        level = (1f - (y - paddingTop) / usable).coerceIn(0f, 1f)
        val hapticStep = (level * HAPTIC_STEPS).roundToInt()
        if (hapticStep != lastHapticStep) {
            lastHapticStep = hapticStep
            if (hapticsEnabled) {
                performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
            }
        }
        invalidate()
        onLevelChanged(level)
    }

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    private companion object {
        const val HAPTIC_STEPS = 20
    }
}
