package com.fan.edgex.config

class GestureZoneGeometryCalculator(
    private val getVal: (String, String) -> String
) {
    companion object {
        const val MIN_PERCENT = 10
        const val MAX_PERCENT = 90
        const val MIN_SEGMENT_PERCENT = 10
        const val DEFAULT_FIRST_PERCENT = 33
        const val DEFAULT_SECOND_PERCENT = 66
        const val DEFAULT_THICKNESS_DP = 16
        const val MIN_THICKNESS_DP = 8
        const val MAX_THICKNESS_DP = 32

        fun clampFirstPercent(first: Int): Int =
            first.coerceIn(MIN_PERCENT, MAX_PERCENT - MIN_SEGMENT_PERCENT)

        fun clampSecondPercent(first: Int, second: Int): Int =
            second.coerceIn(first + MIN_SEGMENT_PERCENT, MAX_PERCENT)

        fun adjustFirst(first: Int, currentSecond: Int): Pair<Int, Int> {
            val f = first.coerceIn(MIN_PERCENT, MAX_PERCENT - MIN_SEGMENT_PERCENT)
            val s = currentSecond.coerceIn(f + MIN_SEGMENT_PERCENT, MAX_PERCENT)
            return Pair(f, s)
        }

        fun adjustSecond(currentFirst: Int, second: Int): Pair<Int, Int> {
            val s = second.coerceIn(MIN_PERCENT + MIN_SEGMENT_PERCENT, MAX_PERCENT)
            val f = currentFirst.coerceIn(MIN_PERCENT, s - MIN_SEGMENT_PERCENT)
            return Pair(f, s)
        }

        fun adjustMiddleHeight(newH: Int, currentFirst: Int, currentSecond: Int): Pair<Int, Int> {
            val h = newH.coerceIn(MIN_SEGMENT_PERCENT, MAX_PERCENT - MIN_PERCENT)
            val currentMid = (currentFirst + currentSecond) / 2.0
            var f = kotlin.math.round(currentMid - h / 2.0).toInt()
            var s = f + h
            if (f < MIN_PERCENT) {
                f = MIN_PERCENT
                s = f + h
            }
            if (s > MAX_PERCENT) {
                s = MAX_PERCENT
                f = s - h
            }
            return Pair(f, s)
        }

        fun resolveSegment(v: Float, totalLength: Float, firstPercent: Int, secondPercent: Int): Int {
            val p1 = totalLength * (firstPercent / 100f)
            val p2 = totalLength * (secondPercent / 100f)
            return when {
                v < p1 -> 0
                v < p2 -> 1
                else -> 2
            }
        }

        fun resolveSegmentWithOffset(
            v: Float,
            startOffset: Float,
            endOffset: Float,
            firstPercent: Int,
            secondPercent: Int
        ): Int {
            val activeLength = endOffset - startOffset
            val p1 = startOffset + activeLength * (firstPercent / 100f)
            val p2 = startOffset + activeLength * (secondPercent / 100f)
            return when {
                v < p1 -> 0
                v < p2 -> 1
                else -> 2
            }
        }
    }

    fun getSplits(edge: String): Pair<Int, Int> {
        val firstKey = AppConfig.zoneSplitFirstPercentKey(edge)
        val secondKey = AppConfig.zoneSplitSecondPercentKey(edge)
        val firstVal = getVal(firstKey, DEFAULT_FIRST_PERCENT.toString()).toIntOrNull() ?: DEFAULT_FIRST_PERCENT
        val secondVal = getVal(secondKey, DEFAULT_SECOND_PERCENT.toString()).toIntOrNull() ?: DEFAULT_SECOND_PERCENT

        val f = clampFirstPercent(firstVal)
        val s = clampSecondPercent(f, secondVal)
        return Pair(f, s)
    }

    fun getThicknessDp(zone: String): Int {
        val key = AppConfig.zoneThicknessKey(zone)
        val value = getVal(key, DEFAULT_THICKNESS_DP.toString()).toIntOrNull() ?: DEFAULT_THICKNESS_DP
        return value.coerceIn(MIN_THICKNESS_DP, MAX_THICKNESS_DP)
    }
}
