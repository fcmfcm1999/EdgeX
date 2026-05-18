package com.fan.edgex.license

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.fan.edgex.BuildConfig
import com.fan.edgex.premium.PremiumInstall
import com.topjohnwu.superuser.Shell
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

object PremiumActivator {
    private const val PREFS_NAME = "premium_activation"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_INSTALLED = "installed"
    private const val KEY_INSTALLED_AT_MS = "installed_at_ms"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000

    fun activate(context: Context, code: String): Result<Unit> = runCatching {
        val normalizedCode = code.trim()
        require(normalizedCode.isNotEmpty()) { "Activation code is empty" }
        val workerUrl = BuildConfig.PREMIUM_WORKER_URL.trimEnd('/')
        require(workerUrl.isNotEmpty()) { "Premium worker URL is not configured" }

        val deviceId = deviceId(context)
        val activateBody = JSONObject()
            .put("code", normalizedCode)
            .put("device_id", deviceId)
            .toString()

        val activateResponse = postJson("$workerUrl/activate", activateBody)
        val token = activateResponse.getString("token")
        val expectedHash = activateResponse.getString("dex_hash").lowercase()
        val dexVersion = activateResponse.optInt("dex_version", PremiumInstall.SUPPORTED_API_VERSION)

        require(dexVersion == PremiumInstall.SUPPORTED_API_VERSION) {
            "Unsupported premium version: $dexVersion"
        }

        val dexBytes = getBytes("$workerUrl/download?token=${urlEncode(token)}")
        val actualHash = sha256Hex(dexBytes)
        require(actualHash == expectedHash) { "Downloaded premium DEX hash mismatch" }

        installDex(context, dexBytes, actualHash, dexVersion)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_INSTALLED, true)
            .putLong(KEY_INSTALLED_AT_MS, System.currentTimeMillis())
            .apply()
    }

    fun isInstalled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_INSTALLED, false) || File(PremiumInstall.META_PATH).isFile

    fun status(context: Context): Status {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val installed = prefs.getBoolean(KEY_INSTALLED, false) || File(PremiumInstall.META_PATH).isFile
        if (!installed) return Status.NotActivated

        val installedAtMs = prefs.getLong(KEY_INSTALLED_AT_MS, 0L)
        if (installedAtMs <= 0L) return Status.Installed

        val bootWallClockMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        return if (installedAtMs > bootWallClockMs) {
            Status.RebootRequired
        } else {
            Status.Installed
        }
    }

    enum class Status {
        NotActivated,
        RebootRequired,
        Installed,
    }

    private fun installDex(context: Context, dexBytes: ByteArray, sha256: String, version: Int) {
        val tempDex = File(context.cacheDir, "premium.dex.tmp")
        val tempMeta = File(context.cacheDir, "premium.meta.tmp")
        tempDex.writeBytes(dexBytes)
        tempMeta.writeText(
            buildString {
                appendLine("version=$version")
                appendLine("sha256=$sha256")
                appendLine("size=${dexBytes.size}")
                appendLine("installed_at=${Instant.now()}")
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
        """.trimIndent()
        val result = Shell.cmd("sh -c ${shellQuote(installScript)}").exec()

        tempDex.delete()
        tempMeta.delete()

        check(result.isSuccess) {
            result.err.joinToString("\n").ifBlank { "Root install failed" }
        }
    }

    private fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }

        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        )
        val value = androidId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, value).apply()
        return value
    }

    private fun postJson(url: String, body: String): JSONObject {
        val connection = openConnection(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return JSONObject(readResponse(connection))
    }

    private fun getBytes(url: String): ByteArray {
        val connection = openConnection(url).apply {
            requestMethod = "GET"
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            error("Download failed ($code): $message")
        }
        return connection.inputStream.use { it.readBytes() }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            error("Activation failed ($code): $body")
        }
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

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
