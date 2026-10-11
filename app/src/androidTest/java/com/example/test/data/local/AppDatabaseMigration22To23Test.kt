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

/**
 * Room 22 -> 23 migration guard: v22 verisi olduğu gibi korunur, yalnız
 * nullable vehicleInferredCandidates kolonu eklenir. Yalnız izole debug
 * paketinde çalışır; gerçek com.yukkap.app veritabanına asla dokunmaz.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration22To23Test {
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
    fun migration22To23_preservesOffers_addsNullableInferredColumn() {
        val expectedOfferCount = 847
        val databaseName = "test_only_migration_22_23.db"
        createdDatabases += databaseName
        context.deleteDatabase(databaseName)

        createFixture(databaseName, expectedOfferCount)
        convertFixtureToRealVersion22Schema(databaseName)
        assertVersion22Fixture(databaseName, expectedOfferCount)

        val migrated = openDatabase(databaseName)
        try {
            val sqlite = migrated.openHelper.writableDatabase

            assertEquals(23, queryInt(sqlite, "PRAGMA user_version"))
            assertEquals(expectedOfferCount, queryInt(sqlite, "SELECT COUNT(*) FROM load_offers"))
            assertEquals(1, queryInt(sqlite, "SELECT MIN(id) FROM load_offers"))
            assertEquals(expectedOfferCount, queryInt(sqlite, "SELECT MAX(id) FROM load_offers"))
            assertEquals(
                expectedOfferCount,
                queryInt(
                    sqlite,
                    "SELECT COUNT(*) FROM load_offers WHERE vehicleInferredCandidates IS NULL"
                )
            )
            // Eski satırların alanları migrasyon sonrası okunabilir kalmalı.
            assertEquals(
                expectedOfferCount,
                queryInt(
                    sqlite,
                    "SELECT COUNT(*) FROM load_offers WHERE vehicleCanonical = 'kamyon' AND priceTry = 15000"
                )
            )
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
                val statement = sqlite.compileStatement(
                    """
                    INSERT INTO load_offers (
                        fromCity, fromDistrict, toCity, toDistrict,
                        tonnageTon, tonnageText, vehicleType, vehicleCanonical,
                        priceTry, phone, notes, sourceGroup, sourceSender,
                        rawMessage, createdAt, sampleId, offerIndex
                    ) VALUES (?, NULL, ?, ?, NULL, ?, ?, ?, ?, '', NULL, ?, ?, ?, ?, NULL, NULL)
                    """.trimIndent()
                )
                repeat(offerCount) { index ->
                    statement.clearBindings()
                    statement.bindString(1, "IZMIR")
                    statement.bindString(2, "MERSIN")
                    statement.bindString(3, "AKDENIZ")
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
                sqlite.setTransactionSuccessful()
            } finally {
                sqlite.endTransaction()
            }
        } finally {
            database.close()
        }
    }

    /**
     * Room ile güncel şema kurulduktan sonra yalnız v23'te gelen kolonu
     * fiziksel olarak kaldırır; fixture gerçekten v22 sütun biçimine sahiptir.
     */
    private fun convertFixtureToRealVersion22Schema(databaseName: String) {
        val path = context.getDatabasePath(databaseName)
        SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.beginTransaction()
            try {
                db.execSQL(
                    """
                    CREATE TABLE `load_offers_v22` (
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
                        `offerIndex` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `load_offers_v22` (
                        id, fromCity, fromDistrict, toCity, toDistrict,
                        tonnageTon, tonnageText, vehicleType, vehicleCanonical,
                        priceTry, phone, notes, sourceGroup, sourceSender,
                        rawMessage, createdAt, sampleId, offerIndex
                    )
                    SELECT
                        id, fromCity, fromDistrict, toCity, toDistrict,
                        tonnageTon, tonnageText, vehicleType, vehicleCanonical,
                        priceTry, phone, notes, sourceGroup, sourceSender,
                        rawMessage, createdAt, sampleId, offerIndex
                    FROM `load_offers`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `load_offers`")
                db.execSQL("ALTER TABLE `load_offers_v22` RENAME TO `load_offers`")
                db.execSQL("CREATE INDEX `index_load_offers_fromCity` ON `load_offers` (`fromCity`)")
                db.execSQL("CREATE INDEX `index_load_offers_toCity` ON `load_offers` (`toCity`)")
                db.execSQL("CREATE INDEX `index_load_offers_createdAt` ON `load_offers` (`createdAt`)")
                db.execSQL("CREATE INDEX `index_load_offers_rawMessage` ON `load_offers` (`rawMessage`)")
                db.version = 22
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun assertVersion22Fixture(databaseName: String, expectedOfferCount: Int) {
        val path = context.getDatabasePath(databaseName)
        SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(22, db.version)
            db.rawQuery("SELECT COUNT(*) FROM `load_offers`", null).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(expectedOfferCount, cursor.getInt(0))
            }
            db.rawQuery("PRAGMA table_info(`load_offers`)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
                assertEquals(false, columns.contains("vehicleInferredCandidates"))
            }
        }
    }

    private fun openDatabase(databaseName: String): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(
                AppDatabase.MIGRATION_20_21,
                AppDatabase.MIGRATION_21_22,
                AppDatabase.MIGRATION_22_23
            )
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
