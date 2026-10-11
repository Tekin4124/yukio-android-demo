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

/** Multi-stop migration must preserve scalar offers and add an optional JSON column. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration24To25Test {
    private lateinit var context: Context
    private val databaseName = "test_only_migration_24_25.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == DEBUG_APPLICATION_ID) {
            "Refusing to run migration guard outside debug: ${context.packageName}"
        }
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration24To25_preservesOffers_andAddsNullableRouteStops() {
        createVersion24Fixture()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_24_25)
            .build()
        try {
            val database = migrated.openHelper.writableDatabase
            assertEquals(25, queryInt(database, "PRAGMA user_version"))
            assertEquals(1, queryInt(database, "SELECT COUNT(*) FROM load_offers"))
            assertEquals(
                1,
                queryInt(
                    database,
                    "SELECT COUNT(*) FROM load_offers " +
                        "WHERE fromCity = 'ELAZIG' AND toCity = 'RIZE' " +
                        "AND routeStops IS NULL"
                )
            )
        } finally {
            migrated.close()
        }
    }

    private fun createVersion24Fixture() {
        Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .build()
            .also { database ->
                database.openHelper.writableDatabase
                database.close()
            }

        val path = context.getDatabasePath(databaseName)
        SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.beginTransaction()
            try {
                db.execSQL("ALTER TABLE `load_offers` RENAME TO `load_offers_v25`")
                db.execSQL(
                    """
                    CREATE TABLE `load_offers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fromCity` TEXT NOT NULL,
                        `fromDistrict` TEXT,
                        `toCity` TEXT NOT NULL,
                        `toDistrict` TEXT,
                        `tonnageTon` INTEGER,
                        `tonnageText` TEXT,
                        `vehicleType` TEXT,
                        `vehicleCanonical` TEXT,
                        `priceTry` INTEGER,
                        `phone` TEXT NOT NULL,
                        `notes` TEXT,
                        `sourceGroup` TEXT NOT NULL,
                        `sourceSender` TEXT NOT NULL,
                        `rawMessage` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `sampleId` TEXT,
                        `offerIndex` INTEGER,
                        `vehicleInferredCandidates` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `load_offers` (
                        id, fromCity, fromDistrict, toCity, toDistrict, tonnageTon,
                        tonnageText, vehicleType, vehicleCanonical, priceTry, phone,
                        notes, sourceGroup, sourceSender, rawMessage, createdAt,
                        sampleId, offerIndex, vehicleInferredCandidates
                    ) VALUES (
                        1, 'ELAZIG', NULL, 'RIZE', NULL, 10,
                        '10 ton', 'TIR', 'tir', NULL, '',
                        NULL, 'test', 'Gizli', 'Elazig -> Rize', 1,
                        NULL, NULL, NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `load_offers_v25`")
                db.execSQL(
                    "CREATE INDEX `index_load_offers_fromCity` ON `load_offers` (`fromCity`)"
                )
                db.execSQL(
                    "CREATE INDEX `index_load_offers_toCity` ON `load_offers` (`toCity`)"
                )
                db.execSQL(
                    "CREATE INDEX `index_load_offers_createdAt` ON `load_offers` (`createdAt`)"
                )
                db.execSQL(
                    "CREATE INDEX `index_load_offers_rawMessage` ON `load_offers` (`rawMessage`)"
                )
                db.version = 24
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
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
