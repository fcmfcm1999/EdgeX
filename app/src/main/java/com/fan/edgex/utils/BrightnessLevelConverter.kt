package com.fan.edgex.utils

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Converts between a user-facing brightness position and display brightness.
 *
 * Android's SystemUI brightness slider uses a gamma-encoded position so equal
 * movement feels like equal brightness changes to the eye. DisplayManager,
 * however, accepts a linear display brightness value.
 */
object BrightnessLevelConverter {
    private const val GAMMA_R = 0.5f
    private const val GAMMA_A = 0.17883277f
    private const val GAMMA_B = 0.28466892f
    private const val GAMMA_C = 0.55991073f

    /** A single Brightness Up/Down action moves 1/32 of the SystemUI slider. */
    const val ACTION_STEP = 1f / 32f

    fun toDisplayBrightness(level: Float, minimum: Float, maximum: Float): Float {
        val range = validRange(minimum, maximum)
        return range.first + gammaToLinear(level.coerceIn(0f, 1f)) * (range.second - range.first)
    }

    fun toUserLevel(brightness: Float, minimum: Float, maximum: Float): Float {
        val range = validRange(minimum, maximum)
        val linear = ((brightness - range.first) / (range.second - range.first)).coerceIn(0f, 1f)
        return linearToGamma(linear)
    }

    fun adjust(brightness: Float, minimum: Float, maximum: Float, increase: Boolean): Float {
        return adjustByUserLevel(brightness, minimum, maximum, if (increase) ACTION_STEP else -ACTION_STEP)
    }

    fun adjustByUserLevel(
        brightness: Float,
        minimum: Float,
        maximum: Float,
        levelDelta: Float,
    ): Float {
        val level = toUserLevel(brightness, minimum, maximum)
        val adjustedLevel = (level + levelDelta).coerceIn(0f, 1f)
        return toDisplayBrightness(adjustedLevel, minimum, maximum)
    }

    private fun gammaToLinear(gamma: Float): Float =
        if (gamma <= GAMMA_R) {
            val normalized = gamma / GAMMA_R
            (normalized * normalized) / GAMMA_SPACE_NORMALIZATION
        } else {
            ((Math.exp(((gamma - GAMMA_C) / GAMMA_A).toDouble()).toFloat() + GAMMA_B) /
                GAMMA_SPACE_NORMALIZATION).coerceIn(0f, 1f)
        }

    private fun linearToGamma(linear: Float): Float {
        val normalized = linear * GAMMA_SPACE_NORMALIZATION
        return if (normalized <= 1f) {
            GAMMA_R * sqrt(normalized)
        } else {
            (GAMMA_A * ln((normalized - GAMMA_B).toDouble()).toFloat() + GAMMA_C).coerceIn(0f, 1f)
        }
    }

    private fun validRange(minimum: Float, maximum: Float): Pair<Float, Float> {
        val min = minimum.takeUnless(Float::isNaN) ?: 0f
        val max = maximum.takeUnless(Float::isNaN)?.coerceAtLeast(min + MIN_RANGE) ?: 1f
        return min to max
    }

    private const val MIN_RANGE = 0.001f
    private const val GAMMA_SPACE_NORMALIZATION = 12f
}
