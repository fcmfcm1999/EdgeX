package com.fan.edgex.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.KeyPairGenerator
import java.security.Signature

class PremiumSignatureVerifierTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun verifyInstallationSignature_acceptsMatchingDexHashDeviceAndSignature() {
        val dex = temp.newFile("premium.dex").apply {
            writeBytes("premium-payload".toByteArray())
        }
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
        val dexHash = PremiumSignatureVerifier.sha256Hex(dex)
        val devicePubkeyHex = "ab".repeat(64)
        val signature = sign("${dexHash}|$devicePubkeyHex".toByteArray(), keyPair.private)

        val verified = PremiumSignatureVerifier.verifyInstallationSignature(
            dex = dex,
            expectedDexHash = dexHash,
            devicePubkeyHex = devicePubkeyHex,
            sigBytes = signature,
            publicKeyDerHex = keyPair.public.encoded.toHex(),
        )

        assertTrue(verified)
    }

    @Test
    fun verifyInstallationSignature_rejectsTamperedDexHash() {
        val dex = temp.newFile("premium.dex").apply {
            writeBytes("premium-payload".toByteArray())
        }
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
        val dexHash = PremiumSignatureVerifier.sha256Hex(dex)
        val devicePubkeyHex = "ab".repeat(64)
        val signature = sign("${dexHash}|$devicePubkeyHex".toByteArray(), keyPair.private)

        dex.writeBytes("tampered".toByteArray())

        val verified = PremiumSignatureVerifier.verifyInstallationSignature(
            dex = dex,
            expectedDexHash = dexHash,
            devicePubkeyHex = devicePubkeyHex,
            sigBytes = signature,
            publicKeyDerHex = keyPair.public.encoded.toHex(),
        )

        assertFalse(verified)
    }

    @Test
    fun verifyInstallationSignature_rejectsWrongDeviceBinding() {
        val dex = temp.newFile("premium.dex").apply {
            writeBytes("premium-payload".toByteArray())
        }
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
        val dexHash = PremiumSignatureVerifier.sha256Hex(dex)
        val issuedDevicePubkeyHex = "ab".repeat(64)
        val otherDevicePubkeyHex = "cd".repeat(64)
        val signature = sign("${dexHash}|$issuedDevicePubkeyHex".toByteArray(), keyPair.private)

        val verified = PremiumSignatureVerifier.verifyInstallationSignature(
            dex = dex,
            expectedDexHash = dexHash,
            devicePubkeyHex = otherDevicePubkeyHex,
            sigBytes = signature,
            publicKeyDerHex = keyPair.public.encoded.toHex(),
        )

        assertFalse(verified)
    }

    private fun sign(message: ByteArray, privateKey: java.security.PrivateKey): ByteArray =
        Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(message)
            sign()
        }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
