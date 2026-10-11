package com.example.test.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadParserApiSampleHeaderTest {

    @Test
    fun `gecerli sampleId basligi kabul edilir`() {
        assertEquals(
            "d8bdL40oLBrp7fV__MFfWg",
            LoadParserApi.sampleIdFromHeader("d8bdL40oLBrp7fV__MFfWg")
        )
        assertEquals(
            "a".repeat(16),
            LoadParserApi.sampleIdFromHeader(" ${"a".repeat(16)} ")
        )
    }

    @Test
    fun `eksik veya bozuk baslik fail-closed null doner`() {
        assertNull(LoadParserApi.sampleIdFromHeader(null))
        assertNull(LoadParserApi.sampleIdFromHeader(""))
        assertNull(LoadParserApi.sampleIdFromHeader("   "))
        assertNull(LoadParserApi.sampleIdFromHeader("kisa"))
        assertNull(LoadParserApi.sampleIdFromHeader("a".repeat(65)))
        assertNull(LoadParserApi.sampleIdFromHeader("gecersiz karakter!"))
        assertNull(LoadParserApi.sampleIdFromHeader("türkçe-karakterli-id-türkçe"))
    }
}
