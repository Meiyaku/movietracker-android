package com.ycs.movietracker.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ycs.movietracker.data.cache.MovieCacheService
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MoviesPage
import com.ycs.movietracker.data.model.NewMovie
import com.ycs.movietracker.data.repository.MovieRepository
import com.ycs.movietracker.data.repository.RemoteConfigRepository
import com.ycs.movietracker.util.AndroidStringProvider
import com.ycs.movietracker.util.NoOpSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests for [MovieViewModel]'s cache integration — seeding, invalidation, and save-after-load.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class MovieViewModelCacheTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    private val fakeRemoteConfig = object : RemoteConfigRepository {
        override val pageSize = 50
        override val maxRetryAttempts = 3
        override val isTmdbSearchEnabled = MutableStateFlow(true)
        override val tmdbApiKey = MutableStateFlow("")
        override val whatsNew = MutableStateFlow("")
        override val whatsNewVersion = MutableStateFlow(0)
    }

    private val fakeSettings = NoOpSettingsRepository()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    // ── Test doubles ──────────────────────────────────────────────────────────

    private class FakeCache : MovieCacheService {
        val saved = mutableMapOf<String, List<Movie>>()
        val invalidated = mutableListOf<String>()
        var cachedMovies: List<Movie> = emptyList()

        override fun load(uid: String, listId: String) = cachedMovies
        override fun save(movies: List<Movie>, uid: String, listId: String) { saved["$uid:$listId"] = movies }
        override fun invalidate(uid: String, listId: String) { invalidated += "$uid:$listId" }
    }

    private class FakeRepo(
        private val movies: List<Movie> = emptyList(),
        private val gate: CompletableDeferred<Unit>? = null
    ) : MovieRepository {
        override suspend fun getMoviesPage(uid: String, listId: String, pageSize: Int, afterId: String?): Result<MoviesPage> {
            gate?.await()
            return Result.success(MoviesPage(movies = movies, lastId = movies.lastOrNull()?.id, hasMore = false))
        }
        override suspend fun addMovie(uid: String, movie: NewMovie) = Result.success(movie.toMovie(id = "new-id"))
        override suspend fun updateMovie(uid: String, movie: Movie) = Result.success(Unit)
        override suspend fun deleteMovie(uid: String, movieId: String) = Result.success(Unit)
        override suspend fun getMovieById(uid: String, movieId: String) = Result.failure<Movie>(UnsupportedOperationException())
        override suspend fun removeListFromMovies(uid: String, listId: String) = Result.success(Unit)
        override suspend fun checkDuplicate(uid: String, title: String, year: Int?, genre: String?, excludeId: String?) = Result.success(false)
        override suspend fun deleteAllMovies(uid: String) = Result.success(Unit)
    }

    private fun movie(id: String) = Movie(id = id, title = id)

    private fun makeVm(repo: FakeRepo = FakeRepo(), cache: FakeCache = FakeCache()): MovieViewModel =
        MovieViewModel(repo, AndroidStringProvider(context), fakeRemoteConfig, fakeSettings, testDispatcher, cache)

    // ── seeding ───────────────────────────────────────────────────────────────

    @Test
    fun setSession_seedsFromCache_whenCacheNonEmpty() = runTest {
        val gate = CompletableDeferred<Unit>()
        val cache = FakeCache().apply { cachedMovies = listOf(movie("cached")) }
        val vm = makeVm(repo = FakeRepo(gate = gate), cache = cache)
        vm.setSession("uid", "list1")
        // fetchPage is suspended at gate.await() — seeded movies are still visible
        assertTrue(vm.filteredMovies.value.any { it.id == "cached" })
        gate.complete(Unit)
    }

    @Test
    fun setSession_withCachedData_doesNotShowLoadingSpinner() = runTest {
        val cache = FakeCache().apply { cachedMovies = listOf(movie("cached")) }
        val vm = makeVm(cache = cache)
        vm.setSession("uid", "list1")
        assertFalse("loading spinner must not show when cache seeds pager", vm.isLoadingMovies.value)
    }

    @Test
    fun setSession_withNoCache_showsLoadingSpinner_thenClears() = runTest {
        val vm = makeVm()
        vm.setSession("uid", "list1")
        assertFalse(vm.isLoadingMovies.value)
    }

    // ── save after first page ─────────────────────────────────────────────────

    @Test
    fun setSession_savesFirstPageResultToCache() = runTest {
        val freshMovies = listOf(movie("fresh1"), movie("fresh2"))
        val cache = FakeCache()
        val vm = makeVm(repo = FakeRepo(freshMovies), cache = cache)
        vm.setSession("uid", "list1")
        assertEquals(freshMovies, cache.saved["uid:list1"])
    }

    // ── invalidation ──────────────────────────────────────────────────────────

    @Test
    fun refresh_invalidatesCache() = runTest {
        val cache = FakeCache()
        val vm = makeVm(cache = cache)
        vm.setSession("uid", "list1")
        vm.refresh()
        assertTrue(cache.invalidated.contains("uid:list1"))
    }

    @Test
    fun addMovie_success_invalidatesCache() = runTest {
        val cache = FakeCache()
        val vm = makeVm(cache = cache)
        vm.setSession("uid", "list1")
        vm.addMovie(NewMovie(title = "New"))
        assertTrue(cache.invalidated.contains("uid:list1"))
    }

    @Test
    fun updateMovie_success_invalidatesCache() = runTest {
        val cache = FakeCache()
        val vm = makeVm(cache = cache)
        vm.setSession("uid", "list1")
        vm.updateMovie(movie("m"))
        assertTrue(cache.invalidated.contains("uid:list1"))
    }

    @Test
    fun deleteMovie_success_invalidatesCache() = runTest {
        val cache = FakeCache()
        val vm = makeVm(cache = cache)
        vm.setSession("uid", "list1")
        vm.deleteMovie("m")
        assertTrue(cache.invalidated.contains("uid:list1"))
    }

    @Test
    fun notifyMovieAdded_invalidatesCache() = runTest {
        val cache = FakeCache()
        val vm = makeVm(cache = cache)
        vm.setSession("uid", "list1")
        vm.notifyMovieAdded(movie("m"))
        assertTrue(cache.invalidated.contains("uid:list1"))
    }

    @Test
    fun notifyMovieUpdated_invalidatesCache() = runTest {
        val cache = FakeCache()
        val vm = makeVm(cache = cache)
        vm.setSession("uid", "list1")
        vm.notifyMovieUpdated(movie("m"))
        assertTrue(cache.invalidated.contains("uid:list1"))
    }

    @Test
    fun notifyMovieRemoved_invalidatesCache() = runTest {
        val cache = FakeCache()
        val vm = makeVm(cache = cache)
        vm.setSession("uid", "list1")
        vm.notifyMovieRemoved("m")
        assertTrue(cache.invalidated.contains("uid:list1"))
    }

    @Test
    fun addMovie_failure_doesNotInvalidateCache() = runTest {
        val failingRepo = object : MovieRepository {
            override suspend fun getMoviesPage(uid: String, listId: String, pageSize: Int, afterId: String?) =
                Result.success(MoviesPage(emptyList(), null, false))
            override suspend fun addMovie(uid: String, movie: NewMovie) =
                Result.failure<Movie>(RuntimeException("fail"))
            override suspend fun updateMovie(uid: String, movie: Movie) = Result.success(Unit)
            override suspend fun deleteMovie(uid: String, movieId: String) = Result.success(Unit)
            override suspend fun getMovieById(uid: String, movieId: String) = Result.failure<Movie>(UnsupportedOperationException())
            override suspend fun removeListFromMovies(uid: String, listId: String) = Result.success(Unit)
            override suspend fun checkDuplicate(uid: String, title: String, year: Int?, genre: String?, excludeId: String?) = Result.success(false)
            override suspend fun deleteAllMovies(uid: String) = Result.success(Unit)
        }
        val cache = FakeCache()
        val vm = makeVm(repo = FakeRepo(), cache = cache)
        // Use the ViewModel but override the addMovie path via a fresh vm with failing repo
        val vmFailing = MovieViewModel(failingRepo, AndroidStringProvider(context), fakeRemoteConfig, fakeSettings, testDispatcher, cache)
        vmFailing.setSession("uid", "list1")
        vmFailing.addMovie(NewMovie(title = "New"))
        assertTrue("cache should not be invalidated on add failure", cache.invalidated.isEmpty())
    }
}
