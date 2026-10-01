package com.blue.hush

import com.blue.hush.processing.CalmnessEstimator
import com.blue.hush.session.StateSample
import org.junit.Assert.*
import org.junit.Test

class CalmnessEstimatorTest {
    private fun sample(second: Int, beta: Double = 0.2, stillness: Double? = null, bpm: Double? = null) = StateSample(
        second, alpha = 0.4, theta = 0.3, beta = beta, stillness = stillness,
        valid = true, eegBandsAvailable = true, heartRateBpm = bpm,
    )

    @Test fun tenMeasuredSecondsCalibrateAndGapsDoNotCount() {
        val estimator = CalmnessEstimator()
        repeat(9) { assertNull(estimator.process(sample(it + 1)).calmness) }
        assertNull(estimator.process(StateSample(10)).calmness)
        assertEquals(9, estimator.calibrationSeconds)
        assertEquals(0.5, estimator.process(sample(11)).calmness!!, 0.000001)
        assertNull(estimator.process(sample(12).copy(eegBandsAvailable = false)).calmness)
    }

    @Test fun lowerBetaRaisesCalmnessAndMotionHasIndependentWeight() {
        val estimator = CalmnessEstimator(smoothingFactor = 1.0)
        repeat(10) { estimator.process(sample(it + 1)) }
        assertTrue(estimator.process(sample(11, beta = 0.1)).calmness!! > 0.5)
        assertTrue(estimator.process(sample(12, beta = 0.4)).calmness!! < 0.5)
        assertEquals(0.5, estimator.process(sample(13)).calmness!!, 0.000001)
        assertEquals((0.6 * 0.5 + 0.25) / 0.85, estimator.process(sample(14, stillness = 1.0)).calmness!!, 0.000001)
        assertNull(estimator.process(sample(15, stillness = 1.0).copy(beta = null)).calmness)
    }

    @Test fun heartJoinsOnlyAfterItsOwnBaselineAndMissingHeartIsExcluded() {
        val estimator = CalmnessEstimator(smoothingFactor = 1.0)
        repeat(10) { estimator.process(sample(it + 1, stillness = 1.0)) }
        repeat(9) { assertEquals(0.55 / 0.85, estimator.process(sample(it + 11, stillness = 1.0, bpm = 80.0)).calmness!!, 0.000001) }
        assertEquals(0.625, estimator.process(sample(20, stillness = 1.0, bpm = 80.0)).calmness!!, 0.000001)
        assertEquals(0.7, estimator.process(sample(21, stillness = 1.0, bpm = 70.0)).calmness!!, 0.000001)
        assertEquals(0.55 / 0.85, estimator.process(sample(22, stillness = 1.0)).calmness!!, 0.000001)
    }

    @Test fun interruptionKeepsCompletedBaselineAndResetStartsANewSession() {
        val estimator = CalmnessEstimator()
        repeat(5) { estimator.process(sample(it + 1)) }
        estimator.interrupt()
        assertEquals(0, estimator.calibrationSeconds)
        repeat(10) { estimator.process(sample(it + 1)) }
        estimator.interrupt()
        assertEquals(10, estimator.calibrationSeconds)
        val before = estimator.process(sample(11)).calmness!!
        val next = estimator.process(sample(12, beta = 0.05, stillness = 1.0)).calmness!!
        assertTrue(next > before && next - before <= 0.1)
        estimator.reset()
        assertNull(estimator.process(sample(1)).calmness)
    }
}
