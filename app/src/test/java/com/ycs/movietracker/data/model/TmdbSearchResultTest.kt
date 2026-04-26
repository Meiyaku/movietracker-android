package com.ycs.movietracker.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [TmdbSearchResult] — computed properties [TmdbSearchResult.year]
 * and [TmdbSearchResult.posterUrl].
 *
 * Run with: ./gradlew test
 */
class TmdbSearchResultTest {

    // ── year ──────────────────────────────────────────────────────────────────

    @Test
    fun year_isNull_whenReleaseDateIsNull() {
        assertNull(result(releaseDate = null).year)
    }

    @Test
    fun year_isNull_whenReleaseDateIsShorterThanFourChars() {
        assertNull(result(releaseDate = "202").year)
    }

    @Test
    fun year_returnsFirstFourChars_whenReleaseDateIsFullDate() {
        assertEquals("2024", result(releaseDate = "2024-07-19").year)
    }

    @Test
    fun year_returnsFirstFourChars_whenReleaseDateIsExactlyFourChars() {
        assertEquals("1999", result(releaseDate = "1999").year)
    }

    // ── posterUrl() ───────────────────────────────────────────────────────────

    @Test
    fun posterUrl_isNull_whenPosterPathIsNull() {
        assertNull(result(posterPath = null).posterUrl())
    }

    @Test
    fun posterUrl_usesW342ByDefault() {
        assertEquals(
            "https://image.tmdb.org/t/p/w342/poster.jpg",
            result(posterPath = "/poster.jpg").posterUrl()
        )
    }

    @Test
    fun posterUrl_usesSuppliedSize() {
        assertEquals(
            "https://image.tmdb.org/t/p/w500/poster.jpg",
            result(posterPath = "/poster.jpg").posterUrl("w500")
        )
    }

    @Test
    fun posterUrl_usesW92ForThumbnails() {
        assertEquals(
            "https://image.tmdb.org/t/p/w92/thumb.jpg",
            result(posterPath = "/thumb.jpg").posterUrl("w92")
        )
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private fun result(
        releaseDate: String? = "2024-01-01",
        posterPath: String? = "/poster.jpg"
    ) = TmdbSearchResult(
        id = 1,
        mediaType = "movie",
        title = "Test Movie",
        name = null,
        releaseDate = releaseDate,
        firstAirDate = null,
        overview = null,
        posterPath = posterPath,
        voteAverage = null
    )
}
