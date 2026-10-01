package com.blue.hush.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.blue.hush.processing.SessionResultClassifier
import com.blue.hush.session.BUNDLED_SIMULATION_SESSION_ID
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
                valid INTEGER NOT NULL,
                FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX samples_session_index ON samples(session_id, elapsed_seconds)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version two recorded imports permanently; bundled history is now restored on startup.
        if (oldVersion < 3) db.execSQL("DROP TABLE IF EXISTS imported_sessions")
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

    fun insertSession(startedAt: Long, plannedSeconds: Int, track: MusicTrack): Long {
        val values = ContentValues().apply {
            put("started_at", startedAt)
            put("planned_seconds", plannedSeconds)
            put("track", track.name)
        }
        return writableDatabase.insertOrThrow("sessions", null, values)
    }

    fun insertSample(sessionId: Long, sample: StateSample) {
        insertSample(writableDatabase, sessionId, sample)
    }

    /** Ensures bundled history exists on startup, restoring it after deletion. */
    fun ensureBundledSimulation(samples: List<StateSample>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val alreadyImported = db.query(
                "sessions", arrayOf("id"), "id = ?",
                arrayOf(BUNDLED_SIMULATION_SESSION_ID.toString()), null, null, null,
            ).use { it.moveToFirst() }
            if (!alreadyImported) {
                val oldestStart = db.rawQuery("SELECT MIN(started_at) FROM sessions", null).use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
                }
                // The source has no timestamps. Place its synthetic interval before all real sessions.
                val end = minOf(oldestStart ?: Long.MAX_VALUE, System.currentTimeMillis()) - 1
                val start = end - samples.size * 1_000L
                val values = ContentValues().apply {
                    put("id", BUNDLED_SIMULATION_SESSION_ID)
                    put("started_at", start)
                    put("ended_at", end)
                    put("planned_seconds", samples.size)
                    put("actual_seconds", samples.size)
                    put("track", MusicTrack.MIST.name)
                    put("result", SessionResultClassifier.classify(samples).name)
                }
                db.insertOrThrow("sessions", null, values)
                samples.forEach { insertSample(db, BUNDLED_SIMULATION_SESSION_ID, it) }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun insertSample(db: SQLiteDatabase, sessionId: Long, sample: StateSample) {
        val values = ContentValues().apply {
            put("session_id", sessionId)
            put("elapsed_seconds", sample.elapsedSeconds)
            sample.alpha?.let { put("alpha", it) } ?: putNull("alpha")
            sample.theta?.let { put("theta", it) } ?: putNull("theta")
            sample.beta?.let { put("beta", it) } ?: putNull("beta")
            sample.stillness?.let { put("stillness", it) } ?: putNull("stillness")
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
                add(
                    SessionSummary(
                        id = id,
                        startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
                        endedAt = cursor.getLong(cursor.getColumnIndexOrThrow("ended_at")),
                        plannedSeconds = cursor.getInt(cursor.getColumnIndexOrThrow("planned_seconds")),
                        actualSeconds = cursor.getInt(cursor.getColumnIndexOrThrow("actual_seconds")),
                        track = MusicTrack.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("track"))),
                        // Map legacy sessions created before short sessions received a result.
                        result = runCatching { ResultLabel.valueOf(storedResult) }.getOrDefault(ResultLabel.STEADY),
                        sampleCount = countSamples(id, validOnly = false),
                        validSampleCount = countSamples(id, validOnly = true),
                    ),
                )
            }
        }
    }

    fun loadSamples(sessionId: Long): List<StateSample> = readableDatabase.query(
        "samples",
        arrayOf("elapsed_seconds", "alpha", "theta", "beta", "stillness", "valid"),
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
                    ),
                )
            }
        }
    }

    private fun countSamples(sessionId: Long, validOnly: Boolean): Int {
        val selection = if (validOnly) "session_id = ? AND valid = 1" else "session_id = ?"
        return readableDatabase.query(
            "samples",
            arrayOf("COUNT(*)"),
            selection,
            arrayOf(sessionId.toString()),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun android.database.Cursor.getDoubleOrNull(index: Int): Double? =
        if (isNull(index)) null else getDouble(index)

    private companion object {
        const val DATABASE_NAME = "hush.db"
        const val DATABASE_VERSION = 3
    }
}
