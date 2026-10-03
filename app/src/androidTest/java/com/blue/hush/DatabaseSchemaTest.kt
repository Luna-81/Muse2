package com.blue.hush

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.platform.app.InstrumentationRegistry
import com.blue.hush.session.MusicTrack
import com.blue.hush.storage.HushDatabase
import org.junit.Assert.*
import org.junit.Test

class DatabaseSchemaTest {
    @Test fun unsupportedSchemaFailsWithoutDeletingHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        context.deleteDatabase("hush.db")
        try {
            HushDatabase(context).use { database ->
                database.insertSession(1000, 600, MusicTrack.RAIN)
                database.writableDatabase.version = 4
            }
            HushDatabase(context).use { database ->
                assertThrows(SQLiteException::class.java) { database.writableDatabase }
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath("hush.db").path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                assertEquals(4, database.version)
                database.rawQuery("SELECT COUNT(*) FROM sessions", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                }
            }
        } finally {
            context.deleteDatabase("hush.db")
        }
    }
}
