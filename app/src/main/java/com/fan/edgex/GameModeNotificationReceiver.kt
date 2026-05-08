package com.fan.edgex

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GameModeNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SHOW -> showNotification(context)
            ACTION_CANCEL -> cancelNotification(context)
        }
    }

    private fun showNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(nm, context)

        val disablePi = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_DISABLE_GAME_MODE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_game_mode)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.game_mode_notification_text))
            .setContentIntent(disablePi)
            .setDeleteIntent(disablePi)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun cancelNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(nm: NotificationManager, context: Context) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.game_mode_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { setShowBadge(false) }
        )
    }

    companion object {
        const val ACTION_SHOW = "com.fan.edgex.GAME_MODE_SHOW_NOTIFICATION"
        const val ACTION_CANCEL = "com.fan.edgex.GAME_MODE_CANCEL_NOTIFICATION"

        private const val ACTION_DISABLE_GAME_MODE = "com.fan.edgex.ACTION_DISABLE_GAME_MODE"
        private const val CHANNEL_ID = "edgex_game_mode"
        private const val NOTIFICATION_ID = 7391
    }
}
