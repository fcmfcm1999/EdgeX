package com.fan.edgex.overlay

import android.content.Context

object DrawerManager {
    val frozenAppsHistory = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun showDrawer(context: Context) {
        val drawer = DrawerWindow(context)
        drawer.show()
    }
}
