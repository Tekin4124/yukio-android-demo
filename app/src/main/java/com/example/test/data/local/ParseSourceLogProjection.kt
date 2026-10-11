package com.example.test.data.local

/**
 * Narrow Room projection used only to associate an offer with its parser source label.
 *
 * In particular this excludes apiResponseJson and review/debug metadata, which can make a cold
 * list load unnecessarily expensive when thousands of parse records are retained.
 */
data class ParseSourceLogProjection(
    val rawMessage: String,
    val sanitizedMessage: String,
    val status: String
)
