package com.blue.hush

import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.blue.hush.processing.SessionScoreCalculator
import com.blue.hush.processing.SignalProcessor
import com.blue.hush.session.MusicTrack
import com.blue.hush.storage.HushDatabase
import com.choosemuse.libmuse.Eeg
import com.choosemuse.libmuse.MuseDataPacket
import com.choosemuse.libmuse.MuseDataPacketType
import com.choosemuse.libmuse.MuseManagerAndroid
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EegPersistenceTest {
    @Test fun sdkGettersPreservePhysicalChannelOrderForRawAndRelativePackets() {
        MuseManagerAndroid.getInstance().setContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val expected = listOf(0.1, 0.2, 0.3, 0.4)
        for (type in listOf(MuseDataPacketType.EEG, MuseDataPacketType.ALPHA_RELATIVE,
                MuseDataPacketType.THETA_RELATIVE, MuseDataPacketType.BETA_RELATIVE, MuseDataPacketType.HSI_PRECISION)) {
            val packet = MuseDataPacket.makePacket(type, 0, ArrayList(expected + listOf(0.9, 0.8)))
            val channels = listOf(Eeg.EEG1, Eeg.EEG2, Eeg.EEG3, Eeg.EEG4).map(packet::getEegChannelValue)
            assertEquals(expected, channels)
        }
    }

    @Test fun persistedPoorWindowsCannotEnterHistoricalCalmness() {
        // Redirect both SQLiteOpenHelper paths to a unique cache file, never the user's history.
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("eeg-test-", ".db", target.cacheDir)
        val context = object : ContextWrapper(target) {
            override fun getDatabasePath(name: String): File = file
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(file.absolutePath, factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(file.absolutePath, factory, errorHandler)
        }
        val database = HushDatabase(context)
        val id = database.insertSession(1, 600, MusicTrack.RAIN)
        try {
            val processor = SignalProcessor()
            val samples = (1..80).map { second ->
                val at = second * 1000L
                processor.accept(MuseDataPacketType.IS_GOOD, listOf(if (second <= 40) 1.0 else 0.0), at)
                processor.accept(MuseDataPacketType.ALPHA_RELATIVE, listOf(0.4), at)
                processor.accept(MuseDataPacketType.THETA_RELATIVE, listOf(0.3), at)
                processor.accept(MuseDataPacketType.BETA_RELATIVE, listOf(0.2), at)
                processor.accept(MuseDataPacketType.ACCELEROMETER, listOf(0.0, 0.0, 1.0), at)
                processor.nextSample(second, at + 100)
            }
            samples.forEach { database.insertSample(id, it) }
            val persisted = database.loadSamples(id)
            assertEquals(80, persisted.size)
            assertTrue(persisted.takeLast(40).all {
                it.valid && it.alpha == null && it.theta == null && it.beta == null && it.calmness == null
            })
            val trustedScores = SessionScoreCalculator.calculate(samples.take(40))
            assertNotNull(trustedScores.calm)
            assertNotNull(trustedScores.stability)
            assertNull(trustedScores.heartRateBpm)
            assertEquals(trustedScores, SessionScoreCalculator.calculate(persisted))
            assertTrue(persisted.all { it.algorithmVersion == 4 })
        } finally {
            database.deleteSession(id)
            database.close()
            SQLiteDatabase.deleteDatabase(file)
        }
    }
}
