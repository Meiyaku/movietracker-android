package com.ycs.movietracker.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MoviesPage
import com.ycs.movietracker.data.model.NewMovie
import com.ycs.movietracker.data.model.SortOrder
import com.ycs.movietracker.data.model.WatchFilter
import com.ycs.movietracker.data.model.WatchStatus
import com.ycs.movietracker.data.repository.MovieRepository
import com.ycs.movietracker.data.repository.RemoteConfigRepository
import com.ycs.movietracker.util.AndroidStringProvider
import com.ycs.movietracker.util.NoOpSettingsRepository
import com.ycs.movietracker.data.repository.StaleCursorException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.ycs.movietracker.util.AppConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for [MovieViewModel] — companion test story US-011-T.
 * Parent story: US-011 (MovieViewModel - filter/sort movies).
 *
 * Covers: search filtering (case-insensitive), sort orders, null-value
 * placement, activeListId reactivity, CRUD delegation, snackbar errors.
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class MovieViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val fakeRemoteConfig = object : RemoteConfigRepository {
        override val pageSize = 50
        override val maxRetryAttempts = 3
        override val isTmdbSearchEnabled = MutableStateFlow(true)
        override val tmdbApiKey = MutableStateFlow("")
        override val whatsNew = MutableStateFlow("")
        override val whatsNewVersion = MutableStateFlow(0)
    }

    private val fakeSettings = NoOpSettingsRepository()

    // Shared scheduler so that viewModelScope (Dispatchers.Main), flowOn(computationDispatcher),
    // and runTest(testDispatcher) all advance the same virtual clock.
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private class FakeMovieRepo(
        private val moviesByList: Map<String, List<Movie>> = emptyMap(),
        private val pageSize: Int = 50,
        private val addId: String = "new-id",
        private val addFailure: Exception? = null,
        private val updateResult: Result<Unit> = Result.success(Unit),
        private val deleteResult: Result<Unit> = Result.success(Unit)
    ) : MovieRepository {
        var addCallCount = 0
        var updateCallCount = 0
        var deleteCallCount = 0
        var lastDeletedId: String? = null

        override suspend fun getMoviesPage(
            uid: String, listId: String, pageSize: Int, afterId: String?
        ): Result<MoviesPage> {
            val all = moviesByList[listId] ?: emptyList()
            val startIndex = if (afterId == null) 0
                else (all.indexOfFirst { it.id == afterId } + 1).coerceAtLeast(0)
            val page = all.drop(startIndex).take(this.pageSize)
            return Result.success(
                MoviesPage(
                    movies = page,
                    lastId = page.lastOrNull()?.id,
                    hasMore = startIndex + page.size < all.size
                )
            )
        }

        override suspend fun addMovie(uid: String, movie: NewMovie): Result<Movie> {
            addCallCount++
            return if (addFailure != null) Result.failure(addFailure)
                   else Result.success(movie.toMovie(id = addId))
        }

        override suspend fun updateMovie(uid: String, movie: Movie): Result<Unit> {
            updateCallCount++
            return updateResult
        }

        override suspend fun deleteMovie(uid: String, movieId: String): Result<Unit> {
            deleteCallCount++
            lastDeletedId = movieId
            return deleteResult
        }

        override suspend fun getMovieById(uid: String, movieId: String): Result<Movie> =
            Result.failure(UnsupportedOperationException())
        override suspend fun removeListFromMovies(uid: String, listId: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun checkDuplicate(uid: String, title: String, year: Int?, genre: String?, excludeId: String?): Result<Boolean> =
            Result.success(false)
        override suspend fun deleteAllMovies(uid: String): Result<Unit> = Result.success(Unit)
    }

    // Movies default into "list-1" — the active list used by makeVm — mirroring the
    // Firestore contract that a movie loaded for a list always carries that list's id.
    private fun movie(
        title: String,
        year: Int? = null,
        rating: Double? = null,
        listIds: List<String> = listOf("list-1")
    ) =
        Movie(id = title, title = title, year = year, rating = rating, listIds = listIds)

    private fun newMovie(title: String, year: Int? = null, rating: Double? = null) =
        NewMovie(title = title, year = year, rating = rating)

    private fun makeVm(
        movies: List<Movie> = emptyList(),
        activeListId: String? = "list-1",
        pageSize: Int = 50,
        addFailure: Exception? = null,
        updateResult: Result<Unit> = Result.success(Unit),
        deleteResult: Result<Unit> = Result.success(Unit),
        computationDispatcher: CoroutineDispatcher = testDispatcher
    ): MovieViewModel {
        val repo = FakeMovieRepo(
            moviesByList = mapOf("list-1" to movies),
            pageSize = pageSize,
            addFailure = addFailure,
            updateResult = updateResult,
            deleteResult = deleteResult
        )
        return MovieViewModel(repo, AndroidStringProvider(context), fakeRemoteConfig, fakeSettings, computationDispatcher).also { it.setSession("uid", activeListId) }
    }

    // ── search filtering ─────────────────────────────────────────────────────

    @Test
    fun filteredMovies_noQuery_returnsAll() = runTest {
        val movies = listOf(movie("The Matrix"), movie("Inception"), movie("Avatar"))
        val vm = makeVm(movies = movies)
        assertEquals(3, vm.filteredMovies.value.size)
    }

    @Test
    fun filteredMovies_filtersBySearchQueryCaseInsensitive() = runTest(testDispatcher) {
        val vm = makeVm(movies = listOf(movie("The Matrix"), movie("Inception"), movie("matrix reloaded")))
        vm.setSearchQuery("matrix")
        advanceTimeBy(AppConfig.SEARCH_DEBOUNCE_MS)
        runCurrent()
        val titles = vm.filteredMovies.value.map { it.title }
        // Default sort is TITLE_ASC; "matrix reloaded" (m) < "The Matrix" (t)
        assertEquals(listOf("matrix reloaded", "The Matrix"), titles)
    }

    @Test
    fun filteredMovies_blankQuery_returnsAll() = runTest {
        val movies = listOf(movie("The Matrix"), movie("Inception"))
        val vm = makeVm(movies = movies)
        vm.setSearchQuery("matrix")
        vm.setSearchQuery("")
        assertEquals(2, vm.filteredMovies.value.size)
    }

    @Test
    fun filteredMovies_noMatchingQuery_returnsEmpty() = runTest(testDispatcher) {
        val vm = makeVm(movies = listOf(movie("The Matrix"), movie("Inception")))
        vm.setSearchQuery("xyzzy")
        advanceTimeBy(AppConfig.SEARCH_DEBOUNCE_MS)
        runCurrent()
        assertTrue(vm.filteredMovies.value.isEmpty())
    }

    // ── sort: TITLE ──────────────────────────────────────────────────────────

    @Test
    fun setSortOrder_titleAsc_producesAlphabeticallyAscendingList() = runTest {
        val movies = listOf(movie("Zorro"), movie("Avatar"), movie("Matrix"))
        val vm = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.TITLE_ASC)
        assertEquals(listOf("Avatar", "Matrix", "Zorro"), vm.filteredMovies.value.map { it.title })
    }

    @Test
    fun setSortOrder_titleDesc_producesAlphabeticallyDescendingList() = runTest {
        val movies = listOf(movie("Avatar"), movie("Zorro"), movie("Matrix"))
        val vm = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.TITLE_DESC)
        assertEquals(listOf("Zorro", "Matrix", "Avatar"), vm.filteredMovies.value.map { it.title })
    }

    @Test
    fun setSortOrder_titleAsc_caseInsensitive() = runTest {
        val movies = listOf(movie("zebra"), movie("Apple"), movie("mango"))
        val vm = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.TITLE_ASC)
        assertEquals(listOf("Apple", "mango", "zebra"), vm.filteredMovies.value.map { it.title })
    }

    // ── sort: YEAR ───────────────────────────────────────────────────────────

    @Test
    fun setSortOrder_yearAsc_placesNullYearAtEnd() = runTest {
        val movies = listOf(movie("A", year = null), movie("B", year = 2020), movie("C", year = 1990))
        val vm = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.YEAR_ASC)
        val result = vm.filteredMovies.value
        assertNull("last movie should have null year", result.last().year)
    }

    @Test
    fun setSortOrder_yearAsc_sortsNonNullYearsAscending() = runTest {
        val movies = listOf(movie("A", year = 2020), movie("B", year = 1990), movie("C", year = null))
        val vm = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.YEAR_ASC)
        val nonNull = vm.filteredMovies.value.filter { it.year != null }
        assertEquals(listOf(1990, 2020), nonNull.map { it.year })
    }

    @Test
    fun setSortOrder_yearDesc_placesNullYearAtEnd() = runTest {
        val movies = listOf(movie("A", year = null), movie("B", year = 2000), movie("C", year = 2010))
        val vm = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.YEAR_DESC)
        assertNull("last movie should have null year", vm.filteredMovies.value.last().year)
    }

    @Test
    fun setSortOrder_yearDesc_sortsNonNullYearsDescending() = runTest {
        val movies = listOf(movie("A", year = 1990), movie("B", year = 2020), movie("C", year = null))
        val vm = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.YEAR_DESC)
        val nonNull = vm.filteredMovies.value.filter { it.year != null }
        assertEquals(listOf(2020, 1990), nonNull.map { it.year })
    }

    // ── sort: RATING ─────────────────────────────────────────────────────────

    @Test
    fun setSortOrder_ratingAsc_placesUnratedMoviesAtEnd() = runTest {
        val movies = listOf(movie("A", rating = null), movie("B", rating = 3.0), movie("C", rating = 1.0))
        val vm = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.RATING_ASC)
        assertNull("last movie should have null rating", vm.filteredMovies.value.last().rating)
    }

    @Test
    fun setSortOrder_ratingAsc_sortsNonNullRatingsAscending() = runTest {
        val movies = listOf(movie("A", rating = 5.0), movie("B", rating = 1.0), movie("C", rating = null))
        val vm = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.RATING_ASC)
        val withRating = vm.filteredMovies.value.filter { it.rating != null }
        assertEquals(listOf(1.0, 5.0), withRating.map { it.rating })
    }

    @Test
    fun setSortOrder_ratingDesc_placesUnratedMoviesAtEnd() = runTest {
        val movies = listOf(movie("A", rating = null), movie("B", rating = 5.0), movie("C", rating = 2.0))
        val vm = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.RATING_DESC)
        assertNull("last movie should have null rating", vm.filteredMovies.value.last().rating)
    }

    // ── activeListId reactivity ───────────────────────────────────────────────

    @Test
    fun filteredMovies_emitsNewResultsWhenActiveListIdChanges() = runTest {
        val list1Movies = listOf(movie("Matrix"))
        val list2Movies = listOf(movie("Inception"), movie("Avatar"))
        val repo = FakeMovieRepo(moviesByList = mapOf("list-1" to list1Movies, "list-2" to list2Movies))
        val vm = MovieViewModel(repo, AndroidStringProvider(context), fakeRemoteConfig, fakeSettings, testDispatcher).also { it.setSession("uid", "list-1") }

        assertEquals(1, vm.filteredMovies.value.size)

        vm.setSession("uid", "list-2")
        assertEquals(2, vm.filteredMovies.value.size)
    }

    @Test
    fun filteredMovies_nullActiveListId_returnsEmpty() = runTest {
        val vm = makeVm(movies = listOf(movie("Matrix")), activeListId = null)
        assertTrue(vm.filteredMovies.value.isEmpty())
    }

    // ── CRUD delegation ───────────────────────────────────────────────────────

    @Test
    fun addMovie_delegatesToRepository() = runTest {
        val repo = FakeMovieRepo()
        val vm = MovieViewModel(repo, AndroidStringProvider(context), fakeRemoteConfig, fakeSettings, testDispatcher).also { it.setSession("uid", "list-1") }
        vm.addMovie(newMovie("New Movie"))
        assertEquals(1, repo.addCallCount)
    }

    @Test
    fun updateMovie_delegatesToRepository() = runTest {
        val repo = FakeMovieRepo()
        val vm = MovieViewModel(repo, AndroidStringProvider(context), fakeRemoteConfig, fakeSettings, testDispatcher).also { it.setSession("uid", "list-1") }
        vm.updateMovie(movie("Existing"))
        assertEquals(1, repo.updateCallCount)
    }

    @Test
    fun deleteMovie_delegatesToRepository() = runTest {
        val repo = FakeMovieRepo()
        val vm = MovieViewModel(repo, AndroidStringProvider(context), fakeRemoteConfig, fakeSettings, testDispatcher).also { it.setSession("uid", "list-1") }
        vm.deleteMovie("movie-123")
        assertEquals(1, repo.deleteCallCount)
        assertEquals("movie-123", repo.lastDeletedId)
    }

    // ── isLoadingMovies ───────────────────────────────────────────────────────

    @Test
    fun isLoadingMovies_falseWhenActiveListIdIsNull() = runTest {
        val vm = makeVm(movies = listOf(movie("Matrix")), activeListId = null)
        assertFalse(vm.isLoadingMovies.value)
    }

    @Test
    fun isLoadingMovies_falseAfterMoviesEmitted() = runTest {
        val vm = makeVm(movies = listOf(movie("Matrix")))
        assertFalse(vm.isLoadingMovies.value)
    }

    @Test
    fun isLoadingMovies_falseAfterEmptyMoviesEmitted() = runTest {
        val vm = makeVm(movies = emptyList())
        assertFalse(vm.isLoadingMovies.value)
    }

    // ── snackbar errors ───────────────────────────────────────────────────────

    @Test
    fun addMovie_failure_setsSnackbarMessage() = runTest {
        val vm = makeVm(addFailure = RuntimeException("Network error"))
        vm.addMovie(newMovie("New Movie"))
        assertNotNull(vm.snackbarMessage.value)
    }

    @Test
    fun updateMovie_failure_setsSnackbarMessage() = runTest {
        val vm = makeVm(updateResult = Result.failure(RuntimeException("fail")))
        vm.updateMovie(movie("Movie"))
        assertNotNull(vm.snackbarMessage.value)
    }

    @Test
    fun deleteMovie_failure_setsSnackbarMessage() = runTest {
        val vm = makeVm(deleteResult = Result.failure(RuntimeException("fail")))
        vm.deleteMovie("id")
        assertNotNull(vm.snackbarMessage.value)
    }

    @Test
    fun clearSnackbarMessage_clearsMessage() = runTest {
        val vm = makeVm(deleteResult = Result.failure(RuntimeException("fail")))
        vm.deleteMovie("id")
        assertNotNull("precondition", vm.snackbarMessage.value)
        vm.clearSnackbarMessage()
        assertNull(vm.snackbarMessage.value)
    }

    // ── pagination ────────────────────────────────────────────────────────────

    @Test
    fun loadMoreMovies_appendsNextPage() = runTest {
        val allMovies = (1..5).map { movie("Movie $it") }
        val vm = makeVm(movies = allMovies, pageSize = 3)
        assertEquals(3, vm.filteredMovies.value.size)
        vm.loadMoreMovies()
        assertEquals(5, vm.filteredMovies.value.size)
    }

    @Test
    fun hasMoreMovies_trueWhenFirstPageIsFull() = runTest {
        val allMovies = (1..5).map { movie("Movie $it") }
        val vm = makeVm(movies = allMovies, pageSize = 3)
        assertTrue(vm.hasMoreMovies.value)
    }

    @Test
    fun hasMoreMovies_falseWhenFewerMoviesThanPageSize() = runTest {
        val vm = makeVm(movies = (1..2).map { movie("Movie $it") }, pageSize = 3)
        assertFalse(vm.hasMoreMovies.value)
    }

    @Test
    fun loadMoreMovies_noopWhenHasMoreIsFalse() = runTest {
        val vm = makeVm(movies = listOf(movie("Only")), pageSize = 3)
        vm.loadMoreMovies()
        assertEquals(1, vm.filteredMovies.value.size)
    }

    @Test
    fun hasMoreMovies_falseWhenPageIsEmptyEvenIfRepositoryClaimsMore() = runTest {
        // Simulate a misbehaving server: returns empty page but says hasMore=true.
        // The safeguard must force hasMoreMovies=false to prevent an infinite load loop
        // where lastMovieCursor resets to null and loadMore keeps re-fetching page 1.
        val lyingRepo = object : MovieRepository {
            override suspend fun getMoviesPage(
                uid: String, listId: String, pageSize: Int, afterId: String?
            ) = Result.success(MoviesPage(movies = emptyList(), lastId = null, hasMore = true))
            override suspend fun addMovie(uid: String, movie: NewMovie) = Result.success(movie.toMovie(id = "id"))
            override suspend fun updateMovie(uid: String, movie: Movie) = Result.success(Unit)
            override suspend fun deleteMovie(uid: String, movieId: String) = Result.success(Unit)
            override suspend fun getMovieById(uid: String, movieId: String) = Result.failure<Movie>(UnsupportedOperationException())
            override suspend fun removeListFromMovies(uid: String, listId: String) = Result.success(Unit)
            override suspend fun checkDuplicate(uid: String, title: String, year: Int?, genre: String?, excludeId: String?) = Result.success(false)
            override suspend fun deleteAllMovies(uid: String) = Result.success(Unit)
        }
        val vm = MovieViewModel(lyingRepo, AndroidStringProvider(context), fakeRemoteConfig, fakeSettings, testDispatcher).also { it.setSession("uid", "list-1") }
        assertFalse(vm.hasMoreMovies.value)
    }

    @Test
    fun loadMoreMovies_restartsFromPageOneWhenCursorIsStale() = runTest {
        val allMovies = (1..6).map { movie("Movie $it") }
        // Repo throws StaleCursorException on the first paginated request, then succeeds normally.
        var callCount = 0
        val staleCursorRepo = object : MovieRepository {
            override suspend fun getMoviesPage(
                uid: String, listId: String, pageSize: Int, afterId: String?
            ): Result<MoviesPage> {
                callCount++
                if (afterId != null && callCount == 2) {
                    return Result.failure(StaleCursorException(afterId))
                }
                val start = if (afterId == null) 0
                    else (allMovies.indexOfFirst { it.id == afterId } + 1).coerceAtLeast(0)
                val page = allMovies.drop(start).take(pageSize)
                return Result.success(MoviesPage(page, page.lastOrNull()?.id, start + page.size < allMovies.size))
            }
            override suspend fun addMovie(uid: String, movie: NewMovie) = Result.success(movie.toMovie(id = "id"))
            override suspend fun updateMovie(uid: String, movie: Movie) = Result.success(Unit)
            override suspend fun deleteMovie(uid: String, movieId: String) = Result.success(Unit)
            override suspend fun getMovieById(uid: String, movieId: String) = Result.failure<Movie>(UnsupportedOperationException())
            override suspend fun removeListFromMovies(uid: String, listId: String) = Result.success(Unit)
            override suspend fun checkDuplicate(uid: String, title: String, year: Int?, genre: String?, excludeId: String?) = Result.success(false)
            override suspend fun deleteAllMovies(uid: String) = Result.success(Unit)
        }
        val vm = MovieViewModel(staleCursorRepo, AndroidStringProvider(context), fakeRemoteConfig, fakeSettings, testDispatcher)
            .also { it.setSession("uid", "list-1") }
        // After stale-cursor recovery the list should be reloaded from page 1 with no error shown.
        assertEquals(allMovies.size, vm.filteredMovies.value.size)
        assertNull("no snackbar on stale cursor", vm.snackbarMessage.value)
    }

    // ── WatchFilter ───────────────────────────────────────────────────────────

    private fun watchedMovie(title: String) =
        Movie(id = title, title = title, status = WatchStatus.WATCHED)

    private fun wantToWatchMovie(title: String) =
        Movie(id = title, title = title, status = WatchStatus.WANT_TO_WATCH)

    @Test
    fun setWatchFilter_all_returnsAllMovies() = runTest {
        val movies = listOf(watchedMovie("A"), wantToWatchMovie("B"), watchedMovie("C"))
        val vm = makeVm(movies = movies)
        vm.setWatchFilter(WatchFilter.ALL)
        assertEquals(3, vm.filteredMovies.value.size)
    }

    @Test
    fun setWatchFilter_watched_returnsOnlyWatchedMovies() = runTest {
        val movies = listOf(watchedMovie("A"), wantToWatchMovie("B"), watchedMovie("C"))
        val vm = makeVm(movies = movies)
        vm.setWatchFilter(WatchFilter.WATCHED)
        val result = vm.filteredMovies.value
        assertEquals(2, result.size)
        assertTrue(result.all { it.status == WatchStatus.WATCHED })
    }

    @Test
    fun setWatchFilter_wantToWatch_returnsOnlyWantToWatchMovies() = runTest {
        val movies = listOf(watchedMovie("A"), wantToWatchMovie("B"), wantToWatchMovie("C"))
        val vm = makeVm(movies = movies)
        vm.setWatchFilter(WatchFilter.WANT_TO_WATCH)
        val result = vm.filteredMovies.value
        assertEquals(2, result.size)
        assertTrue(result.all { it.status == WatchStatus.WANT_TO_WATCH })
    }

    @Test
    fun setWatchFilter_watched_noWatchedMovies_returnsEmpty() = runTest {
        val movies = listOf(wantToWatchMovie("A"), wantToWatchMovie("B"))
        val vm = makeVm(movies = movies)
        vm.setWatchFilter(WatchFilter.WATCHED)
        assertTrue(vm.filteredMovies.value.isEmpty())
    }

    @Test
    fun setWatchFilter_combinedWithSearchQuery_appliesBothFilters() = runTest(testDispatcher) {
        val movies = listOf(
            watchedMovie("Matrix"),
            wantToWatchMovie("Matrix Reloaded"),
            watchedMovie("Inception")
        )
        val vm = makeVm(movies = movies)
        vm.setWatchFilter(WatchFilter.WATCHED)
        vm.setSearchQuery("matrix")
        advanceTimeBy(AppConfig.SEARCH_DEBOUNCE_MS)
        runCurrent()
        val result = vm.filteredMovies.value
        assertEquals(1, result.size)
        assertEquals("Matrix", result.first().title)
    }

    @Test
    fun setWatchFilter_switchingFromWatchedToAll_restoresFullList() = runTest {
        val movies = listOf(watchedMovie("A"), wantToWatchMovie("B"))
        val vm = makeVm(movies = movies)
        vm.setWatchFilter(WatchFilter.WATCHED)
        assertEquals(1, vm.filteredMovies.value.size)
        vm.setWatchFilter(WatchFilter.ALL)
        assertEquals(2, vm.filteredMovies.value.size)
    }

    // ── optimistic CRUD ───────────────────────────────────────────────────────

    @Test
    fun addMovie_optimisticallyAppearsInList() = runTest {
        val vm = makeVm(movies = listOf(movie("Existing")))
        vm.addMovie(newMovie("New"))
        assertTrue(vm.filteredMovies.value.any { it.title == "New" })
    }

    @Test
    fun updateMovie_optimisticallyReplacesInList() = runTest {
        val existing = movie("Old Title")
        val vm = makeVm(movies = listOf(existing))
        val updated = existing.copy(title = "New Title")
        vm.updateMovie(updated)
        assertTrue(vm.filteredMovies.value.any { it.title == "New Title" })
        assertFalse(vm.filteredMovies.value.any { it.title == "Old Title" })
    }

    @Test
    fun updateMovie_removedFromActiveList_dropsFromList() = runTest {
        val removed = movie("Removed")
        val vm = makeVm(movies = listOf(removed, movie("Keep")))
        vm.updateMovie(removed.copy(listIds = emptyList()))
        assertFalse(vm.filteredMovies.value.any { it.id == removed.id })
        assertEquals(1, vm.filteredMovies.value.size)
    }

    @Test
    fun notifyMovieUpdated_removedFromActiveList_dropsFromList() = runTest {
        val removed = movie("Removed")
        val vm = makeVm(movies = listOf(removed, movie("Keep")))
        vm.notifyMovieUpdated(removed.copy(listIds = listOf("other-list")))
        assertFalse(vm.filteredMovies.value.any { it.id == removed.id })
        assertEquals(1, vm.filteredMovies.value.size)
    }

    @Test
    fun deleteMovie_optimisticallyRemovedFromList() = runTest {
        val toDelete = movie("ToDelete")
        val vm = makeVm(movies = listOf(toDelete, movie("Keep")))
        vm.deleteMovie(toDelete.id)
        assertFalse(vm.filteredMovies.value.any { it.id == toDelete.id })
        assertEquals(1, vm.filteredMovies.value.size)
    }

    // ── empty search state (drives "Add Movie from search" UI) ────────────────

    @Test
    fun setSearchQuery_noMatch_filteredMoviesEmptyAndQueryPreserved() = runTest(testDispatcher) {
        val vm = makeVm(movies = listOf(movie("Inception"), movie("The Matrix")))
        vm.setSearchQuery("zzznomatch")
        advanceTimeBy(AppConfig.SEARCH_DEBOUNCE_MS)
        runCurrent()
        assertTrue(vm.filteredMovies.value.isEmpty())
        assertEquals("zzznomatch", vm.searchQuery.value)
    }

    @Test
    fun setSearchQuery_clearAfterNoMatch_restoresFilteredMovies() = runTest(testDispatcher) {
        val vm = makeVm(movies = listOf(movie("Inception"), movie("The Matrix")))
        vm.setSearchQuery("zzznomatch")
        advanceTimeBy(AppConfig.SEARCH_DEBOUNCE_MS)
        runCurrent()
        assertTrue("precondition: no results", vm.filteredMovies.value.isEmpty())
        vm.setSearchQuery("")
        assertEquals(2, vm.filteredMovies.value.size)
    }

    // ── reset filter / sort ───────────────────────────────────────────────────

    @Test
    fun setWatchFilter_resetToAll_afterFilterApplied_restoresAllMovies() = runTest {
        val movies = listOf(watchedMovie("A"), wantToWatchMovie("B"), watchedMovie("C"))
        val vm = makeVm(movies = movies)
        vm.setWatchFilter(WatchFilter.WATCHED)
        assertEquals(2, vm.filteredMovies.value.size)
        vm.setWatchFilter(WatchFilter.ALL)
        assertEquals(3, vm.filteredMovies.value.size)
    }

    @Test
    fun setSortOrder_resetToTitleAsc_afterSortChanged_restoresAlphabeticalOrder() = runTest {
        val movies = listOf(movie("Zorro"), movie("Avatar"), movie("Matrix"))
        val vm = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.TITLE_DESC)
        assertEquals("Zorro", vm.filteredMovies.value.first().title)
        vm.setSortOrder(SortOrder.TITLE_ASC)
        assertEquals("Avatar", vm.filteredMovies.value.first().title)
    }
}
