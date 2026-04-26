package com.fan.edgex.config

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.topjohnwu.superuser.Shell

class ShellCommandProvider : ContentProvider() {

    // Execute synchronously on the Binder thread so the process stays alive
    // for the full duration. Return result to the caller (system_server) which
    // shows the Toast in its own context.
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (Binder.getCallingUid() != android.os.Process.SYSTEM_UID) return null
        if (method != METHOD_EXECUTE) return null

        val command = extras?.getString(EXTRA_COMMAND).orEmpty()
        val runAsRoot = extras?.getBoolean(EXTRA_ROOT, false) ?: false
        if (command.isBlank()) return null

        return try {
            if (runAsRoot) {
                val result = Shell.cmd(command).exec()
                Bundle().apply {
                    putBoolean(RESULT_SUCCESS, result.isSuccess)
                    putString(RESULT_OUTPUT, result.out.joinToString("\n"))
                    putString(RESULT_ERROR, result.err.joinToString("\n"))
                }
            } else {
                val process = ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start()
                process.outputStream.close()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                Bundle().apply {
                    putBoolean(RESULT_SUCCESS, exitCode == 0)
                    putString(RESULT_OUTPUT, output.trim())
                    putString(RESULT_ERROR, "")
                }
            }
        } catch (e: Exception) {
            Bundle().apply {
                putBoolean(RESULT_SUCCESS, false)
                putString(RESULT_ERROR, e.message)
            }
        }
    }

    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "com.fan.edgex.shell"
        const val METHOD_EXECUTE = "execute"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_ROOT = "root"
        const val RESULT_SUCCESS = "success"
        const val RESULT_OUTPUT = "output"
        const val RESULT_ERROR = "error"
    }
}
