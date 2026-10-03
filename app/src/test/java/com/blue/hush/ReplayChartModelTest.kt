package com.blue.hush

import com.blue.hush.session.StateSample
import com.blue.hush.ui.ReplayMetric
import com.blue.hush.ui.ReplayViewport
import com.blue.hush.ui.replayLabelTops
import org.junit.Assert.*
import org.junit.Test

class ReplayChartModelTest {
    @Test fun zoomKeepsAnchorAndClampsPanAndScale() {
        val zoomed = ReplayViewport().transform(2f, 0.25f, 0f, 600)
        assertEquals(0.5f, zoomed.span, 0.0001f)
        assertEquals(150f, zoomed.secondAt(0.25f, 600), 0.0001f)
        assertEquals(0f, zoomed.transform(1f, 0.5f, 10f, 600).start, 0f)
        val end = zoomed.transform(1f, 0.5f, -10f, 600)
        assertEquals(1f, end.end, 0f)
        assertEquals(0.1f, zoomed.transform(100f, 0.5f, 0f, 600).span, 0f)
        assertEquals(ReplayViewport(), zoomed.transform(0.001f, 0.5f, 0f, 600))
        assertEquals(zoomed, zoomed.transform(Float.NaN, 0.5f, 0f, 600))
        assertEquals(ReplayViewport(), ReplayViewport().transform(10f, 0.5f, 0f, 1))
        assertEquals(0f, zoomed.reveal(0f, 600).start, 0f)
        assertEquals(1f, zoomed.reveal(600f, 600).end, 0f)
    }

    @Test fun fixedMappingAndDisplayKeepActualBpm() {
        assertEquals(0.0, ReplayMetric.HEART_RATE.level(40.0), 0.0)
        assertEquals(1.0, ReplayMetric.HEART_RATE.level(180.0), 0.0)
        assertEquals(0.5, ReplayMetric.HEART_RATE.level(110.0), 0.0)
        val sample = StateSample(1, alpha = 0.3214, valid = true, eegBandsAvailable = true,
            calmness = 0.765, heartRateBpm = 110.0)
        assertEquals("Heart Rate 110 BPM", ReplayMetric.HEART_RATE.label(sample))
        assertEquals("Alpha 32.1", ReplayMetric.ALPHA.label(sample))
        assertEquals("Calmness 77", ReplayMetric.CALMNESS.label(sample))
    }

    @Test fun missingEegDoesNotHideIndependentMeasurements() {
        val sample = StateSample(2, alpha = 0.4, stillness = 0.9, valid = true,
            heartRateBpm = 80.0, algorithmVersion = 5)
        assertNull(ReplayMetric.ALPHA.value(sample))
        assertNull(ReplayMetric.CALMNESS.value(sample))
        assertEquals(0.9, ReplayMetric.STABILITY.value(sample)!!, 0.0)
        assertEquals(80.0, ReplayMetric.HEART_RATE.value(sample)!!, 0.0)
        ReplayMetric.entries.forEach { assertNull(it.value(sample.copy(valid = false))) }
    }

    @Test fun invalidMeasurementsStayMissing() {
        val sample = StateSample(1, alpha = Double.NaN, theta = -0.1, beta = 1.1,
            stillness = Double.POSITIVE_INFINITY, calmness = -1.0, heartRateBpm = 181.0,
            valid = true, eegBandsAvailable = true)
        ReplayMetric.entries.forEach {
            assertNull(it.value(sample))
            assertEquals("${it.title} —", it.label(sample))
        }
    }

    @Test fun coincidentAndEdgeLabelsFitWithoutOverlap() {
        listOf(0f, 120f, 240f).forEach { center ->
            val heights = listOf(20f, 32f, 20f, 32f, 20f, 32f)
            val tops = replayLabelTops(List(6) { center }, heights, 240f, 4f)
            assertTrue(tops.first() >= 0)
            assertTrue(tops.last() + heights.last() <= 240f)
            for (index in 1..tops.lastIndex) assertTrue(tops[index] >= tops[index - 1] + heights[index - 1] + 4f)
        }
        assertTrue(replayLabelTops(emptyList(), emptyList(), 240f, 4f).isEmpty())
    }
}
