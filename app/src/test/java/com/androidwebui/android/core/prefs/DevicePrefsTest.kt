package com.androidwebui.android.core.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class DevicePrefsTest {
    @Test
    fun defaultsToSystem() {
        assertEquals(ThemeMode.System, DevicePrefs(InMemoryStorage()).themeMode.value)
    }

    @Test
    fun themeSurvivesRestart() {
        val storage = InMemoryStorage()
        DevicePrefs(storage).setThemeMode(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, DevicePrefs(storage).themeMode.value)
    }

    @Test
    fun unknownStoredValueFallsBackToSystem() {
        val storage = InMemoryStorage().apply { putString("theme_mode", "sepia") }
        assertEquals(ThemeMode.System, DevicePrefs(storage).themeMode.value)
    }
}
