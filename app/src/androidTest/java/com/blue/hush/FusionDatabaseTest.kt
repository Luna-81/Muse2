package com.blue.hush

import androidx.test.platform.app.InstrumentationRegistry
import com.blue.hush.replay.MuseReplaySource
import com.blue.hush.session.*
import com.blue.hush.storage.HushDatabase
import org.junit.Assert.*
import org.junit.Test

class FusionDatabaseTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()

    @Test fun versionsOneTwoAndThreePreserveUserSessionsAndDoNotInventCalmness() {
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
                    assertEquals(4, database.readableDatabase.version)
                    assertEquals(listOf(oldSample), database.loadSamples(id))
                    assertEquals(1, database.loadSummaries().single().resultSampleCount)
                    database.deleteSession(id)
                    assertTrue(database.loadSamples(id).isEmpty())
                }
            } finally { ctx.deleteDatabase("hush.db") }
        }
    }

    @Test fun newSamplesRoundTripAndCalibrationDoesNotCountAsAnAssessment() {
        val ctx = context
        ctx.deleteDatabase("hush.db")
        ctx.getDatabasePath("hush.db").parentFile?.mkdirs()
        val measured = StateSample(2, 0.4, 0.3, 0.2, 0.9, true, true, 72.0, 0.65, 1)
        try {
            var id = 0L
            HushDatabase(ctx).use { database ->
                id = database.insertSession(1000, 600, MusicTrack.TIDE)
                database.insertSample(id, measured.copy(elapsedSeconds = 1, calmness = null))
                database.insertSample(id, measured)
                database.insertSample(id, StateSample(3, algorithmVersion = 1))
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

    @Test fun bundledReplayBackfillsOnceWithoutAddingHeartRateOrChangingUserHistory() {
        val ctx = context
        ctx.deleteDatabase("hush.db")
        ctx.getDatabasePath("hush.db").parentFile?.mkdirs()
        val replay = MuseReplaySource.load(ctx)
        assertTrue(MuseReplaySource.isUsable(replay))
        try {
            HushDatabase(ctx).use { database ->
                val legacy = replay.map { it.copy(calmness = null, algorithmVersion = 0, eegBandsAvailable = false) }
                database.ensureBundledSimulation(legacy)
                val userId = database.insertSession(System.currentTimeMillis(), 600, MusicTrack.MIST)
                database.insertSample(userId, legacy.first())
                database.finishSession(userId, System.currentTimeMillis(), 1, ResultLabel.STEADY)
                database.ensureBundledSimulation(replay)
                database.ensureBundledSimulation(replay)
                assertEquals(replay, database.loadSamples(BUNDLED_SIMULATION_SESSION_ID))
                assertTrue(database.loadSamples(BUNDLED_SIMULATION_SESSION_ID).all { it.heartRateBpm == null })
                assertNull(database.loadSamples(userId).single().calmness)
                assertEquals(591, database.loadSummaries().last().resultSampleCount)
            }
        } finally { ctx.deleteDatabase("hush.db") }
    }
}
