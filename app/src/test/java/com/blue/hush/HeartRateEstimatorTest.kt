package com.blue.hush

import com.blue.hush.processing.HeartRateEstimator
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class HeartRateEstimatorTest {
    @Test fun redFallbackUsesOneSamplePerPacketAndWrongCadenceIsRejected() {
        val estimator = HeartRateEstimator()
        repeat(640) { i -> estimator.accept(Double.NaN, 1000 + 50 * sin(2 * PI * i / 64), i * 1000L / 64) }
        assertEquals(60.0, estimator.estimate(639 * 1000L / 64)!!, 2.0)
        val fast = HeartRateEstimator()
        repeat(640) { i -> fast.accept(1000 + 50 * sin(2 * PI * i / 64), null, i * 1000L / 128) }
        assertNull(fast.estimate(639 * 1000L / 128))
    }
    private fun feed(estimator: HeartRateEstimator, bpm: Double, count: Int = 640) {
        repeat(count) { i -> estimator.accept(1000 + 50 * sin(2 * PI * bpm / 60 * i / 64), null, i * 1000L / 64) }
    }

    @Test fun nominalPpgEstimatesKnownPulseRates() {
        for (bpm in listOf(40.0, 48.0, 60.0, 72.0, 120.0, 168.0, 180.0)) {
            val estimator = HeartRateEstimator()
            feed(estimator, bpm)
            assertEquals(bpm, estimator.estimate(639 * 1000L / 64)!!, 3.0)
        }
    }

    @Test fun shortFlatNoisyAndNonFiniteWindowsDoNotProduceHeartRate() {
        val short = HeartRateEstimator()
        feed(short, 72.0, 400)
        assertNull(short.estimate(399 * 1000L / 64))
        val flat = HeartRateEstimator()
        repeat(640) { flat.accept(1000.0, null, it * 1000L / 64) }
        assertNull(flat.estimate(639 * 1000L / 64))
        val noise = HeartRateEstimator()
        val random = Random(42)
        repeat(640) { noise.accept(1000 + random.nextDouble(-50.0, 50.0), null, it * 1000L / 64) }
        assertNull(noise.estimate(639 * 1000L / 64))
        noise.accept(Double.NaN, Double.NaN, 10_000)
        assertNull(noise.estimate(10_000))
    }

    @Test fun channelChangesQualityFailuresAndInterruptionsResetWindow() {
        val estimator = HeartRateEstimator()
        feed(estimator, 72.0)
        assertNotNull(estimator.estimate(9990))
        estimator.accept(Double.NaN, 1000.0, 10_000)
        assertNull(estimator.estimate(10_000))
        feed(estimator, 72.0)
        estimator.accept(1000.0, null, 10_000, qualityGood = false)
        assertNull(estimator.estimate(10_000))
        feed(estimator, 72.0)
        assertNull(estimator.estimate(11_000))
        estimator.accept(1000.0, null, 11_001)
        assertNull(estimator.estimate(11_001))
    }
}
