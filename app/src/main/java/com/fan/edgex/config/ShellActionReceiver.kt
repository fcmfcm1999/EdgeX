package com.fan.edgex.config

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.fan.edgex.R
import com.topjohnwu.superuser.Shell

class ShellActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EXECUTE_SHELL) return

        val command = intent.getStringExtra(EXTRA_COMMAND).orEmpty()
        val runAsRoot = intent.getBooleanExtra(EXTRA_ROOT, false)
        if (command.isBlank()) {
            Toast.makeText(context, context.getString(R.string.toast_empty_command), Toast.LENGTH_SHORT).show()
            return
        }

        val pendingResult = goAsync()
        Thread {
            try {
                if (runAsRoot) {
                    val result = Shell.cmd(command).exec()
                    if (!result.isSuccess) {
                        showToast(
                            context,
                            context.getString(R.string.toast_command_failed, result.err.joinToString("\n")),
                        )
                    } else {
                        result.out.joinToString("\n").takeIf { it.isNotBlank() }?.let {
                            showToast(context, it.take(100))
                        }
                    }
                } else {
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                    process.outputStream.close()
                    val output = process.inputStream.bufferedReader().readText()
                    val error = process.errorStream.bufferedReader().readText()
                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        output.takeIf { it.isNotBlank() }?.let { showToast(context, it.take(100)) }
                    } else {
                        showToast(context, context.getString(R.string.toast_command_failed, error))
                    }
                }
            } catch (e: Exception) {
                showToast(context, context.getString(R.string.toast_command_error, e.message))
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun showToast(context: Context, text: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_EXECUTE_SHELL = "com.fan.edgex.ACTION_EXECUTE_SHELL"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_ROOT = "root"
    }
}
