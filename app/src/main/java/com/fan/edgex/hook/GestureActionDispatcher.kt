package com.fan.edgex.hook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.view.KeyEvent
import android.widget.Toast
import com.fan.edgex.BuildConfig
import com.fan.edgex.IShellCallback
import com.fan.edgex.IShellExecutor
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.config.HookConfigSnapshot
import com.fan.edgex.overlay.DrawerManager
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
    private var idleUnbindRunnable: Runnable? = null
    private val SHELL_SERVICE_IDLE_TIMEOUT_MS = 5 * 60 * 1000L

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
        val action = resolveConfig(configKey)

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

    fun adjustBrightness(context: Context, up: Boolean) {
        try {
            val displayManager = context.getSystemService("display") as android.hardware.display.DisplayManager
            val getBrightness = android.hardware.display.DisplayManager::class.java
                .getMethod("getBrightness", Int::class.java)
            val setBrightness = android.hardware.display.DisplayManager::class.java
                .getMethod("setBrightness", Int::class.java, Float::class.java)
            val current = getBrightness.invoke(displayManager, 0) as Float
            val step = 1.0f / 16f
            val newVal = if (up) minOf(1.0f, current + step) else maxOf(0.0f, current - step)
            setBrightness.invoke(displayManager, 0, newVal)
        } catch (e: Exception) {
            log("adjustBrightness failed: ${e.message}")
        }
    }

    fun adjustVolume(context: Context, up: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val direction =
                if (up) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                direction,
                android.media.AudioManager.FLAG_SHOW_UI,
            )
        } catch (e: Exception) {
            log("adjustVolume failed: ${e.message}")
        }
    }

    private fun performAction(action: String, context: Context, touchX: Float, touchY: Float) {
        when {
            action == "back" -> {
                GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_BACK)
            }
            action == "home" -> {
                GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_HOME)
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
            action == "clear_background" -> {
                clearBackgroundApps(context)
            }
            action.startsWith("music_control:") -> {
                dispatchMediaKey(context, action)
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
            action == "freezer_drawer" -> {
                DrawerManager.showDrawer(context, resolveConfig)
            }
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
            } catch (e: Exception) {
                log("Failed to launch shortcut: ${e.message}")
                try {
                    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } else {
                        showToast(context, ModuleRes.getString(R.string.toast_cannot_launch_shortcut))
                    }
                } catch (_: Exception) {
                    showToast(context, ModuleRes.getString(R.string.toast_launch_failed))
                }
            }
        } catch (e: Exception) {
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

    private fun dispatchMediaKey(context: Context, action: String) {
        try {
            val keyCode = when (action.removePrefix("music_control:")) {
                "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                "stop"       -> KeyEvent.KEYCODE_MEDIA_STOP
                "next"       -> KeyEvent.KEYCODE_MEDIA_NEXT
                "previous"   -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> return
            }
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val now = SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now + 10, KeyEvent.ACTION_UP, keyCode, 0))
        } catch (e: Exception) {
            log("dispatchMediaKey failed: ${e.message}")
        }
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
        } catch (e: Exception) {
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
}
