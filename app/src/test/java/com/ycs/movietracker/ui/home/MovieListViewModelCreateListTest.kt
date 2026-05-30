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
import com.ycs.movietracker.util.AndroidStringProvider
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for [MovieListViewModel.createList] — companion test story US-007-T.
 * Parent story: US-007 (Create a custom list).
 *
 * Covers: uniqueness validation (including case-insensitive), repository delegation,
 * active list selection on success, error state management.
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class MovieListViewModelCreateListTest {

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

    private class FakeRepo(
        private val initialLists: List<MovieList> = emptyList(),
        private val createResult: Result<String> = Result.success("new-id")
    ) : MovieListRepository {
        var createCallCount = 0
        var lastCreatedList: MovieList? = null

        override fun getLists(uid: String): Flow<Result<List<MovieList>>> = flowOf(Result.success(initialLists))
        override suspend fun createList(uid: String, list: MovieList): Result<String> {
            createCallCount++
            lastCreatedList = list
            return createResult
        }
        override suspend fun updateList(uid: String, list: MovieList): Result<Unit> =
            Result.success(Unit)
        override suspend fun deleteList(uid: String, listId: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun deleteAllLists(uid: String): Result<Unit> = Result.success(Unit)
    }

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

    private fun makeVm(
        initialLists: List<MovieList> = emptyList(),
        createResult: Result<String> = Result.success("new-id")
    ): Pair<MovieListViewModel, FakeRepo> {
        val repo = FakeRepo(initialLists, createResult)
        val onlineMonitor = object : com.ycs.movietracker.util.ConnectivityMonitor { override val isOnline = true }
        val vm = MovieListViewModel(repo, noopMovieRepo, AndroidStringProvider(context), onlineMonitor)
        return vm to repo
    }

    // ── createList — uniqueness validation ───────────────────────────────────

    @Test
    fun createList_duplicateName_setsError() = runTest {
        val existing = listOf(MovieList(id = "1", name = "Action"))
        val (vm, _) = makeVm(initialLists = existing)
        vm.loadLists("uid")
        vm.createList("Action", null, null, "uid")
        assertEquals("A list with this name already exists", (vm.createState.value as ListMutationState.Error).message)
    }

    @Test
    fun createList_duplicateNameCaseInsensitive_setsError() = runTest {
        val existing = listOf(MovieList(id = "1", name = "Action"))
        val (vm, _) = makeVm(initialLists = existing)
        vm.loadLists("uid")
        vm.createList("action", null, null, "uid")
        assertEquals("A list with this name already exists", (vm.createState.value as ListMutationState.Error).message)
    }

    @Test
    fun createList_duplicateName_doesNotCallRepository() = runTest {
        val existing = listOf(MovieList(id = "1", name = "Action"))
        val (vm, repo) = makeVm(initialLists = existing)
        vm.loadLists("uid")
        vm.createList("Action", null, null, "uid")
        assertEquals(0, repo.createCallCount)
    }

    @Test
    fun createList_duplicateWithLeadingTrailingSpaces_setsError() = runTest {
        val existing = listOf(MovieList(id = "1", name = "Action"))
        val (vm, _) = makeVm(initialLists = existing)
        vm.loadLists("uid")
        vm.createList("  Action  ", null, null, "uid")
        assertEquals("A list with this name already exists", (vm.createState.value as ListMutationState.Error).message)
    }

    // ── createList — success path ─────────────────────────────────────────────

    @Test
    fun createList_uniqueName_callsRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.createList("New List", null, null, "uid")
        assertEquals(1, repo.createCallCount)
    }

    @Test
    fun createList_uniqueName_passesCorrectNameToRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.createList("  My List  ", null, null, "uid")
        assertEquals("My List", repo.lastCreatedList?.name)
    }

    @Test
    fun createList_success_selectsNewListById() = runTest {
        val (vm, _) = makeVm(createResult = Result.success("returned-id"))
        vm.createList("New List", null, null, "uid")
        assertEquals("returned-id", vm.activeList.value?.id)
    }

    @Test
    fun createList_success_setsCreateListSuccess() = runTest {
        val (vm, _) = makeVm()
        vm.createList("New List", null, null, "uid")
        assertEquals(ListMutationState.Success, vm.createState.value)
    }

    @Test
    fun createList_success_clearsCreateListError() = runTest {
        val existing = listOf(MovieList(id = "1", name = "Action"))
        val (vm, _) = makeVm(initialLists = existing)
        vm.loadLists("uid")
        // First, produce a duplicate error
        vm.createList("Action", null, null, "uid")
        assertTrue("precondition: error should be set", vm.createState.value is ListMutationState.Error)
        // Now create with a unique name — state should change to Success (not Error)
        vm.createList("Drama", null, null, "uid")
        assertFalse(vm.createState.value is ListMutationState.Error)
    }

    @Test
    fun createList_success_isCreatingListReturnsFalseAfterCompletion() = runTest {
        val (vm, _) = makeVm()
        vm.createList("New List", null, null, "uid")
        assertFalse(vm.createState.value == ListMutationState.Loading)
    }

    // ── createList — failure path ─────────────────────────────────────────────

    @Test
    fun createList_repositoryFailure_setsGenericError() = runTest {
        val (vm, _) = makeVm(createResult = Result.failure(RuntimeException("Firestore error")))
        vm.createList("New List", null, null, "uid")
        assertTrue(vm.createState.value is ListMutationState.Error)
    }

    @Test
    fun createList_repositoryFailure_doesNotSetSuccess() = runTest {
        val (vm, _) = makeVm(createResult = Result.failure(RuntimeException("fail")))
        vm.createList("New List", null, null, "uid")
        assertFalse(vm.createState.value == ListMutationState.Success)
    }

    // ── clearCreateListError / clearCreateListSuccess ─────────────────────────

    @Test
    fun clearCreateListError_clearsError() = runTest {
        val existing = listOf(MovieList(id = "1", name = "Action"))
        val (vm, _) = makeVm(initialLists = existing)
        vm.loadLists("uid")
        vm.createList("Action", null, null, "uid")
        assertTrue("precondition: error should be set", vm.createState.value is ListMutationState.Error)
        vm.resetCreateState()
        assertEquals(ListMutationState.Idle, vm.createState.value)
    }

    @Test
    fun clearCreateListSuccess_clearsSuccess() = runTest {
        val (vm, _) = makeVm()
        vm.createList("New List", null, null, "uid")
        assertEquals("precondition: success should be set", ListMutationState.Success, vm.createState.value)
        vm.resetCreateState()
        assertEquals(ListMutationState.Idle, vm.createState.value)
    }

    // ── createList — subtitle / description ───────────────────────────────────

    @Test
    fun createList_withSubtitle_passesSubtitleToRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.createList("New List", "A great subtitle", null, "uid")
        assertEquals("A great subtitle", repo.lastCreatedList?.subtitle)
    }

    @Test
    fun createList_withDescription_passesDescriptionToRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.createList("New List", null, "A great description", "uid")
        assertEquals("A great description", repo.lastCreatedList?.description)
    }

    @Test
    fun createList_nullSubtitle_passesNullToRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.createList("New List", null, null, "uid")
        assertNull(repo.lastCreatedList?.subtitle)
    }
}
