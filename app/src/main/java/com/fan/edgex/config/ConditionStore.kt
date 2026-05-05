package com.fan.edgex.config

object ConditionStore {

    fun condIfKey(id: String) = "cond_${id}_if"
    fun condIfLabelKey(id: String) = "cond_${id}_if_label"
    fun condThenKey(id: String) = "cond_${id}_then"
    fun condThenLabelKey(id: String) = "cond_${id}_then_label"
    fun condElseKey(id: String) = "cond_${id}_else"
    fun condElseLabelKey(id: String) = "cond_${id}_else_label"

    fun extractId(actionCode: String): String? {
        if (!actionCode.startsWith("condition:")) return null
        return actionCode.removePrefix("condition:").takeIf { it.isNotBlank() }
    }

    fun buildActionCode(id: String) = "condition:$id"
}
