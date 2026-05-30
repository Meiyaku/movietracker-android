package com.ycs.movietracker.data.repository

import com.ycs.movietracker.data.model.MainScreen
import com.ycs.movietracker.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
    fun getMainScreen(): Flow<MainScreen>
    suspend fun setMainScreen(screen: MainScreen)
    suspend fun getLastSeenWhatsNewVersion(): Int?
    suspend fun setLastSeenWhatsNewVersion(version: Int)
}
