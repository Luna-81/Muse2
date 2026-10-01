package com.blue.hush

import com.blue.hush.processing.SessionScoreCalculator
import com.blue.hush.session.SessionScores
import com.blue.hush.session.StateSample
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

class SessionScoreCalculatorTest {
    private fun sample(second: Int, calmness: Double = 0.8, beta: Double = 0.2) = StateSample(
        elapsedSeconds = second, alpha = 0.3, theta = 0.2, beta = beta,
        calmness = calmness, valid = true, eegBandsAvailable = true, algorithmVersion = 1,
    )

    @Test fun constantSignalUsesIndependentFocusBaselineAndUnroundedWeights() {
        val scores = SessionScoreCalculator.calculate((1..40).map { sample(it, 0.805) })
        assertEquals(80.5, scores.calm!!, 0.000001)
        assertEquals(50.0, scores.focus!!, 0.000001)
        assertEquals(100.0, scores.stability!!, 0.000001)
        assertEquals(78.3, scores.overall!!, 0.000001)
        assertEquals(81, scores.calm.roundToInt())
        assertEquals("B", scores.grade)
    }

    @Test fun minimumCountsExcludeBaselineFromFocusAverage() {
        val values = (1..40).map { sample(it, beta = if (it <= 10) 0.2 else 0.4) }
        assertNull(SessionScoreCalculator.calculate(values.take(29)).calm)
        assertNotNull(SessionScoreCalculator.calculate(values.take(30)).calm)
        assertNull(SessionScoreCalculator.calculate(values.take(39)).focus)
        val shift = ln((0.4 + 0.000001) / (0.5 + 0.000001)) - ln((0.2 + 0.000001) / (0.5 + 0.000001))
        assertEquals(100 / (1 + exp(-shift / 0.15)), SessionScoreCalculator.calculate(values).focus!!, 0.000001)
    }

    @Test fun focusUsesMedianAbsoluteDeviationScale() {
        val values = (1..40).map { sample(it, beta = if (it <= 5) 0.1 else 0.4) }
        val low = ln((0.1 + 0.000001) / (0.5 + 0.000001))
        val high = ln((0.4 + 0.000001) / (0.5 + 0.000001))
        val baseline = (low + high) / 2
        val scale = 1.4826 * (high - low) / 2
        assertEquals(100 / (1 + exp(-(high - baseline) / scale)), SessionScoreCalculator.calculate(values).focus!!, 0.000001)
    }

    @Test fun stabilityUsesPopulationDeviationAndClamps() {
        val values = (1..40).map { sample(it, if (it % 2 == 0) 0.6 else 0.8) }
        assertEquals(60.0, SessionScoreCalculator.calculate(values).stability!!, 0.000001)
        assertEquals(0.0, SessionScoreCalculator.calculate((1..40).map { sample(it, if (it % 2 == 0) 0.0 else 1.0) }).stability!!, 0.000001)
    }

    @Test fun missingAndInvalidSamplesNeverBecomeZeroOrReduceScores() {
        val values = (1..40).map { sample(it) }
        val invalid = listOf(
            sample(41).copy(valid = false), sample(42).copy(calmness = Double.NaN, alpha = Double.NaN),
            sample(43).copy(calmness = Double.POSITIVE_INFINITY, beta = Double.POSITIVE_INFINITY),
            sample(44).copy(calmness = null, alpha = null), sample(45).copy(calmness = -0.1, beta = 1.1),
            sample(46).copy(calmness = null, eegBandsAvailable = false, beta = null),
            sample(47).copy(calmness = null, alpha = 0.0, theta = 0.0, beta = 0.0),
        )
        assertEquals(SessionScoreCalculator.calculate(values), SessionScoreCalculator.calculate(values + invalid))
        assertEquals(SessionScores(), SessionScoreCalculator.calculate(invalid))
    }

    @Test fun legacyAndMissingDimensionsHaveNoOverallGrade() {
        assertEquals(SessionScores(), SessionScoreCalculator.calculate((1..40).map { sample(it).copy(algorithmVersion = 0) }))
        val scores = SessionScoreCalculator.calculate((1..40).map { sample(it).copy(beta = null) })
        assertNotNull(scores.calm)
        assertNotNull(scores.stability)
        assertNull(scores.focus)
        assertNull(scores.overall)
        assertNull(scores.grade)
        assertEquals(SessionScores(), SessionScoreCalculator.calculate(emptyList()))
    }

    @Test fun persistedBandAvailabilityMatchesLiveSummaryWithoutTransientFlag() {
        val live = (1..40).map { sample(it) }
        assertEquals(SessionScoreCalculator.calculate(live),
            SessionScoreCalculator.calculate(live.map { it.copy(eegBandsAvailable = false) }))
    }

    @Test fun allGradeBoundariesUseUnroundedOverallScore() {
        val boundaries = listOf(60.0 to "C", 70.0 to "B−", 75.0 to "B", 80.0 to "B+", 85.0 to "A−", 90.0 to "A", 95.0 to "A+")
        val below = listOf("D", "C", "B−", "B", "B+", "A−", "A")
        boundaries.forEachIndexed { index, (score, grade) ->
            assertEquals(grade, SessionScoreCalculator.gradeFor(score))
            assertEquals(below[index], SessionScoreCalculator.gradeFor(score - 0.001))
        }
        assertEquals("D", SessionScoreCalculator.gradeFor(0.0))
        assertEquals("A+", SessionScoreCalculator.gradeFor(100.0))
    }
}
