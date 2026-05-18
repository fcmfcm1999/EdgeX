package com.fan.edgex.hook

import android.content.Context
import android.util.Base64
import com.fan.edgex.premium.IPremiumPlugin
import com.fan.edgex.premium.PremiumInstall
import dalvik.system.DexClassLoader
import de.robv.android.xposed.XposedBridge
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Properties

object PremiumPluginLoader {
    private const val PLUGIN_CLASS = "com.fan.edgex.premium.PremiumPluginImpl"
    private val SHA256_PATTERN = Regex("^[0-9a-fA-F]{64}$")

    @Volatile private var disabledForProcess = false
    @Volatile private var pendingPlugin: IPremiumPlugin? = null

    @Volatile var plugin: IPremiumPlugin? = null
        private set

    /**
     * Stage 1: load and hash-verify the DEX. Called from handleLoadPackage (no Context yet).
     * Sets pendingPlugin on success; plugin remains null until verifyDeviceBinding() passes.
     */
    fun tryLoad() {
        if (disabledForProcess || plugin != null || pendingPlugin != null) return

        val dex = File(PremiumInstall.DEX_PATH)
        val meta = File(PremiumInstall.META_PATH)
        if (!dex.isFile || !meta.isFile) return

        runCatching {
            verifyMeta(dex, meta)
            val parent = IPremiumPlugin::class.java.classLoader
                ?: ClassLoader.getSystemClassLoader()
            val loader = DexClassLoader(dex.absolutePath, null, null, parent)
            val instance = loader.loadClass(PLUGIN_CLASS)
                .getDeclaredConstructor()
                .newInstance() as IPremiumPlugin
            require(instance.apiVersion == PremiumInstall.SUPPORTED_API_VERSION) {
                "unsupported apiVersion=${instance.apiVersion}"
            }
            pendingPlugin = instance
            XposedBridge.log("EdgeX: premium plugin loaded, pending device binding")
        }.onFailure {
            pendingPlugin = null
            disabledForProcess = true
            markBad(dex, meta)
            XposedBridge.log("EdgeX: premium plugin load failed: ${it.message}")
        }
    }

    /**
     * Stage 2: verify device binding via Ed25519 signature. Called once a Context is available
     * (InputManagerService.start()). Promotes pendingPlugin to plugin on success.
     */
    @Suppress("UNUSED_PARAMETER")
    fun verifyDeviceBinding(context: Context) {
        val pending = pendingPlugin ?: return

        val dex = File(PremiumInstall.DEX_PATH)
        val meta = File(PremiumInstall.META_PATH)

        runCatching {
            val properties = Properties()
            FileInputStream(meta).use(properties::load)

            val metaDeviceId = properties.getProperty("device_id")?.trim()
                ?: error("missing device_id in meta — re-activate to apply device binding")
            val sigBase64 = properties.getProperty("device_sig")?.trim()
                ?: error("missing device_sig in meta — re-activate to apply device binding")
            val sigBytes = Base64.decode(sigBase64, Base64.NO_WRAP)

            val currentDeviceId = File(PremiumInstall.DEVICE_ID_PATH)
                .takeIf { it.isFile && it.canRead() }
                ?.readText()?.trim()
                ?: error("device_id file missing — re-activate to register this device")
            require(metaDeviceId == currentDeviceId) { "device_id mismatch" }

            require(pending.verifyInstallation(dex.absolutePath, metaDeviceId, sigBytes)) {
                "installation signature invalid"
            }

            plugin = pending
            XposedBridge.log("EdgeX: premium plugin activated")
        }.onFailure {
            plugin = null
            disabledForProcess = true
            markBad(dex, meta)
            XposedBridge.log("EdgeX: premium device binding failed: ${it.message}")
        }
        pendingPlugin = null
    }

    fun disableForCurrentProcess(cause: Throwable) {
        plugin = null
        pendingPlugin = null
        disabledForProcess = true
        XposedBridge.log("EdgeX: premium plugin disabled for current process: ${cause.message}")
    }

    private fun verifyMeta(dex: File, meta: File) {
        val properties = Properties()
        FileInputStream(meta).use(properties::load)

        val expectedVersion = properties.getProperty("version")?.toIntOrNull()
            ?: error("missing version")
        require(expectedVersion == PremiumInstall.SUPPORTED_API_VERSION) {
            "unsupported version=$expectedVersion"
        }

        val expectedSize = properties.getProperty("size")?.toLongOrNull()
            ?: error("missing size")
        require(dex.length() == expectedSize) {
            "size mismatch expected=$expectedSize actual=${dex.length()}"
        }

        val expectedHash = properties.getProperty("sha256")?.trim()
            ?: error("missing sha256")
        require(SHA256_PATTERN.matches(expectedHash)) {
            "invalid sha256"
        }

        val actualHash = sha256Hex(dex)
        require(actualHash.equals(expectedHash, ignoreCase = true)) {
            "sha256 mismatch"
        }
    }

    private fun markBad(dex: File, meta: File) {
        val suffix = ".bad.${System.currentTimeMillis()}"
        runCatching {
            if (dex.exists()) dex.renameTo(File(dex.parentFile, dex.name + suffix))
            if (meta.exists()) meta.renameTo(File(meta.parentFile, meta.name + suffix))
        }.onFailure {
            XposedBridge.log("EdgeX: failed to mark premium dex bad: ${it.message}")
        }
    }

    private fun sha256Hex(file: File): String {
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
}
