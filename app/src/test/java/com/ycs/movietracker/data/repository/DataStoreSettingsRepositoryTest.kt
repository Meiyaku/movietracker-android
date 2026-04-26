package com.ycs.movietracker.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ycs.movietracker.data.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for [DataStoreSettingsRepository] — companion test story US-010-DS-T.
 * Parent story: US-010 (Settings screen — theme mode persistence).
 *
 * Covers: default value on first use, round-trip set/get for each ThemeMode.
 * Uses [PreferenceDataStoreFactory] with [backgroundScope] so that each test
 * gets a fresh DataStore (avoiding the context-singleton issue) and the DataStore's
 * internal coroutines are cancelled cleanly when the test ends.
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    // ── default value ─────────────────────────────────────────────────────────

    @Test
    fun getThemeMode_returnsSystem_byDefault() = runTest {
        val repo = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tmpFolder.newFile("prefs_default.preferences_pb") }
            )
        )
        assertEquals(ThemeMode.SYSTEM, repo.getThemeMode().first())
    }

    // ── setThemeMode / getThemeMode ───────────────────────────────────────────

    @Test
    fun setThemeMode_dark_getThemeModeReturnsDark() = runTest {
        val repo = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tmpFolder.newFile("prefs_dark.preferences_pb") }
            )
        )
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.getThemeMode().first())
    }

    @Test
    fun setThemeMode_light_getThemeModeReturnsLight() = runTest {
        val repo = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tmpFolder.newFile("prefs_light.preferences_pb") }
            )
        )
        repo.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.getThemeMode().first())
    }

    @Test
    fun setThemeMode_system_returnsSystem_afterBeingSetToDark() = runTest {
        val repo = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tmpFolder.newFile("prefs_reset.preferences_pb") }
            )
        )
        repo.setThemeMode(ThemeMode.DARK)
        repo.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, repo.getThemeMode().first())
    }
}
