package com.example.test.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room 21 -> 22 migration guard: beta9 (v21) verisi olduğu gibi korunur,
 * yalnız nullable sampleId/offerIndex kolonları ve parser_sample_events
 * tablosu eklenir. Yalnız izole debug paketi içinde çalışır; gerçek
 * com.yukkap.app veritabanına asla dokunmaz.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration21To22Test {
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
    fun migration21To22_preserves1091And1263Offers_addsNullableSampleColumns() {
        listOf(1_091, 1_263).forEach(::verifyDataset)
    }

    private fun verifyDataset(expectedOfferCount: Int) {
        val databaseName = "test_only_migration_21_22_$expectedOfferCount.db"
        createdDatabases += databaseName
        context.deleteDatabase(databaseName)

        createFixture(databaseName, expectedOfferCount)
        convertFixtureToRealVersion21Schema(databaseName)
        assertVersion21Fixture(databaseName, expectedOfferCount)

        val migrated = openDatabase(databaseName)
        try {
            val sqlite = migrated.openHelper.writableDatabase

            assertEquals(22, queryInt(sqlite, "PRAGMA user_version"))
            assertEquals(expectedOfferCount, queryInt(sqlite, "SELECT COUNT(*) FROM load_offers"))
            assertEquals(1, queryInt(sqlite, "SELECT MIN(id) FROM load_offers"))
            assertEquals(expectedOfferCount, queryInt(sqlite, "SELECT MAX(id) FROM load_offers"))
            assertEquals(1, queryInt(sqlite, "SELECT COUNT(*) FROM user_profile"))
            assertEquals(
                expectedOfferCount,
                queryInt(
                    sqlite,
                    "SELECT COUNT(*) FROM load_offers WHERE sampleId IS NULL AND offerIndex IS NULL"
                )
            )
            assertEquals(0, queryInt(sqlite, "SELECT COUNT(*) FROM parser_sample_events"))

            // Yeni tablo/DAO migrasyon sonrası gerçekten kullanılabilir olmalı.
            runBlocking {
                val dao = migrated.parserSampleEventDao()
                dao.enqueue(
                    sampleId = "migration-test-sample-id",
                    offerIndex = ParserSampleEventEntity.MESSAGE_LEVEL_OFFER_INDEX,
                    kind = ParserSampleEventEntity.KIND_OPENED,
                    eventType = "OPENED",
                    now = 1L
                )
                assertNotNull(
                    dao.findSlot(
                        "migration-test-sample-id",
                        ParserSampleEventEntity.MESSAGE_LEVEL_OFFER_INDEX,
                        ParserSampleEventEntity.KIND_OPENED
                    )
                )
                assertEquals(1, dao.deleteAllPending())
            }
        } finally {
            migrated.close()
        }
    }

    private fun createFixture(databaseName: String, offerCount: Int) {
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
                        NULL, NULL, 0, 1,
                        1700000000000, 1700000000000
                    )
                    """.trimIndent()
                )
                sqlite.setTransactionSuccessful()
            } finally {
                sqlite.endTransaction()
            }
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
            statement.bindString(1, "IZMIR")
            statement.bindString(2, "MERSIN")
            statement.bindLong(3, 12)
            statement.bindString(4, "12 ton")
            statement.bindString(5, "KAMYON")
            statement.bindString(6, "kamyon")
            statement.bindLong(7, 15_000)
            statement.bindString(8, "synthetic_source")
            statement.bindString(9, "Gizli")
            statement.bindString(10, "synthetic_message_$index")
            statement.bindLong(11, 1_700_000_000_000L + index)
            statement.executeInsert()
        }
    }

    /**
     * Room ile bütün ortak v21 tablolarını kurduktan sonra yalnız v22'de gelen
     * tablo/kolonları fiziksel olarak kaldırır. Sadece user_version değerini
     * geri çekmek migration'ı doğrulamaz; bu fixture gerçekten v21 sütun
     * biçimine sahiptir.
     */
    private fun convertFixtureToRealVersion21Schema(databaseName: String) {
        val path = context.getDatabasePath(databaseName)
        SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.beginTransaction()
            try {
                db.execSQL("DROP TABLE IF EXISTS `parser_sample_events`")
                db.execSQL(
                    """
                    CREATE TABLE `load_offers_v21` (
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
                    INSERT INTO `load_offers_v21` (
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
                db.execSQL("ALTER TABLE `load_offers_v21` RENAME TO `load_offers`")
                db.execSQL("CREATE INDEX `index_load_offers_fromCity` ON `load_offers` (`fromCity`)")
                db.execSQL("CREATE INDEX `index_load_offers_toCity` ON `load_offers` (`toCity`)")
                db.execSQL("CREATE INDEX `index_load_offers_createdAt` ON `load_offers` (`createdAt`)")
                db.execSQL("CREATE INDEX `index_load_offers_rawMessage` ON `load_offers` (`rawMessage`)")
                db.version = 21
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun assertVersion21Fixture(databaseName: String, expectedOfferCount: Int) {
        val path = context.getDatabasePath(databaseName)
        SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(21, db.version)
            db.rawQuery("SELECT COUNT(*) FROM `load_offers`", null).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(expectedOfferCount, cursor.getInt(0))
            }
            db.rawQuery("PRAGMA table_info(`load_offers`)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
                assertEquals(false, columns.contains("sampleId"))
                assertEquals(false, columns.contains("offerIndex"))
            }
            db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='parser_sample_events'",
                null
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
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
