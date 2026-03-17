package com.ycs.movietracker.data.repository

import com.ycs.movietracker.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
}
