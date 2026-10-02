package com.blue.hush

import androidx.test.platform.app.InstrumentationRegistry
import com.blue.hush.session.MusicTrack
import com.blue.hush.session.ResultLabel
import com.blue.hush.storage.HushDatabase
import org.junit.Assert.*
import org.junit.Test

class BundledHistoryTest {
    @Test fun restorationPreservesSamplesAvoidsDuplicatesAndOnlyRestoresBundledDeletions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        context.deleteDatabase("hush.db")
        try {
            HushDatabase(context).use { database ->
                database.restoreBundledHistory(context)
                val originals = database.loadSummaries().sortedBy { it.startedAt }
                assertEquals(2, originals.size)
                val samples = originals.associate { it.startedAt to database.loadSamples(it.id) }
                originals.forEach {
                    assertEquals(600, it.sampleCount)
                    assertEquals((1..600).toList(), samples.getValue(it.startedAt).map { sample -> sample.elapsedSeconds })
                    assertNotNull(it.calm)
                }
                database.restoreBundledHistory(context)
                assertEquals(originals.map { it.id }, database.loadSummaries().sortedBy { it.startedAt }.map { it.id })

                val removed = originals.first()
                database.deleteSession(removed.id)
                assertEquals(1, database.loadSummaries().size)
                assertTrue(database.loadSamples(removed.id).isEmpty())
                val other = database.insertSession(1, 600, MusicTrack.RAIN)
                database.finishSession(other, 1000, 1, ResultLabel.STEADY)
                database.deleteSession(other)

                // A cold-start restore uses the same snapshot, even when local IDs have changed.
                HushDatabase(context).use { reopened ->
                    reopened.restoreBundledHistory(context)
                    val restored = reopened.loadSummaries().sortedBy { it.startedAt }
                    assertEquals(2, restored.size)
                    originals.zip(restored).forEach { (before, after) ->
                        assertEquals(before.copy(id = after.id), after)
                        assertEquals(samples.getValue(before.startedAt), reopened.loadSamples(after.id))
                    }
                    assertTrue(reopened.loadSamples(other).isEmpty())
                }
            }
        } finally {
            context.deleteDatabase("hush.db")
        }
    }
}
