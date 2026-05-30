package com.ycs.movietracker.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [TmdbRepositoryImpl].
 *
 * The Retrofit client in [TmdbRepositoryImpl] is hardcoded to `api.themoviedb.org`, so tests
 * that require a real API key or HTTP-level mocking are out of scope here. These tests cover
 * the blank-key guard paths that fire before any network call is made — the primary risk
 * scenario when Remote Config hasn't delivered a key yet on first launch.
 *
 * For full HTTP-layer tests, extract the [TmdbApiService] creation into an injectable
 * parameter and use OkHttp's MockWebServer.
 */
@RunWith(AndroidJUnit4::class)
class TmdbRepositoryImplTest {

    private fun makeTmdb(apiKey: String) = TmdbRepositoryImpl(
        object : RemoteConfigRepository {
            override val pageSize = 50
            override val maxRetryAttempts = 3
            override val isTmdbSearchEnabled = MutableStateFlow(false)
            override val tmdbApiKey = MutableStateFlow(apiKey)
            override val whatsNew = MutableStateFlow("")
            override val whatsNewVersion = MutableStateFlow(0)
        }
    )

    // ── searchMovies ──────────────────────────────────────────────────────────

    @Test
    fun searchMovies_returnsFailure_whenApiKeyIsBlank() = runBlocking {
        val result = makeTmdb("").searchMovies("Batman")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun searchMovies_returnsFailure_whenApiKeyIsWhitespaceOnly() = runBlocking {
        val result = makeTmdb("   ").searchMovies("Batman")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    // ── getTrailerUrl ─────────────────────────────────────────────────────────

    @Test
    fun getTrailerUrl_returnsSuccessWithNull_whenApiKeyIsBlank() = runBlocking {
        val result = makeTmdb("").getTrailerUrl(550)

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun getTrailerUrl_returnsSuccessWithNull_whenApiKeyIsWhitespaceOnly() = runBlocking {
        val result = makeTmdb("   ").getTrailerUrl(550)

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }
}
