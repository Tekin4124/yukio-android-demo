package com.example.test.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.themePreferencesDataStore by preferencesDataStore(name = "theme_preferences")

enum class AppThemeMode(val storedValue: String) {
    DARK("dark"),
    LIGHT("light");

    companion object {
        fun fromStoredValue(value: String?): AppThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: DARK
    }
}

class ThemePreferenceStore(context: Context) {
    private val appContext = context.applicationContext

    val themeMode: Flow<AppThemeMode> = appContext.themePreferencesDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            AppThemeMode.fromStoredValue(preferences[KEY_THEME_MODE])
        }

    suspend fun setThemeMode(mode: AppThemeMode) {
        appContext.themePreferencesDataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.storedValue
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
