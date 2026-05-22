package com.fan.edgex.hook

import android.content.Context

object PremiumRuntime {
    fun isActive(): Boolean =
        PremiumPluginLoader.plugin != null

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

    fun onFluidEffectDown(
        context: Context,
        edge: String,
        touchX: Float,
        touchY: Float,
        screenWidth: Float,
        screenHeight: Float,
        color: Int,
        sizeProgress: Int,
        alpha: Float,
    ): Boolean {
        val plugin = PremiumPluginLoader.plugin ?: return false
        return runCatching {
            plugin.onFluidEffectDown(
                context,
                edge,
                touchX,
                touchY,
                screenWidth,
                screenHeight,
                color,
                sizeProgress,
                alpha,
            )
        }.getOrElse {
            PremiumPluginLoader.disableForCurrentProcess(it)
            false
        }
    }

    fun onFluidEffectMove(touchX: Float, touchY: Float): Boolean {
        val plugin = PremiumPluginLoader.plugin ?: return false
        return runCatching {
            plugin.onFluidEffectMove(touchX, touchY)
        }.getOrElse {
            PremiumPluginLoader.disableForCurrentProcess(it)
            false
        }
    }

    fun onFluidEffectUp(onComplete: Runnable? = null): Boolean {
        val plugin = PremiumPluginLoader.plugin ?: return false
        return runCatching {
            plugin.onFluidEffectUp(onComplete)
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

    fun dismissEdgeLighting() {
        val plugin = PremiumPluginLoader.plugin ?: return
        runCatching {
            plugin.onScreenOff()
        }.onFailure {
            PremiumPluginLoader.disableForCurrentProcess(it)
        }
    }
}
