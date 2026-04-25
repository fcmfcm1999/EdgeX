package com.fan.edgex.hook

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import com.fan.edgex.R
import com.fan.edgex.config.AppConfig
import com.fan.edgex.overlay.DrawerManager
import com.topjohnwu.superuser.Shell
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

internal class GestureActionDispatcher(
    private val configUri: Uri,
    private val actionBroadcast: String,
    private val actionExtra: String,
    private val resolveConfig: (String) -> String,
    private val systemContextProvider: () -> Context?,
    private val systemUiContextProvider: () -> Context?,
    private val handlerProvider: () -> Handler,
    private val log: (String) -> Unit,
) {
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

    fun handleSystemUiAction(action: String, context: Context) {
        val ctx = systemUiContextProvider() ?: context
        when {
            action.startsWith("app_shortcut:") -> {
                handlerProvider().post { launchShortcut(ctx, action) }
            }
            action.startsWith("launch_app:") -> {
                handlerProvider().post { launchApp(ctx, action) }
            }
            action.startsWith("shell:") -> {
                doExecuteShellCommand(action, ctx)
            }
            action == "freezer_drawer" -> {
                handlerProvider().post { DrawerManager.showDrawer(ctx) }
            }
            action == "refreeze" -> {
                performRefreeze(ctx)
            }
            else -> {
                handlerProvider().post {
                    showToast(ctx, ModuleRes.getString(R.string.toast_unknown_action, action))
                }
            }
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
            log("Brightness $current -> $newVal")
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
        log("performAction called with action='$action'")
        when {
            action == "back" -> {
                log("Performing GLOBAL_ACTION_BACK")
                val result =
                    GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_BACK)
                log("GLOBAL_ACTION_BACK result=$result")
            }
            action == "home" -> {
                log("Performing GLOBAL_ACTION_HOME")
                val result =
                    GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_HOME)
                log("GLOBAL_ACTION_HOME result=$result")
            }
            action == "recent" || action == "recents" -> {
                log("Performing GLOBAL_ACTION_RECENTS")
                val result =
                    GlobalActionHelper.performGlobalAction(context, GlobalActionHelper.GLOBAL_ACTION_RECENTS)
                log("GLOBAL_ACTION_RECENTS result=$result")
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
                sendActionToSystemUI(action, context)
            }
            else -> {
                sendActionToSystemUI(action, context)
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
            log("Killed foreground app: $pkg")
        } catch (e: Exception) {
            log("killForegroundApp failed: ${e.message}")
        }
    }

    private fun doExecuteShellCommand(action: String, context: Context) {
        Thread {
            try {
                val content = action.removePrefix("shell:")
                val parts = content.split(":", limit = 2)
                if (parts.size != 2) {
                    showToast(context, ModuleRes.getString(R.string.toast_shell_invalid_format))
                    return@Thread
                }

                val runAsRoot = parts[0] == "true"
                val command = parts[1]
                if (command.isBlank()) {
                    showToast(context, ModuleRes.getString(R.string.toast_empty_command))
                    return@Thread
                }

                log("Executing shell command (root=$runAsRoot): $command")
                if (runAsRoot) {
                    val result = Shell.cmd(command).exec()
                    if (!result.isSuccess) {
                        val errorMsg = result.err.joinToString("\n")
                        log("Shell command failed: $errorMsg")
                        showToast(context, ModuleRes.getString(R.string.toast_command_failed, errorMsg))
                    } else {
                        val output = result.out.joinToString("\n")
                        log("Shell command output: $output")
                        if (output.isNotBlank()) showToast(context, output.take(100))
                    }
                } else {
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                    process.outputStream.close()
                    val exitCode = process.waitFor()
                    if (exitCode != 0) {
                        val errorMsg = process.errorStream.bufferedReader().readText()
                        log("Shell command failed (exit=$exitCode): $errorMsg")
                        showToast(context, ModuleRes.getString(R.string.toast_command_failed, errorMsg))
                    } else {
                        val output = process.inputStream.bufferedReader().readText()
                        log("Shell command output: $output")
                        if (output.isNotBlank()) showToast(context, output.take(100))
                    }
                }
            } catch (e: Exception) {
                log("Shell command exception: ${e.message}")
                e.printStackTrace()
                showToast(context, ModuleRes.getString(R.string.toast_command_error, e.message))
            }
        }.start()
    }

    private fun sendActionToSystemUI(action: String, context: Context) {
        try {
            val intent = Intent(actionBroadcast).apply {
                putExtra(actionExtra, action)
                setPackage("com.android.systemui")
            }
            val userHandle = android.os.UserHandle::class.java
                .getField("CURRENT").get(null) as android.os.UserHandle
            context.sendBroadcastAsUser(intent, userHandle)
        } catch (e: Exception) {
            log("Failed to send broadcast: ${e.message}")
            e.printStackTrace()
        }
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
            val parts = action.split(":")
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
                        android.os.Process.myUserHandle(),
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
            } else {
                showToast(context, ModuleRes.getString(R.string.toast_requires_android_71))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(context, ModuleRes.getString(R.string.toast_shortcut_launch_failed, e.message))
        }
    }

    private fun performRefreeze(context: Context) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val pm = context.packageManager
                val packageSet = linkedSetOf<String>()
                val cursor: Cursor? = configResolver(context).query(
                    Uri.withAppendedPath(configUri, AppConfig.FREEZER_APP_LIST),
                    null,
                    null,
                    null,
                    null,
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val listStr = it.getString(0)
                        if (!listStr.isNullOrEmpty()) {
                            packageSet.addAll(
                                listStr.split(",")
                                    .map { pkg -> pkg.trim() }
                                    .filter { pkg -> pkg.isNotEmpty() },
                            )
                        }
                    }
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
                                XposedBridge.log("EdgeX: PM API freeze SUCCESS for $pkg")
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
            @Suppress("DEPRECATION")
            val recentTasks = XposedHelpers.callMethod(
                activityManager, "getRecentTasks",
                100, android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE,
            ) as List<*>
            var count = 0
            for (task in recentTasks) {
                try {
                    val id = XposedHelpers.getIntField(task, "persistentId")
                    XposedHelpers.callMethod(activityManager, "removeTask", id)
                    count++
                } catch (e: Exception) {
                    log("removeTask failed: ${e.message}")
                }
            }
            log("Cleared $count background app(s)")
            if (count > 0) {
                showToast(context, ModuleRes.getString(R.string.toast_cleared_background, count))
            }
        } catch (e: Exception) {
            log("clearBackgroundApps failed: ${e.message}")
        }
    }

    private fun dispatchMediaKey(context: Context, action: String) {
        try {
            val keyCode = when (action.removePrefix("music_control:")) {
                "play"       -> KeyEvent.KEYCODE_MEDIA_PLAY
                "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                "stop"       -> KeyEvent.KEYCODE_MEDIA_STOP
                "next"       -> KeyEvent.KEYCODE_MEDIA_NEXT
                "previous"   -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> { log("Unknown music_control subaction: $action"); return }
            }
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val now = SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now + 10, KeyEvent.ACTION_UP, keyCode, 0))
            log("Dispatched media key: $keyCode for $action")
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

    private fun configResolver(context: Context) =
        systemContextProvider()?.contentResolver
            ?: systemUiContextProvider()?.contentResolver
            ?: context.contentResolver

    private fun performScreenshot(context: Context) {
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SYSRQ, 0)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SYSRQ, 0)
        val errors = mutableListOf<String>()

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
}
