package com.fan.edgex.hook

import android.content.res.XModuleResources
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

object ModuleRes {
    private var res: XModuleResources? = null

    fun init(modulePath: String) {
        res = XModuleResources.createInstance(modulePath, null)
    }

    fun getString(@StringRes id: Int, vararg args: Any?): String {
        val r = res ?: return ""
        val raw = r.getString(id)
        return if (args.isEmpty()) raw else String.format(raw, *args)
    }

    fun getDrawable(@DrawableRes id: Int): Drawable? =
        runCatching { res?.getDrawable(id) }.getOrNull()
}
