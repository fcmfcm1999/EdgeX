package com.fan.edgex.config

object AppConfig {
    const val PREFS_NAME = "config"

    // Top-level flags
    const val GESTURES_ENABLED = "gestures_enabled"
    const val KEYS_ENABLED = "keys_enabled"
    const val DEBUG_MATRIX = "debug_matrix_enabled"
    const val FREEZER_ARC_DRAWER = "freezer_arc_drawer_enabled"
    const val FREEZER_APP_LIST = "freezer_app_list"
    const val HAS_MIGRATED_FREEZER_LIST = "has_migrated_freezer_list"
    const val THEME_PRESET = "theme_preset"
    const val THEME_CUSTOM_COLOR = "theme_custom_color"
    const val HAPTIC_FEEDBACK = "haptic_feedback_enabled"
    const val HAPTIC_FEEDBACK_TYPE = "haptic_feedback_type"

    const val HAPTIC_FEEDBACK_TYPE_CLICK = "click"
    const val HAPTIC_FEEDBACK_TYPE_TICK = "tick"
    const val HAPTIC_FEEDBACK_TYPE_HEAVY_CLICK = "heavy_click"
    const val HAPTIC_FEEDBACK_TYPE_DOUBLE_CLICK = "double_click"

    val ZONES = listOf(
        "left_top",
        "left_mid",
        "left_bottom",
        "left",
        "right_top",
        "right_mid",
        "right_bottom",
        "right",
        "top_left",
        "top_mid",
        "top_right",
        "top",
        "bottom_left",
        "bottom_mid",
        "bottom_right",
        "bottom",
    )
    val GESTURES = listOf("click", "double_click", "long_press", "swipe_up", "swipe_down", "swipe_left", "swipe_right")
    val KEY_TRIGGERS = listOf("click", "double_click", "long_press")

    const val SUB_GESTURE_ACTION = "sub_gesture"
    val SUB_GESTURE_DIRECTIONS = listOf("hold", "swipe_left", "swipe_right", "swipe_up", "swipe_down")

    fun subGestureChildKey(parentKey: String, direction: String) = "${parentKey}_sub_${direction}"

    fun zoneEnabled(zone: String) = "zone_enabled_$zone"
    fun gestureAction(zone: String, gesture: String) = "${zone}_${gesture}"
    fun gestureActionLabel(zone: String, gesture: String) = "${zone}_${gesture}_label"
    fun keyEnabled(keyCode: Int) = "key_enabled_$keyCode"
    fun keyAction(keyCode: Int, trigger: String) = "key_${keyCode}_$trigger"
    fun keyActionLabel(keyCode: Int, trigger: String) = "key_${keyCode}_${trigger}_label"

    fun fallbackEdgeZone(zone: String): String? =
        when (zone) {
            "left_top", "left_mid", "left_bottom" -> "left"
            "right_top", "right_mid", "right_bottom" -> "right"
            "top_left", "top_mid", "top_right" -> "top"
            "bottom_left", "bottom_mid", "bottom_right" -> "bottom"
            else -> null
        }
}
