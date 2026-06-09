package com.fan.edgex.hook

import com.fan.edgex.premium.PremiumInstall
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Properties

class PremiumInstallMetadataTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun verify_acceptsLocalDebugMetadataForDebugBuild() {
        val dex = temp.newFile("premium.dex").apply { writeText("local premium") }
        val meta = writeMeta(dex, localDebug = true)

        val verified = PremiumInstallMetadata.verify(dex, meta, allowLocalDebug = true)

        assertTrue(verified.localDebug)
        assertTrue(verified.deviceSigBytes.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun verify_rejectsLocalDebugMetadataForReleaseBuild() {
        val dex = temp.newFile("premium.dex").apply { writeText("local premium") }
        val meta = writeMeta(dex, localDebug = true)

        PremiumInstallMetadata.verify(dex, meta, allowLocalDebug = false)
    }

    private fun writeMeta(dex: java.io.File, localDebug: Boolean): java.io.File =
        temp.newFile("premium.meta").also { meta ->
            Properties().apply {
                setProperty("version", PremiumInstall.SUPPORTED_API_VERSION.toString())
                setProperty("size", dex.length().toString())
                setProperty("sha256", PremiumSignatureVerifier.sha256Hex(dex))
                setProperty("device_pubkey", "ab".repeat(64))
                if (localDebug) setProperty("local_debug", "true")
            }.let { properties ->
                meta.outputStream().use { properties.store(it, null) }
            }
        }
}
