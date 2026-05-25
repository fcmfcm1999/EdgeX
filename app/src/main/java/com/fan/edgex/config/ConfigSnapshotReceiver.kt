package com.fan.edgex.config

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ConfigSnapshotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            HookConfigSnapshot.ACTION_CONFIG_SNAPSHOT_REQUEST -> context.broadcastFullConfigSnapshot()
            HookConfigSnapshot.ACTION_HOOK_STATUS_RESPONSE -> {
                val activeAt = intent.getLongExtra(HookConfigSnapshot.EXTRA_HOOK_ACTIVE_AT, 0L)
                if (activeAt > 0L) {
                    ModuleActivationState.markActive(context, activeAt)
                }
            }
        }
    }
}
