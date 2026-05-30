package com.ycs.movietracker.util

import com.ycs.movietracker.data.model.MainScreen
import com.ycs.movietracker.data.model.ThemeMode
import com.ycs.movietracker.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class NoOpSettingsRepository : SettingsRepository {
    override fun getThemeMode(): Flow<ThemeMode> = flowOf(ThemeMode.SYSTEM)
    override suspend fun setThemeMode(mode: ThemeMode) {}
    override fun getMainScreen(): Flow<MainScreen> = flowOf(MainScreen.MOVIES)
    override suspend fun setMainScreen(screen: MainScreen) {}
    override suspend fun getLastSeenWhatsNewVersion(): Int? = null
    override suspend fun setLastSeenWhatsNewVersion(version: Int) {}
}
