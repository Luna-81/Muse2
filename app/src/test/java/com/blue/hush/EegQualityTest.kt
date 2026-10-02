package com.blue.hush

import com.blue.hush.processing.SignalProcessor
import com.blue.hush.processing.SessionScoreCalculator
import com.blue.hush.session.EegSignalStatus
import com.choosemuse.libmuse.MuseDataPacketType
import org.junit.Assert.*
import org.junit.Test

class EegQualityTest {
    @Test fun usableFitDoesNotLetInterferenceEnterCalibrationOrScores() {
        for (fit in listOf(1.0, 1.5, 2.0)) {
            val processor = SignalProcessor()
            for (second in 1..30) {
                val at = second * 1000L
                processor.accept(MuseDataPacketType.HSI_PRECISION, listOf(fit), at)
                processor.accept(MuseDataPacketType.IS_GOOD, listOf(0.0), at)
                bands(processor, at)
                val sample = processor.nextSample(second, at + 100)
                assertFalse(sample.eegBandsAvailable)
                assertNull(sample.calmness)
                assertEquals(0, processor.calibrationSeconds)
                assertEquals(EegSignalStatus.INTERFERENCE, processor.latestDiagnostics.eegStatus)
                assertEquals(listOf(3, 0, 0, 0), processor.latestDiagnostics.interferenceRejected)
            }
        }
    }

