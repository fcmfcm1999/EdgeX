package com.fan.edgex.utils

import java.io.BufferedReader
import java.io.InputStreamReader

fun findProcessAndKill(processName: String) = runCatching {
    var pid: String? = null
    executeShellCommand("ps -e \n", true,
        { outputs -> outputs.find { out -> out.contains(processName) }?.let { pid = getPid(it) } },
        { errors -> errors.forEach { Xlog.e("ProcessUtils", "[Error] $it") } }
    )
    kill(pid)
}

private fun getPid(line: String): String? {
    return Regex("\\s+").split(line).let {
        if (it.isEmpty()) {
            null
        } else {
            it[1]
        }
    }
}

private fun kill(pid: String?) {
    if (pid.isNullOrBlank()) {
        return
    }
    executeShellCommand("kill $pid \n", true, null) { errors ->
        errors.forEach { Xlog.e("ProcessUtils", "[Error] $it") }
    }
}

fun executeShellCommand(
    command: String,
    su: Boolean,
    outAction: ((List<String>) -> Unit)? = null,
    errorAction: ((List<String>) -> Unit)? = null,
): Boolean {
    val process = Runtime.getRuntime().run {
        if (su) {
            exec("su").also { process ->
                process.outputStream.use {
                    it.write(command.toByteArray())
                    it.flush()
                    it.write("exit\n".toByteArray())
                }
            }
        } else {
            exec(command).also { process ->
                process.outputStream.close()
            }
        }
    }
    val stderr = process.errorStream
    val stdout = process.inputStream
    outAction?.let { BufferedReader(InputStreamReader(stdout)).readLines().run(it) }
    errorAction?.let { BufferedReader(InputStreamReader(stderr)).readLines().run(it) }
    return (process.waitFor() == 0 && process.exitValue() == 0).also { process.destroy() }
}