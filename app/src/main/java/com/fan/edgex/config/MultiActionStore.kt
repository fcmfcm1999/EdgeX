package com.fan.edgex.config

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MultiActionStep(
    val code: String,
    val label: String,
    val iconCode: String = "",
)

data class MultiAction(
    val id: String,
    val name: String,
    val steps: MutableList<MultiActionStep>,
    val iconRef: String = "",
)

object MultiActionStore {
    private const val KEY_INDEX = "multi_actions_index"
    private const val TEMP_STEP_KEY = "_edit_step_tmp"

    fun generateId(): String =
        SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())

    fun actionCode(id: String) = "multi_action:$id"

    fun tempStepKey() = TEMP_STEP_KEY

    fun getAll(prefs: SharedPreferences): List<MultiAction> {
        val ids = prefs.getString(KEY_INDEX, "")
            ?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        return ids.mapNotNull { id -> load(prefs, id) }
    }

    fun get(prefs: SharedPreferences, id: String): MultiAction? = load(prefs, id)

    private fun load(prefs: SharedPreferences, id: String): MultiAction? {
        val name = prefs.getString("multi_action_${id}_name", null) ?: return null
        val stepsJson = prefs.getString("multi_action_${id}_steps", null) ?: "[]"
        val iconRef = prefs.getString("multi_action_${id}_icon", "") ?: ""
        return MultiAction(id, name, parseSteps(stepsJson).toMutableList(), iconRef)
    }

    fun save(prefs: SharedPreferences, multiAction: MultiAction) {
        val existing = prefs.getString(KEY_INDEX, "")
            ?.split(",")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
        if (multiAction.id !in existing) existing.add(multiAction.id)
        prefs.edit()
            .putString(KEY_INDEX, existing.joinToString(","))
            .putString("multi_action_${multiAction.id}_name", multiAction.name)
            .putString("multi_action_${multiAction.id}_steps", serializeSteps(multiAction.steps))
            .putString("multi_action_${multiAction.id}_icon", multiAction.iconRef)
            .commit()
    }

    fun delete(prefs: SharedPreferences, id: String) {
        val existing = prefs.getString(KEY_INDEX, "")
            ?.split(",")?.filter { it.isNotBlank() && it != id } ?: emptyList()
        prefs.edit()
            .putString(KEY_INDEX, existing.joinToString(","))
            .remove("multi_action_${id}_name")
            .remove("multi_action_${id}_steps")
            .remove("multi_action_${id}_icon")
            .commit()
    }

    fun serializeSteps(steps: List<MultiActionStep>): String {
        val arr = JSONArray()
        steps.forEach { step ->
            arr.put(JSONObject().apply {
                put("code", step.code)
                put("label", step.label)
                put("iconCode", step.iconCode)
            })
        }
        return arr.toString()
    }

    fun parseSteps(json: String): List<MultiActionStep> =
        runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                MultiActionStep(
                    code = obj.getString("code"),
                    label = obj.optString("label", obj.getString("code")),
                    iconCode = obj.optString("iconCode", ""),
                )
            }
        }.getOrDefault(emptyList())

    fun getStepsFromConfig(resolveConfig: (String) -> String, id: String): List<MultiActionStep> =
        parseSteps(resolveConfig("multi_action_${id}_steps"))
}
