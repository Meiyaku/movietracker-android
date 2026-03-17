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
 * Unit tests for [MovieListViewModel.renameList] — companion test story US-008-T.
 * Parent story: US-008 (Rename a custom list).
 *
 * Covers: uniqueness validation (excluding self, case-insensitive), repository
 * delegation, active list name update on success, error state management.
 *
 * Run with: ./gradlew test
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MovieListViewModelRenameListTest {

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
        private val updateResult: Result<Unit> = Result.success(Unit)
    ) : MovieListRepository {
        var updateCallCount = 0
        var lastUpdatedList: MovieList? = null

        override fun getLists(uid: String): Flow<List<MovieList>> = flowOf(initialLists)
        override suspend fun createList(uid: String, list: MovieList): Result<String> =
            Result.success("id")
        override suspend fun updateList(uid: String, list: MovieList): Result<Unit> {
            updateCallCount++
            lastUpdatedList = list
            return updateResult
        }
        override suspend fun deleteList(uid: String, listId: String): Result<Unit> =
            Result.success(Unit)
    }

    private val myMovies = MovieList(id = "1", name = "My Movies")
    private val action = MovieList(id = "2", name = "Action")
    private val drama = MovieList(id = "3", name = "Drama")

    private val noopMovieRepo = object : MovieRepository {
        override fun getMoviesForList(uid: String, listId: String): Flow<List<Movie>> = emptyFlow()
        override suspend fun addMovie(uid: String, movie: Movie): Result<String> = Result.success("")
        override suspend fun updateMovie(uid: String, movie: Movie): Result<Unit> = Result.success(Unit)
        override suspend fun deleteMovie(uid: String, movieId: String): Result<Unit> = Result.success(Unit)
        override suspend fun removeListFromMovies(uid: String, listId: String): Result<Unit> = Result.success(Unit)
    }

    private fun makeVm(
        initialLists: List<MovieList> = listOf(myMovies, action, drama),
        updateResult: Result<Unit> = Result.success(Unit)
    ): Pair<MovieListViewModel, FakeRepo> {
        val repo = FakeRepo(initialLists, updateResult)
        val vm = MovieListViewModel(repo, noopMovieRepo)
        return vm to repo
    }

    // ── renameList — uniqueness validation ────────────────────────────────────

    @Test
    fun renameList_duplicateName_setsError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        // Try to rename "Action" to "Drama" — Drama already exists
        vm.renameList(action, "Drama", "uid")
        assertEquals("A list with this name already exists", vm.renameListError.value)
    }

    @Test
    fun renameList_duplicateNameCaseInsensitive_setsError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.renameList(action, "drama", "uid")
        assertEquals("A list with this name already exists", vm.renameListError.value)
    }

    @Test
    fun renameList_duplicateName_doesNotCallRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.loadLists("uid")
        vm.renameList(action, "Drama", "uid")
        assertEquals(0, repo.updateCallCount)
    }

    @Test
    fun renameList_sameNameAsCurrentList_doesNotSetDuplicateError() = runTest {
        // Renaming "Action" to "Action" — same list, not a conflict with another list
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.renameList(action, "Action", "uid")
        assertNull(vm.renameListError.value)
    }

    @Test
    fun renameList_sameNameCaseVariant_doesNotSetDuplicateError() = runTest {
        // "Action" → "action" — only conflicts with itself (excluded), not another list
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.renameList(action, "action", "uid")
        assertNull(vm.renameListError.value)
    }

    // ── renameList — success path ─────────────────────────────────────────────

    @Test
    fun renameList_uniqueName_callsRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.loadLists("uid")
        vm.renameList(action, "Thriller", "uid")
        assertEquals(1, repo.updateCallCount)
    }

    @Test
    fun renameList_uniqueName_passesCorrectNameToRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.loadLists("uid")
        vm.renameList(action, "  Thriller  ", "uid")
        assertEquals("Thriller", repo.lastUpdatedList?.name)
    }

    @Test
    fun renameList_success_setsRenameListSuccess() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.renameList(action, "Thriller", "uid")
        assertTrue(vm.renameListSuccess.value)
    }

    @Test
    fun renameList_success_updatesActiveListNameWhenActive() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.selectList(action)
        vm.renameList(action, "Thriller", "uid")
        assertEquals("Thriller", vm.activeList.value?.name)
    }

    @Test
    fun renameList_success_doesNotChangeActiveListWhenNotActive() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.selectList(myMovies)
        vm.renameList(action, "Thriller", "uid")
        // Active list should still be My Movies, unchanged
        assertEquals("My Movies", vm.activeList.value?.name)
    }

    @Test
    fun renameList_success_clearsRenameListError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        // First produce a duplicate error
        vm.renameList(action, "Drama", "uid")
        assertNotNull("precondition: error should be set", vm.renameListError.value)
        // Now rename to a unique name
        vm.renameList(action, "Thriller", "uid")
        assertNull(vm.renameListError.value)
    }

    @Test
    fun renameList_success_isRenamingListFalseAfterCompletion() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.renameList(action, "Thriller", "uid")
        assertFalse(vm.isRenamingList.value)
    }

    // ── renameList — failure path ─────────────────────────────────────────────

    @Test
    fun renameList_repositoryFailure_setsGenericError() = runTest {
        val (vm, _) = makeVm(updateResult = Result.failure(RuntimeException("Firestore error")))
        vm.loadLists("uid")
        vm.renameList(action, "Thriller", "uid")
        assertNotNull(vm.renameListError.value)
    }

    @Test
    fun renameList_repositoryFailure_doesNotSetSuccess() = runTest {
        val (vm, _) = makeVm(updateResult = Result.failure(RuntimeException("fail")))
        vm.loadLists("uid")
        vm.renameList(action, "Thriller", "uid")
        assertFalse(vm.renameListSuccess.value)
    }

    // ── clearRenameListError / clearRenameListSuccess ─────────────────────────

    @Test
    fun clearRenameListError_clearsError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.renameList(action, "Drama", "uid")
        assertNotNull("precondition: error should be set", vm.renameListError.value)
        vm.clearRenameListError()
        assertNull(vm.renameListError.value)
    }

    @Test
    fun clearRenameListSuccess_clearsSuccess() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.renameList(action, "Thriller", "uid")
        assertTrue("precondition: success should be set", vm.renameListSuccess.value)
        vm.clearRenameListSuccess()
        assertFalse(vm.renameListSuccess.value)
    }
}
