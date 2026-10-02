package com.blue.hush

import com.blue.hush.replay.MuseReplaySource
import com.blue.hush.session.StateSample
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MuseReplaySourceTest {
    private val completeReplay = (1..MuseReplaySource.DURATION_SECONDS).map { second ->
        StateSample(second, alpha = 0.3, theta = 0.3, beta = 0.3, stillness = 0.9,
            valid = true, eegBandsAvailable = true, heartRateBpm = 72.0, calmness = 0.7, algorithmVersion = 5)
    }

    @Test fun requiresConsecutiveSecondsAndFiniteMeasuredValues() {
        assertTrue(MuseReplaySource.isUsable(completeReplay))
        assertFalse(MuseReplaySource.isUsable(completeReplay.drop(10) + completeReplay[10]))
        assertFalse(MuseReplaySource.isUsable(completeReplay.toMutableList().apply { this[5] = this[5].copy(valid = false) }))
        assertFalse(MuseReplaySource.isUsable(completeReplay.toMutableList().apply { this[5] = this[5].copy(alpha = Double.NaN) }))
        assertFalse(MuseReplaySource.isUsable(completeReplay.toMutableList().apply { this[5] = this[5].copy(heartRateBpm = 200.0) }))
        assertFalse(MuseReplaySource.isUsable(completeReplay.toMutableList().apply { this[5] = this[5].copy(heartRateBpm = Double.NaN) }))
    }

    @Test fun preservesRecordedGapsWithoutRequiringEverySensorEverySecond() {
        val replay = completeReplay.toMutableList()
        replay[5] = replay[5].copy(alpha = null, theta = null, beta = null, calmness = null, eegBandsAvailable = false)
        replay[6] = replay[6].copy(heartRateBpm = null)
        replay[7] = StateSample(8, algorithmVersion = 5)
        assertTrue(MuseReplaySource.isUsable(replay))
        assertFalse(MuseReplaySource.isUsable(replay.map { it.copy(calmness = null) }))
    }
}
