package com.ycs.movietracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ycs.movietracker.data.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    @Inject constructor(@ApplicationContext context: Context) : this(context.settingsDataStore)

    private val themeModeKey = stringPreferencesKey("theme_mode")

    override fun getThemeMode(): Flow<ThemeMode> =
        dataStore.data.map { prefs ->
            val name = prefs[themeModeKey] ?: ThemeMode.SYSTEM.name
            ThemeMode.valueOf(name)
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }
}
