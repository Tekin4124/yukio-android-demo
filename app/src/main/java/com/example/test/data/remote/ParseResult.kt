package com.example.test.data.remote

sealed class ParseResult {
    data class Success(
        val ilanlar: List<Ilan>,
        val rawJson: String?,
        val parserSource: String?,
        // Backend kimliksiz sample'ı kabul ettiyse döner; aksi halde null.
        // Null iken hiçbir sample etkileşimi ağa çıkmaz (fail-closed).
        val sampleId: String? = null
    ) : ParseResult()

    data class NoLogisticContent(
        val rawJson: String? = null,
        val parserSource: String? = null
    ) : ParseResult()

    data class Retryable(val cause: Throwable) : ParseResult()
    data class BadResponse(val cause: Throwable) : ParseResult()
}
