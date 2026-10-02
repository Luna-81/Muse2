package com.blue.hush

import androidx.test.platform.app.InstrumentationRegistry
import com.blue.hush.processing.SessionScoreCalculator
import com.blue.hush.session.*
import com.blue.hush.storage.HushDatabase
import org.junit.Assert.*
import org.junit.Test

class SessionScorePersistenceTest {
    @Test fun completedScoresMatchPersistedDetailsIncludingGapsAndTransientFlags() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        context.deleteDatabase("hush.db")
        try {
            val samples = (1..60).map { second ->
                StateSample(second, alpha = 0.3, theta = 0.2, beta = if (second <= 10) 0.2 else 0.25,
                    calmness = if (second <= 9 || second == 45) null else 0.8,
                    stillness = if (second == 30) null else 0.9, heartRateBpm = if (second < 15) null else 72.0,
                    valid = second != 45, eegBandsAvailable = second % 3 != 0, algorithmVersion = 4)
            }
            HushDatabase(context).use { database ->
                val id = database.insertSession(1000, 600, MusicTrack.MIST)
                samples.forEach { database.insertSample(id, it) }
                database.finishSession(id, 61000, 60, ResultLabel.STEADY)
                val scores = SessionScoreCalculator.calculate(samples)
                assertNotNull(scores.calm)
                assertNotNull(scores.stability)
                assertNotNull(scores.heartRateBpm)
                assertEquals(scores, SessionScoreCalculator.calculate(database.loadSamples(id)))
            }
        } finally {
            context.deleteDatabase("hush.db")
        }
    }
}
