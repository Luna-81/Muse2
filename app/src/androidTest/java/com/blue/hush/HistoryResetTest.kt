package com.blue.hush

import androidx.test.platform.app.InstrumentationRegistry
import com.blue.hush.session.*
import com.blue.hush.storage.HushDatabase
import org.junit.Assert.*
import org.junit.Test

class HistoryResetTest {
    @Test fun versionFourUpgradeClearsAllHistoryOnceAndKeepsNewSessionsAfterReopening() {
        // Separate storage prevents the migration fixture from clearing the user's database.
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        context.deleteDatabase("hush.db")
        var completedId = 0L
        var unfinishedId = 0L
        var newId = 0L
        val sample = StateSample(1, alpha = 0.4, theta = 0.3, beta = 0.2, stillness = 0.9,
            heartRateBpm = 72.0, calmness = 0.5, valid = true, eegBandsAvailable = true, algorithmVersion = 4)
        try {
            HushDatabase(context).use { database ->
                completedId = database.insertSession(1000, 600, MusicTrack.RAIN)
                unfinishedId = database.insertSession(2000, 600, MusicTrack.OCEAN)
                database.finishSession(completedId, 2000, 1, ResultLabel.STEADY)
                val db = database.writableDatabase
                // Version four also contained an automatically imported session with reserved ID -1.
                db.execSQL("INSERT INTO sessions (id, started_at, ended_at, planned_seconds, actual_seconds, track) VALUES (-1, 0, 600000, 600, 600, 'MIST')")
                listOf(completedId, unfinishedId, -1L).forEach { database.insertSample(it, sample.copy(algorithmVersion = 3)) }
                db.version = 4
            }
            HushDatabase(context).use { database ->
                val db = database.writableDatabase
                assertEquals(5, db.version)
                assertTrue(database.loadSummaries().isEmpty())
                listOf("samples", "sessions").forEach { table ->
                    db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(0, cursor.getInt(0))
                    }
                }
                listOf(completedId, unfinishedId, -1L).forEach { assertTrue(database.loadSamples(it).isEmpty()) }
                newId = database.insertSession(3000, 600, MusicTrack.RAIN)
                database.insertSample(newId, sample)
                database.finishSession(newId, 4000, 1, ResultLabel.STEADY)
            }
            repeat(2) {
                HushDatabase(context).use { database ->
                    assertEquals(5, database.readableDatabase.version)
                    assertEquals(listOf(newId), database.loadSummaries().map { it.id })
                    assertEquals(listOf(sample), database.loadSamples(newId))
                }
            }
        } finally { context.deleteDatabase("hush.db") }
    }
}
