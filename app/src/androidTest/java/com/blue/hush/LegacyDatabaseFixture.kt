package com.blue.hush

import android.database.sqlite.SQLiteDatabase

/** Recreates the actual pre-fusion sample schema instead of only changing user_version. */
internal fun recreateLegacySampleSchema(db: SQLiteDatabase, version: Int) {
    db.execSQL("ALTER TABLE samples RENAME TO previous_samples")
    db.execSQL("""CREATE TABLE samples (
        id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL,
        elapsed_seconds INTEGER NOT NULL, alpha REAL, theta REAL, beta REAL,
        stillness REAL, valid INTEGER NOT NULL,
        FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE
    )""".trimIndent())
    db.execSQL("""INSERT INTO samples (id, session_id, elapsed_seconds, alpha, theta, beta, stillness, valid)
        SELECT id, session_id, elapsed_seconds, alpha, theta, beta, stillness, valid FROM previous_samples""")
    db.execSQL("DROP TABLE previous_samples")
    db.execSQL("CREATE INDEX samples_session_index ON samples(session_id, elapsed_seconds)")
    db.version = version
}
