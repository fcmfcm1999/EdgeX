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
