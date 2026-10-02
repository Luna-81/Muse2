package com.blue.hush.ui

import com.blue.hush.session.StateSample
import kotlin.math.exp

// Moderate the motion without hiding score differences; preserve the legacy band mapping.
internal fun galaxyAgitation(sample: StateSample?): Float? {
    if (sample?.algorithmVersion != null && sample.algorithmVersion > 0) {
        return sample.calmness?.takeIf { sample.valid && it.isFinite() && it in 0.0..1.0 }?.let {
            val unrest = (1 - it).toFloat()
            // Blend linear and quadratic response to retain visible changes near calm.
            unrest * (0.5f + 0.5f * unrest)
        }
    }
    if (sample?.valid != true || !sample.eegBandsAvailable) return null
    val bands = listOf(sample.alpha, sample.theta, sample.beta)
    if (bands.any { it == null || !it.isFinite() || it !in 0.0..1.0 }) return null
    val total = bands.sumOf { it!! }
    if (total <= 0.0) return null
    return ((sample.beta!! / total - 0.2) / 0.4).toFloat().coerceIn(0f, 1f)
}

/** Continuous visual state, preserved across pause and lifecycle restarts. */
internal class GalaxyMotion(phase: Float = 0f, agitation: Float = 0f, visibility: Float = 0.25f) {
    var phase = phase
        private set
    var agitation = agitation
        private set
    var visibility = visibility
        private set

    fun showRecordedSample(sample: StateSample?) {
        val target = galaxyAgitation(sample)
        phase = (sample?.elapsedSeconds ?: 0) * 0.07f
        agitation = target ?: 0f
        visibility = if (target == null) 0.25f else 1f
    }

    fun advance(seconds: Float, target: Float?, continueWhenMissing: Boolean = false) {
        // Do not catch up missed frames after a stall or background interval.
        val dt = seconds.coerceIn(0f, 0.05f)
        val fade = 1f - exp(-dt / 2.5f)
        visibility += ((if (target == null) 0.25f else 1f) - visibility) * fade
        // Live gaps retain visual parameters and drift; recorded gaps retain the original freeze.
        if (target == null) {
            if (continueWhenMissing) phase += dt * (0.06f + agitation * 0.17f)
            return
        }
        val duration = if (target < agitation) 4f else 2.5f
        agitation += (target - agitation) * (1f - exp(-dt / duration))
        // Slower settled drift, with the same maximum speed at full agitation.
        phase += dt * (0.06f + agitation * 0.17f)
    }
}
