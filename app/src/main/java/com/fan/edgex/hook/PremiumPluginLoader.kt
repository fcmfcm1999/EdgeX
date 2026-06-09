package com.fan.edgex.hook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.fan.edgex.BuildConfig
import com.fan.edgex.IKeystoreVerifier
import com.fan.edgex.premium.IPremiumPlugin
import com.fan.edgex.premium.PremiumInstall
import dalvik.system.DexClassLoader
import de.robv.android.xposed.XposedBridge
import java.io.File
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object PremiumPluginLoader {
    private const val PLUGIN_CLASS = "com.fan.edgex.premium.PremiumPluginImpl"
    private const val VERIFIER_PKG = "com.fan.edgex"
    private const val VERIFIER_SVC = "com.fan.edgex.license.KeystoreVerifierService"
    private const val INITIAL_DELAY_MS = 15_000L
    private const val RETRY_DELAY_MS = 10_000L
    private const val MAX_ATTEMPTS = 8

    @Volatile private var disabledForProcess = false
    @Volatile private var pendingPlugin: IPremiumPlugin? = null
    @Volatile private var storedPubKeyBytes: ByteArray? = null
    @Volatile private var challengeAttempt = 0

    @Volatile var plugin: IPremiumPlugin? = null
        private set

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Stage 1: hash-verify and server-signature-verify the DEX before loading it.
     * Called from handleLoadPackage (no Context yet). Sets pendingPlugin on success;
     * plugin remains null until verifyDeviceBinding() passes.
     */
    fun tryLoad() {
        if (disabledForProcess || plugin != null || pendingPlugin != null) return

        val dex = File(PremiumInstall.DEX_PATH)
        val meta = File(PremiumInstall.META_PATH)
        if (!dex.isFile || !meta.isFile) return

        runCatching {
            val installMeta = PremiumInstallMetadata.verify(dex, meta, BuildConfig.DEBUG)
            if (!installMeta.localDebug) {
                require(PremiumSignatureVerifier.verifyInstallationSignature(
                    dex = dex,
                    expectedDexHash = installMeta.sha256,
                    devicePubkeyHex = installMeta.devicePubkeyHex,
                    sigBytes = installMeta.deviceSigBytes,
                )) {
                    "installation signature invalid"
                }
            }
            storedPubKeyBytes = installMeta.devicePubkeyHex.hexToByteArray()
            val parent = IPremiumPlugin::class.java.classLoader
                ?: ClassLoader.getSystemClassLoader()
            val loader = object : DexClassLoader(dex.absolutePath, null, null, parent) {
                override fun loadClass(name: String, resolve: Boolean): Class<*> {
                    findLoadedClass(name)?.let { return it }
                    return try {
                        findClass(name)
                    } catch (_: ClassNotFoundException) {
                        super.loadClass(name, resolve)
                    }
                }
            }
            val instance = loader.loadClass(PLUGIN_CLASS)
                .getDeclaredConstructor()
                .newInstance() as IPremiumPlugin
            require(instance.apiVersion == PremiumInstall.SUPPORTED_API_VERSION) {
                "unsupported apiVersion=${instance.apiVersion}"
            }
            require(
                instance.verifyInstallation(
                    dexPath = dex.absolutePath,
                    devicePubkeyHex = installMeta.devicePubkeyHex,
                    sigBytes = installMeta.deviceSigBytes,
                    localDebug = installMeta.localDebug,
                ),
            ) {
                "plugin installation verification failed"
            }
            pendingPlugin = instance
            XposedBridge.log("EdgeX: premium plugin verified and loaded, pending device binding")
        }.onFailure {
            pendingPlugin = null
            storedPubKeyBytes = null
            disabledForProcess = true
            markBad(dex, meta)
            XposedBridge.log("EdgeX: premium plugin load failed: ${it.message}")
        }
    }

    /**
     * Stage 2: schedule the Keystore challenge after the DEX has already passed
     * host-side server signature verification.
     * Called once a Context is available (InputManagerService.start()).
     */
    @Suppress("UNUSED_PARAMETER")
    fun verifyDeviceBinding(context: Context) {
        val pending = pendingPlugin ?: return

        val dex = File(PremiumInstall.DEX_PATH)
        val meta = File(PremiumInstall.META_PATH)

        runCatching {
            val metaHash = meta.readLines()
                .firstOrNull { it.startsWith("sha256=") }
                ?.substringAfter("=")?.trim()?.take(8) ?: "?"
            val cl = pending.javaClass.classLoader
            XposedBridge.log("EdgeX: verifyDeviceBinding dex=$metaHash cl=${cl?.javaClass?.simpleName}")
            val intrinsicsOk = runCatching {
                cl?.loadClass("kotlin.jvm.internal.Intrinsics"); true
            }.getOrDefault(false)
            XposedBridge.log("EdgeX: Intrinsics via plugin CL: $intrinsicsOk")
        }

        runCatching {
            require(storedPubKeyBytes != null) {
                "missing verified device pubkey"
            }
            XposedBridge.log("EdgeX: host binding verified, scheduling keystore challenge")
            scheduleChallenge(context)
        }.onFailure {
            pendingPlugin = null
            storedPubKeyBytes = null
            disabledForProcess = true
            markBad(dex, meta)
            XposedBridge.log("EdgeX: premium device binding failed (${it.javaClass.simpleName}): ${it.message}")
        }
    }

    fun disableForCurrentProcess(cause: Throwable) {
        plugin = null
        pendingPlugin = null
        disabledForProcess = true
        XposedBridge.log("EdgeX: premium plugin disabled for current process: ${cause.message}")
    }

    /**
     * Stage 3: ECDSA challenge-response via AIDL to prove the device holds the Keystore
     * private key corresponding to device_pubkey in the META file.
     * Scheduled after static binding succeeds; retried up to MAX_ATTEMPTS times.
     */
    private fun scheduleChallenge(context: Context) {
        challengeAttempt = 0
        handler.postDelayed({ attemptChallenge(context) }, INITIAL_DELAY_MS)
    }

    private fun attemptChallenge(context: Context) {
        if (pendingPlugin == null || disabledForProcess) return

        val intent = Intent().apply {
            component = ComponentName(VERIFIER_PKG, VERIFIER_SVC)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val pubKeyBytes = storedPubKeyBytes
                val success = if (pubKeyBytes == null) {
                    XposedBridge.log("EdgeX: challenge aborted — no stored pubkey")
                    false
                } else {
                    runCatching {
                        val verifier = IKeystoreVerifier.Stub.asInterface(binder)
                        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
                        val sig = verifier.sign(challenge) ?: error("null response from verifier")
                        verifyEcSig(challenge, sig, pubKeyBytes)
                    }.onFailure {
                        XposedBridge.log("EdgeX: keystore challenge error: ${it.message}")
                    }.getOrDefault(false)
                }

                runCatching { context.unbindService(this) }

                if (success) {
                    plugin = pendingPlugin
                    pendingPlugin = null
                    XposedBridge.log("EdgeX: keystore challenge passed — premium active")
                } else {
                    pendingPlugin = null
                    disabledForProcess = true
                    markBad(File(PremiumInstall.DEX_PATH), File(PremiumInstall.META_PATH))
                    XposedBridge.log("EdgeX: keystore challenge failed — premium disabled")
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {}
        }

        val bound = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)

        if (!bound) {
            challengeAttempt++
            if (challengeAttempt < MAX_ATTEMPTS) {
                XposedBridge.log("EdgeX: KeystoreVerifierService bind failed (attempt $challengeAttempt/$MAX_ATTEMPTS), retrying")
                handler.postDelayed({ attemptChallenge(context) }, RETRY_DELAY_MS)
            } else {
                XposedBridge.log("EdgeX: keystore challenge exhausted retries — premium remains inactive")
            }
        }
    }

    private fun verifyEcSig(challenge: ByteArray, sig: ByteArray, pubKeyBytes: ByteArray): Boolean =
        runCatching {
            val pubKey = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(pubKeyBytes))
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(pubKey)
                update(challenge)
                verify(sig)
            }
        }.getOrDefault(false)

    private fun markBad(dex: File, meta: File) {
        val suffix = ".bad.${System.currentTimeMillis()}"
        runCatching {
            if (dex.exists()) dex.renameTo(File(dex.parentFile, dex.name + suffix))
            if (meta.exists()) meta.renameTo(File(meta.parentFile, meta.name + suffix))
        }.onFailure {
            XposedBridge.log("EdgeX: failed to mark premium dex bad: ${it.message}")
        }
    }

    private fun String.hexToByteArray(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
