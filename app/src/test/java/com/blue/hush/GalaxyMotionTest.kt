package com.blue.hush

import com.blue.hush.session.StateSample
import com.blue.hush.ui.GalaxyMotion
import com.blue.hush.ui.galaxyAgitation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalaxyMotionTest {
    @Test fun liveGapKeepsDriftingWithoutChangingTheLastTrustedAgitation() {
        val motion = GalaxyMotion()
        repeat(300) { motion.advance(1f / 60, 0.8f) }
        val agitation = motion.agitation
        val phase = motion.phase
        repeat(600) { motion.advance(1f / 60, null, continueWhenMissing = true) }
        assertTrue(motion.phase > phase)
        assertEquals(agitation, motion.agitation, 0f)
        assertTrue(motion.visibility < 0.27f)
        val pausedPhase = motion.phase
        motion.advance(0f, null, continueWhenMissing = true)
        assertEquals(pausedPhase, motion.phase, 0f)
        val withoutMeasurement = GalaxyMotion()
        withoutMeasurement.advance(0.05f, null, continueWhenMissing = true)
        assertTrue(withoutMeasurement.phase > 0f)
        assertEquals(0f, withoutMeasurement.agitation, 0f)
    }

    @Test fun compositeValuesDriveGalaxyAndWarmupNeverFallsBackToBands() {
        val composite = sample(0.4).copy(algorithmVersion = 1, calmness = 0.8)
        assertEquals(0.12f, galaxyAgitation(composite)!!, 0.000001f)
        assertEquals(0.375f, galaxyAgitation(composite.copy(calmness = 0.5))!!, 0.000001f)
        assertEquals(0f, galaxyAgitation(composite.copy(calmness = 1.0))!!, 0f)
        assertEquals(1f, galaxyAgitation(composite.copy(calmness = 0.0))!!, 0f)
        assertNull(galaxyAgitation(composite.copy(calmness = null)))
        assertNull(galaxyAgitation(composite.copy(calmness = Double.NaN)))
        assertNull(galaxyAgitation(composite.copy(valid = false)))
    }
    @Test fun settledRotationIsSlowerAndPeakSpeedIsPreserved() {
        val calm = GalaxyMotion(agitation = 0f)
        val resting = GalaxyMotion(agitation = 0.375f)
        val peak = GalaxyMotion(agitation = 1f)
        repeat(60) {
            calm.advance(1f / 60, 0f)
            resting.advance(1f / 60, 0.375f)
            peak.advance(1f / 60, 1f)
        }
        assertEquals(0.06f, calm.phase, 0.000001f)
        assertEquals(0.12375f, resting.phase, 0.000001f)
        assertEquals(0.23f, peak.phase, 0.000001f)
    }
    @Test fun recordedFramesAreDeterministicAndRejectMissingBands() {
        val motion = GalaxyMotion()
        val recorded = StateSample(120, 0.3, 0.3, 0.4, valid = true, eegBandsAvailable = true)
        motion.showRecordedSample(recorded)
        assertEquals(8.4f, motion.phase, 0.0001f)
        assertEquals(0.5f, motion.agitation, 0.0001f)
        assertEquals(1f, motion.visibility, 0f)
        motion.showRecordedSample(recorded.copy(alpha = null))
        assertEquals(0.25f, motion.visibility, 0f)
        motion.showRecordedSample(recorded)
        assertEquals(8.4f, motion.phase, 0.0001f)
        assertEquals(0.5f, motion.agitation, 0.0001f)
    }

    private fun sample(beta: Double) = StateSample(
        1, alpha = (1 - beta) / 2, theta = (1 - beta) / 2, beta = beta,
        valid = true, eegBandsAvailable = true,
    )

    @Test fun relativeBandMappingClampsAndInterpolates() {
        assertEquals(0f, galaxyAgitation(sample(0.1))!!, 0.0001f)
        assertEquals(0f, galaxyAgitation(sample(0.2))!!, 0.0001f)
        assertEquals(0.5f, galaxyAgitation(sample(0.4))!!, 0.0001f)
        assertEquals(1f, galaxyAgitation(sample(0.6))!!, 0.0001f)
        assertEquals(1f, galaxyAgitation(sample(0.9))!!, 0.0001f)
    }

    @Test fun invalidOrSubstitutedBandsDoNotDriveAnimation() {
        assertNull(galaxyAgitation(null))
        assertNull(galaxyAgitation(sample(0.4).copy(valid = false)))
        assertNull(galaxyAgitation(sample(0.4).copy(eegBandsAvailable = false)))
        assertNull(galaxyAgitation(sample(0.4).copy(alpha = null)))
        assertNull(galaxyAgitation(sample(0.4).copy(theta = Double.NaN)))
        assertNull(galaxyAgitation(sample(0.4).copy(beta = Double.POSITIVE_INFINITY)))
        assertNull(galaxyAgitation(sample(0.4).copy(beta = -0.1)))
        assertNull(galaxyAgitation(sample(0.4).copy(beta = 1.1)))
        assertNull(galaxyAgitation(sample(0.4).copy(alpha = 0.0, theta = 0.0, beta = 0.0)))
    }

    @Test fun calmScatterAndRegroupSequenceIsContinuous() {
        val motion = GalaxyMotion()
        repeat(60) { motion.advance(1f / 60, 0f) }
        assertEquals(0f, motion.agitation, 0f)
        repeat(600) {
            val before = motion.agitation
            motion.advance(1f / 60, 1f)
            assertTrue(motion.agitation >= before && motion.agitation - before < 0.01f)
        }
        assertTrue(motion.agitation > 0.98f)
        val peak = motion.agitation
        repeat(240) { motion.advance(1f / 60, 0f) }
        assertEquals(peak / kotlin.math.E.toFloat(), motion.agitation, 0.001f)
        repeat(960) {
            val before = motion.agitation
            motion.advance(1f / 60, 0f)
            assertTrue(motion.agitation <= before && before - motion.agitation < 0.01f)
        }
        assertTrue(motion.agitation < 0.01f)
    }

    @Test fun gapHoldsShapeAndResumeDoesNotCatchUp() {
        val motion = GalaxyMotion()
        repeat(300) { motion.advance(1f / 60, 1f) }
        val phase = motion.phase
        val agitation = motion.agitation
        repeat(600) { motion.advance(1f / 60, null) }
        assertEquals(phase, motion.phase, 0f)
        assertEquals(agitation, motion.agitation, 0f)
        assertTrue(motion.visibility < 0.27f)
        motion.advance(0f, 0f)
        assertEquals(phase, motion.phase, 0f)
        assertEquals(agitation, motion.agitation, 0f)
        motion.advance(600f, 0f)
        assertTrue(motion.phase - phase < 0.012f)
        assertTrue(agitation - motion.agitation < 0.02f)
    }
}
