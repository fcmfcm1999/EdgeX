package com.fan.edgex.premium

import android.content.Context

interface IPremiumPlugin {
    val apiVersion: Int

    /**
     * Verifies that this DEX was issued by the server for the given device.
     * The DEX implementation holds the Ed25519 public key and performs the actual crypto.
     * Called from system_server after the DEX is loaded; returning false disables the plugin.
     *
     * @param dexPath absolute path to the installed DEX file (used to compute its hash)
     * @param deviceId the Android ID that was bound at activation time
     * @param sigBytes raw Ed25519 signature over sha256hex(dex)+"|"+deviceId
     */
    fun verifyInstallation(dexPath: String, deviceId: String, sigBytes: ByteArray): Boolean

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
