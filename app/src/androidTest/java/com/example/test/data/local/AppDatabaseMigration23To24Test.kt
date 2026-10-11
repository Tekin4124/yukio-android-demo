package com.example.test.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The debug-review migration must preserve the message/parser evidence. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration23To24Test {
    private lateinit var context: Context
    private val createdDatabases = mutableSetOf<String>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == DEBUG_APPLICATION_ID) {
            "Refusing to run migration guard outside debug: ${context.packageName}"
        }
    }

    @After
    fun tearDown() {
        createdDatabases.forEach(context::deleteDatabase)
    }

    @Test
    fun migration23To24_keepsDebugEvidence_and_marksExistingRowsUnreviewed() {
        val databaseName = "test_only_migration_23_24.db"
        createdDatabases += databaseName
        context.deleteDatabase(databaseName)

        createVersion23Fixture(databaseName)
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(
                AppDatabase.MIGRATION_23_24,
                AppDatabase.MIGRATION_24_25
            )
            .build()
        try {
            val database = migrated.openHelper.writableDatabase
            assertEquals(25, queryInt(database, "PRAGMA user_version"))
            assertEquals(1, queryInt(database, "SELECT COUNT(*) FROM parse_debug_logs"))
            assertEquals(
                1,
                queryInt(
                    database,
                    "SELECT COUNT(*) FROM parse_debug_logs " +
                        "WHERE rawMessage = 'ham mesaj' " +
                        "AND apiResponseJson = '{\"ilanlar\":[]}' " +
                        "AND reviewStatus = 'UNREVIEWED' " +
                        "AND reviewNote IS NULL AND reviewedAt IS NULL"
                )
            )
        } finally {
            migrated.close()
        }
    }

    private fun createVersion23Fixture(databaseName: String) {
        Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .build()
            .also { database ->
                database.openHelper.writableDatabase
                database.close()
            }
        val path = context.getDatabasePath(databaseName)
        SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            database.beginTransaction()
            try {
            database.execSQL(
                """
                CREATE TABLE `parse_debug_logs_v23` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `contentHash` TEXT NOT NULL,
                    `sourceGroup` TEXT NOT NULL,
                    `rawMessage` TEXT NOT NULL,
                    `sanitizedMessage` TEXT NOT NULL,
                    `apiResponseJson` TEXT,
                    `status` TEXT NOT NULL,
                    `savedOfferCount` INTEGER NOT NULL,
                    `skippedOfferCount` INTEGER NOT NULL,
                    `error` TEXT,
                    `receivedAt` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO parse_debug_logs_v23 (
                    contentHash, sourceGroup, rawMessage, sanitizedMessage,
                    apiResponseJson, status, savedOfferCount, skippedOfferCount,
                    error, receivedAt, createdAt
                ) VALUES ('hash', 'grup', 'ham mesaj', 'temiz mesaj',
                    '{"ilanlar":[]}', 'success_backend', 1, 0, NULL, 10, 20)
                """.trimIndent()
            )
            database.execSQL("DROP TABLE `parse_debug_logs`")
            database.execSQL("ALTER TABLE `parse_debug_logs_v23` RENAME TO `parse_debug_logs`")
            database.execSQL("CREATE INDEX `index_parse_debug_logs_createdAt` ON `parse_debug_logs` (`createdAt`)")
            database.execSQL("CREATE INDEX `index_parse_debug_logs_contentHash` ON `parse_debug_logs` (`contentHash`)")
            database.version = 23
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    private fun queryInt(database: SupportSQLiteDatabase, sql: String): Int =
        database.query(sql).use { cursor ->
            check(cursor.moveToFirst()) { "Query returned no rows: $sql" }
            cursor.getInt(0)
        }

    private companion object {
        const val DEBUG_APPLICATION_ID = "com.yukkap.app.dev"
    }
}
