package com.example.test.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real Room 20 -> 22 migration chain (20->21->22) with
 * production-sized synthetic offer counts. The database lives only in the
 * debug application's sandbox; the Play-installed com.yukkap.app database is
 * never opened or modified.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration20To21Test {
    private lateinit var context: Context
    private val createdDatabases = mutableSetOf<String>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == DEBUG_APPLICATION_ID) {
            "Refusing to run migration guard outside the isolated debug package: ${context.packageName}"
        }
    }

    @After
    fun tearDown() {
        createdDatabases.forEach(context::deleteDatabase)
        createdDatabases.clear()
    }

    @Test
    fun migration20To22_preserves1091And1263Offers_andOnlyClearsLegacyGpsState() {
        listOf(1_091, 1_263).forEach(::verifyDataset)
    }

    private fun verifyDataset(expectedOfferCount: Int) {
        val databaseName = "test_only_migration_20_22_$expectedOfferCount.db"
        createdDatabases += databaseName
        context.deleteDatabase(databaseName)

        createCurrentVersionFixture(databaseName, expectedOfferCount)
        convertFixtureToRealVersion20Schema(databaseName)

        val migrated = openDatabase(databaseName)
        try {
            val sqlite = migrated.openHelper.writableDatabase

            assertEquals(22, queryInt(sqlite, "PRAGMA user_version"))
            assertEquals(expectedOfferCount, queryInt(sqlite, "SELECT COUNT(*) FROM load_offers"))
            assertEquals(1, queryInt(sqlite, "SELECT MIN(id) FROM load_offers"))
            assertEquals(expectedOfferCount, queryInt(sqlite, "SELECT MAX(id) FROM load_offers"))

            sqlite.query(
                "SELECT gpsCity, gpsCityAt, gpsAutoEnabled FROM user_profile WHERE id = 1"
            ).use { cursor ->
                check(cursor.moveToFirst()) { "Synthetic user profile was not preserved" }
                assertNull(cursor.getString(0))
                assertNull(cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
            }

            // v22 additions exist and stay empty/null for migrated data.
            assertEquals(
                expectedOfferCount,
                queryInt(sqlite, "SELECT COUNT(*) FROM load_offers WHERE sampleId IS NULL AND offerIndex IS NULL")
            )
            assertEquals(0, queryInt(sqlite, "SELECT COUNT(*) FROM parser_sample_events"))
        } finally {
            migrated.close()
        }
    }

    private fun createCurrentVersionFixture(databaseName: String, offerCount: Int) {
        val database = openDatabase(databaseName)
        try {
            val sqlite = database.openHelper.writableDatabase
            sqlite.beginTransaction()
            try {
                insertSyntheticOffers(sqlite, offerCount)
                sqlite.execSQL(
                    """
                    INSERT INTO user_profile (
                        id, vehicleType, trailerType, maxTonnage,
                        height, width, length, fuelConsumption, costPerKm,
                        priceMin, priceMax, preferredLoadTypes, routes,
                        isVehicleEmpty, currentCities,
                        gpsCity, gpsCityAt, gpsAutoEnabled, notifyOnVehicleMatch,
                        createdAt, updatedAt
                    ) VALUES (
                        1, 'TIR', NULL, 24,
                        NULL, NULL, NULL, NULL, NULL,
                        NULL, NULL, '[]', '[]',
                        1, '[]',
                        'ANKARA', 1700000000000, 1, 1,
                        1700000000000, 1700000000000
                    )
                    """.trimIndent()
                )
                sqlite.setTransactionSuccessful()
            } finally {
                sqlite.endTransaction()
            }

            assertEquals(22, queryInt(sqlite, "PRAGMA user_version"))
            assertEquals(offerCount, queryInt(sqlite, "SELECT COUNT(*) FROM load_offers"))
        } finally {
            database.close()
        }
    }

    private fun insertSyntheticOffers(database: SupportSQLiteDatabase, count: Int) {
        val statement = database.compileStatement(
            """
            INSERT INTO load_offers (
                fromCity, fromDistrict, toCity, toDistrict,
                tonnageTon, tonnageText, vehicleType, vehicleCanonical,
                priceTry, phone, notes, sourceGroup, sourceSender,
                rawMessage, createdAt
            ) VALUES (?, NULL, ?, NULL, ?, ?, ?, ?, ?, '', NULL, ?, ?, ?, ?)
            """.trimIndent()
        )

        repeat(count) { index ->
            statement.clearBindings()
            statement.bindString(1, "ANKARA")
            statement.bindString(2, "ISTANBUL")
            statement.bindLong(3, 24)
            statement.bindString(4, "24 ton")
            statement.bindString(5, "TIR")
            statement.bindString(6, "tir")
            statement.bindLong(7, 20_000)
            statement.bindString(8, "synthetic_source")
            statement.bindString(9, "Gizli")
            statement.bindString(10, "synthetic_message_$index")
            statement.bindLong(11, 1_700_000_000_000L + index)
            statement.executeInsert()
        }
    }

    private fun convertFixtureToRealVersion20Schema(databaseName: String) {
        val path = context.getDatabasePath(databaseName)
        SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.beginTransaction()
            try {
                db.execSQL("DROP TABLE IF EXISTS `parser_sample_events`")
                db.execSQL(
                    """
                    CREATE TABLE `load_offers_v20` (
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
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `load_offers_v20` (
                        id, fromCity, fromDistrict, toCity, toDistrict,
                        tonnageTon, tonnageText, vehicleType, vehicleCanonical,
                        priceTry, phone, notes, sourceGroup, sourceSender,
                        rawMessage, createdAt
                    )
                    SELECT
                        id, fromCity, fromDistrict, toCity, toDistrict,
                        tonnageTon, tonnageText, vehicleType, vehicleCanonical,
                        priceTry, phone, notes, sourceGroup, sourceSender,
                        rawMessage, createdAt
                    FROM `load_offers`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `load_offers`")
                db.execSQL("ALTER TABLE `load_offers_v20` RENAME TO `load_offers`")
                db.execSQL("CREATE INDEX `index_load_offers_fromCity` ON `load_offers` (`fromCity`)")
                db.execSQL("CREATE INDEX `index_load_offers_toCity` ON `load_offers` (`toCity`)")
                db.execSQL("CREATE INDEX `index_load_offers_createdAt` ON `load_offers` (`createdAt`)")
                db.execSQL("CREATE INDEX `index_load_offers_rawMessage` ON `load_offers` (`rawMessage`)")
                db.version = 20
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(20, db.version)
            db.rawQuery("PRAGMA table_info(`load_offers`)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
                assertEquals(false, columns.contains("sampleId"))
                assertEquals(false, columns.contains("offerIndex"))
            }
        }
    }

    private fun openDatabase(databaseName: String): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_20_21, AppDatabase.MIGRATION_21_22)
            .build()
    }

    private fun queryInt(database: SupportSQLiteDatabase, sql: String): Int {
        database.query(sql).use { cursor ->
            check(cursor.moveToFirst()) { "Query returned no rows: $sql" }
            return cursor.getInt(0)
        }
    }

    private companion object {
        const val DEBUG_APPLICATION_ID = "com.yukkap.app.dev"
    }
}
