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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for [MovieListViewModel.renameList] — companion test story US-008-T.
 * Parent story: US-008 (Rename a custom list).
 *
 * Covers: uniqueness validation (excluding self, case-insensitive), repository
 * delegation, active list name update on success, error state management.
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class MovieListViewModelRenameListTest {

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
        private val updateResult: Result<Unit> = Result.success(Unit)
    ) : MovieListRepository {
        var updateCallCount = 0
        var lastUpdatedList: MovieList? = null

        override fun getLists(uid: String): Flow<Result<List<MovieList>>> = flowOf(Result.success(initialLists))
        override suspend fun createList(uid: String, list: MovieList): Result<String> =
            Result.success("id")
        override suspend fun updateList(uid: String, list: MovieList): Result<Unit> {
            updateCallCount++
            lastUpdatedList = list
            return updateResult
        }
        override suspend fun deleteList(uid: String, listId: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun deleteAllLists(uid: String): Result<Unit> = Result.success(Unit)
    }

    private val myMovies = MovieList(id = "1", name = "All Movies")
    private val action = MovieList(id = "2", name = "Action")
    private val drama = MovieList(id = "3", name = "Drama")

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
        initialLists: List<MovieList> = listOf(myMovies, action, drama),
        updateResult: Result<Unit> = Result.success(Unit)
    ): Pair<MovieListViewModel, FakeRepo> {
        val repo = FakeRepo(initialLists, updateResult)
        val vm = MovieListViewModel(repo, noopMovieRepo, context)
        return vm to repo
    }

    // ── renameList — uniqueness validation ────────────────────────────────────

    @Test
    fun renameList_duplicateName_setsError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        // Try to rename "Action" to "Drama" — Drama already exists
        vm.renameList(action, "Drama", "uid")
        assertEquals("A list with this name already exists", (vm.renameState.value as ListMutationState.Error).message)
    }

    @Test
    fun renameList_duplicateNameCaseInsensitive_setsError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.renameList(action, "drama", "uid")
        assertEquals("A list with this name already exists", (vm.renameState.value as ListMutationState.Error).message)
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
        assertFalse(vm.renameState.value is ListMutationState.Error)
    }

    @Test
    fun renameList_sameNameCaseVariant_doesNotSetDuplicateError() = runTest {
        // "Action" → "action" — only conflicts with itself (excluded), not another list
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.renameList(action, "action", "uid")
        assertFalse(vm.renameState.value is ListMutationState.Error)
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
        assertEquals(ListMutationState.Success, vm.renameState.value)
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
        // Active list should still be All Movies, unchanged
        assertEquals("All Movies", vm.activeList.value?.name)
    }

    @Test
    fun renameList_success_clearsRenameListError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        // First produce a duplicate error
        vm.renameList(action, "Drama", "uid")
        assertTrue("precondition: error should be set", vm.renameState.value is ListMutationState.Error)
        // Now rename to a unique name — state should change to Success (not Error)
        vm.renameList(action, "Thriller", "uid")
        assertFalse(vm.renameState.value is ListMutationState.Error)
    }

    @Test
    fun renameList_success_isRenamingListFalseAfterCompletion() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.renameList(action, "Thriller", "uid")
        assertFalse(vm.renameState.value == ListMutationState.Loading)
    }

    // ── renameList — failure path ─────────────────────────────────────────────

    @Test
    fun renameList_repositoryFailure_setsGenericError() = runTest {
        val (vm, _) = makeVm(updateResult = Result.failure(RuntimeException("Firestore error")))
        vm.loadLists("uid")
        vm.renameList(action, "Thriller", "uid")
        assertTrue(vm.renameState.value is ListMutationState.Error)
    }

    @Test
    fun renameList_repositoryFailure_doesNotSetSuccess() = runTest {
        val (vm, _) = makeVm(updateResult = Result.failure(RuntimeException("fail")))
        vm.loadLists("uid")
        vm.renameList(action, "Thriller", "uid")
        assertFalse(vm.renameState.value == ListMutationState.Success)
    }

    // ── clearRenameListError / clearRenameListSuccess ─────────────────────────

    @Test
    fun clearRenameListError_clearsError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.renameList(action, "Drama", "uid")
        assertTrue("precondition: error should be set", vm.renameState.value is ListMutationState.Error)
        vm.resetRenameState()
        assertEquals(ListMutationState.Idle, vm.renameState.value)
    }

    @Test
    fun clearRenameListSuccess_clearsSuccess() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.renameList(action, "Thriller", "uid")
        assertEquals("precondition: success should be set", ListMutationState.Success, vm.renameState.value)
        vm.resetRenameState()
        assertEquals(ListMutationState.Idle, vm.renameState.value)
    }
}
