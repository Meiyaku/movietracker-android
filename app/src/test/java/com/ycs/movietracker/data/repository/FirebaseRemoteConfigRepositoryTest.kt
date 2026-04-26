package com.ycs.movietracker.data.repository

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.ycs.movietracker.util.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit tests for [FirebaseRemoteConfigRepository] — companion test story US-RC-T.
 *
 * Covers:
 *   - pageSize and maxRetryAttempts coercion (min/max clamping, boundary values)
 *   - Initial StateFlow values before the async init block completes
 *   - StateFlow values after the async init block completes (looper drained)
 *
 * FirebaseRemoteConfig is a final SDK class — inline mocking is enabled via
 * src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker.
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class FirebaseRemoteConfigRepositoryTest {

    /**
     * Builds a [FirebaseRemoteConfigRepository] with the given stubbed values.
     * Does NOT drain the main looper — flows will still hold their initial hardcoded values.
     */
    private fun makeRepo(
        tmdbEnabled: Boolean = true,
        apiKey: String = "",
        pageSizeLong: Long = 50L,
        maxRetryLong: Long = 3L
    ): FirebaseRemoteConfigRepository {
        val mockConfig = mock<FirebaseRemoteConfig>()
        whenever(mockConfig.setDefaultsAsync(any<Int>())).thenReturn(Tasks.forResult(null))
        whenever(mockConfig.fetchAndActivate()).thenReturn(Tasks.forResult(true))
        whenever(mockConfig.getBoolean(FirebaseRemoteConfigRepository.KEY_TMDB_SEARCH_ENABLED))
            .thenReturn(tmdbEnabled)
        whenever(mockConfig.getString(FirebaseRemoteConfigRepository.KEY_TMDB_API_KEY))
            .thenReturn(apiKey)
        whenever(mockConfig.getLong(FirebaseRemoteConfigRepository.KEY_PAGE_SIZE))
            .thenReturn(pageSizeLong)
        whenever(mockConfig.getLong(FirebaseRemoteConfigRepository.KEY_MAX_RETRY_ATTEMPTS))
            .thenReturn(maxRetryLong)
        return FirebaseRemoteConfigRepository(mockConfig)
    }

    /**
     * Builds the repo and then idles the main looper so the setDefaultsAsync and
     * fetchAndActivate callbacks fire synchronously — flows reflect the mocked values.
     */
    private fun makeRepoAndIdle(
        tmdbEnabled: Boolean = true,
        apiKey: String = "",
        pageSizeLong: Long = 50L,
        maxRetryLong: Long = 3L
    ): FirebaseRemoteConfigRepository {
        val repo = makeRepo(tmdbEnabled, apiKey, pageSizeLong, maxRetryLong)
        shadowOf(Looper.getMainLooper()).idle()
        return repo
    }

    // ── pageSize ──────────────────────────────────────────────────────────────

    @Test
    fun pageSize_returnsValueWithinRange() {
        assertEquals(50, makeRepo(pageSizeLong = 50L).pageSize)
    }

    @Test
    fun pageSize_returnsDefault_whenValueIsZero() {
        // 0 is the sentinel in remote_config_defaults.xml meaning "no override — use AppConfig.PAGE_SIZE"
        assertEquals(AppConfig.PAGE_SIZE, makeRepo(pageSizeLong = 0L).pageSize)
    }

    @Test
    fun pageSize_clampsToMaximum_whenValueTooHigh() {
        assertEquals(AppConfig.PAGE_SIZE_MAX, makeRepo(pageSizeLong = 9_999L).pageSize)
    }

    @Test
    fun pageSize_returnsMinimum_atLowerBoundary() {
        assertEquals(
            AppConfig.PAGE_SIZE_MIN,
            makeRepo(pageSizeLong = AppConfig.PAGE_SIZE_MIN.toLong()).pageSize
        )
    }

    @Test
    fun pageSize_returnsMaximum_atUpperBoundary() {
        assertEquals(
            AppConfig.PAGE_SIZE_MAX,
            makeRepo(pageSizeLong = AppConfig.PAGE_SIZE_MAX.toLong()).pageSize
        )
    }

    // ── maxRetryAttempts ──────────────────────────────────────────────────────

    @Test
    fun maxRetryAttempts_returnsValueWithinRange() {
        assertEquals(3, makeRepo(maxRetryLong = 3L).maxRetryAttempts)
    }

    @Test
    fun maxRetryAttempts_clampsToMinimum_whenValueTooLow() {
        assertEquals(AppConfig.MAX_RETRY_ATTEMPTS_MIN, makeRepo(maxRetryLong = 0L).maxRetryAttempts)
    }

    @Test
    fun maxRetryAttempts_clampsToMaximum_whenValueTooHigh() {
        assertEquals(AppConfig.MAX_RETRY_ATTEMPTS_MAX, makeRepo(maxRetryLong = 999L).maxRetryAttempts)
    }

    @Test
    fun maxRetryAttempts_returnsMinimum_atLowerBoundary() {
        assertEquals(
            AppConfig.MAX_RETRY_ATTEMPTS_MIN,
            makeRepo(maxRetryLong = AppConfig.MAX_RETRY_ATTEMPTS_MIN.toLong()).maxRetryAttempts
        )
    }

    @Test
    fun maxRetryAttempts_returnsMaximum_atUpperBoundary() {
        assertEquals(
            AppConfig.MAX_RETRY_ATTEMPTS_MAX,
            makeRepo(maxRetryLong = AppConfig.MAX_RETRY_ATTEMPTS_MAX.toLong()).maxRetryAttempts
        )
    }

    // ── isTmdbSearchEnabled — before init completes ───────────────────────────

    @Test
    fun isTmdbSearchEnabled_isTrue_beforeInitCompletes() {
        // The MutableStateFlow is initialised to true regardless of what remoteConfig returns.
        // Passing tmdbEnabled=false confirms the stub value is not yet applied.
        assertTrue(makeRepo(tmdbEnabled = false).isTmdbSearchEnabled.value)
    }

    // ── isTmdbSearchEnabled — after init completes ────────────────────────────

    @Test
    fun isTmdbSearchEnabled_updatesToTrue_afterInitCompletes() {
        assertTrue(makeRepoAndIdle(tmdbEnabled = true).isTmdbSearchEnabled.value)
    }

    @Test
    fun isTmdbSearchEnabled_updatesToFalse_afterInitCompletes() {
        assertFalse(makeRepoAndIdle(tmdbEnabled = false).isTmdbSearchEnabled.value)
    }

    // ── tmdbApiKey — before init completes ────────────────────────────────────

    @Test
    fun tmdbApiKey_isEmpty_beforeInitCompletes() {
        // The MutableStateFlow is initialised to "" regardless of what remoteConfig returns.
        // Passing a non-empty key confirms the stub value is not yet applied.
        assertEquals("", makeRepo(apiKey = "pre-init-key").tmdbApiKey.value)
    }

    // ── tmdbApiKey — after init completes ─────────────────────────────────────

    @Test
    fun tmdbApiKey_updatesToValue_afterInitCompletes() {
        assertEquals("test-api-key-123", makeRepoAndIdle(apiKey = "test-api-key-123").tmdbApiKey.value)
    }

    @Test
    fun tmdbApiKey_remainsEmpty_whenRemoteConfigReturnsEmptyString() {
        assertEquals("", makeRepoAndIdle(apiKey = "").tmdbApiKey.value)
    }
}
