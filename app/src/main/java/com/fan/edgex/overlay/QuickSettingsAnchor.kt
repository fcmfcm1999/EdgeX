package com.fan.edgex.overlay

internal enum class QuickSettingsHorizontalAnchor { START, CENTER, END }
internal enum class QuickSettingsVerticalAnchor { TOP, CENTER, BOTTOM }

internal data class QuickSettingsAnchor(
    val horizontal: QuickSettingsHorizontalAnchor,
    val vertical: QuickSettingsVerticalAnchor,
) {
    companion object {
        fun resolve(
            zone: String?,
            touchX: Float,
            touchY: Float,
            screenWidth: Float,
            screenHeight: Float,
        ): QuickSettingsAnchor {
            val zoneAnchor = zone?.let(::fromZone)
            if (zoneAnchor != null) return zoneAnchor

            if (touchX <= 0f && touchY <= 0f) {
                return QuickSettingsAnchor(QuickSettingsHorizontalAnchor.END, QuickSettingsVerticalAnchor.TOP)
            }
            return QuickSettingsAnchor(
                horizontal = third(touchX, screenWidth).toHorizontal(),
                vertical = third(touchY, screenHeight).toVertical(),
            )
        }

        private fun fromZone(zone: String): QuickSettingsAnchor? = when (zone) {
            "left_top", "top_left" -> QuickSettingsAnchor(QuickSettingsHorizontalAnchor.START, QuickSettingsVerticalAnchor.TOP)
            "right_top", "top_right" -> QuickSettingsAnchor(QuickSettingsHorizontalAnchor.END, QuickSettingsVerticalAnchor.TOP)
            "left_bottom", "bottom_left" -> QuickSettingsAnchor(QuickSettingsHorizontalAnchor.START, QuickSettingsVerticalAnchor.BOTTOM)
            "right_bottom", "bottom_right" -> QuickSettingsAnchor(QuickSettingsHorizontalAnchor.END, QuickSettingsVerticalAnchor.BOTTOM)
            "left_mid", "left" -> QuickSettingsAnchor(QuickSettingsHorizontalAnchor.START, QuickSettingsVerticalAnchor.CENTER)
            "right_mid", "right" -> QuickSettingsAnchor(QuickSettingsHorizontalAnchor.END, QuickSettingsVerticalAnchor.CENTER)
            "top_mid", "top" -> QuickSettingsAnchor(QuickSettingsHorizontalAnchor.CENTER, QuickSettingsVerticalAnchor.TOP)
            "bottom_mid", "bottom" -> QuickSettingsAnchor(QuickSettingsHorizontalAnchor.CENTER, QuickSettingsVerticalAnchor.BOTTOM)
            else -> null
        }

        private fun third(value: Float, total: Float): Int {
            if (total <= 0f) return 2
            return when {
                value < total / 3f -> 0
                value > total * 2f / 3f -> 2
                else -> 1
            }
        }

        private fun Int.toHorizontal(): QuickSettingsHorizontalAnchor = when (this) {
            0 -> QuickSettingsHorizontalAnchor.START
            1 -> QuickSettingsHorizontalAnchor.CENTER
            else -> QuickSettingsHorizontalAnchor.END
        }

        private fun Int.toVertical(): QuickSettingsVerticalAnchor = when (this) {
            0 -> QuickSettingsVerticalAnchor.TOP
            1 -> QuickSettingsVerticalAnchor.CENTER
            else -> QuickSettingsVerticalAnchor.BOTTOM
        }
    }
}
