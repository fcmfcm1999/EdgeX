package com.fan.edgex.hook

import com.fan.edgex.premium.PremiumInstall
import java.io.File
import java.io.FileInputStream
import java.util.Base64
import java.util.Properties

internal object PremiumInstallMetadata {
    private val sha256Pattern = Regex("^[0-9a-fA-F]{64}$")

    fun verify(dex: File, meta: File, allowLocalDebug: Boolean): InstallMeta {
        val properties = Properties()
        FileInputStream(meta).use(properties::load)

        val version = properties.getProperty("version")?.toIntOrNull()
            ?: error("missing version")
        require(version == PremiumInstall.SUPPORTED_API_VERSION) {
            "unsupported version=$version"
        }

        val expectedSize = properties.getProperty("size")?.toLongOrNull()
            ?: error("missing size")
        require(dex.length() == expectedSize) {
            "size mismatch expected=$expectedSize actual=${dex.length()}"
        }

        val expectedHash = properties.getProperty("sha256")?.trim()
            ?: error("missing sha256")
        require(sha256Pattern.matches(expectedHash)) { "invalid sha256" }
        require(PremiumSignatureVerifier.sha256Hex(dex).equals(expectedHash, ignoreCase = true)) {
            "sha256 mismatch"
        }

        val devicePubkeyHex = properties.getProperty("device_pubkey")?.trim()
            ?: error("missing device_pubkey")
        require(devicePubkeyHex.length >= 100 && devicePubkeyHex.length % 2 == 0) {
            "invalid device_pubkey"
        }

        val localDebug = properties.getProperty("local_debug")?.trim() == "true"
        require(!localDebug || allowLocalDebug) { "local_debug requires a debug build" }
        val signature = if (localDebug) {
            ByteArray(0)
        } else {
            val encoded = properties.getProperty("device_sig")?.trim()
                ?: error("missing device_sig")
            Base64.getDecoder().decode(encoded)
        }

        return InstallMeta(
            sha256 = expectedHash.lowercase(),
            devicePubkeyHex = devicePubkeyHex,
            deviceSigBytes = signature,
            localDebug = localDebug,
        )
    }

    data class InstallMeta(
        val sha256: String,
        val devicePubkeyHex: String,
        val deviceSigBytes: ByteArray,
        val localDebug: Boolean,
    )
}
