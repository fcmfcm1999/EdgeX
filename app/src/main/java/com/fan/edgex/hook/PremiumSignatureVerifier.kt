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
            "96ff05c9bf77ae7b60ca7a1af426070bcef91d59ec38850b2134660c159ee503" +
            "e960d8c02e3918f9ded31518247914fd787ae512cf750dea1fceb0c0e7bbf0f0" +
            "d35bbcc3d599247596b355f0c8e563cb079ac1266020e00f5750c80ab242aa9a" +
            "59069434913e477e39e286d3bc4ef526a4dd074c924df77ec34b9a3321025c0f" +
            "71daebf584adf35f02ccceba455034ecf1b6d571844a7c7b8baf8127314f9111" +
            "95a7a6bbc3d0cb8cc70412ee61ccfbe0e546b217ae7afabf127cbe059620388" +
            "5e83aaf33a55498d7f5cfb6e982845f3ee40c2448635f52b18296f7c47cb69b" +
            "ae37dce10237e83b4d58cdeea36d9e67746d1e633f2234a068b7041a6674e17" +
            "14f0203010001"

    fun verifyInstallationSignature(
        dex: File,
        expectedDexHash: String,
        devicePubkeyHex: String,
        sigBytes: ByteArray,
    ): Boolean = runCatching {
        val actualHash = sha256Hex(dex)
        require(actualHash.equals(expectedDexHash, ignoreCase = true)) {
            "sha256 mismatch"
        }

        val message = "${actualHash.lowercase()}|$devicePubkeyHex"
            .toByteArray(Charsets.UTF_8)
        Signature.getInstance("SHA256withRSA").run {
            initVerify(rsaPublicKey())
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

    private fun rsaPublicKey() = KeyFactory.getInstance("RSA").generatePublic(
        X509EncodedKeySpec(RSA_PUBLIC_KEY_DER_HEX.hexToByteArray()),
    )

    private fun String.hexToByteArray(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
