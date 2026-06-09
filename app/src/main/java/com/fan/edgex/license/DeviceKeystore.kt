package com.fan.edgex.license

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

object DeviceKeystore {
    private const val TAG = "EdgeX.DeviceKeystore"
    private const val KEY_ALIAS = "edgex_premium_key"
    private const val PROVIDER = "AndroidKeyStore"

    fun getOrCreatePublicKeyBytes(): ByteArray {
        val ks = KeyStore.getInstance(PROVIDER).also { it.load(null) }
        if (!ks.containsAlias(KEY_ALIAS)) generate(ks)
        val certificate = ks.getCertificate(KEY_ALIAS)
        if (certificate == null) {
            ks.deleteEntry(KEY_ALIAS)
            generate(ks)
        }
        return checkNotNull(ks.getCertificate(KEY_ALIAS)) {
            "Android Keystore did not return the generated certificate"
        }.publicKey.encoded
    }

    fun sign(challenge: ByteArray): ByteArray {
        val ks = KeyStore.getInstance(PROVIDER).also { it.load(null) }
        val privateKey = ks.getKey(KEY_ALIAS, null) as? PrivateKey
            ?: error("Keystore key not found — re-activate premium")
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(challenge)
            sign()
        }
    }

    private fun generate(ks: KeyStore) {
        // Prefer StrongBox (physically isolated secure element); fall back to TEE.
        try {
            generateKeyPair(strongBoxBacked = true)
        } catch (strongBoxError: Exception) {
            Log.w(TAG, "StrongBox key generation failed; falling back to TEE", strongBoxError)
            runCatching { ks.deleteEntry(KEY_ALIAS) }
                .onFailure { Log.w(TAG, "Failed to remove partial StrongBox key", it) }
            generateKeyPair(strongBoxBacked = false)
        }
    }

    private fun generateKeyPair(strongBoxBacked: Boolean) {
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setIsStrongBoxBacked(strongBoxBacked)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER).run {
            initialize(spec)
            generateKeyPair()
        }
    }
}
