package com.example.test.data.remote

import com.google.gson.annotations.SerializedName

data class ParseRequest(
    @SerializedName("text") val text: String,
    // Kimliksiz parser örneği paylaşımı KAPALIYKEN bu alanlar null kalır ve
    // JSON'a hiç yazılmaz (Gson null alanları atlar). Açıkken sample_text,
    // ParserSampleSanitizer'dan geçmiş metindir; ham/maskeli mesaj değildir.
    @SerializedName("sample_text") val sampleText: String? = null,
    @SerializedName("sample_sanitizer_version") val sampleSanitizerVersion: Int? = null
)

/**
 * Sample etkileşim olayı. Sunucu yalnız OPENED / FEEDBACK_CORRECT /
 * FEEDBACK_WRONG / FEEDBACK_MISSING kabul eder; başka alan gönderilmez.
 */
data class ParserSampleEventRequest(
    @SerializedName("sample_id") val sampleId: String,
    @SerializedName("event") val event: String,
    @SerializedName("offer_index") val offerIndex: Int? = null
)
