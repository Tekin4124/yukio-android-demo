package com.example.test.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadParserApiQualityHeaderTest {
    @Test
    fun marksMissingQualityHeaderForReview() {
        assertEquals(
            "backend_v53_review",
            LoadParserApi.parserSourceWithReviewHeader("BACKEND_V53", null)
        )
    }

    @Test
    fun marksExplicitReviewSignal() {
        assertEquals(
            "ai_repaired_review",
            LoadParserApi.parserSourceWithReviewHeader("AI_REPAIRED", "1")
        )
    }

    @Test
    fun keepsTrustedSourceWithoutReviewSuffix() {
        assertEquals(
            "backend_v53",
            LoadParserApi.parserSourceWithReviewHeader("BACKEND_V53", "0")
        )
    }

    @Test
    fun returnsNullOnlyForTrustedEmptySource() {
        assertNull(LoadParserApi.parserSourceWithReviewHeader(null, "0"))
    }
}
