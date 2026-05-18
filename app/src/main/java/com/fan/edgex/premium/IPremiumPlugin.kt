package com.fan.edgex.premium

import android.content.Context

interface IPremiumPlugin {
    val apiVersion: Int

    fun onEdgeLightingShow(
        context: Context,
        effect: String,
        color: Int,
        durationMs: Int,
        widthDp: Int,
        alpha: Float,
    ): Boolean

    fun onScreenOff()
}
