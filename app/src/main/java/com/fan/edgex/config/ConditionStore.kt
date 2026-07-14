package com.fan.edgex.config

object ConditionStore {

    const val FOREGROUND_APP = "foreground_app"

    fun condIfKey(id: String) = "cond_${id}_if"
    fun condIfLabelKey(id: String) = "cond_${id}_if_label"
    fun condThenKey(id: String) = "cond_${id}_then"
    fun condThenLabelKey(id: String) = "cond_${id}_then_label"
    fun condElseKey(id: String) = "cond_${id}_else"
    fun condElseLabelKey(id: String) = "cond_${id}_else_label"
    fun foregroundPackagesKey(id: String) = "cond_${id}_foreground_packages"

    fun extractId(actionCode: String): String? {
        if (!actionCode.startsWith("condition:")) return null
        return actionCode.removePrefix("condition:").takeIf { it.isNotBlank() }
    }

    fun buildActionCode(id: String) = "condition:$id"

    fun encodePackageNames(packageNames: Collection<String>): String =
        normalizePackageNames(packageNames).joinToString(prefix = "[", postfix = "]") { value ->
            buildString {
                append('"')
                value.forEach { char ->
                    when (char) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\b' -> append("\\b")
                        '\u000C' -> append("\\f")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> if (char.code < 0x20) {
                            append("\\u%04x".format(char.code))
                        } else {
                            append(char)
                        }
                    }
                }
                append('"')
            }
        }

    fun decodePackageNames(rawValue: String): Set<String> {
        val value = rawValue.trim()
        if (value.isEmpty()) return emptySet()
        val decoded = if (value.startsWith("[")) {
            parseJsonStringArray(value) ?: return emptySet()
        } else {
            value.split(',')
        }
        return normalizePackageNames(decoded).toCollection(linkedSetOf())
    }

    private fun normalizePackageNames(packageNames: Collection<String>): List<String> =
        packageNames.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .sorted()
            .toList()

    private fun parseJsonStringArray(value: String): List<String>? {
        var index = 0
        fun skipWhitespace() {
            while (index < value.length && value[index].isWhitespace()) index++
        }
        fun readString(): String? {
            if (index >= value.length || value[index] != '"') return null
            index++
            val result = StringBuilder()
            while (index < value.length) {
                val char = value[index++]
                when (char) {
                    '"' -> return result.toString()
                    '\\' -> {
                        if (index >= value.length) return null
                        when (val escaped = value[index++]) {
                            '"', '\\', '/' -> result.append(escaped)
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000C')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> {
                                if (index + 4 > value.length) return null
                                val codePoint = value.substring(index, index + 4).toIntOrNull(16) ?: return null
                                result.append(codePoint.toChar())
                                index += 4
                            }
                            else -> return null
                        }
                    }
                    else -> if (char.code < 0x20) return null else result.append(char)
                }
            }
            return null
        }

        skipWhitespace()
        if (index >= value.length || value[index++] != '[') return null
        skipWhitespace()
        if (index < value.length && value[index] == ']') {
            index++
            skipWhitespace()
            return emptyList<String>().takeIf { index == value.length }
        }
        val result = mutableListOf<String>()
        while (index < value.length) {
            skipWhitespace()
            result += readString() ?: return null
            skipWhitespace()
            when {
                index >= value.length -> return null
                value[index] == ',' -> index++
                value[index] == ']' -> {
                    index++
                    skipWhitespace()
                    return result.takeIf { index == value.length }
                }
                else -> return null
            }
        }
        return null
    }
}

data class ForegroundAppConditionConfig(
    val packageNames: Set<String>,
)
