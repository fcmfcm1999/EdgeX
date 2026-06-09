package com.fan.edgex.premium

import android.content.Context

interface IPremiumPlugin {
    val apiVersion: Int

    /**
     * Verifies that this DEX was issued by the server for the given device.
     * The DEX implementation holds the RSA public key and performs the actual crypto.
     * Called from system_server after the DEX is loaded; returning false disables the plugin.
     *
     * @param dexPath absolute path to the installed DEX file (used to compute its hash)
     * @param devicePubkeyHex hex X.509 encoding of the device's Keystore EC public key
     * @param sigBytes raw RSA/SHA-256 signature over sha256hex(dex)+"|"+devicePubkeyHex
     * @param localDebug true only for a debug host that already verified local_debug metadata
     */
    fun verifyInstallation(
        dexPath: String,
        devicePubkeyHex: String,
        sigBytes: ByteArray,
        localDebug: Boolean,
    ): Boolean

    fun onEdgeLightingShow(
        context: Context,
        effect: String,
        color: Int,
        durationMs: Int,
        widthDp: Int,
        alpha: Float,
    ): Boolean

    fun onEdgeLightingDismiss()

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
    ): Boolean

    fun onFluidEffectMove(touchX: Float, touchY: Float): Boolean

    fun onFluidEffectUp(onComplete: Runnable?): Boolean

    fun onScreenOff()
}
