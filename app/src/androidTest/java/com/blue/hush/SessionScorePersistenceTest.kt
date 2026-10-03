package com.blue.hush

import androidx.test.platform.app.InstrumentationRegistry
import com.blue.hush.processing.SessionScoreCalculator
import com.blue.hush.session.*
import com.blue.hush.storage.HushDatabase
import org.junit.Assert.*
import org.junit.Test

class SessionScorePersistenceTest {
    @Test fun historySummariesCountMeasuredCalmnessAndPreserveEmptySessionsAndOrdering() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        context.deleteDatabase("hush.db")
        try {
            HushDatabase(context).use { database ->
                val shortId = database.insertSession(1000, 600, MusicTrack.RAIN)
                database.insertSample(shortId, StateSample(1, calmness = 0.8, valid = true, algorithmVersion = 5))
                database.insertSample(shortId, StateSample(2, algorithmVersion = 5))
                database.finishSession(shortId, 3000, 2, ResultLabel.STEADY)

                val measuredId = database.insertSession(4000, 600, MusicTrack.RAIN)
                database.insertSample(measuredId, StateSample(1, valid = true, calmness = 0.4, algorithmVersion = 5))
                database.insertSample(measuredId, StateSample(2, valid = true, calmness = 0.6, algorithmVersion = 5))
                database.insertSample(measuredId, StateSample(3, valid = true, algorithmVersion = 5))
                database.insertSample(measuredId, StateSample(4, calmness = 0.9, algorithmVersion = 5))
                database.insertSample(measuredId, StateSample(5, valid = true, calmness = 2.0, algorithmVersion = 5))
                database.finishSession(measuredId, 9000, 5, ResultLabel.VARIABLE)

                val emptyId = database.insertSession(9000, 600, MusicTrack.OCEAN)
                database.finishSession(emptyId, 9000, 0, ResultLabel.STEADY)
                database.insertSession(10000, 600, MusicTrack.FIREPLACE)

                val summaries = database.loadSummaries()
                assertEquals(listOf(emptyId, measuredId, shortId), summaries.map { it.id })
                assertEquals(listOf(0, 5, 2), summaries.map { it.sampleCount })
                assertEquals(listOf(0, 4, 1), summaries.map { it.validSampleCount })
                assertEquals(listOf(0, 2, 1), summaries.map { it.resultSampleCount })
                assertTrue(summaries.all { it.calm == null })
                assertEquals(MusicTrack.RAIN, summaries.last().track)
                assertEquals(ResultLabel.VARIABLE, summaries[1].result)
            }
        } finally {
            context.deleteDatabase("hush.db")
        }
    }

    @Test fun completedScoresMatchPersistedDetailsIncludingGapsAndTransientFlags() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        context.deleteDatabase("hush.db")
        try {
            val samples = (1..60).map { second ->
                StateSample(second, alpha = 0.3, theta = 0.2, beta = if (second <= 10) 0.2 else 0.25,
                    calmness = if (second <= 9 || second == 45) null else 0.8,
                    stillness = if (second == 30) null else 0.9, heartRateBpm = if (second < 15) null else 72.0,
                    valid = second != 45, eegBandsAvailable = second % 3 != 0, algorithmVersion = 5)
            }
            HushDatabase(context).use { database ->
                val id = database.insertSession(1000, 600, MusicTrack.RAIN)
                samples.forEach { database.insertSample(id, it) }
                database.finishSession(id, 61000, 60, ResultLabel.STEADY)
                val scores = SessionScoreCalculator.calculate(samples)
                assertNotNull(scores.calm)
                assertNotNull(scores.stability)
                assertNotNull(scores.heartRateBpm)
                assertEquals(scores, SessionScoreCalculator.calculate(database.loadSamples(id)))
                assertEquals(scores.calm, database.loadSummaries().single().calm)
            }
        } finally {
            context.deleteDatabase("hush.db")
        }
    }
}
