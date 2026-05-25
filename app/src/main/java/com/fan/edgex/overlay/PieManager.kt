package com.fan.edgex.overlay

import android.content.Context
import de.robv.android.xposed.XposedBridge

object PieManager {
    private var activeWindow: PieWindow? = null

    fun show(context: Context, anchorX: Float, anchorY: Float, edge: String, rings: List<PieView.Ring>, accentColor: Int, sizeScale: Float) {
        if (activeWindow?.isShowing() == true) return
        val window = PieWindow(context) { activeWindow = null }
        activeWindow = window
        window.show(anchorX, anchorY, edge, rings, accentColor, sizeScale)
    }

    fun update(x: Float, y: Float) {
        activeWindow?.update(x, y)
    }

    fun commit(): String? {
        val window = activeWindow ?: return null
        activeWindow = null
        return window.commit()
    }

    fun dismiss() {
        try {
            activeWindow?.let { w ->
                if (w.isShowing()) w.dismiss()
                activeWindow = null
            }
        } catch (t: Throwable) {
            XposedBridge.log("EdgeX: PieManager.dismiss failed: ${t.message}")
            activeWindow = null
        }
    }
}
