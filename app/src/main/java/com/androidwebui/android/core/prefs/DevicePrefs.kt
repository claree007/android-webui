package com.androidwebui.android.core.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val storedValue: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.storedValue == value } ?: System
    }
}

/** Device-only state (D7). Never part of imports or exports. */
class DevicePrefs(private val storage: KeyValueStorage) {
    private val _themeMode = MutableStateFlow(ThemeMode.fromStored(storage.getString(KEY_THEME)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        storage.putString(KEY_THEME, mode.storedValue)
        _themeMode.value = mode
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
    }
}