    @Test fun cleanChannelsRemainUsableWhileInterferenceAndPoorFitAreExcluded() {
        val processor = SignalProcessor(smoothingFactor = 1.0)
        processor.accept(MuseDataPacketType.HSI_PRECISION, listOf(1.0, 2.0, 4.0), 0)
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(1.0, 0.0, 1.0), 0)
        bands(processor, 100, listOf(0.4, 0.9, 0.9), listOf(0.3, 0.8, 0.8), listOf(0.2, 0.7, 0.7))
        val sample = processor.nextSample(1, 1000)
        assertEquals(0.4, sample.alpha!!, 0.000001)
        assertEquals(0.3, sample.theta!!, 0.000001)
        assertEquals(0.2, sample.beta!!, 0.000001)
        assertEquals(1, processor.latestDiagnostics.usableChannels)
        assertEquals(listOf(0, 3, 0, 0), processor.latestDiagnostics.interferenceRejected)
        assertEquals(listOf(0, 3, 3, 0), processor.latestDiagnostics.qualityRejected)
    }

    @Test fun missingExpiredInvalidAndFutureFitCannotOverrideBadFlags() {
        for ((fit, fitAt) in listOf(null to 0L, 1.0 to 0L, Double.NaN to 3000L,
                0.0 to 3000L, 5.0 to 3000L, 1.0 to 4000L, 3.0 to 3000L, 4.0 to 3000L)) {
            val processor = SignalProcessor()
            fit?.let { processor.accept(MuseDataPacketType.HSI_PRECISION, listOf(it), fitAt) }
            processor.accept(MuseDataPacketType.IS_GOOD, listOf(0.0), 3000)
            bands(processor, 3000)
            assertFalse(processor.nextSample(1, 3500).eegBandsAvailable)
            assertEquals(0, processor.latestDiagnostics.interferenceRejected.sum())
        }
    }

    @Test fun fitCannotReplaceMissingArtifactFlagsOrMissingBands() {
        val processor = SignalProcessor()
        processor.accept(MuseDataPacketType.HSI_PRECISION, listOf(1.0), 0)
        bands(processor, 2000)
        assertFalse(processor.nextSample(1, 2500).eegBandsAvailable)
        assertFalse(processor.latestDiagnostics.fitFresh)
        processor.accept(MuseDataPacketType.HSI_PRECISION, listOf(1.0), 2600)
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(1.0), 2600)
        processor.accept(MuseDataPacketType.ALPHA_RELATIVE, listOf(0.4), 2600)
        assertFalse(processor.nextSample(2, 3000).eegBandsAvailable)
        bands(processor, 3001, beta = listOf(Double.NaN))
        assertNull(processor.nextSample(3, 3500).beta)
    }

    @Test fun lateFitCannotRetroactivelyAcceptPacketsAndDisconnectClearsFit() {
        val processor = SignalProcessor()
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(0.0), 0)
        bands(processor, 100)
        processor.accept(MuseDataPacketType.HSI_PRECISION, listOf(1.0), 900)
        assertFalse(processor.nextSample(1, 1000).eegBandsAvailable)
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(1.0), 1100)
        bands(processor, 1100)
        assertTrue(processor.nextSample(2, 1200).eegBandsAvailable)
        processor.setCollecting(false)
        processor.setCollecting(true)
        bands(processor, 1300)
        assertFalse(processor.nextSample(3, 1400).eegBandsAvailable)
        assertTrue(processor.latestDiagnostics.fit.isEmpty())
        processor.accept(MuseDataPacketType.HSI_PRECISION, listOf(1.0), 1500)
        processor.reset()
        bands(processor, 1600)
        assertFalse(processor.nextSample(4, 1700).eegBandsAvailable)
    }

    @Test fun poorFitOnAllPhysicalChannelsNeverUsesAuxiliaryFit() {
        val processor = SignalProcessor()
        processor.accept(MuseDataPacketType.HSI_PRECISION, listOf(4.0, 4.0, 4.0, 4.0, 1.0), 0)
        processor.accept(MuseDataPacketType.IS_GOOD, List(5) { 1.0 }, 0)
        bands(processor, 100, List(5) { 0.4 }, List(5) { 0.3 }, List(5) { 0.2 })
        assertFalse(processor.nextSample(1, 1000).eegBandsAvailable)
        assertEquals(EegSignalStatus.LOW_QUALITY, processor.latestDiagnostics.eegStatus)
    }

    private fun bands(processor: SignalProcessor, at: Long, alpha: List<Double> = listOf(0.4),
                      theta: List<Double> = listOf(0.3), beta: List<Double> = listOf(0.2)) {
        processor.accept(MuseDataPacketType.ALPHA_RELATIVE, alpha, at)
        processor.accept(MuseDataPacketType.THETA_RELATIVE, theta, at)
        processor.accept(MuseDataPacketType.BETA_RELATIVE, beta, at)
    }

    @Test fun lateBadFlagDoesNotEraseEarlierTrustedPackets() {
        val processor = SignalProcessor(smoothingFactor = 1.0)
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(1.0), 0)
        bands(processor, 100)
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(0.0), 900)
        bands(processor, 950, alpha = listOf(0.9))
        val sample = processor.nextSample(1, 1000)
        assertTrue(sample.eegBandsAvailable)
        assertEquals(0.4, sample.alpha!!, 0.000001)
        assertEquals(1, processor.calibrationSeconds)
    }

    @Test fun lateGoodFlagDoesNotRetroactivelyTrustBadPackets() {
        val processor = SignalProcessor(smoothingFactor = 1.0)
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(0.0), 0)
        bands(processor, 100, alpha = listOf(0.9))
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(1.0), 900)
        bands(processor, 950)
        assertEquals(0.4, processor.nextSample(1, 1000).alpha!!, 0.000001)
    }

    @Test fun lateGoodFlagWithoutNewBandsCannotCompleteCalibration() {
        val processor = SignalProcessor()
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(0.0), 0)
        bands(processor, 100)
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(1.0), 900)
        val sample = processor.nextSample(1, 1000)
        assertNull(sample.alpha)
        assertNull(sample.calmness)
        assertEquals(0, processor.calibrationSeconds)
        assertEquals(EegSignalStatus.LOW_QUALITY, processor.latestDiagnostics.eegStatus)
        assertEquals(listOf(3, 0, 0, 0), processor.latestDiagnostics.qualityRejected)
    }

    @Test fun flagsMissingExpiredInvalidOrFromTheFutureCannotTrustLiveBands() {
        for ((quality, qualityAt) in listOf(null to 0L, 1.0 to 0L, Double.NaN to 3000L,
                2.0 to 3000L, -1.0 to 3000L, 1.0 to 4000L)) {
            val processor = SignalProcessor()
            quality?.let { processor.accept(MuseDataPacketType.IS_GOOD, listOf(it), qualityAt) }
            bands(processor, 3000)
            val sample = processor.nextSample(1, 3500)
            assertNull(sample.alpha)
            assertNull(sample.calmness)
            assertEquals(EegSignalStatus.UNKNOWN, processor.latestDiagnostics.eegStatus)
            assertEquals(listOf(3, 0, 0, 0), processor.latestDiagnostics.qualityUnknown)
        }
    }

    @Test fun freshAtArrivalRemainsTrustedWhenFlagExpiresBeforeTheTick() {
        val processor = SignalProcessor()
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(1.0), 0)
        bands(processor, 2000)
        assertTrue(processor.nextSample(1, 2500).eegBandsAvailable)
        assertFalse(processor.latestDiagnostics.qualityFresh)
        assertEquals(2500L, processor.latestDiagnostics.qualityAgeMillis)
    }

    @Test fun auxiliaryChannelsNeverProvideAUsableEegChannel() {
        val processor = SignalProcessor()
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(0.0, 0.0, 0.0, 0.0, 1.0), 0)
        bands(processor, 100, List(5) { 0.4 }, List(5) { 0.3 }, List(5) { 0.2 })
        assertNull(processor.nextSample(1, 1000).alpha)
        assertEquals(4, processor.latestDiagnostics.numericChannels)
        assertEquals(0, processor.latestDiagnostics.usableChannels)
        assertEquals(listOf(3, 3, 3, 3), processor.latestDiagnostics.qualityRejected)
    }

    @Test fun bandsRequireACommonTrustedChannelAndPositiveSum() {
        val processor = SignalProcessor()
        processor.accept(MuseDataPacketType.IS_GOOD, List(4) { 1.0 }, 0)
        bands(processor, 100, listOf(0.4, Double.NaN), listOf(Double.NaN, 0.3), listOf(0.2, 0.2))
        val separate = processor.nextSample(1, 1000)
        assertNull(separate.alpha)
        assertNull(separate.theta)
        assertNull(separate.beta)
        assertFalse(separate.eegBandsAvailable)
        bands(processor, 1100, listOf(0.0), listOf(0.0), listOf(0.0))
        assertNull(processor.nextSample(2, 2000).alpha)
        assertEquals(0, processor.calibrationSeconds)
    }

    @Test fun numericAndTrustedChannelsAreReportedSeparately() {
        val processor = SignalProcessor(smoothingFactor = 1.0)
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(0.0, 1.0), 0)
        bands(processor, 100, listOf(0.9, 0.4), listOf(0.8, 0.3), listOf(0.7, 0.2))
        val sample = processor.nextSample(1, 1000)
        assertEquals(0.4, sample.alpha!!, 0.000001)
        assertEquals(2, processor.latestDiagnostics.numericChannels)
        assertEquals(1, processor.latestDiagnostics.usableChannels)
        assertEquals(listOf(0, 3, 0, 0), processor.latestDiagnostics.qualityAccepted)
        assertEquals(listOf(3, 0, 0, 0), processor.latestDiagnostics.qualityRejected)
    }

    @Test fun poorAndMissingSecondsDoNotResetCalibrationOrEnterScores() {
        val processor = SignalProcessor()
        val samples = (1..10).map { second ->
            val at = second * 1000L
            processor.accept(MuseDataPacketType.IS_GOOD, listOf(if (second % 2 == 0) 1.0 else 0.0), at)
            bands(processor, at)
            processor.nextSample(second, at + 100)
        }.toMutableList()
        assertEquals(5, processor.calibrationSeconds)
        processor.nextSample(11, 11100)
        assertEquals(5, processor.calibrationSeconds)
        for (second in 12..16) {
            val at = second * 1000L
            processor.accept(MuseDataPacketType.IS_GOOD, listOf(1.0), at)
            bands(processor, at)
            samples += processor.nextSample(second, at + 100)
        }
        assertEquals(10, processor.calibrationSeconds)
        assertNotNull(samples.last().calmness)
        for (second in 17..60) {
            val at = second * 1000L
            processor.accept(MuseDataPacketType.IS_GOOD, listOf(0.0), at)
            bands(processor, at)
            processor.accept(MuseDataPacketType.ACCELEROMETER, listOf(0.0, 0.0, 1.0), at)
            samples += processor.nextSample(second, at + 100)
        }
        assertNull(SessionScoreCalculator.calculate(samples).heartRateBpm)
        assertNull(SessionScoreCalculator.calculate(samples).calm)
        assertEquals(10, samples.count { it.alpha != null })
        assertTrue(samples.takeLast(44).all { it.valid && it.alpha == null && it.calmness == null })
        assertTrue(samples.all { it.algorithmVersion == 4 })
    }

    @Test fun pauseDisconnectAndNewSessionRejectOldPacketsAndQuality() {
        val processor = SignalProcessor()
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(1.0), 0)
        bands(processor, 100)
        processor.nextSample(1, 1000)
        processor.setCollecting(false)
        bands(processor, 1100)
        processor.setCollecting(true)
        bands(processor, 1200)
        assertNull(processor.nextSample(2, 2000).alpha)
        assertEquals(0, processor.calibrationSeconds)
        assertEquals(EegSignalStatus.UNKNOWN, processor.latestDiagnostics.eegStatus)
        processor.accept(MuseDataPacketType.IS_GOOD, listOf(1.0), 2100)
        bands(processor, 2200)
        processor.reset()
        assertNull(processor.nextSample(3, 3000).alpha)
        assertEquals(0, processor.latestDiagnostics.bandPackets.sum())
        assertEquals(0, processor.latestDiagnostics.qualityAccepted.sum())
    }
}
