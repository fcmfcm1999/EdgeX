package com.fan.edgex.license

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.fan.edgex.BuildConfig
import com.fan.edgex.premium.PremiumInstall
import com.topjohnwu.superuser.Shell
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant

object PremiumActivator {
    private const val PREFS_NAME = "premium_activation"
    private const val KEY_ACTIVATION_CODE = "activation_code"
    private const val KEY_INSTALLED = "installed"
    private const val KEY_INSTALLED_AT_MS = "installed_at_ms"
    private const val KEY_INSTALL_BOOT_COUNT = "install_boot_count"
    private const val KEY_INSTALLED_DEX_HASH = "installed_dex_hash"
    private const val KEY_INSTALLED_DEX_VERSION = "installed_dex_version"
    private const val KEY_DEACTIVATED = "deactivated"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000

    fun activate(context: Context, code: String): Result<Unit> = runCatching {
        val normalizedCode = normalizeCode(code)
        require(normalizedCode.isNotEmpty()) { "Activation code is empty" }

        val activation = requestActivation(normalizedCode)
        downloadAndInstall(context, dexBytes = downloadDex(activation), activation)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVATION_CODE, normalizedCode)
            .putBoolean(KEY_INSTALLED, true)
            .putLong(KEY_INSTALLED_AT_MS, System.currentTimeMillis())
            .putInt(KEY_INSTALL_BOOT_COUNT, bootCount(context))
            .putString(KEY_INSTALLED_DEX_HASH, activation.dexHash)
            .putInt(KEY_INSTALLED_DEX_VERSION, activation.dexVersion)
            .putBoolean(KEY_DEACTIVATED, false)
            .apply()
    }

    fun deactivate(context: Context): Result<Unit> = runCatching {
        val code = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVATION_CODE, null)

        if (apiBaseUrls().isNotEmpty() && !code.isNullOrEmpty()) {
            val body = JSONObject()
                .put("code", code)
                .put("device_pubkey", devicePubkeyHex())
                .toString()
            withApiFallback { baseUrl ->
                postOk("$baseUrl/api/unbind", body)
            }
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACTIVATION_CODE)
            .putBoolean(KEY_INSTALLED, false)
            .remove(KEY_INSTALLED_AT_MS)
            .putBoolean(KEY_DEACTIVATED, true)
            .apply()

        Shell.cmd(
            "rm -f ${PremiumInstall.DEX_PATH} ${PremiumInstall.META_PATH} ${PremiumInstall.LEGACY_DEVICE_ID_PATH}"
        ).exec()
    }

    fun getActivationCode(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVATION_CODE, null)

    data class DexInfo(val apiVersion: Int, val hashPrefix: String, val installedAtMs: Long)

    fun getDexInfo(context: Context): DexInfo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hash = prefs.getString(KEY_INSTALLED_DEX_HASH, null)?.take(8) ?: return null
        val apiVersion = prefs.getInt(KEY_INSTALLED_DEX_VERSION, PremiumInstall.SUPPORTED_API_VERSION)
        val ts = prefs.getLong(KEY_INSTALLED_AT_MS, 0L).takeIf { it > 0 } ?: return null
        return DexInfo(apiVersion, hash, ts)
    }

    data class DexUpdateInfo(val apiVersion: Int, val hashPrefix: String)

    sealed class DexUpdateStatus {
        data object NotInstalled : DexUpdateStatus()
        data object MissingActivationCode : DexUpdateStatus()
        data object UpToDate : DexUpdateStatus()
        data class Available(val info: DexUpdateInfo) : DexUpdateStatus()
    }

    fun checkInstalledDexUpdate(context: Context): Result<DexUpdateStatus> = runCatching {
        if (!isInstalled(context)) return@runCatching DexUpdateStatus.NotInstalled

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_ACTIVATION_CODE, null)?.let(::normalizeCode).orEmpty()
        if (code.isEmpty()) return@runCatching DexUpdateStatus.MissingActivationCode

        val activation = requestActivation(code)
        val installedHash = prefs.getString(KEY_INSTALLED_DEX_HASH, null)
        if (installedHash.equals(activation.dexHash, ignoreCase = true)) {
            DexUpdateStatus.UpToDate
        } else {
            DexUpdateStatus.Available(
                DexUpdateInfo(
                    apiVersion = activation.dexVersion,
                    hashPrefix = activation.dexHash.take(8),
                ),
            )
        }
    }

    fun updateInstalledDexIfNeeded(context: Context): Result<UpdateResult> = runCatching {
        if (!isInstalled(context)) return@runCatching UpdateResult.NotInstalled

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_ACTIVATION_CODE, null)?.let(::normalizeCode).orEmpty()
        if (code.isEmpty()) return@runCatching UpdateResult.SkippedMissingActivationCode

        val activation = requestActivation(code)
        val installedHash = prefs.getString(KEY_INSTALLED_DEX_HASH, null)
        if (installedHash.equals(activation.dexHash, ignoreCase = true)) {
            return@runCatching UpdateResult.UpToDate
        }

        downloadAndInstall(context, downloadDex(activation), activation)

        prefs.edit()
            .putBoolean(KEY_INSTALLED, true)
            .putLong(KEY_INSTALLED_AT_MS, System.currentTimeMillis())
            .putInt(KEY_INSTALL_BOOT_COUNT, bootCount(context))
            .putString(KEY_INSTALLED_DEX_HASH, activation.dexHash)
            .putInt(KEY_INSTALLED_DEX_VERSION, activation.dexVersion)
            .apply()
        UpdateResult.Updated
    }

    fun isInstalled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DEACTIVATED, false)) return false
        return prefs.getBoolean(KEY_INSTALLED, false) || File(PremiumInstall.META_PATH).isFile
    }

    fun status(context: Context): Status {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DEACTIVATED, false)) return Status.NotActivated
        val installed = prefs.getBoolean(KEY_INSTALLED, false) || File(PremiumInstall.META_PATH).isFile
        if (!installed) return Status.NotActivated

        val installedAtMs = prefs.getLong(KEY_INSTALLED_AT_MS, 0L)
        if (installedAtMs <= 0L) return Status.Installed

        val savedBootCount = prefs.getInt(KEY_INSTALL_BOOT_COUNT, -1)
        if (savedBootCount >= 0) {
            return if (bootCount(context) > savedBootCount) Status.Installed else Status.RebootRequired
        }

        val bootWallClockMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        return if (installedAtMs > bootWallClockMs) Status.RebootRequired else Status.Installed
    }

    enum class Status { NotActivated, RebootRequired, Installed }

    enum class UpdateResult { NotInstalled, SkippedMissingActivationCode, UpToDate, Updated }

    private data class ActivationResponse(
        val token: String,
        val dexHash: String,
        val dexVersion: Int,
        val deviceSig: String,
        val baseUrl: String,
    )

    private fun requestActivation(code: String): ActivationResponse {
        require(apiBaseUrls().isNotEmpty()) { "Premium API URL is not configured" }

        val activateBody = JSONObject()
            .put("code", code)
            .put("device_pubkey", devicePubkeyHex())
            .toString()

        val (baseUrl, activateResponse) = withApiFallbackWithBase { baseUrl ->
            postJson("$baseUrl/api/activate", activateBody)
        }
        val token = activateResponse.getString("token")
        val expectedHash = activateResponse.getString("dex_hash").lowercase()
        val dexVersion = activateResponse.optInt("dex_version", PremiumInstall.SUPPORTED_API_VERSION)
        val deviceSig = activateResponse.getString("device_sig")

        require(dexVersion == PremiumInstall.SUPPORTED_API_VERSION) {
            "Unsupported premium version: $dexVersion"
        }

        return ActivationResponse(token, expectedHash, dexVersion, deviceSig, baseUrl)
    }

    private fun downloadDex(activation: ActivationResponse): ByteArray {
        val dexBytes = withApiFallback(preferredBaseUrl = activation.baseUrl) { baseUrl ->
            getBytes("$baseUrl/api/download/dex?token=${urlEncode(activation.token)}")
        }
        val actualHash = sha256Hex(dexBytes)
        require(actualHash == activation.dexHash) { "Downloaded premium DEX hash mismatch" }
        return dexBytes
    }

    private fun downloadAndInstall(context: Context, dexBytes: ByteArray, activation: ActivationResponse) {
        val tempDex = File(context.cacheDir, "premium.dex.tmp")
        val tempMeta = File(context.cacheDir, "premium.meta.tmp")
        val pubkeyHex = devicePubkeyHex()

        tempDex.writeBytes(dexBytes)
        tempMeta.writeText(
            buildString {
                appendLine("version=${activation.dexVersion}")
                appendLine("sha256=${activation.dexHash}")
                appendLine("size=${dexBytes.size}")
                appendLine("installed_at=${Instant.now()}")
                appendLine("device_pubkey=$pubkeyHex")
                appendLine("device_sig=${activation.deviceSig}")
            },
        )

        val installScript = """
            set -e
            mkdir -p ${PremiumInstall.DIR_PATH}
            cp ${shellQuote(tempDex.absolutePath)} ${PremiumInstall.DEX_PATH}.tmp
            cp ${shellQuote(tempMeta.absolutePath)} ${PremiumInstall.META_PATH}.tmp
            chown system:system ${PremiumInstall.DIR_PATH}
            chown system:system ${PremiumInstall.DEX_PATH}.tmp ${PremiumInstall.META_PATH}.tmp
            chmod 0755 ${PremiumInstall.DIR_PATH}
            chmod 0444 ${PremiumInstall.DEX_PATH}.tmp ${PremiumInstall.META_PATH}.tmp
            mv -f ${PremiumInstall.DEX_PATH}.tmp ${PremiumInstall.DEX_PATH}
            mv -f ${PremiumInstall.META_PATH}.tmp ${PremiumInstall.META_PATH}
            chown system:system ${PremiumInstall.DEX_PATH} ${PremiumInstall.META_PATH}
            chmod 0444 ${PremiumInstall.DEX_PATH} ${PremiumInstall.META_PATH}
            rm -f ${PremiumInstall.LEGACY_DEVICE_ID_PATH}
        """.trimIndent()
        val result = Shell.cmd("sh -c ${shellQuote(installScript)}").exec()

        tempDex.delete()
        tempMeta.delete()

        check(result.isSuccess) {
            result.err.joinToString("\n").ifBlank { "Root install failed" }
        }
    }

    private fun devicePubkeyHex(): String =
        DeviceKeystore.getOrCreatePublicKeyBytes()
            .joinToString("") { "%02x".format(it) }

    private fun bootCount(context: Context): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    private fun apiBaseUrls(preferredBaseUrl: String? = null): List<String> {
        val configured = BuildConfig.PREMIUM_API_URLS
            .split(',')
            .map { it.trim().trimEnd('/') }
            .filter { it.isNotEmpty() }
        val ordered = buildList {
            preferredBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }?.let(::add)
            addAll(configured)
        }
        return ordered.distinct()
    }

    private inline fun <T> withApiFallback(
        preferredBaseUrl: String? = null,
        block: (String) -> T,
    ): T =
        withApiFallbackWithBase(preferredBaseUrl, block).second

    private inline fun <T> withApiFallbackWithBase(
        preferredBaseUrl: String? = null,
        block: (String) -> T,
    ): Pair<String, T> {
        val urls = apiBaseUrls(preferredBaseUrl)
        require(urls.isNotEmpty()) { "Premium API URL is not configured" }

        var fallbackFailure: Throwable? = null
        for (baseUrl in urls) {
            try {
                return baseUrl to block(baseUrl)
            } catch (throwable: Throwable) {
                if (!shouldTryNextApi(throwable)) throw throwable
                if (fallbackFailure == null) fallbackFailure = throwable
            }
        }
        throw fallbackFailure ?: error("Premium API URL is not configured")
    }

    private fun shouldTryNextApi(throwable: Throwable): Boolean =
        throwable is IOException ||
            (throwable is HttpStatusException && throwable.statusCode >= 500)

    private fun postJson(url: String, body: String): JSONObject {
        val connection = openConnection(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return JSONObject(readResponse(connection))
    }

    private fun postOk(url: String, body: String) {
        val connection = openConnection(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        if (code !in 200..299) {
            val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw HttpStatusException(code, "Request failed ($code): $message")
        }
    }

    private fun getBytes(url: String): ByteArray {
        val connection = openConnection(url).apply { requestMethod = "GET" }
        val code = connection.responseCode
        if (code !in 200..299) {
            val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw HttpStatusException(code, "Download failed ($code): $message")
        }
        return connection.inputStream.use { it.readBytes() }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw HttpStatusException(code, "Activation failed ($code): $body")
        return body
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun normalizeCode(code: String): String = code.trim().uppercase()

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private class HttpStatusException(
        val statusCode: Int,
        message: String,
    ) : RuntimeException(message)
}
