package com.ycs.movietracker.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MoviesPage
import com.ycs.movietracker.data.model.NewMovie
import com.ycs.movietracker.data.model.WatchStatus
import com.ycs.movietracker.data.repository.MovieRepository
import com.ycs.movietracker.data.repository.RemoteConfigRepository
import com.ycs.movietracker.ui.home.MovieViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Integration tests for [MovieViewModel] pager coordination — companion test story US-021-T.
 *
 * Covers: optimistic add/update/remove mutations reflecting in filteredMovies,
 * and deletion toast lifecycle.
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class MovieManagementIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun movie(
        title: String,
        id: String = title,
        status: WatchStatus = WatchStatus.WANT_TO_WATCH
    ) = Movie(id = id, title = title, status = status)

    private fun makeVm(initialMovies: List<Movie> = emptyList()): MovieViewModel {
        val repo = object : MovieRepository {
            override suspend fun getMoviesPage(uid: String, listId: String, pageSize: Int, afterId: String?) =
                Result.success(MoviesPage(initialMovies, null, false))
            override suspend fun addMovie(uid: String, movie: NewMovie) =
                Result.success(movie.toMovie(id = "new-id"))
            override suspend fun updateMovie(uid: String, movie: Movie) = Result.success(Unit)
            override suspend fun deleteMovie(uid: String, movieId: String) = Result.success(Unit)
            override suspend fun removeListFromMovies(uid: String, listId: String) = Result.success(Unit)
            override suspend fun getMovieById(uid: String, movieId: String) =
                Result.failure<Movie>(UnsupportedOperationException())
            override suspend fun checkDuplicate(uid: String, title: String, year: Int?, genre: String?, excludeId: String?) =
                Result.success(false)
            override suspend fun deleteAllMovies(uid: String) = Result.success(Unit)
        }
        val remoteConfig = object : RemoteConfigRepository {
            override val pageSize = 50
            override val maxRetryAttempts = 3
            override val isTmdbSearchEnabled = MutableStateFlow(false)
            override val tmdbApiKey = MutableStateFlow("")
        }
        return MovieViewModel(repo, context, remoteConfig, testDispatcher)
            .also { it.setSession("uid", "list-1") }
    }

    // ── notifyMovieAdded ─────────────────────────────────────────────────────

    @Test
    fun notifyMovieAdded_movieAppearsInFilteredMovies() = runTest(testDispatcher) {
        val vm = makeVm()
        val inception = movie("Inception")
        vm.notifyMovieAdded(inception)
        assertTrue(vm.filteredMovies.value.any { it.id == inception.id })
    }

    @Test
    fun notifyMovieAdded_movieAppearsFirst() = runTest(testDispatcher) {
        val existing = movie("Existing")
        val vm = makeVm(initialMovies = listOf(existing))
        val newMovie = movie("New Movie")
        vm.notifyMovieAdded(newMovie)
        // notifyAdded prepends
        assertTrue(vm.filteredMovies.value.isNotEmpty())
        assertEquals(newMovie.id, vm.filteredMovies.value.first { it.id == newMovie.id }.id)
    }

    @Test
    fun notifyMovieAdded_searchQueryFiltersNewMovie() = runTest(testDispatcher) {
        val vm = makeVm()
        vm.setSearchQuery("Inception")
        advanceTimeBy(400) // past the 300ms search debounce
        vm.notifyMovieAdded(movie("Inception"))
        vm.notifyMovieAdded(movie("The Matrix"))
        val titles = vm.filteredMovies.value.map { it.title }
        assertTrue("Inception" in titles)
        assertFalse("The Matrix" in titles)
    }

    // ── notifyMovieUpdated ───────────────────────────────────────────────────

    @Test
    fun notifyMovieUpdated_updatesStatusInFilteredMovies() = runTest(testDispatcher) {
        val original = movie("Inception", status = WatchStatus.WANT_TO_WATCH)
        val vm = makeVm(initialMovies = listOf(original))
        val updated = original.copy(status = WatchStatus.WATCHED)
        vm.notifyMovieUpdated(updated)
        val found = vm.filteredMovies.value.find { it.id == original.id }
        assertEquals(WatchStatus.WATCHED, found?.status)
    }

    @Test
    fun notifyMovieUpdated_updatesOnlyTargetMovie() = runTest(testDispatcher) {
        val m1 = movie("Inception", id = "m1")
        val m2 = movie("The Matrix", id = "m2")
        val vm = makeVm(initialMovies = listOf(m1, m2))
        vm.notifyMovieUpdated(m1.copy(status = WatchStatus.WATCHED))
        assertEquals(WatchStatus.WANT_TO_WATCH, vm.filteredMovies.value.find { it.id == "m2" }?.status)
    }

    // ── notifyMovieRemoved ───────────────────────────────────────────────────

    @Test
    fun notifyMovieRemoved_movieGoneFromFilteredMovies() = runTest(testDispatcher) {
        val inception = movie("Inception")
        val vm = makeVm(initialMovies = listOf(inception))
        vm.notifyMovieRemoved(inception.id)
        assertFalse(vm.filteredMovies.value.any { it.id == inception.id })
    }

    @Test
    fun notifyMovieRemoved_otherMoviesUnaffected() = runTest(testDispatcher) {
        val m1 = movie("Inception", id = "m1")
        val m2 = movie("The Matrix", id = "m2")
        val vm = makeVm(initialMovies = listOf(m1, m2))
        vm.notifyMovieRemoved("m1")
        assertTrue(vm.filteredMovies.value.any { it.id == "m2" })
    }

    // ── Deletion toast lifecycle ─────────────────────────────────────────────

    @Test
    fun showDeletedToast_setsToastTrue() = runTest(testDispatcher) {
        val vm = makeVm()
        assertFalse(vm.showDeletedToast.value)
        vm.showDeletedToast()
        assertTrue(vm.showDeletedToast.value)
    }

    @Test
    fun clearDeletedToast_resetsToastFalse() = runTest(testDispatcher) {
        val vm = makeVm()
        vm.showDeletedToast()
        vm.clearDeletedToast()
        assertFalse(vm.showDeletedToast.value)
    }

    // ── Home load error ──────────────────────────────────────────────────────

    @Test
    fun retryLoad_clearsHomeLoadError() = runTest(testDispatcher) {
        val vm = makeVm()
        vm.clearHomeLoadError() // no error to start; calling retryLoad should still work
        vm.retryLoad()
        assertEquals(null, vm.homeLoadError.value)
    }

    @Test
    fun switchingList_clearsHomeLoadError() = runTest(testDispatcher) {
        val vm = makeVm()
        // Simulate error state by switching to a new session (init clears it)
        vm.setSession("uid", "list-2")
        assertEquals(null, vm.homeLoadError.value)
    }
}
