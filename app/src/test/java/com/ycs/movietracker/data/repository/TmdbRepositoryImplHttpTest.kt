package com.ycs.movietracker.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class TmdbRepositoryImplHttpTest {

    private lateinit var server: MockWebServer
    private lateinit var remoteConfig: RemoteConfigRepository
    private lateinit var repo: TmdbRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        remoteConfig = mock()
        whenever(remoteConfig.tmdbApiKey).thenReturn(MutableStateFlow("test-key"))
        repo = TmdbRepositoryImpl(remoteConfig, server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    fun `search returns movie results mapped correctly`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {
              "results": [
                {
                  "id": 123,
                  "media_type": "movie",
                  "title": "Inception",
                  "original_title": "Inception",
                  "overview": "A thief who steals corporate secrets",
                  "release_date": "2010-07-16",
                  "poster_path": "/poster.jpg",
                  "vote_average": 8.4,
                  "genre_ids": [28, 878]
                }
              ]
            }
        """.trimIndent()).setResponseCode(200))

        val result = repo.search("Inception")

        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(123, item.id)
        assertEquals("movie", item.mediaType)
        assertEquals("Inception", item.title)
        assertEquals("2010-07-16", item.releaseDate)
        assertEquals("/poster.jpg", item.posterPath)
        assertEquals(8.4, item.voteAverage!!, 0.01)
        assertEquals("Action / Science Fiction", item.genre)
    }

    @Test
    fun `search returns tv results mapped correctly`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {
              "results": [
                {
                  "id": 456,
                  "media_type": "tv",
                  "name": "Breaking Bad",
                  "overview": "A chemistry teacher turned drug manufacturer",
                  "first_air_date": "2008-01-20",
                  "poster_path": "/bb.jpg",
                  "vote_average": 9.5,
                  "genre_ids": [18, 80]
                }
              ]
            }
        """.trimIndent()).setResponseCode(200))

        val result = repo.search("Breaking Bad")

        assertTrue(result.isSuccess)
        val item = result.getOrThrow()[0]
        assertEquals(456, item.id)
        assertEquals("tv", item.mediaType)
        assertEquals("Breaking Bad", item.name)
        assertEquals("2008-01-20", item.firstAirDate)
        assertEquals("Drama / Crime", item.genre)
    }

    @Test
    fun `search filters out non-movie and non-tv results`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {
              "results": [
                { "id": 1, "media_type": "person", "name": "Tom Hanks", "genre_ids": [] },
                { "id": 2, "media_type": "movie", "title": "Cast Away", "genre_ids": [] },
                { "id": 3, "media_type": "collection", "name": "Hanks Collection", "genre_ids": [] }
              ]
            }
        """.trimIndent()).setResponseCode(200))

        val result = repo.search("Tom Hanks")

        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(1, items.size)
        assertEquals(2, items[0].id)
    }

    @Test
    fun `search returns empty list when results array is empty`() = runTest {
        server.enqueue(MockResponse().setBody("""{"results":[]}""").setResponseCode(200))

        val result = repo.search("xyznonexistent")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `search genre is null when genre_ids is empty`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {
              "results": [
                { "id": 7, "media_type": "movie", "title": "Unknown Genre", "genre_ids": [] }
              ]
            }
        """.trimIndent()).setResponseCode(200))

        val result = repo.search("Unknown Genre")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow()[0].genre)
    }

    @Test
    fun `search returns failure on server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repo.search("anything")

        assertTrue(result.isFailure)
    }

    @Test
    fun `search returns failure on malformed JSON`() = runTest {
        server.enqueue(MockResponse().setBody("not json at all").setResponseCode(200))

        val result = repo.search("anything")

        assertTrue(result.isFailure)
    }

    @Test
    fun `search returns failure when api key is blank`() = runTest {
        whenever(remoteConfig.tmdbApiKey).thenReturn(MutableStateFlow(""))
        val blankKeyRepo = TmdbRepositoryImpl(remoteConfig, server.url("/").toString())

        val result = blankKeyRepo.search("anything")

        assertTrue(result.isFailure)
    }

    // ── getTrailerUrl ─────────────────────────────────────────────────────────

    @Test
    fun `getTrailerUrl returns official trailer when available`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {
              "results": [
                { "key": "unofficialKey", "site": "YouTube", "type": "Trailer", "official": false },
                { "key": "officialKey",   "site": "YouTube", "type": "Trailer", "official": true }
              ]
            }
        """.trimIndent()).setResponseCode(200))

        val result = repo.getTrailerUrl(123, "movie")

        assertTrue(result.isSuccess)
        assertEquals("https://www.youtube.com/watch?v=officialKey", result.getOrThrow())
    }

    @Test
    fun `getTrailerUrl falls back to unofficial trailer when no official exists`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {
              "results": [
                { "key": "unofficialKey", "site": "YouTube", "type": "Trailer", "official": false }
              ]
            }
        """.trimIndent()).setResponseCode(200))

        val result = repo.getTrailerUrl(123, "movie")

        assertTrue(result.isSuccess)
        assertEquals("https://www.youtube.com/watch?v=unofficialKey", result.getOrThrow())
    }

    @Test
    fun `getTrailerUrl returns null when no YouTube trailers exist`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {
              "results": [
                { "key": "vimeoKey", "site": "Vimeo", "type": "Trailer", "official": true },
                { "key": "clipKey",  "site": "YouTube", "type": "Clip",    "official": true }
              ]
            }
        """.trimIndent()).setResponseCode(200))

        val result = repo.getTrailerUrl(123, "movie")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun `getTrailerUrl returns null when results list is empty`() = runTest {
        server.enqueue(MockResponse().setBody("""{"results":[]}""").setResponseCode(200))

        val result = repo.getTrailerUrl(123, "movie")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun `getTrailerUrl returns failure on server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repo.getTrailerUrl(123, "movie")

        assertTrue(result.isFailure)
    }

    @Test
    fun `getTrailerUrl returns success null when api key is blank`() = runTest {
        whenever(remoteConfig.tmdbApiKey).thenReturn(MutableStateFlow(""))
        val blankKeyRepo = TmdbRepositoryImpl(remoteConfig, server.url("/").toString())

        val result = blankKeyRepo.getTrailerUrl(123, "movie")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }
}
