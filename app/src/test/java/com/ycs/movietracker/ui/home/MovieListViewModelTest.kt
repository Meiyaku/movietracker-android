package com.ycs.movietracker.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.model.MoviesPage
import com.ycs.movietracker.data.model.NewMovie
import com.ycs.movietracker.data.repository.MovieListRepository
import com.ycs.movietracker.data.repository.MovieRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for [MovieListViewModel] — companion test story US-006-T.
 * Parent story: US-006 (Display and navigate lists).
 *
 * Covers: sort order, active list defaulting, list selection.
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class MovieListViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private val noopMovieRepo = object : MovieRepository {
        override suspend fun getMoviesPage(uid: String, listId: String, pageSize: Int, afterId: String?): Result<MoviesPage> =
            Result.success(MoviesPage(emptyList(), null, false))
        override suspend fun addMovie(uid: String, movie: NewMovie): Result<Movie> = Result.success(movie.toMovie(id = ""))
        override suspend fun updateMovie(uid: String, movie: Movie): Result<Unit> = Result.success(Unit)
        override suspend fun deleteMovie(uid: String, movieId: String): Result<Unit> = Result.success(Unit)
        override suspend fun removeListFromMovies(uid: String, listId: String): Result<Unit> = Result.success(Unit)
        override suspend fun getMovieById(uid: String, movieId: String): Result<Movie> = Result.failure(UnsupportedOperationException())
        override suspend fun checkDuplicate(uid: String, title: String, year: Int?, genre: String?, excludeId: String?): Result<Boolean> = Result.success(false)
        override suspend fun deleteAllMovies(uid: String): Result<Unit> = Result.success(Unit)
    }

    private fun makeVm(lists: List<MovieList>): MovieListViewModel {
        val fakeRepo = object : MovieListRepository {
            override fun getLists(uid: String): Flow<Result<List<MovieList>>> = flowOf(Result.success(lists))
            override suspend fun createList(uid: String, list: MovieList): Result<String> =
                Result.success("id")
            override suspend fun updateList(uid: String, list: MovieList): Result<Unit> =
                Result.success(Unit)
            override suspend fun deleteList(uid: String, listId: String): Result<Unit> =
                Result.success(Unit)
            override suspend fun deleteAllLists(uid: String): Result<Unit> = Result.success(Unit)
        }
        return MovieListViewModel(fakeRepo, noopMovieRepo, context)
    }

    // ── sort() ───────────────────────────────────────────────────────────────

    @Test
    fun sort_myMoviesIsAlwaysFirst() {
        val input = listOf(
            MovieList(id = "2", name = "Action"),
            MovieList(id = "1", name = "All Movies"),
            MovieList(id = "3", name = "Sci-Fi")
        )
        val result = MovieListViewModel.sort(input)
        assertEquals("All Movies", result.first().name)
    }

    @Test
    fun sort_remainingListsAreAlphabeticalAfterMyMovies() {
        val input = listOf(
            MovieList(id = "3", name = "Sci-Fi"),
            MovieList(id = "1", name = "All Movies"),
            MovieList(id = "2", name = "Action"),
            MovieList(id = "4", name = "Drama")
        )
        val result = MovieListViewModel.sort(input)
        assertEquals(listOf("All Movies", "Action", "Drama", "Sci-Fi"), result.map { it.name })
    }

    @Test
    fun sort_caseInsensitiveAlphabeticalOrder() {
        val input = listOf(
            MovieList(id = "1", name = "zebra"),
            MovieList(id = "2", name = "Apple"),
            MovieList(id = "3", name = "mango")
        )
        val result = MovieListViewModel.sort(input)
        assertEquals(listOf("Apple", "mango", "zebra"), result.map { it.name })
    }

    @Test
    fun sort_emptyListReturnsEmpty() {
        assertEquals(emptyList<MovieList>(), MovieListViewModel.sort(emptyList()))
    }

    // ── loadLists / activeList defaulting ───────────────────────────────────

    @Test
    fun loadLists_defaultsActiveListToMyMoviesWhenPresent() = runTest {
        val lists = listOf(
            MovieList(id = "2", name = "Action"),
            MovieList(id = "1", name = "All Movies")
        )
        val vm = makeVm(lists)
        vm.loadLists("uid-123")
        assertEquals("All Movies", vm.activeList.value?.name)
    }

    @Test
    fun loadLists_fallsBackToFirstListWhenNoMyMovies() = runTest {
        val lists = listOf(
            MovieList(id = "2", name = "Action"),
            MovieList(id = "3", name = "Sci-Fi")
        )
        val vm = makeVm(lists)
        vm.loadLists("uid-123")
        // After sort, "Action" comes before "Sci-Fi"
        assertEquals("Action", vm.activeList.value?.name)
    }

    @Test
    fun loadLists_emptyListsLeavesActiveListNull() = runTest {
        val vm = makeVm(emptyList())
        vm.loadLists("uid-123")
        assertNull(vm.activeList.value)
    }

    @Test
    fun loadLists_populatesListsStateFlow() = runTest {
        val lists = listOf(
            MovieList(id = "1", name = "All Movies"),
            MovieList(id = "2", name = "Action")
        )
        val vm = makeVm(lists)
        vm.loadLists("uid-123")
        assertEquals(2, vm.lists.value.size)
    }

    // ── isLoadingLists ────────────────────────────────────────────────────────

    @Test
    fun isLoadingLists_falseInitially() {
        val vm = makeVm(emptyList())
        assertFalse(vm.isLoadingLists.value)
    }

    @Test
    fun isLoadingLists_falseAfterListsLoaded() = runTest {
        val vm = makeVm(listOf(MovieList(id = "1", name = "All Movies")))
        vm.loadLists("uid")
        assertFalse(vm.isLoadingLists.value)
    }

    @Test
    fun isLoadingLists_falseAfterEmptyListLoaded() = runTest {
        val vm = makeVm(emptyList())
        vm.loadLists("uid")
        assertFalse(vm.isLoadingLists.value)
    }

    // ── selectList ───────────────────────────────────────────────────────────

    @Test
    fun selectList_updatesActiveList() = runTest {
        val lists = listOf(
            MovieList(id = "1", name = "All Movies"),
            MovieList(id = "2", name = "Action")
        )
        val vm = makeVm(lists)
        vm.loadLists("uid-123")
        val action = lists.first { it.name == "Action" }
        vm.selectList(action)
        assertEquals(action.id, vm.activeList.value?.id)
    }

    @Test
    fun selectList_canSwitchBackToMyMovies() = runTest {
        val myMovies = MovieList(id = "1", name = "All Movies")
        val action = MovieList(id = "2", name = "Action")
        val vm = makeVm(listOf(myMovies, action))
        vm.loadLists("uid-123")
        vm.selectList(action)
        vm.selectList(myMovies)
        assertEquals("All Movies", vm.activeList.value?.name)
    }
}
