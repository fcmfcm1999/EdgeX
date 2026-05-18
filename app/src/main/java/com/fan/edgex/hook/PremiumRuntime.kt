package com.fan.edgex.hook

import android.content.Context

object PremiumRuntime {
    fun showEdgeLighting(
        context: Context,
        effect: String,
        color: Int,
        durationMs: Int,
        widthDp: Int,
        alpha: Float,
    ): Boolean {
        val plugin = PremiumPluginLoader.plugin ?: return false
        return runCatching {
            plugin.onEdgeLightingShow(context, effect, color, durationMs, widthDp, alpha)
        }.getOrElse {
            PremiumPluginLoader.disableForCurrentProcess(it)
            false
        }
    }

    fun onScreenOff() {
        val plugin = PremiumPluginLoader.plugin ?: return
        runCatching {
            plugin.onScreenOff()
        }.onFailure {
            PremiumPluginLoader.disableForCurrentProcess(it)
        }
    }
}
