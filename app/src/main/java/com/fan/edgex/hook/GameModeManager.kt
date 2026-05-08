package com.fan.edgex.hook

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.widget.Toast
import com.fan.edgex.BuildConfig
import com.fan.edgex.R
import de.robv.android.xposed.XposedBridge

object GameModeManager {

    const val ACTION_DISABLE = "com.fan.edgex.ACTION_DISABLE_GAME_MODE"
    private const val ACTION_SHOW_NOTIFICATION = "com.fan.edgex.GAME_MODE_SHOW_NOTIFICATION"
    private const val ACTION_CANCEL_NOTIFICATION = "com.fan.edgex.GAME_MODE_CANCEL_NOTIFICATION"

    @Volatile var isActive = false
        private set

    fun enable(context: Context, handler: Handler) {
        if (isActive) return
        isActive = true
        handler.post {
            try {
                Toast.makeText(context, ModuleRes.getString(R.string.game_mode_toast_on), Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {}
        }
        sendToApp(context, ACTION_SHOW_NOTIFICATION)
    }

    fun disable(context: Context) {
        if (!isActive) return
        isActive = false
        sendToApp(context, ACTION_CANCEL_NOTIFICATION)
    }

    private fun sendToApp(context: Context, action: String) {
        try {
            val intent = Intent(action).apply {
                setPackage(BuildConfig.APPLICATION_ID)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)
        } catch (t: Throwable) {
            XposedBridge.log("EdgeX: GameModeManager.sendToApp($action) failed: ${t.message}")
        }
    }
}
