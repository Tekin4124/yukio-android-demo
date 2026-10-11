package com.example.test.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LoadOfferEntity::class,
        FailedMessage::class,
        UserProfileEntity::class,
        UserInteractionEntity::class,
        ParseDebugLogEntity::class,
        WhatsappSourceEntity::class,
        ParserSampleEventEntity::class
    ],
    version = 25,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase :  RoomDatabase() {

    abstract fun loadOfferDao(): LoadOfferDao
    abstract fun failedMessageDao(): FailedMessageDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun userInteractionDao(): UserInteractionDao
    abstract fun parseDebugLogDao(): ParseDebugLogDao
    abstract fun whatsappSourceDao(): WhatsappSourceDao
    abstract fun parserSampleEventDao(): ParserSampleEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `load_offers_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fromCity` TEXT NOT NULL,
                        `fromDistrict` TEXT,
                        `toCity` TEXT NOT NULL,
                        `toDistrict` TEXT,
                        `tonnageTon` INTEGER,
                        `vehicleType` TEXT,
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
                    INSERT INTO `load_offers_new` (
                        `id`,
                        `fromCity`,
                        `fromDistrict`,
                        `toCity`,
                        `toDistrict`,
                        `tonnageTon`,
                        `vehicleType`,
                        `priceTry`,
                        `phone`,
                        `notes`,
                        `sourceGroup`,
                        `sourceSender`,
                        `rawMessage`,
                        `createdAt`
                    )
                    SELECT
                        `id`,
                        `fromCity`,
                        `fromDistrict`,
                        `toCity`,
                        `toDistrict`,
                        `tonnageTon`,
                        `vehicleType`,
                        `priceTry`,
                        `phone`,
                        `notes`,
                        `sourceGroup`,
                        `sourceSender`,
                        `rawMessage`,
                        `createdAt`
                    FROM `load_offers`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `load_offers`")
                db.execSQL("ALTER TABLE `load_offers_new` RENAME TO `load_offers`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_load_offers_fromCity` ON `load_offers` (`fromCity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_load_offers_toCity` ON `load_offers` (`toCity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_load_offers_createdAt` ON `load_offers` (`createdAt`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_load_offers_phone_rawMessage` ON `load_offers` (`phone`, `rawMessage`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val hasTonnageText = db.query("PRAGMA table_info(`load_offers`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    var found = false
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0 && cursor.getString(nameIndex) == "tonnageText") {
                            found = true
                            break
                        }
                    }
                    found
                }

                if (!hasTonnageText) {
                    db.execSQL("ALTER TABLE `load_offers` ADD COLUMN `tonnageText` TEXT")
                }
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM `failed_messages`
                    WHERE `id` NOT IN (
                        SELECT MIN(`id`) FROM `failed_messages` GROUP BY `contentHash`
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_interactions_loadId` ON `user_interactions` (`loadId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_interactions_timestamp` ON `user_interactions` (`timestamp`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_failed_messages_contentHash` ON `failed_messages` (`contentHash`)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val hasDevicePhone = db.query("PRAGMA table_info(`failed_messages`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    var found = false
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0 && cursor.getString(nameIndex) == "devicePhone") {
                            found = true
                            break
                        }
                    }
                    found
                }

                if (!hasDevicePhone) {
                    db.execSQL("ALTER TABLE `failed_messages` ADD COLUMN `devicePhone` TEXT")
                }
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val hasDeviceNames = db.query("PRAGMA table_info(`failed_messages`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    var found = false
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0 && cursor.getString(nameIndex) == "deviceNames") {
                            found = true
                            break
                        }
                    }
                    found
                }

                if (!hasDeviceNames) {
                    db.execSQL("ALTER TABLE `failed_messages` ADD COLUMN `deviceNames` TEXT")
                }
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val hasDevicePhones = db.query("PRAGMA table_info(`failed_messages`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    var found = false
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0 && cursor.getString(nameIndex) == "devicePhones") {
                            found = true
                            break
                        }
                    }
                    found
                }

                if (!hasDevicePhones) {
                    db.execSQL("ALTER TABLE `failed_messages` ADD COLUMN `devicePhones` TEXT")
                }
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_load_offers_phone_rawMessage`")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_load_offers_rawMessage_fromCity_toCity_phone`
                    ON `load_offers` (`rawMessage`, `fromCity`, `toCity`, `phone`)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_load_offers_rawMessage_fromCity_toCity_phone`")
                db.execSQL("DROP INDEX IF EXISTS `index_load_offers_phone_rawMessage`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_load_offers_rawMessage` ON `load_offers` (`rawMessage`)")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `parse_debug_logs` (
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
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_parse_debug_logs_createdAt` ON `parse_debug_logs` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_parse_debug_logs_contentHash` ON `parse_debug_logs` (`contentHash`)")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE `load_offers` SET `sourceSender` = 'Gizli'")
                db.execSQL("UPDATE `failed_messages` SET `sender` = 'Gizli'")
                sanitizeWhatsappSourceColumn(db, "load_offers", "sourceGroup")
                sanitizeWhatsappSourceColumn(db, "failed_messages", "groupName")
                sanitizeWhatsappSourceColumn(db, "parse_debug_logs", "sourceGroup")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val failedMessageCreatedAtColumn = if (hasColumn(db, "failed_messages", "createdAt")) {
                    "createdAt"
                } else {
                    "timestamp"
                }

                db.execSQL(
                    """
                    DELETE FROM `failed_messages`
                    WHERE `retryCount` >= 1
                    AND `$failedMessageCreatedAtColumn` < (strftime('%s','now')*1000 - 7*24*60*60*1000)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    DELETE FROM `failed_messages`
                    WHERE `retryCount` > 0
                    AND (
                        LOWER(`groupName`) LIKE '%sabah%' OR
                        LOWER(`groupName`) LIKE '%vakif%' OR
                        LOWER(`groupName`) LIKE '%vakıf%'
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    DELETE FROM `parse_debug_logs`
                    WHERE `createdAt` < (strftime('%s','now')*1000 - 14*24*60*60*1000)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Backend vehicleDetector'dan gelen kanonik araç/dorse etiketleri.
                // Filtre eşleştirmesi için kullanılır. Eski kayıtlarda null kalır.
                if (!hasColumn(db, "load_offers", "vehicleCanonical")) {
                    db.execSQL("ALTER TABLE `load_offers` ADD COLUMN `vehicleCanonical` TEXT")
                }
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Profil: "şu an buradayım" şehir listesi + son GPS şehri + araç-eşleşme
                // bildirim ayarı. Eski profillerde varsayılan değerlerle doldurulur.
                if (!hasColumn(db, "user_profile", "currentCities")) {
                    db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `currentCities` TEXT NOT NULL DEFAULT '[]'")
                }
                if (!hasColumn(db, "user_profile", "gpsCity")) {
                    db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `gpsCity` TEXT")
                }
                if (!hasColumn(db, "user_profile", "gpsCityAt")) {
                    db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `gpsCityAt` INTEGER")
                }
                if (!hasColumn(db, "user_profile", "notifyOnVehicleMatch")) {
                    db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `notifyOnVehicleMatch` INTEGER NOT NULL DEFAULT 1")
                }
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // GPS otomatik konum tespiti aktif/pasif anahtarı.
                if (!hasColumn(db, "user_profile", "gpsAutoEnabled")) {
                    db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `gpsAutoEnabled` INTEGER NOT NULL DEFAULT 1")
                }
            }
        }

        internal val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Beta9 does not request location or expose the map/GPS flow.
                // Remove location-derived state left by beta8 upgrades so it
                // can no longer influence matching or remain stored locally.
                db.execSQL(
                    "UPDATE `user_profile` SET `gpsCity` = NULL, " +
                        "`gpsCityAt` = NULL, `gpsAutoEnabled` = 0"
                )
            }
        }

        internal val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Kimliksiz parser örneği eşlemesi: yalnız nullable kolon ekler,
                // mevcut ilanları/ID'leri/profili değiştirmez. hasColumn
                // korumaları yarım kalmış bir yükseltmeyi de güvenle tamamlar.
                if (!hasColumn(db, "load_offers", "sampleId")) {
                    db.execSQL("ALTER TABLE `load_offers` ADD COLUMN `sampleId` TEXT")
                }
                if (!hasColumn(db, "load_offers", "offerIndex")) {
                    db.execSQL("ALTER TABLE `load_offers` ADD COLUMN `offerIndex` INTEGER")
                }
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `parser_sample_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sampleId` TEXT NOT NULL,
                        `offerIndex` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `attempts` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_parser_sample_events_sampleId_offerIndex_kind` " +
                        "ON `parser_sample_events` (`sampleId`, `offerIndex`, `kind`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_parser_sample_events_state` " +
                        "ON `parser_sample_events` (`state`)"
                )
            }
        }

        internal val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Araç tahmini adayları: yalnız nullable kolon ekler, mevcut
                // ilanlara/ID'lere dokunmaz. hasColumn koruması yarım kalmış
                // yükseltmeyi güvenle tamamlar.
                if (!hasColumn(db, "load_offers", "vehicleInferredCandidates")) {
                    db.execSQL(
                        "ALTER TABLE `load_offers` ADD COLUMN `vehicleInferredCandidates` TEXT"
                    )
                }
            }
        }

        /**
         * Debug parser incelemesindeki insan kararlari yalnizca cihazda tutulur.
         * Mevcut parse loglari "UNREVIEWED" baslar; ham mesaj veya parser sonucu
         * degistirilmez.
         */
        internal val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "parse_debug_logs", "reviewStatus")) {
                    db.execSQL(
                        "ALTER TABLE `parse_debug_logs` ADD COLUMN " +
                            "`reviewStatus` TEXT NOT NULL DEFAULT 'UNREVIEWED'"
                    )
                }
                if (!hasColumn(db, "parse_debug_logs", "reviewNote")) {
                    db.execSQL("ALTER TABLE `parse_debug_logs` ADD COLUMN `reviewNote` TEXT")
                }
                if (!hasColumn(db, "parse_debug_logs", "reviewedAt")) {
                    db.execSQL("ALTER TABLE `parse_debug_logs` ADD COLUMN `reviewedAt` INTEGER")
                }
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_parse_debug_logs_reviewStatus` " +
                        "ON `parse_debug_logs` (`reviewStatus`)"
                )
            }
        }

        internal val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "load_offers", "routeStops")) {
                    db.execSQL(
                        "ALTER TABLE `load_offers` ADD COLUMN " +
                            "`routeStops` TEXT"
                    )
                }
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `whatsapp_sources` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `groupName` TEXT NOT NULL,
                        `normalizedName` TEXT NOT NULL,
                        `mode` TEXT NOT NULL,
                        `lastSeenAt` INTEGER NOT NULL,
                        `seenCount` INTEGER NOT NULL,
                        `lastDecision` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_whatsapp_sources_normalizedName` ON `whatsapp_sources` (`normalizedName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_whatsapp_sources_lastSeenAt` ON `whatsapp_sources` (`lastSeenAt`)")
            }
        }

        private fun sanitizeWhatsappSourceColumn(
            db: SupportSQLiteDatabase,
            table: String,
            column: String
        ) {
            db.execSQL("UPDATE `$table` SET `$column` = REPLACE(REPLACE(`$column`, char(8206), ''), char(8239), ' ')")
            db.execSQL("UPDATE `$table` SET `$column` = trim(substr(`$column`, 1, instr(`$column`, ': ~') - 1)) WHERE instr(`$column`, ': ~') > 0")
            (1..99).forEach { count ->
                db.execSQL("UPDATE `$table` SET `$column` = trim(REPLACE(`$column`, ' ($count mesaj)', ' '))")
            }
            db.execSQL("UPDATE `$table` SET `$column` = 'WhatsApp' WHERE trim(`$column`) = ''")
        }

        private fun hasColumn(
            db: SupportSQLiteDatabase,
            table: String,
            column: String
        ): Boolean {
            return db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == column) {
                        found = true
                        break
                    }
                }
                found
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?:  synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yuk_bulucu_db"
                )
                    .addMigrations(
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19,
                        MIGRATION_19_20,
                        MIGRATION_20_21,
                        MIGRATION_21_22,
                        MIGRATION_22_23,
                        MIGRATION_23_24,
                        MIGRATION_24_25
                    )
                    // Guvenlik agi: tanimli migration yolu olmayan durumlarda crash yerine
                    // DB'yi sifirdan kur. SADECE bilinen bosluklar icin:
                    //  - downgrade (kullanici eski APK kurarsa)
                    //  - cok eski surumler (1-4); bu surumler icin migration tanimlanmamis.
                    // Ileri yondeki normal yukseltmelerde (5->20 ve sonrasi) blanket destructive
                    // KULLANILMIYOR; boylece ileride eksik kalan bir migration sessizce veri
                    // silmek yerine fark edilebilir sekilde hata verir.
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
