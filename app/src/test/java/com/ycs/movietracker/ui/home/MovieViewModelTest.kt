package com.ycs.movietracker.ui.home

import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.SortOrder
import com.ycs.movietracker.data.model.WatchStatus
import com.ycs.movietracker.data.repository.MovieRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MovieViewModel] — companion test story US-011-T.
 * Parent story: US-011 (MovieViewModel - filter/sort movies).
 *
 * Covers: search filtering (case-insensitive), sort orders, null-value
 * placement, activeListId reactivity, CRUD delegation, snackbar errors.
 *
 * Run with: ./gradlew test
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MovieViewModelTest {

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

    private class FakeMovieRepo(
        private val moviesByList: Map<String, List<Movie>> = emptyMap(),
        private val addResult: Result<String> = Result.success("new-id"),
        private val updateResult: Result<Unit> = Result.success(Unit),
        private val deleteResult: Result<Unit> = Result.success(Unit)
    ) : MovieRepository {
        var addCallCount = 0
        var updateCallCount = 0
        var deleteCallCount = 0
        var lastDeletedId: String? = null

        override fun getMoviesForList(uid: String, listId: String): Flow<List<Movie>> =
            flowOf(moviesByList[listId] ?: emptyList())

        override suspend fun addMovie(uid: String, movie: Movie): Result<String> {
            addCallCount++
            return addResult
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

        override suspend fun removeListFromMovies(uid: String, listId: String): Result<Unit> =
            Result.success(Unit)
    }

    private fun movie(title: String, year: Int? = null, rating: Int? = null) =
        Movie(id = title, title = title, year = year, rating = rating)

    private fun makeVm(
        movies: List<Movie> = emptyList(),
        activeListId: String? = "list-1",
        addResult: Result<String> = Result.success("new-id"),
        updateResult: Result<Unit> = Result.success(Unit),
        deleteResult: Result<Unit> = Result.success(Unit)
    ): Pair<MovieViewModel, MutableStateFlow<String?>> {
        val repo = FakeMovieRepo(
            moviesByList = mapOf("list-1" to movies),
            addResult = addResult,
            updateResult = updateResult,
            deleteResult = deleteResult
        )
        val activeListIdFlow = MutableStateFlow(activeListId)
        val vm = MovieViewModel(repo, MutableStateFlow("uid"), activeListIdFlow)
        return vm to activeListIdFlow
    }

    // ── search filtering ─────────────────────────────────────────────────────

    @Test
    fun filteredMovies_noQuery_returnsAll() = runTest {
        val movies = listOf(movie("The Matrix"), movie("Inception"), movie("Avatar"))
        val (vm, _) = makeVm(movies = movies)
        assertEquals(3, vm.filteredMovies.value.size)
    }

    @Test
    fun filteredMovies_filtersBySearchQueryCaseInsensitive() = runTest {
        val movies = listOf(movie("The Matrix"), movie("Inception"), movie("matrix reloaded"))
        val (vm, _) = makeVm(movies = movies)
        vm.setSearchQuery("matrix")
        val titles = vm.filteredMovies.value.map { it.title }
        // Default sort is TITLE_ASC; "matrix reloaded" (m) < "The Matrix" (t)
        assertEquals(listOf("matrix reloaded", "The Matrix"), titles)
    }

    @Test
    fun filteredMovies_blankQuery_returnsAll() = runTest {
        val movies = listOf(movie("The Matrix"), movie("Inception"))
        val (vm, _) = makeVm(movies = movies)
        vm.setSearchQuery("matrix")
        vm.setSearchQuery("")
        assertEquals(2, vm.filteredMovies.value.size)
    }

    @Test
    fun filteredMovies_noMatchingQuery_returnsEmpty() = runTest {
        val movies = listOf(movie("The Matrix"), movie("Inception"))
        val (vm, _) = makeVm(movies = movies)
        vm.setSearchQuery("xyzzy")
        assertTrue(vm.filteredMovies.value.isEmpty())
    }

    // ── sort: TITLE ──────────────────────────────────────────────────────────

    @Test
    fun setSortOrder_titleAsc_producesAlphabeticallyAscendingList() = runTest {
        val movies = listOf(movie("Zorro"), movie("Avatar"), movie("Matrix"))
        val (vm, _) = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.TITLE_ASC)
        assertEquals(listOf("Avatar", "Matrix", "Zorro"), vm.filteredMovies.value.map { it.title })
    }

    @Test
    fun setSortOrder_titleDesc_producesAlphabeticallyDescendingList() = runTest {
        val movies = listOf(movie("Avatar"), movie("Zorro"), movie("Matrix"))
        val (vm, _) = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.TITLE_DESC)
        assertEquals(listOf("Zorro", "Matrix", "Avatar"), vm.filteredMovies.value.map { it.title })
    }

    @Test
    fun setSortOrder_titleAsc_caseInsensitive() = runTest {
        val movies = listOf(movie("zebra"), movie("Apple"), movie("mango"))
        val (vm, _) = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.TITLE_ASC)
        assertEquals(listOf("Apple", "mango", "zebra"), vm.filteredMovies.value.map { it.title })
    }

    // ── sort: YEAR ───────────────────────────────────────────────────────────

    @Test
    fun setSortOrder_yearAsc_placesNullYearAtEnd() = runTest {
        val movies = listOf(movie("A", year = null), movie("B", year = 2020), movie("C", year = 1990))
        val (vm, _) = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.YEAR_ASC)
        val result = vm.filteredMovies.value
        assertNull("last movie should have null year", result.last().year)
    }

    @Test
    fun setSortOrder_yearAsc_sortsNonNullYearsAscending() = runTest {
        val movies = listOf(movie("A", year = 2020), movie("B", year = 1990), movie("C", year = null))
        val (vm, _) = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.YEAR_ASC)
        val nonNull = vm.filteredMovies.value.filter { it.year != null }
        assertEquals(listOf(1990, 2020), nonNull.map { it.year })
    }

    @Test
    fun setSortOrder_yearDesc_placesNullYearAtEnd() = runTest {
        val movies = listOf(movie("A", year = null), movie("B", year = 2000), movie("C", year = 2010))
        val (vm, _) = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.YEAR_DESC)
        assertNull("last movie should have null year", vm.filteredMovies.value.last().year)
    }

    @Test
    fun setSortOrder_yearDesc_sortsNonNullYearsDescending() = runTest {
        val movies = listOf(movie("A", year = 1990), movie("B", year = 2020), movie("C", year = null))
        val (vm, _) = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.YEAR_DESC)
        val nonNull = vm.filteredMovies.value.filter { it.year != null }
        assertEquals(listOf(2020, 1990), nonNull.map { it.year })
    }

    // ── sort: RATING ─────────────────────────────────────────────────────────

    @Test
    fun setSortOrder_ratingAsc_placesUnratedMoviesAtEnd() = runTest {
        val movies = listOf(movie("A", rating = null), movie("B", rating = 3), movie("C", rating = 1))
        val (vm, _) = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.RATING_ASC)
        assertNull("last movie should have null rating", vm.filteredMovies.value.last().rating)
    }

    @Test
    fun setSortOrder_ratingAsc_sortsNonNullRatingsAscending() = runTest {
        val movies = listOf(movie("A", rating = 5), movie("B", rating = 1), movie("C", rating = null))
        val (vm, _) = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.RATING_ASC)
        val withRating = vm.filteredMovies.value.filter { it.rating != null }
        assertEquals(listOf(1, 5), withRating.map { it.rating })
    }

    @Test
    fun setSortOrder_ratingDesc_placesUnratedMoviesAtEnd() = runTest {
        val movies = listOf(movie("A", rating = null), movie("B", rating = 5), movie("C", rating = 2))
        val (vm, _) = makeVm(movies = movies)
        vm.setSortOrder(SortOrder.RATING_DESC)
        assertNull("last movie should have null rating", vm.filteredMovies.value.last().rating)
    }

    // ── activeListId reactivity ───────────────────────────────────────────────

    @Test
    fun filteredMovies_emitsNewResultsWhenActiveListIdChanges() = runTest {
        val list1Movies = listOf(movie("Matrix"))
        val list2Movies = listOf(movie("Inception"), movie("Avatar"))
        val repo = FakeMovieRepo(moviesByList = mapOf("list-1" to list1Movies, "list-2" to list2Movies))
        val activeListIdFlow = MutableStateFlow<String?>("list-1")
        val vm = MovieViewModel(repo, MutableStateFlow("uid"), activeListIdFlow)

        assertEquals(1, vm.filteredMovies.value.size)

        activeListIdFlow.value = "list-2"
        assertEquals(2, vm.filteredMovies.value.size)
    }

    @Test
    fun filteredMovies_nullActiveListId_returnsEmpty() = runTest {
        val (vm, _) = makeVm(movies = listOf(movie("Matrix")), activeListId = null)
        assertTrue(vm.filteredMovies.value.isEmpty())
    }

    // ── CRUD delegation ───────────────────────────────────────────────────────

    @Test
    fun addMovie_delegatesToRepository() = runTest {
        val repo = FakeMovieRepo()
        val vm = MovieViewModel(repo, MutableStateFlow("uid"), MutableStateFlow("list-1"))
        vm.addMovie(movie("New Movie"))
        assertEquals(1, repo.addCallCount)
    }

    @Test
    fun updateMovie_delegatesToRepository() = runTest {
        val repo = FakeMovieRepo()
        val vm = MovieViewModel(repo, MutableStateFlow("uid"), MutableStateFlow("list-1"))
        vm.updateMovie(movie("Existing"))
        assertEquals(1, repo.updateCallCount)
    }

    @Test
    fun deleteMovie_delegatesToRepository() = runTest {
        val repo = FakeMovieRepo()
        val vm = MovieViewModel(repo, MutableStateFlow("uid"), MutableStateFlow("list-1"))
        vm.deleteMovie("movie-123")
        assertEquals(1, repo.deleteCallCount)
        assertEquals("movie-123", repo.lastDeletedId)
    }

    // ── snackbar errors ───────────────────────────────────────────────────────

    @Test
    fun addMovie_failure_setsSnackbarMessage() = runTest {
        val (vm, _) = makeVm(addResult = Result.failure(RuntimeException("Network error")))
        vm.addMovie(movie("New Movie"))
        assertNotNull(vm.snackbarMessage.value)
    }

    @Test
    fun updateMovie_failure_setsSnackbarMessage() = runTest {
        val (vm, _) = makeVm(updateResult = Result.failure(RuntimeException("fail")))
        vm.updateMovie(movie("Movie"))
        assertNotNull(vm.snackbarMessage.value)
    }

    @Test
    fun deleteMovie_failure_setsSnackbarMessage() = runTest {
        val (vm, _) = makeVm(deleteResult = Result.failure(RuntimeException("fail")))
        vm.deleteMovie("id")
        assertNotNull(vm.snackbarMessage.value)
    }

    @Test
    fun clearSnackbarMessage_clearsMessage() = runTest {
        val (vm, _) = makeVm(deleteResult = Result.failure(RuntimeException("fail")))
        vm.deleteMovie("id")
        assertNotNull("precondition", vm.snackbarMessage.value)
        vm.clearSnackbarMessage()
        assertNull(vm.snackbarMessage.value)
    }
}
