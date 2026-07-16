package com.fan.edgex.hook

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.TelephonyManager
import android.view.KeyEvent
import android.widget.Toast
import com.fan.edgex.BuildConfig
import com.fan.edgex.IShellCallback
import com.fan.edgex.IShellExecutor
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.ThemeColorResolver
import com.fan.edgex.config.HookConfigSnapshot
import com.fan.edgex.config.ConditionStore
import com.fan.edgex.config.ForegroundAppConditionConfig
import com.fan.edgex.overlay.DrawerManager
import com.fan.edgex.overlay.PanelOverlayManager
import com.fan.edgex.overlay.PieManager
import com.fan.edgex.overlay.PieView
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

internal class GestureActionDispatcher(
    private val resolveConfig: (String) -> String,
    private val handlerProvider: () -> Handler,
    private val log: (String) -> Unit,
) {
    @Volatile private var shellExecutor: IShellExecutor? = null
    @Volatile private var serviceBound = false
    private var serviceContext: Context? = null
    private val pendingCommands = ArrayDeque<Pair<String, Context>>()
    private val pendingUnlockActions = ArrayDeque<PendingUnlockAction>()
    private var idleUnbindRunnable: Runnable? = null
    private val SHELL_SERVICE_IDLE_TIMEOUT_MS = 5 * 60 * 1000L

    private data class PendingUnlockAction(
        val action: String,
        val context: Context,
        val touchX: Float,
        val touchY: Float,
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            shellExecutor = IShellExecutor.Stub.asInterface(binder)
            drainPendingCommands()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            shellExecutor = null
            serviceBound = false
            serviceContext?.let {
                handlerProvider().postDelayed({ bindShellService(it) }, 2000)
            }
        }
    }

    private fun drainPendingCommands() {
        while (pendingCommands.isNotEmpty()) {
            val (action, ctx) = pendingCommands.removeFirst()
            doExecuteShellCommand(action, ctx)
        }
    }

    private fun scheduleIdleUnbind() {
        val handler = handlerProvider()
        idleUnbindRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            idleUnbindRunnable = null
            unbindShellService()
        }
        idleUnbindRunnable = runnable
        handler.postDelayed(runnable, SHELL_SERVICE_IDLE_TIMEOUT_MS)
    }

    private fun unbindShellService() {
        val ctx = serviceContext ?: return
        if (!serviceBound) return
        try {
            ctx.unbindService(serviceConnection)
        } catch (e: Exception) {
            log("ShellExecutorService unbind failed: ${e.message}")
        }
        shellExecutor = null
        serviceBound = false
    }

    fun bindShellService(context: Context) {
        if (serviceBound) return
        serviceContext = context
        val intent = Intent().apply {
            component = ComponentName(
                BuildConfig.APPLICATION_ID,
                "${BuildConfig.APPLICATION_ID}.config.ShellExecutorService",
            )
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        if (bound) serviceBound = true
    }
    fun triggerGestureAction(
        zone: String,
        gestureType: String,
        context: Context,
        touchX: Float,
        touchY: Float,
    ) {
        val configKey = AppConfig.gestureAction(zone, gestureType)
        var action = resolveConfig(configKey)

        // If not found in the specific zone, check the fallback zone.
        // This mirrors the resolveAction() fallback in GestureManager so that a touch
        // landing in an enabled specific zone (e.g. "right_mid") can still execute an
        // action that was configured on the full-edge fallback zone (e.g. "right").
        if (action.isEmpty() || action == "none") {
            val fallbackZone = AppConfig.fallbackEdgeZone(zone)
            if (fallbackZone != null) {
                action = resolveConfig(AppConfig.gestureAction(fallbackZone, gestureType))
            }
        }

        log("[Gesture] triggerAction key=$configKey action='$action'")
        if (action.isNotEmpty() && action != "none") {
            handlerProvider().post {
                performAction(action, context, touchX, touchY)
            }
        }
    }

    fun executeKeyAction(action: String, context: Context) {
        handlerProvider().post {
            performAction(action, context, 0f, 0f)
        }
    }

    fun adjustBrightness(context: Context, up: Boolean) =
        com.fan.edgex.action.AppActionExecutor.adjustBrightness(context, up)

    fun adjustVolume(context: Context, up: Boolean) =
        com.fan.edgex.action.AppActionExecutor.adjustVolume(context, up)

    private fun vibrateActionFeedback(context: Context) {
        if (resolveConfig(AppConfig.HAPTIC_FEEDBACK) != "true") return
        try {
            val vibrator = context.getSystemService(Vibrator::class.java) ?: return
            val effect = when (resolveConfig(AppConfig.HAPTIC_FEEDBACK_TYPE)) {
                AppConfig.HAPTIC_FEEDBACK_TYPE_TICK -> VibrationEffect.EFFECT_TICK
                AppConfig.HAPTIC_FEEDBACK_TYPE_HEAVY_CLICK -> VibrationEffect.EFFECT_HEAVY_CLICK
                AppConfig.HAPTIC_FEEDBACK_TYPE_DOUBLE_CLICK -> VibrationEffect.EFFECT_DOUBLE_CLICK
                else -> VibrationEffect.EFFECT_CLICK
            }
            vibrator.vibrate(VibrationEffect.createPredefined(effect))
        } catch (_: Throwable) {
        }
    }

    private fun performAction(action: String, context: Context, touchX: Float, touchY: Float) {
        vibrateActionFeedback(context)
        dispatchAction(action, context, touchX, touchY)
    }

    private fun dispatchAction(action: String, context: Context, touchX: Float, touchY: Float) {
        if (LockscreenActionPolicy.requiresUnlock(action) && isKeyguardLocked(context)) {
            pendingUnlockActions.addLast(PendingUnlockAction(action, context, touchX, touchY))
            log("Action queued until unlock: '$action'")
            return
        }

        when {
            action == "back" -> {
                GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_BACK)
            }
            action == "home" -> {
                GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_HOME)
            }
            action == "assistant" -> {
                try {
                    val intent = Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    log("assistant failed: ${e.message}")
                }
            }
            action == "recent" || action == "recents" -> {
                GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_RECENTS)
            }
            action == "notifications" || action == "expand_notifications" -> {
                GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_NOTIFICATIONS)
            }
            action == "quick_settings" -> {
                GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_QUICK_SETTINGS)
            }
            action == "power_dialog" -> {
                GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_POWER_DIALOG)
            }
            action == "lock_screen" -> {
                GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_LOCK_SCREEN)
            }
            action == "kill_app" -> {
                killForegroundApp(context)
            }
            action == "prev_app" -> {
                switchApp(context, forward = false)
            }
            action == "next_app" -> {
                switchApp(context, forward = true)
            }
            action == "clear_background" -> {
                clearBackgroundApps(context)
            }
            action.startsWith("music_control:") -> {
                com.fan.edgex.action.AppActionExecutor.dispatchMusicControl(context, action)
            }
            action.startsWith("fast_scroll:") -> {
                injectScrollEvent(context, action == "fast_scroll:to_top", touchX, touchY)
            }
            action == "brightness_up" || action == "brightness_down" -> {
                adjustBrightness(context, action == "brightness_up")
            }
            action == "volume_up" || action == "volume_down" -> {
                adjustVolume(context, action == "volume_up")
            }
            action == "screenshot" -> {
                performScreenshot(context)
            }
            action == AppConfig.PARTIAL_SCREENSHOT_ACTION -> {
                PartialScreenshotOverlay.show(context)
            }
            action == "refreeze" -> {
                performRefreeze(context)
            }
            action == "universal_copy" -> {
                UniversalCopyManager.collectAllTexts(context) { result ->
                    when (result.status) {
                        UniversalCopyManager.CollectStatus.FOUND -> {
                            TextSelectionOverlay.show(context, result.blocks)
                        }
                        UniversalCopyManager.CollectStatus.NO_TEXT -> {
                            showToast(context, ModuleRes.getString(R.string.toast_no_text_found))
                        }
                        UniversalCopyManager.CollectStatus.UNAVAILABLE -> {
                            showToast(context, ModuleRes.getString(R.string.toast_copy_unavailable))
                        }
                    }
                }
            }
            action.startsWith("shell:") -> {
                doExecuteShellCommand(action, context)
            }
            action.startsWith("app_shortcut:") -> {
                launchShortcut(context, action)
            }
            action.startsWith("launch_app:") -> {
                launchApp(context, action)
            }
            action.startsWith("launch_activity:") -> {
                try {
                    val fullTarget = action.substringAfter("launch_activity:")
                    val parts = fullTarget.split("/")
                    if (parts.size == 2) {
                        val pkg = parts[0]
                        val cls = parts[1]
                        val intent = Intent().apply {
                            component = ComponentName(pkg, cls)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                } catch (e: Exception) {
                    log("launchActivity failed: ${e.message}")
                }
            }
            action == "clipboard" -> {
                ClipboardOverlay.show(context)
            }
            action == "freezer_drawer" -> {
                DrawerManager.showDrawer(context, resolveConfig)
            }
            action == AppConfig.CUSTOM_PANEL_ACTION -> {
                PanelOverlayManager.showCustomPanel(context, resolveConfig) { selected ->
                    dispatchAction(selected, context, touchX, touchY)
                }
            }
            action == AppConfig.SIDE_BAR_LEFT_ACTION -> {
                PanelOverlayManager.showSideBar(context, resolveConfig, "left") { selected ->
                    dispatchAction(selected, context, touchX, touchY)
                }
            }
            action == AppConfig.SIDE_BAR_RIGHT_ACTION -> {
                PanelOverlayManager.showSideBar(context, resolveConfig, "right") { selected ->
                    dispatchAction(selected, context, touchX, touchY)
                }
            }
            action.startsWith("multi_action:") -> {
                executeMultiAction(action, context, touchX, touchY)
            }
            action.startsWith("condition:") -> {
                executeConditionAction(action, context, touchX, touchY)
            }
            action == "toggle_flashlight" -> {
                FlashlightManager.toggle(context, handlerProvider())
            }
            action == "toggle_wifi" -> {
                toggleWifi(context)
            }
            action == "toggle_mobile_data" -> {
                toggleMobileData(context)
            }
            action == "game_mode" -> {
                GameModeManager.enable(context, handlerProvider())
            }
        }
    }

    fun onUserUnlocked(context: Context) {
        handlerProvider().post {
            if (isKeyguardLocked(context) || pendingUnlockActions.isEmpty()) return@post

            val actions = buildList {
                while (pendingUnlockActions.isNotEmpty()) {
                    add(pendingUnlockActions.removeFirst())
                }
            }
            log("Executing ${actions.size} action(s) queued during lockscreen")

            var delay = 0L
            actions.forEach { pending ->
                handlerProvider().postDelayed({
                    dispatchAction(
                        pending.action,
                        pending.context,
                        pending.touchX,
                        pending.touchY,
                    )
                }, delay)
                delay += stepSettleDuration(pending.action)
            }
        }
    }

    private fun isKeyguardLocked(context: Context): Boolean = try {
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
    } catch (_: Throwable) {
        false
    }

    private fun executeConditionAction(action: String, context: Context, touchX: Float, touchY: Float) {
        val id = action.removePrefix("condition:")
        if (id.isBlank()) return
        val condCode = resolveConfig(ConditionStore.condIfKey(id))
        if (condCode.isBlank()) return
        val foregroundAppConfig = if (condCode == ConditionStore.FOREGROUND_APP) {
            ForegroundAppConditionConfig(
                packageNames = ConditionStore.decodePackageNames(
                    resolveConfig(ConditionStore.foregroundPackagesKey(id)),
                ),
            )
        } else {
            null
        }
        val result = ConditionEvaluator.evaluate(condCode, context, foregroundAppConfig)
        val nextAction = if (result) {
            resolveConfig(ConditionStore.condThenKey(id))
        } else {
            resolveConfig(ConditionStore.condElseKey(id))
        }
        if (nextAction.isNotBlank() && nextAction != "none") {
            dispatchAction(nextAction, context, touchX, touchY)
        }
    }

    private fun executeMultiAction(action: String, context: Context, touchX: Float, touchY: Float) {
        val id = action.removePrefix("multi_action:")
        if (id.isBlank()) return
        val steps = com.fan.edgex.config.MultiActionStore.getStepsFromConfig(resolveConfig, id)
        if (steps.isEmpty()) return
        val handler = handlerProvider()
        var delay = 0L
        for (step in steps) {
            if (step.code.isBlank() || step.code == "none") continue
            val code = step.code
            de.robv.android.xposed.XposedBridge.log("EdgeX: multi_action step='$code' scheduledAt=${delay}ms")
            handler.postDelayed({
                try {
                    de.robv.android.xposed.XposedBridge.log("EdgeX: multi_action executing step='$code'")
                    dispatchAction(code, context, touchX, touchY)
                } catch (t: Throwable) {
                    log("multi_action step '$code' failed: ${t.message}")
                }
            }, delay)
            delay += stepSettleDuration(code)
        }
    }

    // How long to wait after this action before firing the next step.
    // Navigation actions (HOME, BACK, etc.) animate for ~300ms; give 600ms to be safe.
    // App launches need ~500ms for the window to fully appear.
    // Everything else (brightness, volume, media) is near-instant.
    private fun stepSettleDuration(code: String): Long =
        com.fan.edgex.action.AppActionExecutor.stepSettleDuration(code)

    fun showPie(context: Context, anchorX: Float, anchorY: Float, edge: String) {
        val rings = (1..AppConfig.PIE_RINGS).map { ring ->
            PieView.Ring((0 until AppConfig.PIE_SLOTS_PER_RING).mapNotNull { slot ->
                val action = resolveConfig(AppConfig.pieSlot(edge, ring, slot))
                if (action.isEmpty() || action == "none") null
                else {
                    val label = resolveConfig(AppConfig.pieSlotLabel(edge, ring, slot)).ifEmpty { pieActionToLabel(action) }
                    val icon = loadActionIcon(context, action)
                    PieView.Slot(label, action, icon)
                }
            })
        }
        if (rings.all { it.slots.isEmpty() }) return
        PieManager.show(context, anchorX, anchorY, edge, rings, resolvePieColor(), resolvePieSizeScale())
    }

    private fun resolvePieColor(): Int {
        return ThemeColorResolver.resolveConfiguredColor(AppConfig.PIE_COLOR, resolveConfig)
    }

    private fun resolvePieSizeScale(): Float =
        resolveConfig(AppConfig.PIE_SIZE_SCALE)
            .toFloatOrNull()
            ?.coerceIn(0.8f, 1.2f)
            ?: AppConfig.PIE_SIZE_SCALE_DEFAULT

    private fun loadActionIcon(context: Context, action: String): Drawable? {
        if (action.startsWith("launch_app:")) {
            val pkg = action.substringAfter("launch_app:")
            try { return context.packageManager.getApplicationIcon(pkg) } catch (_: Exception) {}
        }
        val resId = actionToIconRes(action)
        if (resId == 0) return null
        return ModuleRes.getDrawable(resId)
    }

    private fun actionToIconRes(action: String): Int = when {
        action == "back"                     -> R.drawable.ic_arrow_back
        action == "home"                     -> R.drawable.ic_home
        action == "recents"                  -> R.drawable.ic_recents
        action == "screenshot"               -> R.drawable.ic_camera
        action == AppConfig.PARTIAL_SCREENSHOT_ACTION -> R.drawable.ic_partial_screenshot
        action == "lock_screen"              -> R.drawable.ic_power
        action == "expand_notifications"     -> R.drawable.ic_notifications
        action == "kill_app"                 -> R.drawable.ic_kill_app
        action == "prev_app"                 -> R.drawable.ic_prev_app
        action == "next_app"                 -> R.drawable.ic_next_app
        action == "clipboard"                -> R.drawable.ic_paste
        action == "universal_copy"           -> R.drawable.ic_content_copy
        action == "freezer_drawer"           -> R.drawable.ic_freezer
        action == "refreeze"                 -> R.drawable.ic_refreeze
        action == "brightness_up"            -> R.drawable.ic_brightness_up
        action == "brightness_down"          -> R.drawable.ic_brightness_down
        action == "volume_up"                -> R.drawable.ic_volume_up
        action == "volume_down"              -> R.drawable.ic_volume_down
        action == "clear_background"         -> R.drawable.ic_clear_recent
        action == "sub_gesture"              -> R.drawable.ic_sub_gesture
        action.startsWith("music_control:")  -> when (action.substringAfter("music_control:")) {
            "play_pause" -> R.drawable.ic_music_play_pause
            "stop"       -> R.drawable.ic_music_stop
            "previous"   -> R.drawable.ic_music_previous
            "next"       -> R.drawable.ic_music_next
            else         -> R.drawable.ic_music
        }
        action.startsWith("fast_scroll:")  -> when (action.substringAfter("fast_scroll:")) {
            "to_top"    -> R.drawable.ic_scroll_to_top
            "to_bottom" -> R.drawable.ic_scroll_to_bottom
            else        -> R.drawable.ic_fast_scroll
        }
        action.startsWith("shell:")          -> R.drawable.ic_terminal
        action.startsWith("app_shortcut:")   -> R.drawable.ic_app_shortcut
        action.startsWith("launch_app:")     -> R.drawable.ic_launch_app
        action == AppConfig.SIDE_BAR_LEFT_ACTION -> R.drawable.ic_side_bar_left
        action == AppConfig.SIDE_BAR_RIGHT_ACTION -> R.drawable.ic_side_bar_right
        action == "toggle_flashlight"            -> R.drawable.ic_flashlight
        action == "toggle_wifi"                  -> R.drawable.ic_wifi
        action == "toggle_mobile_data"           -> R.drawable.ic_mobile_data
        action == "game_mode"                    -> R.drawable.ic_game_mode
        else                                 -> 0
    }

    fun commitPieAction(context: Context) {
        val action = PieManager.commit() ?: return
        performAction(action, context, 0f, 0f)
    }

    private fun pieActionToLabel(action: String): String = when {
        action == "back"              -> "Back"
        action == "home"              -> "Home"
        action == "recents" || action == "recent" -> "Recents"
        action == "screenshot"        -> "Screenshot"
        action == AppConfig.PARTIAL_SCREENSHOT_ACTION -> "Partial SS"
        action == "lock_screen"       -> "Lock"
        action == "expand_notifications" -> "Notifs"
        action == "kill_app"          -> "Kill App"
        action == "prev_app"          -> "Prev App"
        action == "next_app"          -> "Next App"
        action == "clipboard"         -> "Clipboard"
        action == "universal_copy"    -> "Copy"
        action == "freezer_drawer"    -> "Freezer"
        action == "refreeze"          -> "Refreeze"
        action == "brightness_up"     -> "Bright+"
        action == "brightness_down"   -> "Bright-"
        action == "volume_up"         -> "Vol+"
        action == "volume_down"       -> "Vol-"
        action == "clear_background"  -> "Clear"
        action.startsWith("music_control:") -> "Music"
        action.startsWith("fast_scroll:") -> when (action.substringAfter("fast_scroll:")) {
            "to_top" -> "Scroll Up"
            "to_bottom" -> "Scroll Down"
            else -> "Scroll"
        }
        action.startsWith("shell:")   -> "Shell"
        action.startsWith("launch_app:") -> "Launch"
        action.startsWith("app_shortcut:") -> "Shortcut"
        action == "toggle_flashlight" -> "Flashlight"
        action == "toggle_wifi"       -> "Wi-Fi"
        action == "toggle_mobile_data" -> "Data"
        action == "game_mode"         -> "GameMode"
        else                          -> action.take(8)
    }

    @Suppress("DEPRECATION")
    private fun toggleWifi(context: Context) {
        try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager == null) {
                showToast(context, ModuleRes.getString(R.string.toast_wifi_toggle_failed))
                return
            }
            val enable = !wifiManager.isWifiEnabled
            val success = wifiManager.setWifiEnabled(enable)
            if (success) {
                showToast(
                    context,
                    ModuleRes.getString(
                        if (enable) R.string.wifi_toast_on else R.string.wifi_toast_off,
                    ),
                )
            } else {
                showToast(context, ModuleRes.getString(R.string.toast_wifi_toggle_failed))
            }
        } catch (t: Throwable) {
            log("toggleWifi failed: ${t.message}")
            showToast(context, ModuleRes.getString(R.string.toast_wifi_toggle_failed))
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun toggleMobileData(context: Context) {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager == null) {
                showToast(context, ModuleRes.getString(R.string.toast_mobile_data_toggle_failed))
                return
            }
            val enable = !telephonyManager.isDataEnabled
            telephonyManager.setDataEnabled(enable)
            showToast(
                context,
                ModuleRes.getString(
                    if (enable) R.string.mobile_data_toast_on else R.string.mobile_data_toast_off,
                ),
            )
        } catch (t: Throwable) {
            log("toggleMobileData failed: ${t.message}")
            showToast(context, ModuleRes.getString(R.string.toast_mobile_data_toggle_failed))
        }
    }

    private fun killForegroundApp(context: Context) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            val tasks = activityManager.getRunningTasks(1)
            if (tasks.isNullOrEmpty()) return
            val pkg = tasks[0].topActivity?.packageName ?: return
            if (pkg == context.packageName) return
            XposedHelpers.callMethod(activityManager, "forceStopPackage", pkg)
        } catch (e: Exception) {
            log("killForegroundApp failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun switchApp(context: Context, forward: Boolean) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            val tasks = activityManager.getRunningTasks(50)
            if (tasks.isNullOrEmpty()) return

            val homePkgs = getHomeLauncherPackages(context)
            val filtered = tasks.filter { task ->
                val pkg = task.topActivity?.packageName ?: return@filter false
                pkg != context.packageName && pkg !in homePkgs
            }
            if (filtered.size < 2) return

            val currentPkg = filtered[0].topActivity?.packageName ?: return

            val sorted = filtered.sortedBy { it.topActivity?.packageName ?: "" }
            val idx = sorted.indexOfFirst { it.topActivity?.packageName == currentPkg }
            val step = if (forward) 1 else -1
            val nextIdx = ((if (idx < 0) 0 else idx) + step + sorted.size) % sorted.size
            val target = sorted[nextIdx]
            if (target.topActivity?.packageName == currentPkg) return
            val targetTaskId = target.taskId

            @Suppress("DEPRECATION")
            activityManager.moveTaskToFront(targetTaskId, 0)
        } catch (t: Throwable) {
            log("switchApp failed: ${t.message}")
        }
    }

    private fun getHomeLauncherPackages(context: Context): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return try {
            context.packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        } catch (_: Throwable) {
            emptySet()
        }
    }

    private fun doExecuteShellCommand(action: String, context: Context) {
        val content = action.removePrefix("shell:")
        val parts = content.split(":", limit = 2)
        if (parts.size != 2) {
            showToast(context, ModuleRes.getString(R.string.toast_shell_invalid_format))
            return
        }
        val runAsRoot = parts[0] == "true"
        val command = parts[1]
        if (command.isBlank()) {
            showToast(context, ModuleRes.getString(R.string.toast_empty_command))
            return
        }

        val executor = shellExecutor
        if (executor == null) {
            pendingCommands.addLast(action to context)
            bindShellService(context)
            return
        }

        scheduleIdleUnbind()
        executor.execute(command, runAsRoot, object : IShellCallback.Stub() {
            override fun onResult(success: Boolean, output: String?) {
                if (success) {
                    output?.trim()?.takeIf { it.isNotBlank() }?.let {
                        showToast(context, it.take(200))
                    }
                } else {
                    showToast(context, ModuleRes.getString(R.string.toast_command_failed, output?.trim()?.take(200).orEmpty()))
                }
            }
        })
    }

    private fun showToast(context: Context, text: String) {
        handlerProvider().post {
            try {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {
            }
        }
    }

    private fun launchShortcut(context: Context, action: String) {
        try {
            val parts = action.split(":", limit = 3)
            if (parts.size != 3) {
                showToast(context, ModuleRes.getString(R.string.toast_shortcut_format_error))
                return
            }

            val packageName = parts[1]
            val shortcutId = parts[2]

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
                val launcherApps =
                    context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
                try {
                    launcherApps.startShortcut(
                        packageName,
                        shortcutId,
                        null,
                        null,
                        currentUserHandle(),
                    )
                } catch (e: Throwable) {
                    log("Failed to launch shortcut: ${e.message}")
                    try {
                        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } else {
                            showToast(context, ModuleRes.getString(R.string.toast_cannot_launch_shortcut))
                        }
                    } catch (_: Throwable) {
                        showToast(context, ModuleRes.getString(R.string.toast_launch_failed))
                    }
                }
            } else {
                showToast(context, ModuleRes.getString(R.string.toast_requires_android_71))
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            showToast(context, ModuleRes.getString(R.string.toast_shortcut_launch_failed, e.message))
        }
    }

    private fun currentUserHandle(): UserHandle {
        val currentUserId = runCatching {
            XposedHelpers.callStaticMethod(
                android.app.ActivityManager::class.java,
                "getCurrentUser",
            ) as Int
        }.getOrDefault(0)
        return runCatching {
            XposedHelpers.callStaticMethod(UserHandle::class.java, "of", currentUserId) as UserHandle
        }.getOrDefault(android.os.Process.myUserHandle())
    }

    private fun performRefreeze(context: Context) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val pm = context.packageManager
                val packageSet = linkedSetOf<String>()
                val listStr = readConfigValue(context, AppConfig.FREEZER_APP_LIST)
                if (listStr.isNotEmpty()) {
                    packageSet.addAll(
                        listStr.split(",")
                            .map { pkg -> pkg.trim() }
                            .filter { pkg -> pkg.isNotEmpty() },
                    )
                }

                if (packageSet.isEmpty()) {
                    handler.post {
                        Toast.makeText(
                            context,
                            ModuleRes.getString(R.string.toast_freezer_list_empty),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@Thread
                }

                var count = 0
                for (pkg in packageSet) {
                    try {
                        val info = pm.getApplicationInfo(pkg, 0)
                        if (info.enabled) {
                            var success = false
                            try {
                                pm.setApplicationEnabledSetting(
                                    pkg,
                                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                    0,
                                )
                                success = true
                            } catch (e: Exception) {
                                XposedBridge.log("EdgeX: PM API freeze FAILED for $pkg: ${e.message}")
                            }
                            if (success) count++
                        }
                    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (count > 0) {
                    handler.post {
                        Toast.makeText(
                            context,
                            ModuleRes.getString(R.string.toast_refrozen_apps, count),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                } else {
                    handler.post {
                        Toast.makeText(
                            context,
                            ModuleRes.getString(R.string.toast_no_apps_to_freeze),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post {
                    Toast.makeText(
                        context,
                        ModuleRes.getString(R.string.toast_freeze_error, e.message),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }.start()
    }

    private fun clearBackgroundApps(context: Context) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val activityTaskManager = getActivityTaskManagerService()
            @Suppress("DEPRECATION")
            val recentTasks = XposedHelpers.callMethod(
                activityManager, "getRecentTasks",
                100, android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE,
            ) as List<*>
            var count = 0
            for ((index, task) in recentTasks.withIndex()) {
                if (index == 0 || task == null) continue
                try {
                    val taskId = getRecentTaskId(task)
                    if (taskId < 0) continue
                    val removed = XposedHelpers.callMethod(activityTaskManager, "removeTask", taskId) as? Boolean
                    if (removed != false) count++
                } catch (t: Throwable) {
                    log("removeTask failed: ${t.message}")
                }
            }
            if (count > 0) {
                showToast(context, ModuleRes.getString(R.string.toast_cleared_background, count))
            }
        } catch (t: Throwable) {
            log("clearBackgroundApps failed: ${t.message}")
        }
    }

    private fun getActivityTaskManagerService(): Any {
        try {
            val activityTaskManager = XposedHelpers.findClass(
                "android.app.ActivityTaskManager",
                ClassLoader.getSystemClassLoader(),
            )
            val service = XposedHelpers.callStaticMethod(activityTaskManager, "getService")
            if (service != null) return service
        } catch (t: Throwable) {
            log("ActivityTaskManager.getService failed: ${t.message}")
        }

        try {
            val service = XposedHelpers.callStaticMethod(android.app.ActivityManager::class.java, "getTaskService")
            if (service != null) return service
        } catch (t: Throwable) {
            log("ActivityManager.getTaskService failed: ${t.message}")
        }

        val serviceManager = XposedHelpers.findClass("android.os.ServiceManager", ClassLoader.getSystemClassLoader())
        val binder = XposedHelpers.callStaticMethod(serviceManager, "getService", "activity_task")
        val stub = XposedHelpers.findClass(
            "android.app.IActivityTaskManager.Stub",
            ClassLoader.getSystemClassLoader(),
        )
        return XposedHelpers.callStaticMethod(stub, "asInterface", binder)
    }

    private fun getRecentTaskId(task: Any): Int {
        for (field in listOf("taskId", "persistentId", "id")) {
            try {
                val id = XposedHelpers.getIntField(task, field)
                if (id >= 0) return id
            } catch (_: Throwable) {
            }
        }
        return -1
    }


    private fun launchApp(context: Context, action: String) {
        try {
            val packageName = action.removePrefix("launch_app:")
            if (packageName.isBlank()) return
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                showToast(context, ModuleRes.getString(R.string.toast_app_not_found))
            }
        } catch (e: Throwable) {
            log("launchApp failed: ${e.message}")
        }
    }

    private fun readConfigValue(context: Context, key: String): String {
        val cached = resolveConfig(key)
        if (cached.isNotEmpty()) return cached

        val snapshot = HookConfigSnapshot.readFromHookFile()
        if (snapshot.containsKey(key)) return snapshot.getValue(key)

        log("Config value missing without Provider fallback: $key")
        return ""
    }

    private fun performScreenshot(context: Context) {
        val errors = mutableListOf<String>()

        if (injectScreenshotChord(context, errors)) return

        try {
            val result = GlobalActionHelper.performGlobalAction(
                context,
                GlobalActionHelper.GLOBAL_ACTION_TAKE_SCREENSHOT,
            )
            if (result) return
            errors.add("GLOBAL_ACTION_TAKE_SCREENSHOT: false")
        } catch (t: Throwable) {
            errors.add("GLOBAL_ACTION_TAKE_SCREENSHOT: ${t.message}")
        }

        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SYSRQ, 0)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SYSRQ, 0)

        try {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE)
            if (inputManager != null) {
                val injectMethod = inputManager.javaClass.getMethod(
                    "injectInputEvent",
                    Class.forName("android.view.InputEvent"),
                    Int::class.javaPrimitiveType,
                )
                injectMethod.invoke(inputManager, down, 0)
                injectMethod.invoke(inputManager, up, 0)
                return
            }
        } catch (t: Throwable) {
            errors.add("INPUT_SERVICE: ${t.message}")
        }

        try {
            val globalCls = Class.forName("android.hardware.input.InputManagerGlobal")
            val getInstance = globalCls.getMethod("getInstance")
            val global = getInstance.invoke(null)
            val injectMethod = globalCls.getMethod(
                "injectInputEvent",
                Class.forName("android.view.InputEvent"),
                Int::class.javaPrimitiveType,
            )
            injectMethod.invoke(global, down, 0)
            injectMethod.invoke(global, up, 0)
            return
        } catch (t: Throwable) {
            errors.add("InputManagerGlobal: ${t.message}")
        }

        try {
            Runtime.getRuntime().exec("input keyevent 120")
        } catch (e: Exception) {
            errors.add("shell: ${e.message}")
            log("screenshot failed -> ${errors.joinToString(" | ")}")
        }
    }

    private fun injectScreenshotChord(context: Context, errors: MutableList<String>): Boolean {
        val now = SystemClock.uptimeMillis()
        val events = arrayOf(
            KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0),
            KeyEvent(now, now + 30, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_POWER, 0),
            KeyEvent(now, now + 160, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_POWER, 0),
            KeyEvent(now, now + 170, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0),
        )

        return try {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE)
            if (inputManager != null) {
                val injectMethod = inputManager.javaClass.getMethod(
                    "injectInputEvent",
                    Class.forName("android.view.InputEvent"),
                    Int::class.javaPrimitiveType,
                )
                events.forEach { event ->
                    KeyManager.markInjectedEvent(event)
                    injectMethod.invoke(inputManager, event, 0)
                }
                true
            } else {
                errors.add("screenshot chord: INPUT_SERVICE null")
                false
            }
        } catch (t: Throwable) {
            errors.add("screenshot chord: ${t.message}")
            false
        }
    }

    private fun injectScrollEvent(context: Context, toTop: Boolean, touchX: Float, touchY: Float) {
        var targetX = touchX
        var targetY = touchY
        if (targetX == 0f && targetY == 0f) {
            val metrics = context.resources.displayMetrics
            targetX = metrics.widthPixels / 2f
            targetY = metrics.heightPixels / 2f
        }

        val now = SystemClock.uptimeMillis()
        val properties = arrayOf(android.view.MotionEvent.PointerProperties().apply {
            id = 0
            toolType = android.view.MotionEvent.TOOL_TYPE_MOUSE
        })
        val coords = arrayOf(android.view.MotionEvent.PointerCoords().apply {
            x = targetX
            y = targetY
            setAxisValue(android.view.MotionEvent.AXIS_VSCROLL, if (toTop) 100000.0f else -100000.0f)
        })
        val event = android.view.MotionEvent.obtain(
            now, now,
            android.view.MotionEvent.ACTION_SCROLL,
            1,
            properties, coords,
            0, 0, 0f, 0f, 0, 0,
            8194, // InputDevice.SOURCE_MOUSE
            0
        )

        try {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE)
            if (inputManager != null) {
                val injectMethod = inputManager.javaClass.getMethod(
                    "injectInputEvent",
                    Class.forName("android.view.InputEvent"),
                    Int::class.javaPrimitiveType,
                )
                injectMethod.invoke(inputManager, event, 0)
                return
            }
        } catch (t: Throwable) {
            log("injectScroll INPUT_SERVICE: ${t.message}")
        }

        try {
            val globalCls = Class.forName("android.hardware.input.InputManagerGlobal")
            val getInstance = globalCls.getMethod("getInstance")
            val global = getInstance.invoke(null)
            val injectMethod = globalCls.getMethod(
                "injectInputEvent",
                Class.forName("android.view.InputEvent"),
                Int::class.javaPrimitiveType,
            )
            injectMethod.invoke(global, event, 0)
        } catch (t: Throwable) {
            log("injectScroll InputManagerGlobal: ${t.message}")
        }
    }
}
