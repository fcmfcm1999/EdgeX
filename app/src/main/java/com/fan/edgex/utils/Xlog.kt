package com.fan.edgex.utils

import android.util.Log

private const val LOG_TAG = "EdgeX"
private const val LOG_LEVEL = Log.DEBUG
private const val LOG_TO_XPOSED = false

inline fun <reified T> className(): String {
    return T::class.simpleName ?: T::class.java.simpleName
}

object Xlog {
    private fun log(priority: Int, tag: String?, message: String, vararg args: Any) {
        if (priority < LOG_LEVEL) {
            return
        }
        val msg = if (args.isNotEmpty()) {
            String.format(message, *args).let {
                if (args[args.size - 1] is Throwable) {
                    val throwable = args[args.size - 1] as Throwable
                    val stacktraceStr = Log.getStackTraceString(throwable)
                    "$it\n$stacktraceStr"
                } else {
                    it
                }
            }
        } else {
            message
        }
        Log.println(priority, LOG_TAG, "${tag?.let { "$it:" }} $msg")
        if (LOG_TO_XPOSED) {
            Log.println(priority, "Xposed", "$LOG_TAG: ${tag?.let { "$it: " }}$msg")
        }
    }

    fun v(tag: String?, message: String, vararg args: Any) {
        log(Log.VERBOSE, tag, message, *args)
    }

    fun d(tag: String?, message: String, vararg args: Any) {
        log(Log.DEBUG, tag, message, *args)
    }

    fun i(tag: String?, message: String, vararg args: Any) {
        log(Log.INFO, tag, message, *args)
    }

    fun w(tag: String?, message: String, vararg args: Any) {
        log(Log.WARN, tag, message, *args)
    }

    fun e(tag: String?, message: String, vararg args: Any) {
        log(Log.ERROR, tag, message, *args)
    }
}