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

    fun show(anchorX: Float, anchorY: Float, edge: String, slots: List<PieView.Slot>) {
        if (added) return
        pieView.anchorX = anchorX
        pieView.anchorY = anchorY
        pieView.edge = edge
        pieView.slots = slots

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
        pieView.highlightedIndex = pieView.hitTest(x, y)
    }

    fun commit(): String? {
        val idx = pieView.highlightedIndex
        val selected = if (idx >= 0 && idx < pieView.slots.size) pieView.slots[idx].action else null
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
