package com.blue.hush.processing

import com.blue.hush.session.SessionScores
import com.blue.hush.session.StateSample
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Summary-only demo heuristics, independent of the real-time fusion calibration. */
object SessionScoreCalculator {
    fun calculate(samples: List<StateSample>): SessionScores {
        // Legacy rows must not acquire a new assessment when viewed in history.
        val current = samples.filter { it.algorithmVersion > 0 && it.valid }
        val calmness = current.mapNotNull { it.calmness?.takeIf { value -> value.isFinite() && value in 0.0..1.0 } }
        val calm = calmness.takeIf { it.size >= 30 }?.average()?.times(100)
        val stability = if (calm != null) {
            val mean = calmness.average()
            val deviation = sqrt(calmness.map { (it - mean) * (it - mean) }.average())
            100 * (1 - deviation / 0.25).coerceIn(0.0, 1.0)
        } else null
        val features = current.mapNotNull { sample ->
            val bands = listOf(sample.alpha, sample.theta, sample.beta)
            // Availability is inferred from persisted bands: the live flag is not stored.
            if (bands.any { it == null || !it.isFinite() || it !in 0.0..1.0 } || bands.sumOf { it ?: 0.0 } <= 0) null
            else ln((sample.beta!! + SignalRules.EPSILON) / (sample.alpha!! + sample.theta!! + SignalRules.EPSILON))
        }
        val focus = if (features.size >= 40) {
            val baseline = features.take(10)
            val median = baseline.median()
            val scale = (SignalRules.MAD_SCALE * baseline.map { abs(it - median) }.median()).coerceAtLeast(SignalRules.EEG_MIN_SCALE)
            features.drop(10).map { 1 / (1 + exp(-((it - median) / scale).coerceIn(-30.0, 30.0))) }.average() * 100
        } else null
        val overall = if (calm != null && focus != null && stability != null) calm * 0.6 + focus * 0.2 + stability * 0.2 else null
        return SessionScores(calm, focus, stability, overall, overall?.let(::gradeFor))
    }

    internal fun gradeFor(score: Double): String = when {
        score >= 95 -> "A+"
        score >= 90 -> "A"
        score >= 85 -> "A−"
        score >= 80 -> "B+"
        score >= 75 -> "B"
        score >= 70 -> "B−"
        score >= 60 -> "C"
        else -> "D"
    }
}
