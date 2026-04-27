package com.fan.edgex.config

import com.fan.edgex.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

object HookClipboardHistoryStore {
    private const val HISTORY_FILE = "clipboard_history.properties"
    private const val TEMP_FILE = "$HISTORY_FILE.tmp"
    private const val KEY_VERSION = "__version"
    private const val KEY_COUNT = "count"
    private const val ENTRY_PREFIX = "entry."
    private const val VERSION = "1"

    fun readForHook(maxItems: Int): List<String> =
        read(historyFileForHook(), maxItems)

    fun writeForHook(items: List<String>, maxItems: Int): Boolean =
        write(systemHistoryFile(), items.take(maxItems))

    private fun historyFileForHook(): File =
        systemHistoryFile().takeIf { it.isFile && it.canRead() }
            ?: File("/data/user_de/0/${BuildConfig.APPLICATION_ID}/files/$HISTORY_FILE")

    private fun read(file: File, maxItems: Int): List<String> {
        if (!file.isFile || !file.canRead()) return emptyList()
        return runCatching {
            val properties = Properties()
            FileInputStream(file).use(properties::load)
            val count = properties.getProperty(KEY_COUNT, "0").toIntOrNull() ?: 0
            (0 until count.coerceAtMost(maxItems)).mapNotNull { index ->
                properties.getProperty("$ENTRY_PREFIX$index")?.takeIf { it.isNotEmpty() }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(file: File, items: List<String>): Boolean {
        return runCatching {
            file.parentFile?.mkdirs()

            val properties = Properties()
            properties.setProperty(KEY_VERSION, VERSION)
            properties.setProperty(KEY_COUNT, items.size.toString())
            items.forEachIndexed { index, text ->
                properties.setProperty("$ENTRY_PREFIX$index", text)
            }

            val temp = File(file.parentFile, TEMP_FILE)
            FileOutputStream(temp).use { out ->
                properties.store(out, "EdgeX clipboard history")
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

    private fun systemHistoryFile(): File =
        File("/data/system/edgex/$HISTORY_FILE")

    private fun makeHookReadable(file: File) {
        file.setReadable(true, false)
        file.setWritable(true, true)

        file.parentFile?.let { filesDir ->
            filesDir.setExecutable(true, false)
            filesDir.setReadable(true, false)
        }

        file.parentFile?.parentFile?.setExecutable(true, false)
    }
}
