package com.blue.hush.processing

import com.blue.hush.session.SessionScores
import com.blue.hush.session.StateSample

/** Independent summary metrics; missing measurements never become zero scores. */
object SessionScoreCalculator {
    private const val MIN_MEASURED_SECONDS = 30

    fun calculate(samples: List<StateSample>): SessionScores {
        val current = samples.filter { it.algorithmVersion > 0 && it.valid }
        val calmness = current.mapNotNull { it.calmness?.takeIf { value -> value.isFinite() && value in 0.0..1.0 } }
        val stillness = current.mapNotNull { it.stillness?.takeIf { value -> value.isFinite() && value in 0.0..1.0 } }
        val heartRate = current.mapNotNull { it.heartRateBpm?.takeIf { value ->
            value.isFinite() && value in SignalRules.HEART_MIN_BPM..SignalRules.HEART_MAX_BPM
        } }
        // Each sensor has its own availability; EEG gaps do not reject motion or PPG.
        return SessionScores(
            calm = measuredAverage(calmness)?.times(100),
            stability = measuredAverage(stillness)?.times(100),
            heartRateBpm = measuredAverage(heartRate),
        )
    }

    private fun measuredAverage(values: List<Double>): Double? =
        values.takeIf { it.size >= MIN_MEASURED_SECONDS }?.average()
}
