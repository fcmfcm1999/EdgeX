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
        }
    }
}
