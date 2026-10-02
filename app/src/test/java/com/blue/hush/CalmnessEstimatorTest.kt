package com.blue.hush

import com.blue.hush.processing.CalmnessEstimator
import com.blue.hush.processing.SignalRules
import com.blue.hush.session.StateSample
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp

class CalmnessEstimatorTest {
    private fun sample(second: Int, beta: Double = 0.2) = StateSample(
        second, alpha = 0.4, theta = 0.3, beta = beta, valid = true, eegBandsAvailable = true,
    )

    // Choose bands whose log ratio is exactly the public resting reference.
    private fun resting(second: Int): StateSample {
        val beta = 0.2
        val sum = exp(SignalRules.EEG_REFERENCE_BASELINE) * (beta + SignalRules.EPSILON) - SignalRules.EPSILON
        return sample(second, beta).copy(alpha = sum / 2, theta = sum / 2)
    }

    @Test fun firstTrustedSecondAtReferenceScoresFiftyWithoutPersonalCalibration() {
        val estimator = CalmnessEstimator()
        assertEquals(0.5, estimator.process(resting(1)).calmness!!, 1e-12)
        assertEquals(5, estimator.process(resting(2)).algorithmVersion)
        assertNull(estimator.process(StateSample(3)).calmness)
        assertEquals(0.5, estimator.process(resting(4)).calmness!!, 1e-12)
    }

    @Test fun higherRatioRaisesScoreAndExtremeInputsStayFinite() {
        val estimator = CalmnessEstimator(smoothingFactor = 1.0)
        assertTrue(estimator.process(resting(1).copy(beta = 0.1)).calmness!! > 0.5)
        assertTrue(estimator.process(resting(2).copy(beta = 0.4)).calmness!! < 0.5)
        val low = estimator.process(sample(3).copy(alpha = 0.0, theta = 0.0, beta = 1.0)).calmness!!
        val high = estimator.process(sample(4).copy(alpha = 1.0, theta = 1.0, beta = 0.0)).calmness!!
        assertTrue(low.isFinite() && low in 0.0..1.0 && low < 0.0001)
        assertTrue(high.isFinite() && high in 0.0..1.0 && high > 0.9999)
    }

    @Test fun invalidBandsAndUnavailableEegNeverReceiveScores() {
        val estimator = CalmnessEstimator()
        val invalid = listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1)
        for (value in invalid) {
            assertNull(estimator.process(sample(1).copy(alpha = value)).calmness)
            assertNull(estimator.process(sample(1).copy(theta = value)).calmness)
            assertNull(estimator.process(sample(1).copy(beta = value)).calmness)
        }
        assertNull(estimator.process(sample(1).copy(valid = false)).calmness)
        assertNull(estimator.process(sample(1).copy(eegBandsAvailable = false)).calmness)
        assertNull(estimator.process(sample(1).copy(alpha = 0.0, theta = 0.0, beta = 0.0)).calmness)
    }

    @Test fun heartRateAndMotionDoNotAffectSmoothedCalmness() {
        val reference = CalmnessEstimator()
        val measured = CalmnessEstimator()
        for (second in 1..40) {
            val input = sample(second, beta = if (second % 2 == 0) 0.1 else 0.4)
            val expected = reference.process(input)
            val actual = measured.process(input.copy(
                stillness = if (second % 2 == 0) 0.0 else 1.0,
                heartRateBpm = if (second % 2 == 0) 40.0 else 180.0,
            ))
            assertEquals(expected.calmness, actual.calmness)
            assertEquals(input.copy(stillness = actual.stillness, heartRateBpm = actual.heartRateBpm,
                calmness = expected.calmness, algorithmVersion = 5), actual)
        }
    }

    @Test fun gapsPreserveSmoothingAndResetStartsFreshWithoutChangingReference() {
        val estimator = CalmnessEstimator()
        estimator.process(resting(1))
        assertNull(estimator.process(StateSample(2)).calmness)
        val input = resting(3).copy(beta = 0.05)
        val target = CalmnessEstimator(smoothingFactor = 1.0).process(input).calmness!!
        assertEquals(0.5 + 0.2 * (target - 0.5), estimator.process(input).calmness!!, 1e-12)
        estimator.reset()
        assertEquals(target, estimator.process(input).calmness!!, 1e-12)
        estimator.reset()
        assertEquals(0.5, estimator.process(resting(1)).calmness!!, 1e-12)
    }
}
