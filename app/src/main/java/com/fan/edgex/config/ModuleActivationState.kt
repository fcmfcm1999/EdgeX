package com.fan.edgex.config

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.fan.edgex.BuildConfig

object ModuleActivationState {
    private const val PREFS_NAME = "module_activation"
    private const val KEY_ACTIVE_AT = "active_at"

    fun markActive(context: Context, activeAt: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_ACTIVE_AT, activeAt)
            .apply()
    }

    fun isActive(context: Context): Boolean {
        val activeAt = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_ACTIVE_AT, 0L)
        val bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        return activeAt > bootTime
    }

    fun requestRefresh(context: Context) {
        context.sendBroadcast(Intent(HookConfigSnapshot.ACTION_HOOK_STATUS_REQUEST).apply {
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        })
    }

    fun responseIntent(activeAt: Long): Intent =
        Intent(HookConfigSnapshot.ACTION_HOOK_STATUS_RESPONSE).apply {
            component = ComponentName(
                BuildConfig.APPLICATION_ID,
                "${BuildConfig.APPLICATION_ID}.config.ConfigSnapshotReceiver",
            )
            setPackage(BuildConfig.APPLICATION_ID)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            putExtra(HookConfigSnapshot.EXTRA_HOOK_ACTIVE_AT, activeAt)
        }
}
