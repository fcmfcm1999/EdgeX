package com.fan.edgex.license

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

object DeviceKeystore {
    private const val KEY_ALIAS = "edgex_premium_key"
    private const val PROVIDER = "AndroidKeyStore"

    fun getOrCreatePublicKeyBytes(): ByteArray {
        val ks = KeyStore.getInstance(PROVIDER).also { it.load(null) }
        if (!ks.containsAlias(KEY_ALIAS)) generate(ks)
        return ks.getCertificate(KEY_ALIAS).publicKey.encoded
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
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER)
        val specBuilder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)

        // Prefer StrongBox (physically isolated secure element); fall back to TEE.
        try {
            kpg.initialize(specBuilder.setIsStrongBoxBacked(true).build())
            kpg.generateKeyPair()
        } catch (_: StrongBoxUnavailableException) {
            kpg.initialize(specBuilder.setIsStrongBoxBacked(false).build())
            kpg.generateKeyPair()
        }
    }
}
