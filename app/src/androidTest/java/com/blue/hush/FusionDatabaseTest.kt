package com.blue.hush

import androidx.test.platform.app.InstrumentationRegistry
import com.blue.hush.replay.MuseReplaySource
import com.blue.hush.session.*
import com.blue.hush.storage.HushDatabase
import org.junit.Assert.*
import org.junit.Test

class FusionDatabaseTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()

    @Test fun versionsOneTwoAndThreeUpgradeToEmptyHistory() {
        for (version in 1..3) {
            val ctx = context
            ctx.deleteDatabase("hush.db")
            ctx.getDatabasePath("hush.db").parentFile?.mkdirs()
            val oldSample = StateSample(1, 0.4, 0.3, 0.2, 0.9, valid = true)
            var id = 0L
            try {
                HushDatabase(ctx).use { database ->
                    id = database.insertSession(1000, 600, MusicTrack.MIST)
                    database.insertSample(id, oldSample)
                    database.finishSession(id, 2000, 1, ResultLabel.STEADY)
                    if (version == 2) database.writableDatabase.execSQL("CREATE TABLE imported_sessions (session_id INTEGER PRIMARY KEY)")
                    recreateLegacySampleSchema(database.writableDatabase, version)
                }
                HushDatabase(ctx).use { database ->
                    assertEquals(5, database.readableDatabase.version)
                    assertTrue(database.loadSamples(id).isEmpty())
                    assertTrue(database.loadSummaries().isEmpty())
                    database.deleteSession(id)
                    assertTrue(database.loadSamples(id).isEmpty())
                }
            } finally { ctx.deleteDatabase("hush.db") }
        }
    }

    @Test fun storedSamplesRoundTripAndMissingCalmnessDoesNotCountAsAnAssessment() {
        val ctx = context
        ctx.deleteDatabase("hush.db")
        ctx.getDatabasePath("hush.db").parentFile?.mkdirs()
        val measured = StateSample(2, 0.4, 0.3, 0.2, 0.9, true, true, 72.0, 0.65, 4)
        try {
            var id = 0L
            HushDatabase(ctx).use { database ->
                id = database.insertSession(1000, 600, MusicTrack.TIDE)
                database.insertSample(id, measured.copy(elapsedSeconds = 1, calmness = null))
                database.insertSample(id, measured)
                database.insertSample(id, StateSample(3, algorithmVersion = 4))
                database.finishSession(id, 4000, 3, ResultLabel.STEADY)
            }
            HushDatabase(ctx).use { database ->
                assertEquals(measured, database.loadSamples(id)[1])
                assertNull(database.loadSamples(id).last().calmness)
                assertEquals(2, database.loadSummaries().single().validSampleCount)
                assertEquals(1, database.loadSummaries().single().resultSampleCount)
            }
        } finally { ctx.deleteDatabase("hush.db") }
    }

    @Test fun loadingSimulationDoesNotCreateHistoryAndNewSimulationPersistsWithoutHeartRate() {
        val ctx = context
        ctx.deleteDatabase("hush.db")
        try {
            var id = 0L
            val replay = MuseReplaySource.load(ctx)
            assertTrue(MuseReplaySource.isUsable(replay))
            assertTrue(replay.all { it.algorithmVersion == 5 && it.heartRateBpm == null })
            assertTrue(replay.all { it.calmness != null })
            val estimator = com.blue.hush.processing.CalmnessEstimator()
            assertEquals(replay.map { it.calmness }, replay.map { estimator.process(it).calmness })
            HushDatabase(ctx).use { database ->
                assertTrue(database.loadSummaries().isEmpty())
                id = database.insertSession(1000, 600, MusicTrack.RAIN)
                replay.forEach { database.insertSample(id, it) }
                database.finishSession(id, 601000, 600, ResultLabel.STEADY)
            }
            HushDatabase(ctx).use { database ->
                val stored = database.loadSamples(id)
                assertEquals(replay, stored)
                assertEquals(listOf(id), database.loadSummaries().map { it.id })
                assertEquals(600, database.loadSummaries().single().resultSampleCount)
                val metrics = com.blue.hush.processing.SessionScoreCalculator.calculate(stored)
                assertNotNull(metrics.calm)
                assertNotNull(metrics.stability)
                assertNull(metrics.heartRateBpm)
                database.deleteSession(id)
            }
            HushDatabase(ctx).use { database ->
                assertTrue(database.loadSummaries().isEmpty())
                assertTrue(database.loadSamples(id).isEmpty())
            }
        } finally { ctx.deleteDatabase("hush.db") }
    }
}
