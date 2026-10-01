package com.blue.hush

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blue.hush.session.BUNDLED_SIMULATION_SESSION_ID
import com.blue.hush.session.MusicTrack
import com.blue.hush.session.ResultLabel
import com.blue.hush.session.StateSample
import com.blue.hush.storage.HushDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledSimulationHistoryTest {
    @Test fun deletionRemovesSamplesAndOnlyBundledSessionReturnsAfterReopening() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        context.deleteDatabase("hush.db")
        context.getDatabasePath("hush.db").parentFile?.mkdirs()
        val replay = listOf(StateSample(1, alpha = 0.3, valid = true))
        try {
            HushDatabase(context).use { database ->
                val first = database.insertSession(1_700_000_000_000L, 600, MusicTrack.MIST)
                val second = database.insertSession(1_700_001_000_000L, 600, MusicTrack.TIDE)
                for (id in listOf(first, second)) {
                    database.insertSample(id, replay.first())
                    database.finishSession(id, 1_700_002_000_000L, 1, ResultLabel.STEADY)
                }
                database.ensureBundledSimulation(replay)
                database.deleteSession(first)
                database.deleteSession(first)
                assertTrue(database.loadSamples(first).isEmpty())
                assertEquals(listOf(second, BUNDLED_SIMULATION_SESSION_ID), database.loadSummaries().map { it.id })
                assertEquals(replay, database.loadSamples(second))
                database.deleteSession(BUNDLED_SIMULATION_SESSION_ID)
                assertTrue(database.loadSamples(BUNDLED_SIMULATION_SESSION_ID).isEmpty())
            }
            HushDatabase(context).use { database ->
                database.ensureBundledSimulation(replay)
                assertEquals(2, database.loadSummaries().size)
                assertEquals(BUNDLED_SIMULATION_SESSION_ID, database.loadSummaries().last().id)
                assertEquals(replay, database.loadSamples(BUNDLED_SIMULATION_SESSION_ID))
            }
        } finally {
            context.deleteDatabase("hush.db")
        }
    }

    @Test fun versionOneUpgradePreservesExistingHistoryAndAllowsDeletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        context.deleteDatabase("hush.db")
        context.getDatabasePath("hush.db").parentFile?.mkdirs()
        val replay = listOf(StateSample(1, alpha = 0.3, valid = true))
        try {
            HushDatabase(context).use { database ->
                database.ensureBundledSimulation(replay)
                recreateLegacySampleSchema(database.writableDatabase, 1)
            }
            HushDatabase(context).use { database ->
                assertEquals(4, database.readableDatabase.version)
                database.ensureBundledSimulation(replay)
                assertEquals(1, database.loadSummaries().size)
                assertEquals(replay, database.loadSamples(BUNDLED_SIMULATION_SESSION_ID))
                database.deleteSession(BUNDLED_SIMULATION_SESSION_ID)
                database.ensureBundledSimulation(replay)
                assertEquals(1, database.loadSummaries().size)
                assertEquals(replay, database.loadSamples(BUNDLED_SIMULATION_SESSION_ID))
            }
        } finally {
            context.deleteDatabase("hush.db")
        }
    }

    @Test fun versionTwoImportMarkerDoesNotPreventRestoringBundledHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        context.deleteDatabase("hush.db")
        context.getDatabasePath("hush.db").parentFile?.mkdirs()
        val replay = listOf(StateSample(1, alpha = 0.3, valid = true))
        try {
            HushDatabase(context).use { database ->
                val db = database.writableDatabase
                db.execSQL("CREATE TABLE imported_sessions (session_id INTEGER PRIMARY KEY)")
                db.execSQL("INSERT INTO imported_sessions (session_id) VALUES (?)", arrayOf(BUNDLED_SIMULATION_SESSION_ID))
                recreateLegacySampleSchema(db, 2)
            }
            HushDatabase(context).use { database ->
                database.ensureBundledSimulation(replay)
                assertEquals(4, database.readableDatabase.version)
                assertEquals(BUNDLED_SIMULATION_SESSION_ID, database.loadSummaries().single().id)
                assertEquals(replay, database.loadSamples(BUNDLED_SIMULATION_SESSION_ID))
            }
        } finally {
            context.deleteDatabase("hush.db")
        }
    }

    @Test fun importIsIdempotentAndOlderThanExistingSessions() {
        // Device-protected storage is separate from the app's ordinary session database.
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        context.deleteDatabase("hush.db")
        context.getDatabasePath("hush.db").parentFile?.mkdirs()
        try {
            HushDatabase(context).use { database ->
                val existingStart = 1_700_000_000_000L
                val existingId = database.insertSession(existingStart, 600, MusicTrack.TIDE)
                database.finishSession(existingId, existingStart + 600_000L, 600, ResultLabel.STEADY)
                val replay = (1..600).map { second ->
                    StateSample(second, alpha = 0.3, theta = 0.3, beta = 0.3, stillness = 0.9, valid = true)
                }

                database.ensureBundledSimulation(replay)
                database.ensureBundledSimulation(replay)

                val history = database.loadSummaries()
                assertEquals(2, history.size)
                assertEquals(BUNDLED_SIMULATION_SESSION_ID, history.last().id)
                assertTrue(history.last().startedAt < existingStart)
                assertEquals(600, database.loadSamples(BUNDLED_SIMULATION_SESSION_ID).size)
            }
        } finally {
            context.deleteDatabase("hush.db")
        }
    }
}
