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
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.widget.Toast
import com.fan.edgex.R
import de.robv.android.xposed.XposedBridge

object FlashlightManager {

    const val ACTION_TURN_OFF = "com.fan.edgex.ACTION_TURN_OFF_FLASHLIGHT"
    private const val NOTIFICATION_ID = 7392
    private const val CHANNEL_ID = "edgex_flashlight"

    @Volatile private var torchOn = false
    @Volatile private var cameraId: String? = null

    fun initialize(context: Context, handler: Handler) {
        try {
            val cm = context.getSystemService(CameraManager::class.java) ?: return
            resolveBackCamera(cm)
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(id: String, enabled: Boolean) {
                    if (id == cameraId) torchOn = enabled
                }
                override fun onTorchModeUnavailable(id: String) {
                    if (id == cameraId) torchOn = false
                }
            }, handler)
        } catch (t: Throwable) {
            XposedBridge.log("EdgeX: FlashlightManager.initialize failed: ${t.message}")
        }
    }

    fun toggle(context: Context, handler: Handler) {
        try {
            val cm = context.getSystemService(CameraManager::class.java) ?: return
            val id = cameraId ?: resolveBackCamera(cm) ?: return
            val target = !torchOn
            cm.setTorchMode(id, target)
            if (target) {
                handler.post { toast(context, ModuleRes.getString(R.string.flashlight_toast_on)) }
                showNotification(context)
            } else {
                handler.post { toast(context, ModuleRes.getString(R.string.flashlight_toast_off)) }
                cancelNotification(context)
            }
        } catch (t: Throwable) {
            XposedBridge.log("EdgeX: FlashlightManager.toggle failed: ${t.message}")
        }
    }

    fun turnOff(context: Context, handler: Handler) {
        try {
            val cm = context.getSystemService(CameraManager::class.java) ?: return
            val id = cameraId ?: resolveBackCamera(cm) ?: return
            cm.setTorchMode(id, false)
            handler.post { toast(context, ModuleRes.getString(R.string.flashlight_toast_off)) }
            cancelNotification(context)
        } catch (t: Throwable) {
            XposedBridge.log("EdgeX: FlashlightManager.turnOff failed: ${t.message}")
        }
    }

    private fun toast(context: Context, text: String) {
        try { Toast.makeText(context, "EdgeX: $text", Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
    }

    private fun showNotification(context: Context) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            ensureChannel(nm)
            val pi = PendingIntent.getBroadcast(
                context, 0,
                Intent(ACTION_TURN_OFF),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(buildIcon())
                .setContentTitle("EdgeX")
                .setContentText(ModuleRes.getString(R.string.flashlight_notification_text))
                .setContentIntent(pi)
                .setDeleteIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            XposedBridge.log("EdgeX: FlashlightManager.showNotification failed: ${t.message}")
        }
    }

    private fun cancelNotification(context: Context) {
        try {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        } catch (t: Throwable) {
            XposedBridge.log("EdgeX: FlashlightManager.cancelNotification failed: ${t.message}")
        }
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                ModuleRes.getString(R.string.flashlight_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { setShowBadge(false) }
        )
    }

    private fun buildIcon(): Icon {
        val drawable = ModuleRes.getDrawable(R.drawable.ic_flashlight)
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

    private fun resolveBackCamera(cm: CameraManager): String? {
        if (cameraId != null) return cameraId
        for (id in cm.cameraIdList) {
            try {
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id; return id
                }
            } catch (_: Throwable) {}
        }
        for (id in cm.cameraIdList) {
            try {
                if (cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                    cameraId = id; return id
                }
            } catch (_: Throwable) {}
        }
        return null
    }
}
