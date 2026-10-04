package com.blue.hush.ui.galaxy

import com.blue.hush.session.StateSample
import com.blue.hush.ui.charts.chartCalmness
import kotlin.math.exp

// Blend linear and quadratic response to retain visible changes near calm.
internal fun galaxyAgitation(sample: StateSample?): Float? = sample?.chartCalmness()?.let {
    val unrest = (1 - it).toFloat()
    unrest * (0.5f + 0.5f * unrest)
}

/** Continuous visual state, preserved across pause and lifecycle restarts. */
internal class GalaxyMotion(phase: Float = 0f, agitation: Float = 0f, visibility: Float = 0.25f) {
    var phase = phase
        private set
    var agitation = agitation
        private set
    var visibility = visibility
        private set

    fun showRecordedSample(sample: StateSample?, retainedSample: StateSample? = null) {
        val target = galaxyAgitation(sample) ?: galaxyAgitation(retainedSample)
        phase = (sample?.elapsedSeconds ?: 0) * 0.07f
        agitation = target ?: 0f
        visibility = if (target == null) 0.25f else 1f
    }

    fun advance(seconds: Float, target: Float?, continueWhenMissing: Boolean = false) {
        // Do not catch up missed frames after a stall or background interval.
        val dt = seconds.coerceIn(0f, 0.05f)
        // Missing measurements preserve appearance and speed without creating a measurement.
        if (target == null) {
            if (continueWhenMissing) {
                phase += dt * (0.06f + agitation * 0.17f)
                return
            }
            val fade = 1f - exp(-dt / 2.5f)
            visibility += (0.25f - visibility) * fade
            return
        }
        val fade = 1f - exp(-dt / 2.5f)
        visibility += (1f - visibility) * fade
        val duration = if (target < agitation) 4f else 2.5f
        agitation += (target - agitation) * (1f - exp(-dt / duration))
        // Slower settled drift, with the same maximum speed at full agitation.
        phase += dt * (0.06f + agitation * 0.17f)
    }
}
