package com.fan.edgex.config

import android.content.Context
import android.os.Build
import com.fan.edgex.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

object HookConfigSnapshot {
    val ACTION_CONFIG_CHANGED = "${BuildConfig.APPLICATION_ID}.ACTION_CONFIG_CHANGED"
    const val EXTRA_KEYS = "keys"
    const val EXTRA_VALUES = "values"
    const val EXTRA_FULL_SNAPSHOT = "full_snapshot"

    private const val SNAPSHOT_FILE = "hook_config.properties"
    private const val TEMP_FILE = "$SNAPSHOT_FILE.tmp"
    private const val KEY_VERSION = "__version"
    private const val VERSION = "1"

    fun snapshotFileForHook(): File =
        systemSnapshotFile().takeIf { it.isFile && it.canRead() }
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            File("/data/user_de/0/${BuildConfig.APPLICATION_ID}/files/$SNAPSHOT_FILE")
        } else {
            File("/data/data/${BuildConfig.APPLICATION_ID}/files/$SNAPSHOT_FILE")
        }

    fun writeFromPreferences(context: Context): Boolean {
        val prefs = context.configPrefs()
        val values = prefs.all.mapValues { (_, value) -> value?.toString() ?: "" }
        return write(context, valuesForHook(values))
    }

    fun readFromHookFile(): Map<String, String> =
        read(snapshotFileForHook())

    fun readFromContext(context: Context): Map<String, String> =
        read(snapshotFile(context))

    fun writeForHook(values: Map<String, String>): Boolean =
        write(systemSnapshotFile(), valuesForHook(values))

    fun isHookRuntimeKey(key: String): Boolean =
        key != KEY_VERSION && !key.endsWith("_label")

    private fun write(context: Context, values: Map<String, String>): Boolean {
        return write(snapshotFile(context), values)
    }

    private fun write(file: File, values: Map<String, String>): Boolean {
        return runCatching {
            file.parentFile?.mkdirs()

            val properties = Properties()
            properties.setProperty(KEY_VERSION, VERSION)
            values.forEach { (key, value) ->
                properties.setProperty(key, value)
            }

            val temp = File(file.parentFile, TEMP_FILE)
            FileOutputStream(temp).use { out ->
                properties.store(out, "EdgeX hook config snapshot")
                out.fd.sync()
            }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            makeHookReadable(file)
            true
        }.getOrDefault(false)
    }

    private fun read(file: File): Map<String, String> {
        if (!file.isFile || !file.canRead()) return emptyMap()
        return runCatching {
            val properties = Properties()
            FileInputStream(file).use(properties::load)
            properties.stringPropertyNames()
                .filter(::isHookRuntimeKey)
                .associateWith { properties.getProperty(it, "") }
        }.getOrDefault(emptyMap())
    }

    private fun valuesForHook(values: Map<String, String>): Map<String, String> =
        values.filterKeys(::isHookRuntimeKey)

    private fun snapshotFile(context: Context): File {
        val storageContext =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }
        return File(storageContext.filesDir, SNAPSHOT_FILE)
    }

    private fun systemSnapshotFile(): File =
        File("/data/system/edgex/$SNAPSHOT_FILE")

    private fun makeHookReadable(file: File) {
        file.setReadable(true, false)
        file.setWritable(true, true)

        file.parentFile?.let { filesDir ->
            filesDir.setExecutable(true, false)
            filesDir.setReadable(true, false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            file.parentFile?.parentFile?.let { dataDir ->
                dataDir.setExecutable(true, false)
            }
        }
    }
}
