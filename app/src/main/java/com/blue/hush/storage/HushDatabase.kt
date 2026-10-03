package com.blue.hush.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.blue.hush.processing.SessionScoreCalculator
import com.blue.hush.session.MusicTrack
import com.blue.hush.session.ResultLabel
import com.blue.hush.session.SessionSummary
import com.blue.hush.session.StateSample

class HushDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                planned_seconds INTEGER NOT NULL,
                actual_seconds INTEGER NOT NULL DEFAULT 0,
                track TEXT NOT NULL,
                result TEXT NOT NULL DEFAULT 'STEADY'
            )""".trimIndent(),
        )
        db.execSQL(
            """CREATE TABLE samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                elapsed_seconds INTEGER NOT NULL,
                alpha REAL,
                theta REAL,
                beta REAL,
                stillness REAL,
                heart_rate_bpm REAL,
                calmness REAL,
                algorithm_version INTEGER NOT NULL DEFAULT 0,
                valid INTEGER NOT NULL,
                FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX samples_session_index ON samples(session_id, elapsed_seconds)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Unsupported schemas must fail without silently deleting the user's history.
        throw android.database.sqlite.SQLiteException("Unsupported database upgrade: $oldVersion to $newVersion")
    }

    fun deleteSession(sessionId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Foreign-key enforcement was not enabled in existing databases.
            db.delete("samples", "session_id = ?", arrayOf(sessionId.toString()))
            db.delete("sessions", "id = ?", arrayOf(sessionId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Restore the two project snapshots at startup without duplicating existing history. */
    fun restoreBundledHistory(context: Context) {
        val sessions = listOf(BundledSessionSource.EARLIEST_ASSET, BundledSessionSource.SECOND_EARLIEST_ASSET)
            .map { BundledSessionSource.load(context, it) }
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (session in sessions) {
                val startedAt = session.startedAt
                val endedAt = session.endedAt
                // Local IDs change after deletion and restoration; original timestamps identify the snapshot.
                val exists = db.rawQuery(
                    "SELECT 1 FROM sessions WHERE started_at = ? AND ended_at = ? LIMIT 1",
                    arrayOf(startedAt.toString(), endedAt.toString()),
                ).use { it.moveToFirst() }
                if (exists) continue
                val values = ContentValues().apply {
                    put("started_at", startedAt)
                    put("ended_at", endedAt)
                    put("planned_seconds", session.plannedSeconds)
                    put("actual_seconds", session.actualSeconds)
                    put("track", session.track.name)
                    put("result", session.result.name)
                }
                val id = db.insertOrThrow("sessions", null, values)
                session.samples.forEach { insertSample(db, id, it) }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun insertSession(startedAt: Long, plannedSeconds: Int, track: MusicTrack): Long {
        val values = ContentValues().apply {
            put("started_at", startedAt)
            put("planned_seconds", plannedSeconds)
            put("track", track.name)
        }
        return writableDatabase.insertOrThrow("sessions", null, values)
    }

    fun updateSessionTrack(sessionId: Long, track: MusicTrack) {
        val values = ContentValues().apply { put("track", track.name) }
        writableDatabase.update("sessions", values, "id = ?", arrayOf(sessionId.toString()))
    }

    fun insertSample(sessionId: Long, sample: StateSample) {
        insertSample(writableDatabase, sessionId, sample)
    }

    private fun insertSample(db: SQLiteDatabase, sessionId: Long, sample: StateSample) {
        val values = ContentValues().apply {
            put("session_id", sessionId)
            put("elapsed_seconds", sample.elapsedSeconds)
            sample.alpha?.let { put("alpha", it) } ?: putNull("alpha")
            sample.theta?.let { put("theta", it) } ?: putNull("theta")
            sample.beta?.let { put("beta", it) } ?: putNull("beta")
            sample.stillness?.let { put("stillness", it) } ?: putNull("stillness")
            sample.heartRateBpm?.let { put("heart_rate_bpm", it) } ?: putNull("heart_rate_bpm")
            sample.calmness?.let { put("calmness", it) } ?: putNull("calmness")
            put("algorithm_version", sample.algorithmVersion)
            put("valid", if (sample.valid) 1 else 0)
        }
        db.insertOrThrow("samples", null, values)
    }

    fun finishSession(sessionId: Long, endedAt: Long, actualSeconds: Int, result: ResultLabel) {
        val values = ContentValues().apply {
            put("ended_at", endedAt)
            put("actual_seconds", actualSeconds)
            put("result", result.name)
        }
        writableDatabase.update("sessions", values, "id = ?", arrayOf(sessionId.toString()))
    }

    fun loadSummaries(): List<SessionSummary> = readableDatabase.query(
        "sessions",
        null,
        "ended_at IS NOT NULL",
        null,
        null,
        null,
        "started_at DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                val storedResult = cursor.getString(cursor.getColumnIndexOrThrow("result"))
                val samples = loadSamples(id)
                val validSampleCount = samples.count { it.valid }
                add(
                    SessionSummary(
                        id = id,
                        startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
                        endedAt = cursor.getLong(cursor.getColumnIndexOrThrow("ended_at")),
                        plannedSeconds = cursor.getInt(cursor.getColumnIndexOrThrow("planned_seconds")),
                        actualSeconds = cursor.getInt(cursor.getColumnIndexOrThrow("actual_seconds")),
                        track = MusicTrack.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("track"))),
                        result = ResultLabel.valueOf(storedResult),
                        sampleCount = samples.size,
                        validSampleCount = validSampleCount,
                        resultSampleCount = samples.count { it.valid && it.calmness?.let { value -> value.isFinite() && value in 0.0..1.0 } == true },
                        calm = SessionScoreCalculator.calculate(samples).calm,
                    ),
                )
            }
        }
    }

    fun loadSamples(sessionId: Long): List<StateSample> = readableDatabase.query(
        "samples",
        arrayOf("elapsed_seconds", "alpha", "theta", "beta", "stillness", "valid", "heart_rate_bpm", "calmness", "algorithm_version"),
        "session_id = ?",
        arrayOf(sessionId.toString()),
        null,
        null,
        "elapsed_seconds ASC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    StateSample(
                        elapsedSeconds = cursor.getInt(0),
                        alpha = cursor.getDoubleOrNull(1),
                        theta = cursor.getDoubleOrNull(2),
                        beta = cursor.getDoubleOrNull(3),
                        stillness = cursor.getDoubleOrNull(4),
                        valid = cursor.getInt(5) == 1,
                        eegBandsAvailable = cursor.getInt(8) > 0 && cursor.getInt(5) == 1 && (1..3).all { !cursor.isNull(it) },
                        heartRateBpm = cursor.getDoubleOrNull(6),
                        calmness = cursor.getDoubleOrNull(7),
                        algorithmVersion = cursor.getInt(8),
                    ),
                )
            }
        }
    }

    private fun android.database.Cursor.getDoubleOrNull(index: Int): Double? =
        if (isNull(index)) null else getDouble(index)

    private companion object {
        const val DATABASE_NAME = "hush.db"
        const val DATABASE_VERSION = 5
    }
}
