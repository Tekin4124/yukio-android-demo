package com.example.test.data.remote

import com.google.gson.Gson
import java.io.IOException
import retrofit2.HttpException
import timber.log.Timber

object LoadParserApi {
    private const val TAG = "LoadParserApi"
    private val gson = Gson()

    /**
     * @param sampleText Kullanıcı kimliksiz örnek paylaşımını AÇTIYSA ve
     * ParserSampleSanitizer başarılıysa dolu gelir; diğer her durumda null
     * olmalı ve istekte sample alanı hiç taşınmamalıdır.
     */
    suspend fun parseIlan(text: String, sampleText: String? = null): ParseResult {
        return try {
            Timber.tag(TAG).d("Parser istegi gonderiliyor; chars=%d", text.length)

            val request = ParseRequest(
                text = text,
                sampleText = sampleText,
                sampleSanitizerVersion = sampleText?.let {
                    com.example.test.util.ParserSampleSanitizer.SANITIZER_VERSION
                }
            )
            val response = RetrofitClient.apiService.parseIlan(request)
            if (!response.isSuccessful) {
                // Response<T> sarmalayici HTTP hatalarinda exception FIRLATMAZ;
                // eski davranisi korumak icin elle HttpException'a ceviriyoruz.
                throw HttpException(response)
            }
            val body = response.body()
                ?: return ParseResult.BadResponse(IOException("Parser bos govde dondu"))
            // Backend parser kaynagini header'da bildirir: BACKEND_V53, AI_FALLBACK,
            // BACKEND_V53_CACHE gibi. Debug panelde ai/backend etiketi buradan gelir.
            val parserSource = parserSourceWithReviewHeader(
                parserSource = response.headers()["X-Parse-Source"],
                requiresReviewHeader = response.headers()["X-Parse-Requires-Review"]
            )
            val sampleId = sampleIdFromHeader(response.headers()["X-Parser-Sample-Id"])
            val rawJson = gson.toJson(body)

            if (!body.hata.isNullOrBlank()) {
                Timber.tag(TAG).d("Parser API hata durumu dondu")
                return if (isRetryableApiError(body.hata)) {
                    ParseResult.Retryable(IOException("Parser API gecici hata: ${body.hata}"))
                } else {
                    ParseResult.NoLogisticContent(rawJson, parserSource)
                }
            }

            val ilanlar = body.ilanlar.orEmpty()
            Timber.tag(TAG).d("Parsed %s ilan", ilanlar.size)

            if (ilanlar.isEmpty()) {
                ParseResult.NoLogisticContent(rawJson, parserSource)
            } else {
                ParseResult.Success(ilanlar, rawJson, parserSource, sampleId)
            }
        } catch (e: IOException) {
            Timber.tag(TAG).e("Network/parser exception: %s", e::class.java.simpleName)
            ParseResult.Retryable(e)
        } catch (e: HttpException) {
            Timber.tag(TAG).e("HTTP exception; status=%d", e.code())
            if (e.code() == 408 || e.code() == 429 || e.code() in 500..599) {
                ParseResult.Retryable(e)
            } else {
                ParseResult.BadResponse(e)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e("Parser exception: %s", e::class.java.simpleName)
            ParseResult.BadResponse(e)
        }
    }

    private fun isRetryableApiError(error: String): Boolean {
        return error.contains("gecici", ignoreCase = true) ||
            error.contains("geçici", ignoreCase = true) ||
            error.contains("OPENAI", ignoreCase = true) ||
            error.contains("servis", ignoreCase = true)
    }

    /**
     * Missing veya bozuk header fail-closed davranır: sampleId null kalır ve
     * cihaz bu mesaj için hiçbir sample etkileşimi göndermez.
     */
    internal fun sampleIdFromHeader(value: String?): String? {
        val candidate = value?.trim() ?: return null
        return candidate.takeIf { Regex("^[A-Za-z0-9_-]{16,64}$").matches(it) }
    }

    internal fun parserSourceWithReviewHeader(
        parserSource: String?,
        requiresReviewHeader: String?
    ): String? {
        val normalizedSource = parserSource
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
        // Old/incompatible servers do not emit this header. Treat absence,
        // malformed values and any non-zero value as requiring user review.
        val requiresReview = requiresReviewHeader?.trim() != "0"
        return listOfNotNull(
            normalizedSource,
            "review".takeIf { requiresReview }
        ).joinToString("_").takeIf { it.isNotBlank() }
    }
}
