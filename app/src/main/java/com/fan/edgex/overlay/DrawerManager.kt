package com.fan.edgex.overlay

import android.content.Context

object DrawerManager {
    fun showDrawer(context: Context) {
        val drawer = DrawerWindow(context)
        drawer.show()
    }
}
