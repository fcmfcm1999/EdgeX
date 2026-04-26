package com.fan.edgex.overlay

import android.content.Context

object DrawerManager {
    private var activeDrawer: DrawerWindow? = null

    fun showDrawer(context: Context, resolveConfig: (String) -> String) {
        if (activeDrawer?.isShowing() == true) return
        val drawer = DrawerWindow(context, resolveConfig) { activeDrawer = null }
        activeDrawer = drawer
        drawer.show()
    }

    /**
     * Force-dismiss the active drawer, e.g. on SCREEN_OFF.
     * Prevents drawer overlay from blocking touch after unlock.
     */
    fun dismissDrawer() {
        try {
            activeDrawer?.let {
                if (it.isShowing()) {
                    it.forceDismiss()
                }
                activeDrawer = null
            }
        } catch (t: Throwable) {
            de.robv.android.xposed.XposedBridge.log("EdgeX: DrawerManager.dismissDrawer failed: ${t.message}")
            activeDrawer = null
        }
    }
}
