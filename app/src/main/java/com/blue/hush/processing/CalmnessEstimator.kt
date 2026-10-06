package com.blue.hush.processing

import com.blue.hush.session.StateSample
import kotlin.math.exp
import kotlin.math.ln

class CalmnessEstimator(private val smoothingFactor: Double = SignalRules.SMOOTHING) {
    private var smoothed: Double? = null

    fun process(sample: StateSample): StateSample {
        val bands = listOf(sample.alpha, sample.theta, sample.beta)
        val eegValid = sample.valid && sample.eegBandsAvailable && bands.all { it != null && it.isFinite() && it in 0.0..1.0 } && bands.sumOf { it ?: 0.0 } > 0
        if (!eegValid) return sample.copy(calmness = null, algorithmVersion = SignalRules.VERSION)
        val feature = ln((sample.alpha!! + sample.theta!! + SignalRules.EPSILON) / (sample.beta!! + SignalRules.EPSILON))
        val eeg = 1.0 / (1.0 + exp(-((feature - SignalRules.EEG_REFERENCE_BASELINE) / SignalRules.EEG_REFERENCE_SCALE).coerceIn(-30.0, 30.0)))
        smoothed = smoothed?.let { it + smoothingFactor * (eeg - it) } ?: eeg
        return sample.copy(calmness = smoothed, algorithmVersion = SignalRules.VERSION)
    }

    fun reset() {
        smoothed = null
    }
}