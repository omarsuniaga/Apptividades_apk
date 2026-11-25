package com.example.domain

import com.example.data.port.AppThemeMode
import com.example.data.port.PrivacyPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeAndTypographyTest {

    @Test
    fun `AppThemeMode parses storage values and falls back safely to SYSTEM`() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorage("system"))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromStorage("light"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStorage("DARK"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorage(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorage("unknown"))
    }

    @Test
    fun `PrivacyPreferences defaults to SYSTEM theme mode`() {
        assertEquals(AppThemeMode.SYSTEM, PrivacyPreferences().themeMode)
    }
}
