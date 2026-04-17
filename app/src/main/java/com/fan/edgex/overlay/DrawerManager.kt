package com.fan.edgex.overlay

import android.content.Context

object DrawerManager {
    val frozenAppsHistory = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private var activeDrawer: DrawerWindow? = null

    fun showDrawer(context: Context) {
        if (activeDrawer?.isShowing() == true) return
        val drawer = DrawerWindow(context) { activeDrawer = null }
        activeDrawer = drawer
        drawer.show()
    }
}
