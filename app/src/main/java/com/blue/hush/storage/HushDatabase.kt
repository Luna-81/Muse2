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
import org.json.JSONObject

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
        // Remove the obsolete version-two simulation import marker.
        if (oldVersion < 3) db.execSQL("DROP TABLE IF EXISTS imported_sessions")
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE samples ADD COLUMN heart_rate_bpm REAL")
            db.execSQL("ALTER TABLE samples ADD COLUMN calmness REAL")
            db.execSQL("ALTER TABLE samples ADD COLUMN algorithm_version INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 5) {
            // SQLiteOpenHelper runs upgrades in a transaction. Reset incompatible history once,
            // including unfinished and formerly auto-imported simulation sessions.
            db.delete("samples", null, null)
            db.delete("sessions", null, null)
        }
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
        val source = context.assets.open("history/saved_sessions.json").bufferedReader().use { JSONObject(it.readText()) }
        require(source.getInt("format_version") == 1) { "Unsupported bundled history format" }
        val sessions = source.getJSONArray("sessions")
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (index in 0 until sessions.length()) {
                val session = sessions.getJSONObject(index)
                val startedAt = session.getLong("started_at")
                val endedAt = session.getLong("ended_at")
                // Local IDs change after deletion and restoration; original timestamps identify the snapshot.
                val exists = db.rawQuery(
                    "SELECT 1 FROM sessions WHERE started_at = ? AND ended_at = ? LIMIT 1",
                    arrayOf(startedAt.toString(), endedAt.toString()),
                ).use { it.moveToFirst() }
                if (exists) continue
                val values = ContentValues().apply {
                    put("started_at", startedAt)
                    put("ended_at", endedAt)
                    put("planned_seconds", session.getInt("planned_seconds"))
                    put("actual_seconds", session.getInt("actual_seconds"))
                    put("track", session.getString("track"))
                    put("result", session.getString("result"))
                }
                val id = db.insertOrThrow("sessions", null, values)
                val samples = session.getJSONArray("samples")
                for (sampleIndex in 0 until samples.length()) {
                    val sample = samples.getJSONObject(sampleIndex)
                    insertSample(db, id, StateSample(
                        elapsedSeconds = sample.getInt("elapsed_seconds"),
                        alpha = sample.getDoubleOrNull("alpha"),
                        theta = sample.getDoubleOrNull("theta"),
                        beta = sample.getDoubleOrNull("beta"),
                        stillness = sample.getDoubleOrNull("stillness"),
                        heartRateBpm = sample.getDoubleOrNull("heart_rate_bpm"),
                        calmness = sample.getDoubleOrNull("calmness"),
                        algorithmVersion = sample.getInt("algorithm_version"),
                        valid = sample.getInt("valid") == 1,
                    ))
                }
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
                        resultSampleCount = if (hasCompositeSamples(id)) countCalmnessSamples(id) else countSamples(id, validOnly = true),
                        calm = SessionScoreCalculator.calculate(loadSamples(id)).calm,
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

    private fun hasCompositeSamples(sessionId: Long): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM samples WHERE session_id = ? AND algorithm_version > 0 LIMIT 1", arrayOf(sessionId.toString()),
    ).use { it.moveToFirst() }

    private fun countCalmnessSamples(sessionId: Long): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM samples WHERE session_id = ? AND valid = 1 AND calmness IS NOT NULL", arrayOf(sessionId.toString()),
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

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

    private fun JSONObject.getDoubleOrNull(key: String): Double? =
        if (isNull(key)) null else getDouble(key)

    private companion object {
        const val DATABASE_NAME = "hush.db"
        const val DATABASE_VERSION = 5
    }
}
