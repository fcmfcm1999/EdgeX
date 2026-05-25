package com.fan.edgex.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.WindowManager
import de.robv.android.xposed.XposedBridge

class PieWindow(
    private val context: Context,
    private val onDismiss: () -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val pieView = PieView(context)
    private var added = false

    fun show(anchorX: Float, anchorY: Float, edge: String, rings: List<PieView.Ring>, accentColor: Int, sizeScale: Float) {
        if (added) return
        pieView.anchorX = anchorX
        pieView.anchorY = anchorY
        pieView.edge = edge
        pieView.rings = rings
        pieView.accentColor = accentColor
        pieView.sizeScale = sizeScale

        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )

        try {
            windowManager.addView(pieView, params)
            added = true
            pieView.animateIn()
        } catch (t: Throwable) {
            XposedBridge.log("EdgeX: PieWindow.show failed: ${t.message}")
            onDismiss()
        }
    }

    fun update(x: Float, y: Float) {
        if (!added) return
        val hit = pieView.hitTest(x, y)
        pieView.highlightedRing = hit?.first ?: -1
        pieView.highlightedSlot = hit?.second ?: -1
    }

    fun commit(): String? {
        val r = pieView.highlightedRing
        val s = pieView.highlightedSlot
        val selected = if (pieView.isAnimationComplete() && r >= 0 && s >= 0)
            pieView.rings.getOrNull(r)?.slots?.getOrNull(s)?.action
        else null
        dismiss()
        return selected
    }

    fun dismiss() {
        if (!added) return
        added = false
        try {
            windowManager.removeView(pieView)
        } catch (t: Throwable) {
            XposedBridge.log("EdgeX: PieWindow.dismiss failed: ${t.message}")
        }
        onDismiss()
    }

    fun isShowing() = added
}
