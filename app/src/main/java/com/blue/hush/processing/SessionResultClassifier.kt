package com.blue.hush.processing

import com.blue.hush.session.ResultLabel
import com.blue.hush.session.StateSample

object SessionResultClassifier {
    fun classify(samples: List<StateSample>): ResultLabel {
        val calmness = samples.mapNotNull { sample ->
            sample.calmness?.takeIf { sample.valid && it.isFinite() && it in 0.0..1.0 }
        }
        if (calmness.isEmpty()) return ResultLabel.STEADY
        val mean = calmness.average()
        val variance = calmness.map { (it - mean) * (it - mean) }.average()
        val quarterSize = (calmness.size / 4).coerceAtLeast(1)
        val first = calmness.take(quarterSize).average()
        val last = calmness.takeLast(quarterSize).average()

        return when {
            last - first >= 0.08 -> ResultLabel.SETTLING
            variance <= 0.008 -> ResultLabel.STEADY
            else -> ResultLabel.VARIABLE
        }
    }

}
