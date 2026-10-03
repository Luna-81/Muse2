package com.blue.hush.ui

import com.blue.hush.session.StateSample
import java.util.Locale

internal data class ReplayViewport(val start: Float = 0f, val span: Float = 1f) {
    val end: Float get() = start + span

    fun transform(zoom: Float, anchor: Float, pan: Float, totalSeconds: Int): ReplayViewport {
        if (!zoom.isFinite() || zoom <= 0f || !anchor.isFinite() || !pan.isFinite()) return this
        val minimum = maxOf(0.1f, 1f / totalSeconds.coerceAtLeast(1)).coerceAtMost(1f)
        val nextSpan = (span / zoom).coerceIn(minimum, 1f)
        val fraction = anchor.coerceIn(0f, 1f)
        val nextStart = start + span * fraction - nextSpan * (fraction + pan)
        return ReplayViewport(nextStart.coerceIn(0f, 1f - nextSpan), nextSpan)
    }

    fun secondAt(fraction: Float, totalSeconds: Int): Float =
        (start + fraction.coerceIn(0f, 1f) * span) * totalSeconds

    fun reveal(second: Float, totalSeconds: Int): ReplayViewport {
        val position = (second / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
        return copy(start = when {
            position < start -> position
            position > end -> position - span
            else -> start
        }.coerceIn(0f, 1f - span))
    }
}

internal enum class ReplayMetric(val title: String) {
    CALMNESS("Calmness"), ALPHA("Alpha"), THETA("Theta"), BETA("Beta"),
    HEART_RATE("Heart Rate"), STABILITY("Stability");

    fun value(sample: StateSample?): Double? {
        if (sample == null || !sample.valid) return null
        if (this in listOf(ALPHA, THETA, BETA) && !sample.eegBandsAvailable) return null
        val value = when (this) {
            CALMNESS -> sample.calmness
            ALPHA -> sample.alpha
            THETA -> sample.theta
            BETA -> sample.beta
            HEART_RATE -> sample.heartRateBpm
            STABILITY -> sample.stillness
        }
        val range = if (this == HEART_RATE) 40.0..180.0 else 0.0..1.0
        return value?.takeIf { it.isFinite() && it in range }
    }

    fun level(value: Double): Double = if (this == HEART_RATE) (value - 40) / 140 else value

    fun label(sample: StateSample?): String {
        val value = value(sample) ?: return "$title —"
        val number = when (this) {
            HEART_RATE -> String.format(Locale.US, "%.0f BPM", value)
            ALPHA, THETA, BETA -> String.format(Locale.US, "%.1f", value * 100)
            else -> String.format(Locale.US, "%.0f", value * 100)
        }
        return "$title $number"
    }
}

// Inputs are sorted by intersection height. Move labels, never their measured points.
internal fun replayLabelTops(centers: List<Float>, heights: List<Float>, availableHeight: Float, gap: Float): List<Float> {
    if (centers.isEmpty()) return emptyList()
    val tops = mutableListOf<Float>()
    centers.forEachIndexed { index, center ->
        val minimum = if (index == 0) 0f else tops[index - 1] + heights[index - 1] + gap
        tops += (center - heights[index] / 2).coerceAtLeast(minimum)
    }
    tops[tops.lastIndex] = tops.last().coerceAtMost(availableHeight - heights.last())
    for (index in tops.lastIndex - 1 downTo 0) {
        tops[index] = tops[index].coerceAtMost(tops[index + 1] - gap - heights[index])
    }
    return tops
}
