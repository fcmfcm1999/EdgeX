package com.fan.edgex.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.hook.ModuleRes

object PanelOverlayManager {
    private var activeWindow: PanelOverlayWindow? = null

    fun showCustomPanel(
        context: Context,
        resolveConfig: (String) -> String,
        dispatchAction: (String) -> Unit,
    ) {
        show(context, resolveConfig, dispatchAction, PanelMode.Custom)
    }

    fun showSideBar(
        context: Context,
        resolveConfig: (String) -> String,
        side: String,
        dispatchAction: (String) -> Unit,
    ) {
        show(context, resolveConfig, dispatchAction, PanelMode.SideBar(side))
    }

    fun dismiss() {
        activeWindow?.forceDismiss()
        activeWindow = null
    }

    private fun show(
        context: Context,
        resolveConfig: (String) -> String,
        dispatchAction: (String) -> Unit,
        mode: PanelMode,
    ) {
        if (activeWindow?.isShowing() == true) return
        val window = PanelOverlayWindow(context, resolveConfig, dispatchAction, mode) {
            activeWindow = null
        }
        activeWindow = window
        window.show()
    }
}

private sealed class PanelMode {
    object Custom : PanelMode()
    data class SideBar(val side: String) : PanelMode()
}

private class PanelOverlayWindow(
    private val context: Context,
    private val resolveConfig: (String) -> String,
    private val dispatchAction: (String) -> Unit,
    private val mode: PanelMode,
    private val onDismiss: () -> Unit,
) {
    private data class PanelItem(val action: String, val title: String)

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: FrameLayout? = null
    private var panelView: View? = null
    private val dp = context.resources.displayMetrics.density

    fun isShowing(): Boolean = rootView != null

    fun show() {
        if (rootView != null) return
        val items = loadItems()
        if (items.isEmpty()) return

        rootView = object : FrameLayout(context) {
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

        val panel = when (mode) {
            PanelMode.Custom -> buildCustomPanel(items)
            is PanelMode.SideBar -> buildSideBar(items, mode.side)
        }
        panelView = panel
        rootView?.addView(panel)

        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND
            dimAmount = if (mode is PanelMode.SideBar) OverlayTheme.DIM_SIDEBAR else OverlayTheme.DIM_PANEL
        }

        try {
            windowManager.addView(rootView, params)
            animateIn(panel)
        } catch (t: Throwable) {
            rootView = null
            de.robv.android.xposed.XposedBridge.log("EdgeX: PanelOverlay addView failed: ${t.message}")
        }
    }

    fun forceDismiss() {
        val root = rootView ?: return
        try {
            windowManager.removeView(root)
        } catch (_: Throwable) {
        } finally {
            rootView = null
            panelView = null
            onDismiss()
        }
    }

    private fun animateIn(panel: View) {
        when (val currentMode = mode) {
            PanelMode.Custom -> {
                panel.alpha = 0f
                panel.scaleX = 0.94f
                panel.scaleY = 0.94f
                panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            is PanelMode.SideBar -> {
                // measuredWidth is 0 before layout; use screen width to guarantee
                // the panel is off-screen on the very first draw frame.
                val screenWidth = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .currentWindowMetrics.bounds.width().toFloat()
                panel.translationX = if (currentMode.side == "left") -screenWidth else screenWidth
                panel.post {
                    val start = if (currentMode.side == "left") -panel.width.toFloat() else panel.width.toFloat()
                    ValueAnimator.ofFloat(start, 0f).apply {
                        duration = 180
                        interpolator = DecelerateInterpolator(1.8f)
                        addUpdateListener { panel.translationX = it.animatedValue as Float }
                        start()
                    }
                }
            }
        }
    }

    private fun animateOut() {
        val panel = panelView ?: return forceDismiss()
        when (val currentMode = mode) {
            PanelMode.Custom -> panel.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f)
                .setDuration(110).withEndAction { forceDismiss() }.start()
            is PanelMode.SideBar -> {
                val end = if (currentMode.side == "left") -panel.width.toFloat() else panel.width.toFloat()
                panel.animate().translationX(end).alpha(0f).setDuration(140)
                    .withEndAction { forceDismiss() }.start()
            }
        }
    }

    private fun isOutsidePanel(x: Float, y: Float): Boolean {
        val panel = panelView ?: return false
        val loc = IntArray(2)
        panel.getLocationOnScreen(loc)
        return x < loc[0] || x > loc[0] + panel.width || y < loc[1] || y > loc[1] + panel.height
    }

    private fun loadItems(): List<PanelItem> {
        return when (val currentMode = mode) {
            PanelMode.Custom -> {
                val items = mutableListOf<PanelItem>()
                repeat(AppConfig.CUSTOM_PANEL_ROWS) { row ->
                    repeat(AppConfig.CUSTOM_PANEL_COLUMNS) { column ->
                        AppConfig.customPanelSlot(row, column).toPanelItem()?.let(items::add)
                    }
                }
                items
            }
            is PanelMode.SideBar -> {
                (0 until AppConfig.SIDE_BAR_SLOTS).mapNotNull { index ->
                    AppConfig.sideBarSlot(currentMode.side, index).toPanelItem()
                }
            }
        }
    }

    private fun String.toPanelItem(): PanelItem? {
        val action = resolveConfig(this)
        if (action.isBlank() || action == "none") return null
        val title = displayTitleForAction(action, resolveConfig("${this}_title"))
        return PanelItem(action, title)
    }

    private fun buildCustomPanel(items: List<PanelItem>): View {
        val metrics = context.resources.displayMetrics
        val columns = 4
        val itemSize = (72 * dp).toInt()
        val gap = (10 * dp).toInt()
        val panelWidth = (columns * itemSize + (columns - 1) * gap + 28 * dp).toInt()
            .coerceAtMost((metrics.widthPixels * 0.92f).toInt())

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt())
            background = roundedBg(OverlayTheme.SURFACE_BG_DARK, OverlayTheme.CORNER_POPUP_DP)
            elevation = OverlayTheme.ELEVATION_DP * dp
        }
        var row: LinearLayout? = null
        items.forEachIndexed { index, item ->
            if (index % columns == 0) {
                row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                panel.addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (index > 0) topMargin = gap
                })
            }
            row?.addView(createActionButton(item, itemSize, showText = true), LinearLayout.LayoutParams(
                itemSize,
                itemSize,
            ).apply {
                if (index % columns != 0) leftMargin = gap
            })
        }

        return panel.apply {
            layoutParams = FrameLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
    }

    private fun buildSideBar(items: List<PanelItem>, side: String): View {
        val width = (76 * dp).toInt()
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt())
            background = roundedBg(OverlayTheme.SURFACE_BG_DARK, OverlayTheme.CORNER_BAR_DP)
            elevation = OverlayTheme.ELEVATION_DP * dp
        }
        items.forEachIndexed { index, item ->
            panel.addView(createActionButton(item, (56 * dp).toInt(), showText = false), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (72 * dp).toInt(),
            ).apply {
                if (index > 0) topMargin = (6 * dp).toInt()
            })
        }
        return panel.apply {
            layoutParams = FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = (if (side == "left") Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
                leftMargin = if (side == "left") (8 * dp).toInt() else 0
                rightMargin = if (side == "right") (8 * dp).toInt() else 0
            }
        }
    }

    private fun createActionButton(item: PanelItem, size: Int, showText: Boolean): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = roundedBg(Color.TRANSPARENT, 8f)
            setOnClickListener {
                animateOut()
                dispatchAction(item.action)
            }
        }
        val iconSize = when {
            item.action.usesAppIcon() && showText -> (42 * dp).toInt()
            item.action.usesAppIcon() -> (48 * dp).toInt()
            showText -> (30 * dp).toInt()
            else -> (34 * dp).toInt()
        }
        container.addView(ImageView(context).apply {
            val icon = drawableForAction(item.action)
            setImageDrawable(icon)
            if (!item.action.usesAppIcon()) {
                setColorFilter(Color.WHITE)
            }
        }, LinearLayout.LayoutParams(iconSize, iconSize))
        if (showText) {
            container.addView(TextView(context).apply {
                text = item.title
                textSize = 11f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(size, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (6 * dp).toInt()
            })
        }
        return container
    }

    private fun roundedBg(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * dp
        }

    private fun shortTitle(action: String): String = when {
        action == "back" -> "Back"
        action == "home" -> "Home"
        action == "recent" || action == "recents" -> "Recents"
        action == "expand_notifications" -> "Notify"
        action == "clear_background" -> "Clear"
        action == "freezer_drawer" -> "Freezer"
        action == "refreeze" -> "Refreeze"
        action == "screenshot" -> "Shot"
        action == "clipboard" -> "Clipboard"
        action == "universal_copy" -> "Copy"
        action == "lock_screen" -> "Lock"
        action == "kill_app" -> "Kill"
        action == "prev_app" -> "Prev App"
        action == "next_app" -> "Next App"
        action == "brightness_up" -> "Bright+"
        action == "brightness_down" -> "Bright-"
        action == "volume_up" -> "Vol+"
        action == "volume_down" -> "Vol-"
        action == "toggle_flashlight" -> "Torch"
        action == "game_mode" -> "Game"
        action == AppConfig.PARTIAL_SCREENSHOT_ACTION -> "Crop Shot"
        action == "pie" -> "Pie"
        action == "sub_gesture" -> "SubGesture"
        action == "condition" -> "Condition"
        action == "toggle_wifi" -> "Wi-Fi"
        action == "toggle_mobile_data" -> "Data"
        action.startsWith("launch_app:") -> "App"
        action.startsWith("app_shortcut:") -> "Shortcut"
        action.startsWith("shell:") -> "Shell"
        action.startsWith("music_control:") -> "Music"
        action.startsWith("fast_scroll:") -> when (action.removePrefix("fast_scroll:")) {
            "to_top" -> "Scroll Up"
            "to_bottom" -> "Scroll Down"
            else -> "Scroll"
        }
        action.startsWith("multi_action:") -> "Multi"
        else -> action
    }

    private fun drawableForAction(action: String): Drawable? {
        if (action.startsWith("launch_app:")) {
            val packageName = action.removePrefix("launch_app:")
            val appIcon = runCatching {
                context.packageManager.getApplicationIcon(packageName)
            }.getOrNull()
            if (appIcon != null) return appIcon.foregroundOrSelf()
        }
        if (action.startsWith("app_shortcut:")) {
            val packageName = action.removePrefix("app_shortcut:").substringBefore(":")
            val appIcon = runCatching {
                context.packageManager.getApplicationIcon(packageName)
            }.getOrNull()
            if (appIcon != null) return appIcon.foregroundOrSelf()
        }
        return ModuleRes.getDrawable(iconForAction(action))?.mutate()
    }

    private fun displayTitleForAction(action: String, savedTitle: String): String {
        val noneStr = ModuleRes.getString(R.string.action_none)
        val title = if (savedTitle.isBlank() || savedTitle == "None" || savedTitle == "无" || savedTitle == noneStr) "" else savedTitle
        return when {
            action.startsWith("launch_app:") -> appLabel(action.removePrefix("launch_app:"))
                ?: stripKnownPrefix(title, "App:", "App: ", "应用：", "应用:", "应用: ")
                ?: shortTitle(action)
            action.startsWith("app_shortcut:") -> stripKnownPrefix(
                title,
                "Shortcut:",
                "Shortcut: ",
                "快捷方式:",
                "快捷方式: ",
                "快捷方式：",
            ) ?: appLabel(action.removePrefix("app_shortcut:").substringBefore(":"))
                ?: shortTitle(action)
            action.startsWith("shell:") -> shellCommandTitle(action, title)
            else -> title.ifBlank { shortTitle(action) }
        }
    }

    private fun appLabel(packageName: String): String? = runCatching {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        appInfo.loadLabel(context.packageManager).toString()
    }.getOrNull()

    private fun shellCommandTitle(action: String, savedTitle: String): String {
        val saved = savedTitle.trim()
        if (saved.isNotBlank() && saved != "Shell" && saved != "Shell Command" && saved != "Shell 命令") {
            return saved
        }
        return action.removePrefix("shell:").split(":", limit = 2).getOrNull(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: shortTitle(action)
    }

    private fun stripKnownPrefix(value: String, vararg prefixes: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        val match = prefixes.firstOrNull { trimmed.startsWith(it) }
        return (match?.let { trimmed.removePrefix(it).trim() } ?: trimmed).takeIf { it.isNotBlank() }
    }

    private fun Drawable.foregroundOrSelf(): Drawable =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this is AdaptiveIconDrawable) {
            foreground?.mutate() ?: mutate()
        } else {
            mutate()
        }

    private fun String.usesAppIcon(): Boolean =
        startsWith("launch_app:") || startsWith("app_shortcut:")

    private fun iconForAction(action: String): Int = when {
        action == "back" -> R.drawable.ic_arrow_back
        action == "home" -> R.drawable.ic_home
        action == "recent" || action == "recents" -> R.drawable.ic_recents
        action == "expand_notifications" -> R.drawable.ic_notifications
        action.startsWith("shell:") -> R.drawable.ic_terminal
        action.startsWith("launch_app:") -> R.drawable.ic_launch_app
        action.startsWith("app_shortcut:") -> R.drawable.ic_app_shortcut
        action == "clear_background" -> R.drawable.ic_clear_recent
        action == "freezer_drawer" -> R.drawable.ic_freezer
        action == "refreeze" -> R.drawable.ic_refreeze
        action == "screenshot" -> R.drawable.ic_camera
        action == "clipboard" -> R.drawable.ic_paste
        action == "universal_copy" -> R.drawable.ic_content_copy
        action == "lock_screen" -> R.drawable.ic_power
        action == "kill_app" -> R.drawable.ic_kill_app
        action == "prev_app" -> R.drawable.ic_prev_app
        action == "next_app" -> R.drawable.ic_next_app
        action == "brightness_up" -> R.drawable.ic_brightness_up
        action == "brightness_down" -> R.drawable.ic_brightness_down
        action == "volume_up" -> R.drawable.ic_volume_up
        action == "volume_down" -> R.drawable.ic_volume_down
        action.startsWith("music_control:") -> R.drawable.ic_music
        action.startsWith("fast_scroll:") -> when (action.removePrefix("fast_scroll:")) {
            "to_top" -> R.drawable.ic_scroll_to_top
            "to_bottom" -> R.drawable.ic_scroll_to_bottom
            else -> R.drawable.ic_fast_scroll
        }
        action.startsWith("multi_action:") -> R.drawable.ic_multi_action
        action == "toggle_flashlight" -> R.drawable.ic_flashlight
        action == "toggle_wifi" -> R.drawable.ic_wifi
        action == "toggle_mobile_data" -> R.drawable.ic_mobile_data
        action == "game_mode" -> R.drawable.ic_game_mode
        action == AppConfig.PARTIAL_SCREENSHOT_ACTION -> R.drawable.ic_partial_screenshot
        action == "sub_gesture" -> R.drawable.ic_sub_gesture
        action == "pie" -> R.drawable.ic_pie_menu
        action == "condition" -> R.drawable.ic_condition
        action == AppConfig.CUSTOM_PANEL_ACTION -> R.drawable.ic_apps
        action == AppConfig.SIDE_BAR_LEFT_ACTION -> R.drawable.ic_side_bar_left
        action == AppConfig.SIDE_BAR_RIGHT_ACTION -> R.drawable.ic_side_bar_right
        else -> R.drawable.ic_action_dot
    }
}
