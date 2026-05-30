package com.ycs.movietracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ycs.movietracker.data.model.MainScreen
import com.ycs.movietracker.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    constructor(context: Context) : this(context.settingsDataStore)

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val mainScreenKey = stringPreferencesKey("main_screen")
    private val whatsNewVersionKey = intPreferencesKey("last_seen_whats_new_version")

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

    override fun getMainScreen(): Flow<MainScreen> =
        dataStore.data.map { prefs ->
            val name = prefs[mainScreenKey] ?: MainScreen.MY_LISTS.name
            runCatching { MainScreen.valueOf(name) }.getOrDefault(MainScreen.MY_LISTS)
        }

    override suspend fun setMainScreen(screen: MainScreen) {
        dataStore.edit { prefs ->
            prefs[mainScreenKey] = screen.name
        }
    }

    override suspend fun getLastSeenWhatsNewVersion(): Int? =
        dataStore.data.first()[whatsNewVersionKey]

    override suspend fun setLastSeenWhatsNewVersion(version: Int) {
        dataStore.edit { prefs ->
            prefs[whatsNewVersionKey] = version
        }
    }
}
