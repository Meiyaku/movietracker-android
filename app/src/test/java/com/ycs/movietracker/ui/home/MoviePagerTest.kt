package com.ycs.movietracker.ui.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MoviesPage
import com.ycs.movietracker.data.model.NewMovie
import com.ycs.movietracker.data.repository.MovieRepository
import com.ycs.movietracker.data.repository.StaleCursorException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for [MoviePager] — pagination logic in isolation.
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class MoviePagerTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun pager(repo: MovieRepository, scope: TestScope) =
        MoviePager(repo, scope)

    // ── Fake repo ─────────────────────────────────────────────────────────────

    private class FakeRepo(
        private val pages: List<List<Movie>> = emptyList(),
        private val error: Throwable? = null
    ) : MovieRepository {
        var callCount = 0

        override suspend fun getMoviesPage(
            uid: String, listId: String, pageSize: Int, afterId: String?
        ): Result<MoviesPage> {
            if (error != null) return Result.failure(error)
            val pageIndex = callCount++
            val movies = pages.getOrElse(pageIndex) { emptyList() }
            return Result.success(
                MoviesPage(
                    movies = movies,
                    lastId = movies.lastOrNull()?.id,
                    hasMore = pageIndex < pages.size - 1
                )
            )
        }

        override suspend fun addMovie(uid: String, movie: NewMovie) = Result.success(Movie(id = "x", title = movie.title))
        override suspend fun updateMovie(uid: String, movie: Movie) = Result.success(Unit)
        override suspend fun deleteMovie(uid: String, movieId: String) = Result.success(Unit)
        override suspend fun getMovieById(uid: String, movieId: String) = Result.failure<Movie>(UnsupportedOperationException())
        override suspend fun removeListFromMovies(uid: String, listId: String) = Result.success(Unit)
        override suspend fun checkDuplicate(uid: String, title: String, year: Int?, genre: String?, excludeId: String?) = Result.success(false)
        override suspend fun deleteAllMovies(uid: String) = Result.success(Unit)
    }

    private fun movie(id: String) = Movie(id = id, title = id)

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun initialState_moviesEmpty_notLoading_noMore() = runTest(dispatcher) {
        val p = pager(FakeRepo(), this)
        assertTrue(p.movies.value.isEmpty())
        assertFalse(p.isLoading.value)
        assertFalse(p.isLoadingMore.value)
        assertFalse(p.hasMore.value)
    }

    // ── loadFirstPage ─────────────────────────────────────────────────────────

    @Test
    fun loadFirstPage_populatesMovies() = runTest(dispatcher) {
        val page = listOf(movie("a"), movie("b"))
        val p = pager(FakeRepo(pages = listOf(page)), this)
        p.loadFirstPage("uid", "list1")
        assertEquals(page, p.movies.value)
    }

    @Test
    fun loadFirstPage_setsHasMore_whenMorePagesExist() = runTest(dispatcher) {
        val repo = FakeRepo(pages = listOf(listOf(movie("a")), listOf(movie("b"))))
        val p = pager(repo, this)
        p.loadFirstPage("uid", "list1")
        assertTrue(p.hasMore.value)
    }

    @Test
    fun loadFirstPage_clearsHasMore_whenLastPage() = runTest(dispatcher) {
        val p = pager(FakeRepo(pages = listOf(listOf(movie("a")))), this)
        p.loadFirstPage("uid", "list1")
        assertFalse(p.hasMore.value)
    }

    @Test
    fun loadFirstPage_emitsLoading_thenClearsIt() = runTest(dispatcher) {
        val p = pager(FakeRepo(pages = listOf(listOf(movie("a")))), this)
        p.loadFirstPage("uid", "list1")
        assertFalse(p.isLoading.value)
    }

    // ── loadMore ──────────────────────────────────────────────────────────────

    @Test
    fun loadMore_appendsNextPage() = runTest(dispatcher) {
        val page1 = listOf(movie("a"))
        val page2 = listOf(movie("b"))
        val repo = FakeRepo(pages = listOf(page1, page2))
        val p = pager(repo, this)
        p.loadFirstPage("uid", "list1")
        p.loadMore("uid", "list1")
        assertEquals(page1 + page2, p.movies.value)
    }

    @Test
    fun loadMore_noOp_whenHasMoreFalse() = runTest(dispatcher) {
        val page1 = listOf(movie("a"))
        val repo = FakeRepo(pages = listOf(page1))
        val p = pager(repo, this)
        p.loadFirstPage("uid", "list1")
        p.loadMore("uid", "list1")
        assertEquals(page1, p.movies.value)
        assertEquals(1, repo.callCount)
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    fun reset_clearsMoviesAndCursor() = runTest(dispatcher) {
        val repo = FakeRepo(pages = listOf(listOf(movie("a"))))
        val p = pager(repo, this)
        p.loadFirstPage("uid", "list1")
        p.reset()
        assertTrue(p.movies.value.isEmpty())
        assertFalse(p.hasMore.value)
    }

    @Test
    fun loadFirstPage_afterReset_reloadsFromBeginning() = runTest(dispatcher) {
        val page1 = listOf(movie("a"))
        val repo = FakeRepo(pages = listOf(page1, page1))
        val p = pager(repo, this)
        p.loadFirstPage("uid", "list1")
        p.reset()
        p.loadFirstPage("uid", "list1")
        assertEquals(page1, p.movies.value)
    }

    // ── stale cursor recovery ─────────────────────────────────────────────────

    @Test
    fun fetchPage_staleCursorException_restartsFromPage1() = runTest(dispatcher) {
        val freshPage = listOf(movie("fresh"))
        var callCount = 0
        val repo = object : MovieRepository {
            override suspend fun getMoviesPage(
                uid: String, listId: String, pageSize: Int, afterId: String?
            ): Result<MoviesPage> = when (callCount++) {
                0 -> Result.success(MoviesPage(listOf(movie("stale")), lastId = "stale", hasMore = true))
                1 -> Result.failure(StaleCursorException("stale"))
                else -> Result.success(MoviesPage(freshPage, lastId = "fresh", hasMore = false))
            }
            override suspend fun addMovie(uid: String, movie: NewMovie) = Result.success(Movie(id = "x", title = movie.title))
            override suspend fun updateMovie(uid: String, movie: Movie) = Result.success(Unit)
            override suspend fun deleteMovie(uid: String, movieId: String) = Result.success(Unit)
            override suspend fun getMovieById(uid: String, movieId: String) = Result.failure<Movie>(UnsupportedOperationException())
            override suspend fun removeListFromMovies(uid: String, listId: String) = Result.success(Unit)
            override suspend fun checkDuplicate(uid: String, title: String, year: Int?, genre: String?, excludeId: String?) = Result.success(false)
            override suspend fun deleteAllMovies(uid: String) = Result.success(Unit)
        }
        val p = pager(repo, this)
        p.loadFirstPage("uid", "list1")
        p.loadMore("uid", "list1")
        assertEquals(freshPage, p.movies.value)
    }

    // ── error emission ────────────────────────────────────────────────────────

    @Test
    fun fetchPage_nonStaleCursorError_emitsToErrors() = runTest(dispatcher) {
        val boom = RuntimeException("network error")
        val p = pager(FakeRepo(error = boom), this)
        var emitted: Throwable? = null
        backgroundScope.launch { emitted = p.errors.first() }
        p.loadFirstPage("uid", "list1")
        assertEquals(boom, emitted)
    }

    // ── local mutations ───────────────────────────────────────────────────────

    @Test
    fun notifyAdded_prependsToList() = runTest(dispatcher) {
        val existing = movie("existing")
        val p = pager(FakeRepo(pages = listOf(listOf(existing))), this)
        p.loadFirstPage("uid", "list1")
        val added = movie("new")
        p.notifyAdded(added)
        assertEquals(listOf(added, existing), p.movies.value)
    }

    @Test
    fun notifyUpdated_replacesMatchingMovie() = runTest(dispatcher) {
        val original = movie("a")
        val p = pager(FakeRepo(pages = listOf(listOf(original, movie("b")))), this)
        p.loadFirstPage("uid", "list1")
        val updated = original.copy(title = "A-updated")
        p.notifyUpdated(updated)
        assertEquals(updated, p.movies.value[0])
        assertEquals(movie("b"), p.movies.value[1])
    }

    @Test
    fun notifyRemoved_removesMatchingMovie() = runTest(dispatcher) {
        val keep = movie("keep")
        val remove = movie("remove")
        val p = pager(FakeRepo(pages = listOf(listOf(keep, remove))), this)
        p.loadFirstPage("uid", "list1")
        p.notifyRemoved("remove")
        assertEquals(listOf(keep), p.movies.value)
    }

    @Test
    fun notifyUpdated_unknownId_leavesListUnchanged() = runTest(dispatcher) {
        val a = movie("a")
        val p = pager(FakeRepo(pages = listOf(listOf(a))), this)
        p.loadFirstPage("uid", "list1")
        p.notifyUpdated(movie("z"))
        assertEquals(listOf(a), p.movies.value)
    }
}
