package com.fan.edgex.hook

import java.io.File
import java.io.FileInputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object PremiumSignatureVerifier {
    private const val RSA_PUBLIC_KEY_DER_HEX =
        "30820122300d06092a864886f70d01010105000382010f003082010a0282010100" +
            "ce29f8eed32e307c8d8f1012925c7a5ca1a9046dbdbd45f95e20e6c019c8fb" +
            "8f774be35a42470bea7b45c1fa2e6c484984f7338d92ee0dcd3676d65c9a212" +
            "9f1c32b1aabd3c4f99828752bba2c66c62d2b3d05984f73f13bf4ed24e184bc" +
            "89c4cc1710dad90cfebd72775821cc38d732c68b17a023909b87c11df0de6ae" +
            "80e617c63268a7c768194dea6447afb095d3356bd8cf2978878f672576000daf" +
            "e64cb684e361cd6019fbb6d33521cd9d2b2e56940ac4edb97fc2730485e659f" +
            "098f8c551065ef0675c904d3c8b1a3ca7f74d787c7f381f3f9fe367c5c302ae" +
            "b3cdcc537f2be506e9a50b9ab1048915d719968c799874b0f6c946006ede4c" +
            "0f21b761b0203010001"

    fun verifyInstallationSignature(
        dex: File,
        expectedDexHash: String,
        devicePubkeyHex: String,
        sigBytes: ByteArray,
    ): Boolean = verifyInstallationSignature(
        dex = dex,
        expectedDexHash = expectedDexHash,
        devicePubkeyHex = devicePubkeyHex,
        sigBytes = sigBytes,
        publicKeyDerHex = RSA_PUBLIC_KEY_DER_HEX,
    )

    internal fun verifyInstallationSignature(
        dex: File,
        expectedDexHash: String,
        devicePubkeyHex: String,
        sigBytes: ByteArray,
        publicKeyDerHex: String,
    ): Boolean = runCatching {
        val actualHash = sha256Hex(dex)
        require(actualHash.equals(expectedDexHash, ignoreCase = true)) {
            "sha256 mismatch"
        }

        val message = "${actualHash.lowercase()}|$devicePubkeyHex"
            .toByteArray(Charsets.UTF_8)
        Signature.getInstance("SHA256withRSA").run {
            initVerify(rsaPublicKey(publicKeyDerHex))
            update(message)
            verify(sigBytes)
        }
    }.getOrDefault(false)

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun rsaPublicKey(publicKeyDerHex: String) = KeyFactory.getInstance("RSA").generatePublic(
        X509EncodedKeySpec(publicKeyDerHex.hexToByteArray()),
    )

    private fun String.hexToByteArray(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
