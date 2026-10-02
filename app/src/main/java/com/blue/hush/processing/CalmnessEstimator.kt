package com.blue.hush.processing

import com.blue.hush.session.StateSample
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/** Demo heuristics, not a validated meditation or medical assessment. */
internal object SignalRules {
    const val VERSION = 3
    const val BASELINE_SECONDS = 10
    const val EPSILON = 0.000001
    const val MAD_SCALE = 1.4826
    const val EEG_MIN_SCALE = 0.15
    const val EEG_WEIGHT = 0.80
    const val HEART_WEIGHT = 0.20
    const val HEART_BASELINE_RANGE = 20.0
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
    private val eegBaseline = mutableListOf<Double>()
    private val heartBaseline = mutableListOf<Double>()
    private var smoothed: Double? = null
    val calibrationSeconds: Int get() = eegBaseline.size

    fun process(sample: StateSample): StateSample {
        val heart = sample.heartRateBpm?.takeIf { sample.valid && it.isFinite() && it in SignalRules.HEART_MIN_BPM..SignalRules.HEART_MAX_BPM }
        if (heart != null && heartBaseline.size < SignalRules.BASELINE_SECONDS) heartBaseline += heart
        val bands = listOf(sample.alpha, sample.theta, sample.beta)
        val eegValid = sample.valid && sample.eegBandsAvailable && bands.all { it != null && it.isFinite() && it in 0.0..1.0 } && bands.sumOf { it ?: 0.0 } > 0
        if (!eegValid) return sample.copy(calmness = null, algorithmVersion = SignalRules.VERSION)
        val feature = ln((sample.alpha!! + sample.theta!! + SignalRules.EPSILON) / (sample.beta!! + SignalRules.EPSILON))
        if (eegBaseline.size < SignalRules.BASELINE_SECONDS) eegBaseline += feature
        if (eegBaseline.size < SignalRules.BASELINE_SECONDS) return sample.copy(calmness = null, algorithmVersion = SignalRules.VERSION)
        val baseline = eegBaseline.median()
        val scale = (SignalRules.MAD_SCALE * eegBaseline.map { abs(it - baseline) }.median()).coerceAtLeast(SignalRules.EEG_MIN_SCALE)
        val eeg = 1.0 / (1.0 + exp(-((feature - baseline) / scale).coerceIn(-30.0, 30.0)))
        var weighted = eeg * SignalRules.EEG_WEIGHT
        var weight = SignalRules.EEG_WEIGHT
        if (heart != null && heartBaseline.size == SignalRules.BASELINE_SECONDS) {
            val heartCalmness = (0.5 + (heartBaseline.median() - heart) / SignalRules.HEART_BASELINE_RANGE).coerceIn(0.0, 1.0)
            weighted += heartCalmness * SignalRules.HEART_WEIGHT
            weight += SignalRules.HEART_WEIGHT
        }
        val target = weighted / weight
        smoothed = smoothed?.let { it + smoothingFactor * (target - it) } ?: target
        return sample.copy(calmness = smoothed, algorithmVersion = SignalRules.VERSION)
    }

    /** Keep completed baselines, but never finish a calibration across a pause or disconnect. */
    fun interrupt() {
        if (eegBaseline.size < SignalRules.BASELINE_SECONDS) eegBaseline.clear()
        if (heartBaseline.size < SignalRules.BASELINE_SECONDS) heartBaseline.clear()
    }

    fun reset() {
        eegBaseline.clear()
        heartBaseline.clear()
        smoothed = null
    }
}
