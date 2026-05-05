package com.fan.edgex.action

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import com.fan.edgex.config.MultiActionStep
import com.topjohnwu.superuser.Shell

/**
 * Executes actions using standard Android APIs available in any process context.
 * Used by both GestureActionDispatcher (system_server) and the UI (app process).
 *
 * Actions that require Xposed / system_server privileges (back, lock_screen, kill_app,
 * screenshot, etc.) return false from execute() and must be handled by the caller.
 */
object AppActionExecutor {

    /**
     * Execute an action. Returns true if handled, false if caller must handle it
     * (e.g. system_server-only actions).
     */
    fun execute(context: Context, code: String): Boolean = when {
        code == "volume_up"   -> { adjustVolume(context, true);  true }
        code == "volume_down" -> { adjustVolume(context, false); true }
        code == "brightness_up"   -> { adjustBrightness(context, true);  true }
        code == "brightness_down" -> { adjustBrightness(context, false); true }
        code.startsWith("music_control:") -> { dispatchMusicControl(context, code); true }
        code == "home" -> { launchHome(context); true }
        code == "recents" || code == "recent" -> { toggleRecents(context); true }
        code == "expand_notifications" || code == "notifications" -> { expandNotifications(context); true }
        code.startsWith("launch_app:") -> { launchApp(context, code); true }
        code.startsWith("app_shortcut:") -> { launchShortcut(context, code); true }
        code.startsWith("shell:") -> { executeShell(context, code); true }
        else -> false
    }

    fun adjustVolume(context: Context, up: Boolean) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI,
            )
        } catch (_: Exception) {}
    }

    fun adjustBrightness(context: Context, up: Boolean) {
        try {
            val dm = context.getSystemService("display") as android.hardware.display.DisplayManager
            val get = android.hardware.display.DisplayManager::class.java.getMethod("getBrightness", Int::class.java)
            val set = android.hardware.display.DisplayManager::class.java.getMethod("setBrightness", Int::class.java, Float::class.java)
            val current = get.invoke(dm, 0) as Float
            val step = 1.0f / 16f
            set.invoke(dm, 0, if (up) minOf(1.0f, current + step) else maxOf(0.0f, current - step))
        } catch (_: Exception) {}
    }

    fun dispatchMusicControl(context: Context, action: String) {
        try {
            val keyCode = when (action.removePrefix("music_control:")) {
                "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                "stop"       -> KeyEvent.KEYCODE_MEDIA_STOP
                "next"       -> KeyEvent.KEYCODE_MEDIA_NEXT
                "previous"   -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> return
            }
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val now = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(now, now,      KeyEvent.ACTION_DOWN, keyCode, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now + 10, KeyEvent.ACTION_UP,   keyCode, 0))
        } catch (_: Exception) {}
    }

    private fun launchHome(context: Context) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun toggleRecents(context: Context) {
        runCatching {
            val sb = context.getSystemService("statusbar") ?: return
            Class.forName("android.app.StatusBarManager").getMethod("toggleRecentApps").invoke(sb)
        }
    }

    private fun expandNotifications(context: Context) {
        runCatching {
            val sb = context.getSystemService("statusbar") ?: return
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        }
    }

    private fun launchApp(context: Context, action: String) {
        runCatching {
            val pkg = action.removePrefix("launch_app:").takeIf { it.isNotBlank() } ?: return
            val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * Execute a list of steps sequentially with action-type-aware delays between them.
     * Uses a main-thread Handler so callers don't need to manage one.
     */
    fun executeSteps(context: Context, steps: List<MultiActionStep>, handler: Handler = Handler(Looper.getMainLooper())) {
        var delay = 0L
        for (step in steps) {
            if (step.code.isBlank() || step.code == "none") continue
            val code = step.code
            handler.postDelayed({
                runCatching { execute(context, code) }
            }, delay)
            delay += stepSettleDuration(code)
        }
    }

    /**
     * How long to wait after dispatching [code] before the next step is safe to fire.
     * Matches the delays used in GestureActionDispatcher for consistency.
     */
    fun stepSettleDuration(code: String): Long = when {
        code == "home" || code == "back" || code == "recent" || code == "recents"
            || code == "lock_screen" || code == "notifications" || code == "expand_notifications"
            || code == "quick_settings" -> 600L
        code.startsWith("launch_app:") || code.startsWith("app_shortcut:") -> 500L
        code == "screenshot" || code == "clear_background" || code == "refreeze" -> 300L
        code == "prev_app" || code == "next_app" -> 500L
        else -> 150L
    }

    private fun executeShell(context: Context, action: String) {
        val content = action.removePrefix("shell:")
        val parts = content.split(":", limit = 2)
        if (parts.size != 2) return
        val runAsRoot = parts[0] == "true"
        val command = parts[1].takeIf { it.isNotBlank() } ?: return
        Thread {
            try {
                val (success, output) = if (runAsRoot) {
                    val result = Shell.cmd(command).exec()
                    result.isSuccess to (if (result.isSuccess) result.out else result.err)
                        .joinToString("\n").trim()
                } else {
                    val process = ProcessBuilder("sh", "-c", command)
                        .redirectErrorStream(true).start()
                    process.outputStream.close()
                    val out = process.inputStream.bufferedReader().readText().trim()
                    (process.waitFor() == 0) to out
                }
                val msg = output.take(200).ifBlank { if (success) "OK" else "Failed" }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, e.message ?: "Error", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun launchShortcut(context: Context, action: String) {
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
            val parts = action.split(":", limit = 3)
            if (parts.size != 3) return
            val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
            la.startShortcut(parts[1], parts[2], null, null, android.os.Process.myUserHandle())
        }
    }
}
