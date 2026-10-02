package com.blue.hush.processing

import com.blue.hush.session.StateSample
import kotlin.math.exp
import kotlin.math.ln

/** Demo heuristics, not a validated meditation or medical assessment. */
internal object SignalRules {
    const val VERSION = 5
    const val EPSILON = 0.000001
    // CogWear resting reference, subject-balanced over 10 pilot participants.
    // Reproduce with tools/cogwear_reference.py; provenance is in docs/eeg-reference-dataset.md.
    const val EEG_REFERENCE_BASELINE = 0.76507906870225273
    const val EEG_REFERENCE_SCALE = 0.32801364656479376
    const val SMOOTHING = 0.2
    const val GRAVITY_SECONDS = 1.0
    const val ACCELEROMETER_HZ = 52.0
    const val MOTION_SCALE_G = 0.05
    const val PPG_HZ = 64
    const val PPG_WINDOW_SECONDS = 8
    const val PPG_LOW_HZ = 0.7
    const val PPG_HIGH_HZ = 3.0
    const val PPG_REFRACTORY_SECONDS = 0.333
    const val HEART_MIN_BPM = 40.0
    const val HEART_MAX_BPM = 180.0
    const val INTERVAL_RELATIVE_MAD = 0.2
    const val PEAK_THRESHOLD_STD = 0.3
    const val CONSISTENT_BEAT_FRACTION = 0.8
    const val PPG_CADENCE_TOLERANCE = 0.2
    const val SENSOR_GAP_MILLIS = 500L
    const val QUALITY_TTL_MILLIS = 2_000L
}

internal fun List<Double>.median(): Double {
    val sorted = sorted()
    val middle = size / 2
    return if (size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
}

/** Shared by live processing and the known-good CSV; missing inputs never become defaults. */
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
