package com.blue.hush

import com.blue.hush.processing.SignalProcessor
import com.choosemuse.libmuse.MuseDataPacketType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SignalProcessorTest {
    @Test fun diagnosticsDistinguishMissingBandsFromQualityFilteringAndResetEachSecond() {
        val processor = SignalProcessor()
        processor.accept(MuseDataPacketType.EEG, listOf(1.0), 0)
        processor.accept(MuseDataPacketType.ACCELEROMETER, listOf(0.0, 0.0, 1.0), 0)
        processor.nextSample(1, 1000)
        assertEquals("EEG_WITHOUT_BANDS", processor.latestDiagnostics.status)
        assertEquals(1, processor.latestDiagnostics.rawEegPackets)
        assertEquals(1, processor.latestDiagnostics.accelerationPackets)

        processor.accept(MuseDataPacketType.IS_GOOD, listOf(0.0), 1100)
        processor.accept(MuseDataPacketType.ALPHA_RELATIVE, listOf(0.4), 1100)
        processor.accept(MuseDataPacketType.THETA_RELATIVE, listOf(0.3), 1100)
        processor.accept(MuseDataPacketType.BETA_RELATIVE, listOf(0.2), 1100)
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(0.0), 1100)
        val rejected = processor.nextSample(2, 2000)
        assertNull(rejected.calmness)
        assertEquals("LOW_QUALITY", processor.latestDiagnostics.status)
        assertEquals(listOf(1, 1, 1), processor.latestDiagnostics.bandPackets)
        assertEquals(1, processor.latestDiagnostics.numericChannels)
        assertEquals(0, processor.latestDiagnostics.usableChannels)
        assertEquals(0, processor.latestDiagnostics.rawEegPackets)

        processor.nextSample(3, 3000)
        assertEquals("NO_EEG_PACKETS", processor.latestDiagnostics.status)
        assertEquals(listOf(0, 0, 0), processor.latestDiagnostics.bandPackets)

        // A stale quality flag leaves arriving bands untrusted rather than accepting them.
        processor.accept(MuseDataPacketType.ALPHA_RELATIVE, listOf(0.4), 3500)
        processor.accept(MuseDataPacketType.THETA_RELATIVE, listOf(0.3), 3500)
        processor.accept(MuseDataPacketType.BETA_RELATIVE, listOf(0.2), 3500)
        assertFalse(processor.nextSample(4, 4000).eegBandsAvailable)
        assertFalse(processor.latestDiagnostics.qualityFresh)
        assertEquals("QUALITY_UNKNOWN", processor.latestDiagnostics.status)
        processor.setCollecting(false)
        processor.accept(MuseDataPacketType.EEG, listOf(1.0), 4500)
        processor.nextSample(5, 5000)
        assertEquals("NOT_COLLECTING", processor.latestDiagnostics.status)
        assertEquals(0, processor.latestDiagnostics.rawEegPackets)
    }

    @Test fun repeatedCollectingNotificationKeepsTheCurrentPacketWindow() {
        val processor = SignalProcessor()
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(1.0))
        processor.accept(MuseDataPacketType.ALPHA_RELATIVE, listOf(0.4))
        processor.setCollecting(true)
        processor.accept(MuseDataPacketType.THETA_RELATIVE, listOf(0.3))
        processor.accept(MuseDataPacketType.BETA_RELATIVE, listOf(0.2))
        assertTrue(processor.nextSample(1).eegBandsAvailable)
    }

    @Test fun repeatedCollectingNotificationDoesNotRestartCalibration() {
        val processor = SignalProcessor()
        var sample = processor.nextSample(0, 0)
        for (second in 1..10) {
            processor.setCollecting(true)
            processor.accept(MuseDataPacketType.IS_GOOD, listOf(1.0))
            processor.accept(MuseDataPacketType.ALPHA_RELATIVE, listOf(0.4))
            processor.accept(MuseDataPacketType.THETA_RELATIVE, listOf(0.3))
            processor.accept(MuseDataPacketType.BETA_RELATIVE, listOf(0.2))
            sample = processor.nextSample(second)
        }
        assertEquals(10, processor.calibrationSeconds)
        assertTrue(sample.calmness != null)
    }

    @Test fun ppgPipelineJoinsFusionAndPauseOrBadQualityDropsHeartRate() {
        val processor = SignalProcessor()
        var sample = processor.nextSample(0, 0)
        for (second in 1..22) {
            processor.accept(MuseDataPacketType.IS_GOOD, listOf(1.0), second * 1000L - 500)
            processor.accept(MuseDataPacketType.ALPHA_RELATIVE, listOf(0.4), second * 1000L - 500)
            processor.accept(MuseDataPacketType.THETA_RELATIVE, listOf(0.3), second * 1000L - 500)
            processor.accept(MuseDataPacketType.BETA_RELATIVE, listOf(0.2), second * 1000L - 500)
            repeat(64) { offset ->
                val index = (second - 1) * 64 + offset
                processor.accept(MuseDataPacketType.PPG, listOf(1000 + 50 * sin(2 * PI * 1.2 * index / 64), 1000.0), index * 1000L / 64)
            }
            processor.accept(MuseDataPacketType.ACCELEROMETER, listOf(0.0, 0.0, 1.0), second * 1000L)
            sample = processor.nextSample(second, second * 1000L)
            if (second < 8) assertNull(sample.heartRateBpm)
            if (second < 10) assertNull(sample.calmness)
        }
        assertEquals(72.0, sample.heartRateBpm!!, 3.0)
        assertEquals(0.5, sample.calmness!!, 0.000001)
        processor.accept(MuseDataPacketType.IS_HEART_GOOD, listOf(0.0), 22001)
        assertNull(processor.nextSample(23, 22002).heartRateBpm)
        processor.setCollecting(false)
        processor.setCollecting(true)
        assertNull(processor.nextSample(24, 24000).heartRateBpm)
        assertEquals(10, processor.calibrationSeconds)
        processor.reset()
        assertEquals(0, processor.calibrationSeconds)
    }
    @Test fun gravityRotationAndAlternatingAccelerationDoNotCancel() {
        val processor = SignalProcessor(smoothingFactor = 1.0)
        repeat(52) { processor.accept(MuseDataPacketType.ACCELEROMETER, listOf(0.0, 0.0, 1.0), it * 1000L / 52) }
        assertEquals(1.0, processor.nextSample(1, 1000).stillness!!, 0.000001)
        repeat(52) { processor.accept(MuseDataPacketType.ACCELEROMETER, listOf(0.0, 0.0, if (it % 2 == 0) 0.8 else 1.2), 1000 + it * 1000L / 52) }
        assertTrue(processor.nextSample(2, 2000).stillness!! < 0.1)
        repeat(52) { processor.accept(MuseDataPacketType.ACCELEROMETER, listOf(1.0, 0.0, 0.0), 2000 + it * 1000L / 52) }
        assertTrue(processor.nextSample(3, 3000).stillness!! < 0.1)
        assertNull(processor.nextSample(4, 4000).stillness)
    }

    @Test fun explicitPoorQualityRejectsEegButKeepsMotionMeasured() {
        val processor = SignalProcessor()
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(0.0), 0)
        processor.accept(MuseDataPacketType.ALPHA_RELATIVE, listOf(0.4), 0)
        processor.accept(MuseDataPacketType.THETA_RELATIVE, listOf(0.3), 0)
        processor.accept(MuseDataPacketType.BETA_RELATIVE, listOf(0.2), 0)
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(0.0), 1)
        processor.accept(MuseDataPacketType.ACCELEROMETER, listOf(0.0, 0.0, 1.0), 1)
        val sample = processor.nextSample(1, 1000)
        assertTrue(sample.valid)
        assertFalse(sample.eegBandsAvailable)
        assertNull(sample.alpha)
        assertNull(sample.calmness)
        assertEquals(0, processor.calibrationSeconds)
    }

    @Test fun pausedCallbacksCannotLeakIntoResumeOrNewSession() {
        val processor = SignalProcessor()
        processor.accept(MuseDataPacketType.ACCELEROMETER, listOf(0.0, 0.0, 1.0))
        processor.setCollecting(false)
        processor.accept(MuseDataPacketType.ACCELEROMETER, listOf(0.0, 0.0, 1.0))
        processor.setCollecting(true)
        assertFalse(processor.nextSample(1).valid)
        processor.reset()
        assertEquals(0, processor.calibrationSeconds)
    }
    @Test
    fun aggregatesAndSmoothsValidSecond() {
        val processor = SignalProcessor(smoothingFactor = 0.5)
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(1.0, 1.0))
        repeat(4) {
            processor.accept(MuseDataPacketType.ALPHA_RELATIVE, listOf(0.4, 0.6))
            processor.accept(MuseDataPacketType.THETA_RELATIVE, listOf(0.2, 0.4))
            processor.accept(MuseDataPacketType.BETA_RELATIVE, listOf(0.1, 0.3))
            processor.accept(MuseDataPacketType.ACCELEROMETER, listOf(0.0, 0.0, 1.0))
        }
        val sample = processor.nextSample(1)
        assertTrue(sample.valid)
        assertTrue(sample.eegBandsAvailable)
        assertTrue(sample.alpha!! in 0.49..0.51)
        assertTrue(sample.stillness!! > 0.99)
    }

    @Test
    fun sensorPacketsRemainUsableWhenMuseReportsPoorSignalQuality() {
        val processor = SignalProcessor()
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(0.0))
        processor.accept(MuseDataPacketType.ACCELEROMETER, listOf(0.0, 0.0, 1.0))
        assertTrue(processor.nextSample(1).valid)
    }

    @Test
    fun noSensorPacketsProduceExplicitGap() {
        assertFalse(SignalProcessor().nextSample(1).valid)
    }

    @Test
    fun liveBandAvailabilityDoesNotCarryAcrossSeconds() {
        val processor = SignalProcessor()
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(1.0))
        processor.accept(MuseDataPacketType.ALPHA_RELATIVE, listOf(0.4))
        processor.accept(MuseDataPacketType.THETA_RELATIVE, listOf(0.3))
        processor.accept(MuseDataPacketType.BETA_RELATIVE, listOf(0.2))
        assertTrue(processor.nextSample(1).eegBandsAvailable)
        processor.accept(MuseDataPacketType.ALPHA_RELATIVE, listOf(0.4))
        processor.accept(MuseDataPacketType.THETA_RELATIVE, listOf(0.3))
        val partial = processor.nextSample(2)
        assertTrue(partial.valid)
        assertFalse(partial.eegBandsAvailable)
        assertNull(partial.alpha)
        assertNull(partial.theta)
        assertNull(partial.beta)
        assertFalse(processor.nextSample(3).eegBandsAvailable)
    }

    @Test
    fun nonFiniteAndOutOfRangeBandsAreNotMeasuredEeg() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1).forEach { invalid ->
            val processor = SignalProcessor()
            processor.accept(MuseDataPacketType.IS_GOOD, listOf(1.0))
            processor.accept(MuseDataPacketType.ALPHA_RELATIVE, listOf(0.4))
            processor.accept(MuseDataPacketType.THETA_RELATIVE, listOf(0.3))
            processor.accept(MuseDataPacketType.BETA_RELATIVE, listOf(invalid))
            assertFalse(processor.nextSample(1).eegBandsAvailable)
        }
    }
}
