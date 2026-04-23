package com.fan.edgex.config

import android.content.Context
import android.content.Intent
import java.util.concurrent.atomic.AtomicBoolean

object FreezerBootstrap {
    private val started = AtomicBoolean(false)

    fun ensureMigrated(context: Context) {
        val appContext = context.applicationContext
        if (appContext.getConfigBool(AppConfig.HAS_MIGRATED_FREEZER_LIST)) return
        if (!started.compareAndSet(false, true)) return

        Thread {
            try {
                if (appContext.getConfigBool(AppConfig.HAS_MIGRATED_FREEZER_LIST)) return@Thread

                val pm = appContext.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val disabledLauncherApps = pm.queryIntentActivities(
                    mainIntent,
                    android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS,
                )
                    .asSequence()
                    .map { it.activityInfo.applicationInfo }
                    .filter { !it.enabled }
                    .map { it.packageName.trim() }
                    .filter { it.isNotEmpty() }
                    .toCollection(linkedSetOf())

                android.util.Log.d(
                    "EdgeX",
                    "Freezer bootstrap scan: found ${disabledLauncherApps.size} apps: $disabledLauncherApps",
                )

                val currentSet = appContext.getConfigString(AppConfig.FREEZER_APP_LIST)
                    .split(',')
                    .asSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toCollection(linkedSetOf())
                currentSet.addAll(disabledLauncherApps)

                val persisted = appContext.putConfigsSync(
                    AppConfig.FREEZER_APP_LIST to currentSet.joinToString(","),
                    AppConfig.HAS_MIGRATED_FREEZER_LIST to true.toString(),
                )
                android.util.Log.d(
                    "EdgeX",
                    "Freezer bootstrap persisted=$persisted, size=${currentSet.size}",
                )
            } finally {
                started.set(false)
            }
        }.start()
    }
}
