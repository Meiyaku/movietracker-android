package com.ycs.movietracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ycs.movietracker.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    private val themeModeKey = stringPreferencesKey("theme_mode")

    override fun getThemeMode(): Flow<ThemeMode> =
        context.settingsDataStore.data.map { prefs ->
            val name = prefs[themeModeKey] ?: ThemeMode.SYSTEM.name
            ThemeMode.valueOf(name)
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }
}
