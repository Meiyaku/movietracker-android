package com.ycs.movietracker.ui.settings

import com.ycs.movietracker.data.model.ThemeMode
import com.ycs.movietracker.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SettingsViewModel] — companion test story US-010-T.
 * Parent story: US-010 (Settings screen).
 *
 * Covers: initial themeMode value, setThemeMode delegation and state update.
 *
 * Run with: ./gradlew test
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private class FakeSettingsRepository(
        initial: ThemeMode = ThemeMode.SYSTEM
    ) : SettingsRepository {
        private val _themeMode = MutableStateFlow(initial)
        var lastSetMode: ThemeMode? = null
        var setCallCount = 0

        override fun getThemeMode(): Flow<ThemeMode> = _themeMode

        override suspend fun setThemeMode(mode: ThemeMode) {
            setCallCount++
            lastSetMode = mode
            _themeMode.value = mode
        }
    }

    private fun makeVm(initial: ThemeMode = ThemeMode.SYSTEM): Pair<SettingsViewModel, FakeSettingsRepository> {
        val repo = FakeSettingsRepository(initial)
        val vm = SettingsViewModel(repo)
        return vm to repo
    }

    // ── themeMode initial value ───────────────────────────────────────────────

    @Test
    fun themeMode_initialValueIsSystem() = runTest {
        val (vm, _) = makeVm()
        assertEquals(ThemeMode.SYSTEM, vm.themeMode.value)
    }

    @Test
    fun themeMode_reflectsRepositoryInitialValue() = runTest {
        val (vm, _) = makeVm(initial = ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
    }

    // ── setThemeMode ──────────────────────────────────────────────────────────

    @Test
    fun setThemeMode_delegatesToRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.setThemeMode(ThemeMode.DARK)
        assertEquals(1, repo.setCallCount)
    }

    @Test
    fun setThemeMode_passesCorrectModeToRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.lastSetMode)
    }

    @Test
    fun setThemeMode_dark_updatesThenThemeModeFlow() = runTest {
        val (vm, _) = makeVm()
        vm.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, vm.themeMode.first())
    }

    @Test
    fun setThemeMode_light_updatesThemeModeFlow() = runTest {
        val (vm, _) = makeVm(initial = ThemeMode.DARK)
        vm.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, vm.themeMode.first())
    }

    @Test
    fun setThemeMode_system_updatesThemeModeFlow() = runTest {
        val (vm, _) = makeVm(initial = ThemeMode.DARK)
        vm.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, vm.themeMode.first())
    }
}
