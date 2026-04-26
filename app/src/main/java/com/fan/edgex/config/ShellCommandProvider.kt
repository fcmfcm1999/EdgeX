package com.fan.edgex.config

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.fan.edgex.R
import com.topjohnwu.superuser.Shell

class ShellCommandProvider : ContentProvider() {
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_EXECUTE) return null
        val command = extras?.getString(EXTRA_COMMAND).orEmpty()
        val runAsRoot = extras?.getBoolean(EXTRA_ROOT, false) ?: false
        if (command.isBlank()) return null

        Thread {
            try {
                if (runAsRoot) {
                    val result = Shell.cmd(command).exec()
                    if (!result.isSuccess) {
                        showToast(context!!.getString(R.string.toast_command_failed, result.err.joinToString("\n")))
                    } else {
                        result.out.joinToString("\n").takeIf { it.isNotBlank() }?.let {
                            showToast(it.take(100))
                        }
                    }
                } else {
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                    process.outputStream.close()
                    val output = process.inputStream.bufferedReader().readText()
                    val error = process.errorStream.bufferedReader().readText()
                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        output.takeIf { it.isNotBlank() }?.let { showToast(it.take(100)) }
                    } else {
                        showToast(context!!.getString(R.string.toast_command_failed, error))
                    }
                }
            } catch (e: Exception) {
                showToast(context!!.getString(R.string.toast_command_error, e.message))
            }
        }.start()

        return null
    }

    private fun showToast(text: String) {
        mainHandler.post {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
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
    }
}
