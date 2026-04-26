package com.fan.edgex.utils

import com.topjohnwu.superuser.Shell

fun findProcessAndKill(processName: String) = runCatching {
    val result = Shell.cmd("ps -e").exec()
    val line = result.out.find { it.contains(processName) } ?: return@runCatching
    val pid = getPid(line) ?: return@runCatching
    Shell.cmd("kill $pid").exec()
}

private fun getPid(line: String): String? {
    return Regex("\\s+").split(line).let {
        if (it.isEmpty()) null else it[1]
    }
}

fun executeShellCommand(
    command: String,
    su: Boolean,
    outAction: ((List<String>) -> Unit)? = null,
    errorAction: ((List<String>) -> Unit)? = null,
): Boolean {
    if (su) {
        val result = Shell.cmd(command).exec()
        outAction?.invoke(result.out)
        errorAction?.invoke(result.err)
        return result.isSuccess
    } else {
        val process = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", command))
        process.outputStream.close()
        outAction?.invoke(process.inputStream.bufferedReader().readLines())
        errorAction?.invoke(process.errorStream.bufferedReader().readLines())
        return process.waitFor() == 0
    }
}
