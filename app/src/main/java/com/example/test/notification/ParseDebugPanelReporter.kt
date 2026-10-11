package com.example.test.notification

import com.example.test.BuildConfig
import com.example.test.data.local.ParseDebugLogEntity
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

object ParseDebugPanelReporter {
    private const val TAG = "ParseDebugPanel"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(750, TimeUnit.MILLISECONDS)
        .readTimeout(750, TimeUnit.MILLISECONDS)
        .writeTimeout(750, TimeUnit.MILLISECONDS)
        .build()

    fun report(log: ParseDebugLogEntity) {
        val url = BuildConfig.DEBUG_PANEL_URL.takeIf { it.isNotBlank() } ?: return
        val payload = PanelPayload(
            id = log.id,
            contentHash = log.contentHash,
            sourceGroup = log.sourceGroup,
            rawMessage = log.rawMessage,
            sanitizedMessage = log.sanitizedMessage,
            apiResponseJson = log.apiResponseJson,
            status = log.status,
            savedOfferCount = log.savedOfferCount,
            skippedOfferCount = log.skippedOfferCount,
            error = log.error,
            receivedAt = log.receivedAt,
            createdAt = log.createdAt
        )
        val request = Request.Builder()
            .url(url)
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Timber.tag(TAG).d("PC panel baglantisi yok: %s", e::class.java.simpleName)
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    private data class PanelPayload(
        val id: Long,
        val contentHash: String,
        val sourceGroup: String,
        val rawMessage: String,
        val sanitizedMessage: String,
        val apiResponseJson: String?,
        val status: String,
        val savedOfferCount: Int,
        val skippedOfferCount: Int,
        val error: String?,
        val receivedAt: Long,
        val createdAt: Long
    )
}
