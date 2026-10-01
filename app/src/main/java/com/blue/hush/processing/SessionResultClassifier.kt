package com.blue.hush.processing

import com.blue.hush.session.ResultLabel
import com.blue.hush.session.StateSample
import kotlin.math.abs

object SessionResultClassifier {
    fun classify(samples: List<StateSample>): ResultLabel {
        val composite = samples.any { it.algorithmVersion > 0 }
        val valid = samples.filter { it.valid && (if (composite) it.calmness else it.stillness)?.isFinite() == true }
        val stillness = valid.mapNotNull { if (composite) it.calmness else it.stillness }
        if (stillness.isEmpty()) return ResultLabel.STEADY
        val mean = stillness.average()
        val variance = stillness.map { (it - mean) * (it - mean) }.average()
        val quarterSize = (valid.size / 4).coerceAtLeast(1)
        val first = stillness.take(quarterSize).averageOrNull() ?: mean
        val last = stillness.takeLast(quarterSize).averageOrNull() ?: mean

        return when {
            last - first >= 0.08 -> ResultLabel.SETTLING
            variance <= 0.008 -> ResultLabel.STEADY
            abs(last - first) >= 0.12 -> ResultLabel.VARIABLE
            else -> ResultLabel.VARIABLE
        }
    }

    private fun List<Double>.averageOrNull(): Double? = takeIf { isNotEmpty() }?.average()
}
