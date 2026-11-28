package com.fan.edgex.hook

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.fan.edgex.R
import de.robv.android.xposed.XposedBridge

internal object FreezerAppLauncher {

    internal const val MAX_LAUNCH_RETRIES = 10
    internal const val LAUNCH_RETRY_DELAY_MS = 200L

    fun isPackageFrozen(pm: PackageManager, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            !pm.getApplicationInfo(packageName, 0).enabled
        } catch (_: Throwable) {
            false
        }
    }

    fun launchOrUnfreeze(
        context: Context,
        packageName: String,
        handler: Handler = Handler(Looper.getMainLooper()),
        onLaunched: (() -> Unit)? = null,
        log: (String) -> Unit = { XposedBridge.log("EdgeX: $it") },
    ) {
        val trimmed = packageName.trim()
        if (trimmed.isBlank()) return
        val pm = context.packageManager
        val appInfo = try {
            pm.getApplicationInfo(trimmed, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            EdgeXSystemToast.show(context, ModuleRes.getString(R.string.toast_app_not_found))
            return
        } catch (t: Throwable) {
            log("FreezerAppLauncher.getApplicationInfo failed for $trimmed: ${t.message}")
            EdgeXSystemToast.show(context, ModuleRes.getString(R.string.toast_app_not_found))
            return
        }

        if (!appInfo.enabled) {
            unfreezeAndLaunch(
                context = context,
                packageName = trimmed,
                handler = handler,
                onLaunched = onLaunched,
                log = log,
            )
        } else {
            val intent = pm.getLaunchIntentForPackage(trimmed)
            if (intent != null) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    context.startActivity(intent)
                    onLaunched?.invoke()
                } catch (t: Throwable) {
                    log("FreezerAppLauncher.launch failed for $trimmed: ${t.message}")
                    EdgeXSystemToast.show(context, ModuleRes.getString(R.string.toast_launch_error, t.message))
                }
            } else {
                EdgeXSystemToast.show(context, ModuleRes.getString(R.string.toast_app_not_found))
            }
        }
    }

    fun unfreezeAndLaunch(
        context: Context,
        packageName: String,
        handler: Handler = Handler(Looper.getMainLooper()),
        onLaunched: (() -> Unit)? = null,
        log: (String) -> Unit = { XposedBridge.log("EdgeX: $it") },
    ) {
        val pm = context.packageManager
        log("FreezerAppLauncher.unfreezeAndLaunch - packageName: $packageName")
        unfreezeAndLaunchCore(
            runAsync = { block -> Thread(block).start() },
            enablePackage = {
                pm.setApplicationEnabledSetting(
                    packageName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    0,
                )
                log("PM API unfreeze SUCCESS for $packageName")
            },
            postToMain = { block -> handler.post(block) },
            postDelayedToMain = { delayMs, block -> handler.postDelayed(block, delayMs) },
            resolveLaunchIntent = { pm.getLaunchIntentForPackage(packageName) },
            startActivity = { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(intent)
            },
            onLaunched = onLaunched,
            onUnfreezeFailed = { error ->
                log("PM API unfreeze FAILED for $packageName: ${error.message}")
                EdgeXSystemToast.show(context, ModuleRes.getString(R.string.toast_unfreeze_failed_drawer))
            },
            onLaunchTimeout = {
                EdgeXSystemToast.show(context, ModuleRes.getString(R.string.toast_launch_timeout))
            },
            onLaunchError = { error ->
                log("FreezerAppLauncher.launch error for $packageName: ${error.message}")
                EdgeXSystemToast.show(context, ModuleRes.getString(R.string.toast_launch_error, error.message))
            },
        )
    }

    fun launchWithRetry(
        context: Context,
        pm: PackageManager,
        packageName: String,
        handler: Handler = Handler(Looper.getMainLooper()),
        onLaunched: (() -> Unit)? = null,
    ) {
        launchWithRetryCore(
            postDelayedToMain = { delayMs, block -> handler.postDelayed(block, delayMs) },
            resolveLaunchIntent = { pm.getLaunchIntentForPackage(packageName) },
            startActivity = { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(intent)
            },
            onLaunched = onLaunched,
            onLaunchTimeout = {
                EdgeXSystemToast.show(context, ModuleRes.getString(R.string.toast_launch_timeout))
            },
            onLaunchError = { error ->
                EdgeXSystemToast.show(context, ModuleRes.getString(R.string.toast_launch_error, error.message))
            },
        )
    }

    internal fun <I> unfreezeAndLaunchCore(
        runAsync: (() -> Unit) -> Unit,
        enablePackage: () -> Unit,
        postToMain: (() -> Unit) -> Unit,
        postDelayedToMain: (Long, () -> Unit) -> Unit,
        resolveLaunchIntent: () -> I?,
        startActivity: (I) -> Unit,
        onLaunched: (() -> Unit)? = null,
        onUnfreezeFailed: (Throwable) -> Unit,
        onLaunchTimeout: () -> Unit,
        onLaunchError: (Throwable) -> Unit,
    ) {
        runAsync {
            var error: Throwable? = null
            try {
                enablePackage()
            } catch (t: Throwable) {
                error = t
            }
            postToMain {
                if (error == null) {
                    launchWithRetryCore(
                        postDelayedToMain = postDelayedToMain,
                        resolveLaunchIntent = resolveLaunchIntent,
                        startActivity = startActivity,
                        onLaunched = onLaunched,
                        onLaunchTimeout = onLaunchTimeout,
                        onLaunchError = onLaunchError,
                    )
                } else {
                    onUnfreezeFailed(error)
                }
            }
        }
    }

    internal fun <I> launchWithRetryCore(
        postDelayedToMain: (Long, () -> Unit) -> Unit,
        resolveLaunchIntent: () -> I?,
        startActivity: (I) -> Unit,
        onLaunched: (() -> Unit)? = null,
        onLaunchTimeout: () -> Unit,
        onLaunchError: (Throwable) -> Unit,
        maxRetries: Int = MAX_LAUNCH_RETRIES,
        retryDelayMs: Long = LAUNCH_RETRY_DELAY_MS,
    ) {
        var retries = 0
        lateinit var attempt: () -> Unit
        attempt = {
            try {
                val intent = resolveLaunchIntent()
                if (intent != null) {
                    startActivity(intent)
                    onLaunched?.invoke()
                } else if (++retries < maxRetries) {
                    postDelayedToMain(retryDelayMs, attempt)
                } else {
                    onLaunchTimeout()
                }
            } catch (t: Throwable) {
                onLaunchError(t)
            }
        }
        attempt()
    }
}
