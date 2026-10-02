package com.blue.hush

import com.blue.hush.processing.SessionScoreCalculator
import com.blue.hush.session.SessionScores
import com.blue.hush.session.StateSample
import org.junit.Assert.*
import org.junit.Test

class SessionScoreCalculatorTest {
    private fun sample(second: Int) = StateSample(
        elapsedSeconds = second, calmness = 0.805, stillness = 0.9, heartRateBpm = 72.5,
        valid = true, algorithmVersion = 4,
    )

    @Test fun independentMetricsKeepUnroundedAverages() {
        val scores = SessionScoreCalculator.calculate((1..30).map(::sample))
        assertEquals(80.5, scores.calm!!, 0.000001)
        assertEquals(90.0, scores.stability!!, 0.000001)
        assertEquals(72.5, scores.heartRateBpm!!, 0.000001)
    }

    @Test fun eachDimensionRequiresThirtyMeasuredSecondsIncludingNonconsecutiveSamples() {
        val values = (1..30).map { sample(it * 2) }
        assertEquals(SessionScores(), SessionScoreCalculator.calculate(values.take(29)))
        assertNotNull(SessionScoreCalculator.calculate(values).heartRateBpm)
        val fewerMotionAndHeart = values.mapIndexed { index, sample ->
            if (index == 0) sample.copy(stillness = null, heartRateBpm = null) else sample
        }
        val scores = SessionScoreCalculator.calculate(fewerMotionAndHeart)
        assertNotNull(scores.calm)
        assertNull(scores.stability)
        assertNull(scores.heartRateBpm)
        val noCalm = SessionScoreCalculator.calculate(values.map { it.copy(calmness = null) })
        assertNull(noCalm.calm)
        assertNotNull(noCalm.stability)
        assertNotNull(noCalm.heartRateBpm)
    }

    @Test fun stabilityDependsOnMotionRatherThanCalmnessVariation() {
        val values = (1..30).map { sample(it).copy(calmness = if (it % 2 == 0) 0.0 else 1.0) }
        assertEquals(90.0, SessionScoreCalculator.calculate(values).stability!!, 0.000001)
        val moving = values.map { it.copy(stillness = 0.2) }
        assertEquals(20.0, SessionScoreCalculator.calculate(moving).stability!!, 0.000001)
        assertEquals(SessionScoreCalculator.calculate(values).calm, SessionScoreCalculator.calculate(moving).calm)
        assertEquals(SessionScoreCalculator.calculate(values).heartRateBpm, SessionScoreCalculator.calculate(moving).heartRateBpm)
    }

    @Test fun missingInvalidAndLegacyValuesNeverBecomeZeroOrReduceMetrics() {
        val values = (1..30).map(::sample)
        val invalid = listOf(
            sample(31).copy(valid = false), sample(32).copy(algorithmVersion = 0),
            sample(33).copy(calmness = null, stillness = null, heartRateBpm = null),
            sample(34).copy(calmness = Double.NaN, stillness = Double.NaN, heartRateBpm = Double.NaN),
            sample(35).copy(calmness = Double.POSITIVE_INFINITY, stillness = Double.NEGATIVE_INFINITY, heartRateBpm = Double.POSITIVE_INFINITY),
            sample(36).copy(calmness = -0.1, stillness = -0.1, heartRateBpm = 39.9),
            sample(37).copy(calmness = 1.1, stillness = 1.1, heartRateBpm = 180.1),
        )
        assertEquals(SessionScoreCalculator.calculate(values), SessionScoreCalculator.calculate(values + invalid))
        assertEquals(SessionScores(), SessionScoreCalculator.calculate(invalid))
        assertEquals(SessionScores(), SessionScoreCalculator.calculate(emptyList()))
    }

    @Test fun rangeEndpointsAreAcceptedAndDimensionsAreFilteredIndependently() {
        val values = (1..30).map { sample(it).copy(
            calmness = if (it % 2 == 0) 0.0 else 1.0,
            stillness = if (it % 2 == 0) 0.0 else 1.0,
            heartRateBpm = if (it % 2 == 0) 40.0 else 180.0,
        ) }
        val scores = SessionScoreCalculator.calculate(values)
        assertEquals(50.0, scores.calm!!, 0.000001)
        assertEquals(50.0, scores.stability!!, 0.000001)
        assertEquals(110.0, scores.heartRateBpm!!, 0.000001)
        val motionOnly = values.map { it.copy(calmness = Double.NaN, heartRateBpm = null) }
        assertEquals(SessionScores(stability = 50.0), SessionScoreCalculator.calculate(motionOnly))
        val heartOnly = values.map { it.copy(calmness = null, stillness = Double.NaN) }
        assertEquals(SessionScores(heartRateBpm = 110.0), SessionScoreCalculator.calculate(heartOnly))
    }
}
