package com.fan.edgex.config

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import com.fan.edgex.IShellCallback
import com.fan.edgex.IShellExecutor
import com.topjohnwu.superuser.Shell

class ShellExecutorService : Service() {

    private val stub = object : IShellExecutor.Stub() {
        override fun execute(command: String, runAsRoot: Boolean, callback: IShellCallback?) {
            val callerUid = Binder.getCallingUid()
            val callerPackages = packageManager.getPackagesForUid(callerUid)
            if (callerUid != Process.SYSTEM_UID || callerPackages?.contains("android") != true) {
                callback?.onResult(false, "")
                return
            }
            Thread {
                try {
                    if (runAsRoot) {
                        val result = Shell.cmd(command).exec()
                        val output = if (result.isSuccess) {
                            result.out.joinToString("\n").trim()
                        } else {
                            result.err.joinToString("\n").trim()
                        }
                        callback?.onResult(result.isSuccess, output)
                    } else {
                        val process = ProcessBuilder("sh", "-c", command)
                            .redirectErrorStream(true)
                            .start()
                        process.outputStream.close()
                        val output = process.inputStream.bufferedReader().readText().trim()
                        val exitCode = process.waitFor()
                        callback?.onResult(exitCode == 0, output)
                    }
                } catch (e: Exception) {
                    callback?.onResult(false, e.message.orEmpty())
                }
            }.start()
        }
    }

    override fun onBind(intent: Intent): IBinder = stub
}
