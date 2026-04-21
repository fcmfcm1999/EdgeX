package com.fan.edgex.config

object AppConfig {
    const val PREFS_NAME = "config"

    // Top-level flags
    const val GESTURES_ENABLED = "gestures_enabled"
    const val KEYS_ENABLED = "keys_enabled"
    const val DEBUG_MATRIX = "debug_matrix_enabled"
    const val FREEZER_ARC_DRAWER = "freezer_arc_drawer_enabled"
    const val FREEZER_APP_LIST = "freezer_app_list"
    const val HAS_INIT_FREEZER = "has_init_freezer_list"

    const val EDGE_THRESHOLD_DP = "edge_threshold_dp"
    const val SWIPE_THRESHOLD_DP = "swipe_threshold_dp"
    const val DEFAULT_EDGE_THRESHOLD_DP = 8
    const val DEFAULT_SWIPE_THRESHOLD_DP = 60

    val ZONES = listOf("left_top", "left_mid", "left_bottom", "right_top", "right_mid", "right_bottom")
    val GESTURES = listOf("click", "double_click", "long_press", "swipe_up", "swipe_down", "swipe_left", "swipe_right")
    val KEY_TRIGGERS = listOf("click", "double_click", "long_press")

    fun zoneEnabled(zone: String) = "zone_enabled_$zone"
    fun gestureAction(zone: String, gesture: String) = "${zone}_${gesture}"
    fun gestureActionLabel(zone: String, gesture: String) = "${zone}_${gesture}_label"
    fun keyEnabled(keyCode: Int) = "key_enabled_$keyCode"
    fun keyAction(keyCode: Int, trigger: String) = "key_${keyCode}_$trigger"
    fun keyActionLabel(keyCode: Int, trigger: String) = "key_${keyCode}_${trigger}_label"
}
