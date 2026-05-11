package com.ycs.movietracker.data.cache

import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MovieCacheServiceTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private fun makeCache(ttlMs: Long = FileMovieCacheService.TTL_MS) =
        FileMovieCacheService(tempDir.root, ttlMs)

    private fun movie(id: String, title: String = id) = Movie(
        id = id,
        title = title,
        year = 2024,
        genre = "Drama",
        status = WatchStatus.WATCHED,
        rating = 4.5
    )

    // ── load ─────────────────────────────────────────────────────────────────

    @Test
    fun load_returnsEmpty_whenNoCacheFileExists() {
        val result = makeCache().load("uid", "list1")
        assertTrue(result.isEmpty())
    }

    @Test
    fun load_returnsEmpty_whenTtlExpired() {
        val cache = makeCache(ttlMs = 100)
        cache.save(listOf(movie("a")), "uid", "list1")
        Thread.sleep(150)
        assertTrue(cache.load("uid", "list1").isEmpty())
    }

    @Test
    fun load_returnsMovies_whenWithinTtl() {
        val cache = makeCache()
        val movies = listOf(movie("m1"), movie("m2"))
        cache.save(movies, "uid", "list1")
        val result = cache.load("uid", "list1")
        assertEquals(2, result.size)
    }

    @Test
    fun load_returnsEmpty_whenFileIsCorrupt() {
        val file = tempDir.newFile("uid_list1.json")
        file.writeText("not valid json {{{")
        assertTrue(makeCache().load("uid", "list1").isEmpty())
    }

    // ── save / round-trip ────────────────────────────────────────────────────

    @Test
    fun saveAndLoad_preservesAllFields() {
        val cache = makeCache()
        val original = Movie(
            id = "id-1",
            title = "Inception",
            year = 2010,
            genre = "Sci-Fi",
            status = WatchStatus.WATCHED,
            rating = 4.5,
            description = "A thief steals secrets",
            notes = "Great film",
            trailerUrl = "https://youtube.com/trailer",
            posterUrl = "https://example.com/poster.jpg",
            listIds = listOf("list-a", "list-b")
        )
        cache.save(listOf(original), "uid", "list1")
        val loaded = cache.load("uid", "list1").first()

        assertEquals(original.id, loaded.id)
        assertEquals(original.title, loaded.title)
        assertEquals(original.year, loaded.year)
        assertEquals(original.genre, loaded.genre)
        assertEquals(original.status, loaded.status)
        assertEquals(original.rating, loaded.rating)
        assertEquals(original.description, loaded.description)
        assertEquals(original.notes, loaded.notes)
        assertEquals(original.trailerUrl, loaded.trailerUrl)
        assertEquals(original.posterUrl, loaded.posterUrl)
        assertEquals(original.listIds, loaded.listIds)
    }

    @Test
    fun saveAndLoad_preservesWantToWatchStatus() {
        val cache = makeCache()
        val movie = Movie(id = "m", title = "Queued", status = WatchStatus.WANT_TO_WATCH, rating = null)
        cache.save(listOf(movie), "uid", "list1")
        assertEquals(WatchStatus.WANT_TO_WATCH, cache.load("uid", "list1").first().status)
    }

    @Test
    fun saveAndLoad_preservesNullOptionalFields() {
        val cache = makeCache()
        val sparse = Movie(id = "s", title = "Sparse")
        cache.save(listOf(sparse), "uid", "list1")
        val loaded = cache.load("uid", "list1").first()
        assertEquals(null, loaded.year)
        assertEquals(null, loaded.genre)
        assertEquals(null, loaded.rating)
        assertEquals(null, loaded.posterUrl)
    }

    @Test
    fun save_overwritesPreviousCache() {
        val cache = makeCache()
        cache.save(listOf(movie("old")), "uid", "list1")
        cache.save(listOf(movie("new1"), movie("new2")), "uid", "list1")
        val result = cache.load("uid", "list1")
        assertEquals(2, result.size)
        assertEquals("new1", result[0].id)
    }

    // ── invalidate ───────────────────────────────────────────────────────────

    @Test
    fun invalidate_causesLoadToReturnEmpty() {
        val cache = makeCache()
        cache.save(listOf(movie("m")), "uid", "list1")
        cache.invalidate("uid", "list1")
        assertTrue(cache.load("uid", "list1").isEmpty())
    }

    @Test
    fun invalidate_noOp_whenNoCacheFileExists() {
        makeCache().invalidate("uid", "list1") // must not throw
    }

    // ── isolation per uid/listId ──────────────────────────────────────────────

    @Test
    fun caches_areIsolatedByUid() {
        val cache = makeCache()
        cache.save(listOf(movie("user1-movie")), "uid1", "list1")
        cache.save(listOf(movie("user2-movie")), "uid2", "list1")
        assertEquals("user1-movie", cache.load("uid1", "list1").first().id)
        assertEquals("user2-movie", cache.load("uid2", "list1").first().id)
    }

    @Test
    fun caches_areIsolatedByListId() {
        val cache = makeCache()
        cache.save(listOf(movie("movie-a")), "uid", "listA")
        cache.save(listOf(movie("movie-b")), "uid", "listB")
        assertEquals("movie-a", cache.load("uid", "listA").first().id)
        assertEquals("movie-b", cache.load("uid", "listB").first().id)
    }

    @Test
    fun invalidate_onlyRemovesTargetedList() {
        val cache = makeCache()
        cache.save(listOf(movie("a")), "uid", "listA")
        cache.save(listOf(movie("b")), "uid", "listB")
        cache.invalidate("uid", "listA")
        assertTrue(cache.load("uid", "listA").isEmpty())
        assertEquals(1, cache.load("uid", "listB").size)
    }

    // ── NoOpMovieCacheService ────────────────────────────────────────────────

    @Test
    fun noOp_load_alwaysReturnsEmpty() {
        assertTrue(NoOpMovieCacheService.load("uid", "list").isEmpty())
    }

    @Test
    fun noOp_save_doesNotThrow() {
        NoOpMovieCacheService.save(listOf(movie("m")), "uid", "list")
    }

    @Test
    fun noOp_invalidate_doesNotThrow() {
        NoOpMovieCacheService.invalidate("uid", "list")
    }
}
