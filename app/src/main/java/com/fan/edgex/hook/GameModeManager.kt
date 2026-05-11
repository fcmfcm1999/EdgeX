package com.fan.edgex.hook

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Handler
import android.widget.Toast
import com.fan.edgex.R
import de.robv.android.xposed.XposedBridge

object GameModeManager {

    const val ACTION_DISABLE = "com.fan.edgex.ACTION_DISABLE_GAME_MODE"
    private const val CHANNEL_ID = "edgex_game_mode_2"
    private const val NOTIFICATION_ID = 7391

    @Volatile var isActive = false
        private set

    fun enable(context: Context, handler: Handler) {
        if (isActive) return
        isActive = true
        handler.post {
            try {
                Toast.makeText(context, "EdgeX: ${ModuleRes.getString(R.string.game_mode_toast_on)}", Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {}
        }
        showNotification(context)
    }

    fun disable(context: Context, handler: Handler) {
        if (!isActive) return
        isActive = false
        handler.post {
            try {
                Toast.makeText(context, "EdgeX: ${ModuleRes.getString(R.string.game_mode_toast_off)}", Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {}
        }
        cancelNotification(context)
    }

    private fun showNotification(context: Context) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            ensureChannel(nm)

            val disablePi = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_DISABLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(buildIcon())
                .setContentTitle("EdgeX")
                .setContentText(ModuleRes.getString(R.string.game_mode_notification_text))
                .setContentIntent(disablePi)
                .setDeleteIntent(disablePi)
                .setAutoCancel(true)
                .build()

            nm.notify(NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            XposedBridge.log("EdgeX: GameModeManager.showNotification failed: ${t.message}")
        }
    }

    private fun cancelNotification(context: Context) {
        try {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        } catch (t: Throwable) {
            XposedBridge.log("EdgeX: GameModeManager.cancelNotification failed: ${t.message}")
        }
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                ModuleRes.getString(R.string.game_mode_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { setShowBadge(false) }
        )
    }

    private fun buildIcon(): Icon {
        val drawable = ModuleRes.getDrawable(R.drawable.ic_game_mode)
        if (drawable != null) {
            val size = 96
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            return Icon.createWithBitmap(bmp)
        }
        return Icon.createWithResource("android", android.R.drawable.stat_sys_warning)
    }
}
