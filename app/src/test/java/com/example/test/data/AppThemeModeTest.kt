package com.example.test.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun `stored light value restores white theme`() {
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromStoredValue("light"))
    }

    @Test
    fun `missing or unknown value keeps existing dark default`() {
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStoredValue(null))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStoredValue("unknown"))
    }
}
