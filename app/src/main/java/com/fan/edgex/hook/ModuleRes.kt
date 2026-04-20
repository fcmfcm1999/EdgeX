package com.fan.edgex.hook

import android.content.res.XModuleResources
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
}
