package com.example.test.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticSourceFilterTest {

    @Test
    fun `vip yazimlari nakliye kaynagi kabul edilir`() {
        assertTrue(LogisticSourceFilter.isAllowed("VIP"))
        assertTrue(LogisticSourceFilter.isAllowed("V.I.P nakliye"))
        assertTrue(LogisticSourceFilter.isAllowed("V\u0130P yuk grubu"))
        assertTrue(LogisticSourceFilter.isAllowed("v i p"))
    }

    @Test
    fun `seramik sevkiyat kaynaklari kabul edilir`() {
        assertTrue(LogisticSourceFilter.isAllowed("\u00c7andan Seramik Sevkiyat"))
    }

    @Test
    fun `nakliye disi grup adi reddedilir`() {
        assertFalse(LogisticSourceFilter.isAllowed("KYK erkek yurdu"))
        assertFalse(LogisticSourceFilter.isAllowed("Arkadaslar"))
    }
}
