package com.example.test.util

import android.content.Context
import android.os.Build
import android.os.Environment
import com.example.test.BuildConfig
import com.example.test.data.local.AppDatabase
import com.example.test.data.local.ParseDebugLogEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

object AuditExporter {
    suspend fun export(context: Context, db: AppDatabase, daysBack: Int = 30): File {
        val now = System.currentTimeMillis()
        val since = now - daysBack * 24L * 60L * 60L * 1000L
        val database = db.openHelper.readableDatabase

        val loadOffers = queryTable(
            database = database,
            sql = "SELECT * FROM load_offers WHERE createdAt >= ? ORDER BY createdAt DESC",
            args = arrayOf(since.toString())
        ).map(::normalizeLoadOffer)

        val parseDebugLogs = queryTable(
            database = database,
            sql = "SELECT * FROM parse_debug_logs WHERE createdAt >= ? ORDER BY createdAt DESC",
            args = arrayOf(since.toString())
        ).map(::normalizeParseDebugLog)

        val failedMessages = queryTable(
            database = database,
            sql = "SELECT * FROM failed_messages WHERE timestamp >= ? ORDER BY timestamp DESC",
            args = arrayOf(since.toString())
        )

        val whatsappSources = queryTable(
            database = database,
            sql = "SELECT * FROM whatsapp_sources ORDER BY lastSeenAt DESC"
        )

        val reviewSummary = JSONObject()
            .put("unreviewed", parseDebugLogs.countReview(ParseDebugLogEntity.REVIEW_UNREVIEWED))
            .put("correct", parseDebugLogs.countReview(ParseDebugLogEntity.REVIEW_CORRECT))
            .put("incorrect", parseDebugLogs.countReview(ParseDebugLogEntity.REVIEW_INCORRECT))
            .put("uncertain", parseDebugLogs.countReview(ParseDebugLogEntity.REVIEW_UNCERTAIN))

        val root = JSONObject()
            .put("exported_at", isoDate(now))
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("build_config_debug", BuildConfig.DEBUG)
            .put(
                "device",
                JSONObject()
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL)
                    .put("android_sdk", Build.VERSION.SDK_INT)
                    .put("android_release", Build.VERSION.RELEASE)
            )
            .put(
                "stats",
                JSONObject()
                    .put("load_offers_count", loadOffers.size)
                    .put("parse_debug_logs_count", parseDebugLogs.size)
                    .put("parser_review", reviewSummary)
                    .put("failed_messages_count", failedMessages.size)
                    .put("whatsapp_sources_count", whatsappSources.size)
            )
            .put("load_offers", JSONArray(loadOffers))
            .put("parse_debug_logs", JSONArray(parseDebugLogs))
            .put("failed_messages", JSONArray(failedMessages))
            .put("whatsapp_sources", JSONArray(whatsappSources))

        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Download")
        if (!directory.exists()) directory.mkdirs()

        val fileName = "yukbulucu_audit_${fileStamp(now)}.json"
        return File(directory, fileName).also { file ->
            file.writeText(root.toString(2), Charsets.UTF_8)
        }
    }

    private fun queryTable(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        sql: String,
        args: Array<String> = emptyArray()
    ): List<JSONObject> {
        val rows = mutableListOf<JSONObject>()
        database.query(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                val row = JSONObject()
                for (index in 0 until cursor.columnCount) {
                    val name = cursor.getColumnName(index)
                    when (cursor.getType(index)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> row.put(name, JSONObject.NULL)
                        android.database.Cursor.FIELD_TYPE_INTEGER -> row.put(name, cursor.getLong(index))
                        android.database.Cursor.FIELD_TYPE_FLOAT -> row.put(name, cursor.getDouble(index))
                        android.database.Cursor.FIELD_TYPE_STRING -> row.put(name, cursor.getString(index))
                        android.database.Cursor.FIELD_TYPE_BLOB -> row.put(name, "<blob:${cursor.getBlob(index).size}>")
                    }
                }
                rows += row
            }
        }
        return rows
    }

    private fun normalizeLoadOffer(row: JSONObject): JSONObject {
        return copyOf(row)
            .put("tonnage", row.optNullable("tonnageText") ?: row.optNullable("tonnageTon"))
            .put("loadType", row.optNullable("notes"))
            .put("price", row.optNullable("priceTry"))
            .put("sender", row.optNullable("sourceGroup"))
            .put("receivedAt", row.optNullable("createdAt"))
            .put("parsedAt", row.optNullable("createdAt"))
    }

    private fun normalizeParseDebugLog(row: JSONObject): JSONObject {
        return copyOf(row)
            .put("sender", row.optNullable("sourceGroup"))
            .put("reason", row.optNullable("error"))
    }

    private fun List<JSONObject>.countReview(status: String): Int = count { row ->
        row.optString("reviewStatus", ParseDebugLogEntity.REVIEW_UNREVIEWED) == status
    }

    private fun copyOf(row: JSONObject): JSONObject {
        val copy = JSONObject()
        row.keys().forEach { key -> copy.put(key, row.opt(key)) }
        return copy
    }

    private fun JSONObject.optNullable(key: String): Any? {
        return if (!has(key) || isNull(key)) null else opt(key)
    }

    private fun isoDate(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestamp))
    }

    private fun fileStamp(timestamp: Long): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
    }
}
