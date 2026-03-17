package com.ycs.movietracker.ui.home

import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.repository.MovieListRepository
import com.ycs.movietracker.data.repository.MovieRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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

/**
 * Unit tests for [MovieListViewModel.createList] — companion test story US-007-T.
 * Parent story: US-007 (Create a custom list).
 *
 * Covers: uniqueness validation (including case-insensitive), repository delegation,
 * active list selection on success, error state management.
 *
 * Run with: ./gradlew test
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MovieListViewModelCreateListTest {

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

        override fun getLists(uid: String): Flow<List<MovieList>> = flowOf(initialLists)
        override suspend fun createList(uid: String, list: MovieList): Result<String> {
            createCallCount++
            lastCreatedList = list
            return createResult
        }
        override suspend fun updateList(uid: String, list: MovieList): Result<Unit> =
            Result.success(Unit)
        override suspend fun deleteList(uid: String, listId: String): Result<Unit> =
            Result.success(Unit)
    }

    private val noopMovieRepo = object : MovieRepository {
        override fun getMoviesForList(uid: String, listId: String): Flow<List<Movie>> = emptyFlow()
        override suspend fun addMovie(uid: String, movie: Movie): Result<String> = Result.success("")
        override suspend fun updateMovie(uid: String, movie: Movie): Result<Unit> = Result.success(Unit)
        override suspend fun deleteMovie(uid: String, movieId: String): Result<Unit> = Result.success(Unit)
        override suspend fun removeListFromMovies(uid: String, listId: String): Result<Unit> = Result.success(Unit)
    }

    private fun makeVm(
        initialLists: List<MovieList> = emptyList(),
        createResult: Result<String> = Result.success("new-id")
    ): Pair<MovieListViewModel, FakeRepo> {
        val repo = FakeRepo(initialLists, createResult)
        val vm = MovieListViewModel(repo, noopMovieRepo)
        return vm to repo
    }

    // ── createList — uniqueness validation ───────────────────────────────────

    @Test
    fun createList_duplicateName_setsError() = runTest {
        val existing = listOf(MovieList(id = "1", name = "Action"))
        val (vm, _) = makeVm(initialLists = existing)
        vm.loadLists("uid")
        vm.createList("Action", "uid")
        assertEquals("A list with this name already exists", vm.createListError.value)
    }

    @Test
    fun createList_duplicateNameCaseInsensitive_setsError() = runTest {
        val existing = listOf(MovieList(id = "1", name = "Action"))
        val (vm, _) = makeVm(initialLists = existing)
        vm.loadLists("uid")
        vm.createList("action", "uid")
        assertEquals("A list with this name already exists", vm.createListError.value)
    }

    @Test
    fun createList_duplicateName_doesNotCallRepository() = runTest {
        val existing = listOf(MovieList(id = "1", name = "Action"))
        val (vm, repo) = makeVm(initialLists = existing)
        vm.loadLists("uid")
        vm.createList("Action", "uid")
        assertEquals(0, repo.createCallCount)
    }

    @Test
    fun createList_duplicateWithLeadingTrailingSpaces_setsError() = runTest {
        val existing = listOf(MovieList(id = "1", name = "Action"))
        val (vm, _) = makeVm(initialLists = existing)
        vm.loadLists("uid")
        vm.createList("  Action  ", "uid")
        assertEquals("A list with this name already exists", vm.createListError.value)
    }

    // ── createList — success path ─────────────────────────────────────────────

    @Test
    fun createList_uniqueName_callsRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.createList("New List", "uid")
        assertEquals(1, repo.createCallCount)
    }

    @Test
    fun createList_uniqueName_passesCorrectNameToRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.createList("  My List  ", "uid")
        assertEquals("My List", repo.lastCreatedList?.name)
    }

    @Test
    fun createList_success_selectsNewListById() = runTest {
        val (vm, _) = makeVm(createResult = Result.success("returned-id"))
        vm.createList("New List", "uid")
        assertEquals("returned-id", vm.activeList.value?.id)
    }

    @Test
    fun createList_success_setsCreateListSuccess() = runTest {
        val (vm, _) = makeVm()
        vm.createList("New List", "uid")
        assertTrue(vm.createListSuccess.value)
    }

    @Test
    fun createList_success_clearsCreateListError() = runTest {
        val existing = listOf(MovieList(id = "1", name = "Action"))
        val (vm, _) = makeVm(initialLists = existing)
        vm.loadLists("uid")
        // First, produce a duplicate error
        vm.createList("Action", "uid")
        assertNotNull("precondition: error should be set", vm.createListError.value)
        // Now create with a unique name — error should clear
        vm.createList("Drama", "uid")
        assertNull(vm.createListError.value)
    }

    @Test
    fun createList_success_isCreatingListReturnsFalseAfterCompletion() = runTest {
        val (vm, _) = makeVm()
        vm.createList("New List", "uid")
        assertFalse(vm.isCreatingList.value)
    }

    // ── createList — failure path ─────────────────────────────────────────────

    @Test
    fun createList_repositoryFailure_setsGenericError() = runTest {
        val (vm, _) = makeVm(createResult = Result.failure(RuntimeException("Firestore error")))
        vm.createList("New List", "uid")
        assertNotNull(vm.createListError.value)
    }

    @Test
    fun createList_repositoryFailure_doesNotSetSuccess() = runTest {
        val (vm, _) = makeVm(createResult = Result.failure(RuntimeException("fail")))
        vm.createList("New List", "uid")
        assertFalse(vm.createListSuccess.value)
    }

    // ── clearCreateListError / clearCreateListSuccess ─────────────────────────

    @Test
    fun clearCreateListError_clearsError() = runTest {
        val existing = listOf(MovieList(id = "1", name = "Action"))
        val (vm, _) = makeVm(initialLists = existing)
        vm.loadLists("uid")
        vm.createList("Action", "uid")
        assertNotNull("precondition: error should be set", vm.createListError.value)
        vm.clearCreateListError()
        assertNull(vm.createListError.value)
    }

    @Test
    fun clearCreateListSuccess_clearsSuccess() = runTest {
        val (vm, _) = makeVm()
        vm.createList("New List", "uid")
        assertTrue("precondition: success should be set", vm.createListSuccess.value)
        vm.clearCreateListSuccess()
        assertFalse(vm.createListSuccess.value)
    }
}
