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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for [MovieListViewModel.editList] — companion test story US-008-T.
 * Parent story: US-008 (Edit a custom list — name, subtitle, description).
 *
 * Covers: uniqueness validation (excluding self, case-insensitive), subtitle/description
 * propagation to the repository, active list update on success, error state management.
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class MovieListViewModelEditListTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private class FakeRepo(
        private val initialLists: List<MovieList> = emptyList(),
        private val updateResult: Result<Unit> = Result.success(Unit)
    ) : MovieListRepository {
        var updateCallCount = 0
        var lastUpdatedList: MovieList? = null

        override fun getLists(uid: String): Flow<Result<List<MovieList>>> = flowOf(Result.success(initialLists))
        override suspend fun createList(uid: String, list: MovieList): Result<String> = Result.success("id")
        override suspend fun updateList(uid: String, list: MovieList): Result<Unit> {
            updateCallCount++
            lastUpdatedList = list
            return updateResult
        }
        override suspend fun deleteList(uid: String, listId: String): Result<Unit> = Result.success(Unit)
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
        val onlineMonitor = object : com.ycs.movietracker.util.ConnectivityMonitor { override val isOnline = true }
        val vm = MovieListViewModel(repo, noopMovieRepo, context, onlineMonitor)
        return vm to repo
    }

    // ── editList — uniqueness validation ──────────────────────────────────────

    @Test
    fun editList_duplicateName_setsError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.editList(action, "Drama", null, null, "uid")
        assertEquals("A list with this name already exists", (vm.editState.value as ListMutationState.Error).message)
    }

    @Test
    fun editList_duplicateNameCaseInsensitive_setsError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.editList(action, "drama", null, null, "uid")
        assertEquals("A list with this name already exists", (vm.editState.value as ListMutationState.Error).message)
    }

    @Test
    fun editList_duplicateName_doesNotCallRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.loadLists("uid")
        vm.editList(action, "Drama", null, null, "uid")
        assertEquals(0, repo.updateCallCount)
    }

    @Test
    fun editList_sameNameAsCurrentList_doesNotSetDuplicateError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.editList(action, "Action", null, null, "uid")
        assertFalse(vm.editState.value is ListMutationState.Error)
    }

    @Test
    fun editList_sameNameCaseVariant_doesNotSetDuplicateError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.editList(action, "action", null, null, "uid")
        assertFalse(vm.editState.value is ListMutationState.Error)
    }

    // ── editList — success path ───────────────────────────────────────────────

    @Test
    fun editList_uniqueName_callsRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.loadLists("uid")
        vm.editList(action, "Thriller", null, null, "uid")
        assertEquals(1, repo.updateCallCount)
    }

    @Test
    fun editList_uniqueName_passesCorrectNameToRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.loadLists("uid")
        vm.editList(action, "  Thriller  ", null, null, "uid")
        assertEquals("Thriller", repo.lastUpdatedList?.name)
    }

    @Test
    fun editList_withSubtitle_passesSubtitleToRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.loadLists("uid")
        vm.editList(action, "Action", "Best action films", null, "uid")
        assertEquals("Best action films", repo.lastUpdatedList?.subtitle)
    }

    @Test
    fun editList_withDescription_passesDescriptionToRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.loadLists("uid")
        vm.editList(action, "Action", null, "A great collection", "uid")
        assertEquals("A great collection", repo.lastUpdatedList?.description)
    }

    @Test
    fun editList_nullSubtitle_clearsSubtitleOnRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.loadLists("uid")
        vm.editList(action, "Action", null, null, "uid")
        assertNull(repo.lastUpdatedList?.subtitle)
    }

    @Test
    fun editList_success_setsEditListSuccess() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.editList(action, "Thriller", null, null, "uid")
        assertEquals(ListMutationState.Success, vm.editState.value)
    }

    @Test
    fun editList_success_updatesActiveListNameWhenActive() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.selectList(action)
        vm.editList(action, "Thriller", null, null, "uid")
        assertEquals("Thriller", vm.activeList.value?.name)
    }

    @Test
    fun editList_success_updatesActiveListSubtitleWhenActive() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.selectList(action)
        vm.editList(action, "Action", "Top picks", null, "uid")
        assertEquals("Top picks", vm.activeList.value?.subtitle)
    }

    @Test
    fun editList_success_doesNotChangeActiveListWhenNotActive() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.selectList(myMovies)
        vm.editList(action, "Thriller", null, null, "uid")
        assertEquals("All Movies", vm.activeList.value?.name)
    }

    @Test
    fun editList_success_clearsEditListError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.editList(action, "Drama", null, null, "uid")
        assertTrue("precondition: error should be set", vm.editState.value is ListMutationState.Error)
        vm.editList(action, "Thriller", null, null, "uid")
        assertFalse(vm.editState.value is ListMutationState.Error)
    }

    @Test
    fun editList_success_isEditingListFalseAfterCompletion() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.editList(action, "Thriller", null, null, "uid")
        assertFalse(vm.editState.value == ListMutationState.Loading)
    }

    // ── editList — failure path ───────────────────────────────────────────────

    @Test
    fun editList_repositoryFailure_setsGenericError() = runTest {
        val (vm, _) = makeVm(updateResult = Result.failure(RuntimeException("Firestore error")))
        vm.loadLists("uid")
        vm.editList(action, "Thriller", null, null, "uid")
        assertTrue(vm.editState.value is ListMutationState.Error)
    }

    @Test
    fun editList_repositoryFailure_doesNotSetSuccess() = runTest {
        val (vm, _) = makeVm(updateResult = Result.failure(RuntimeException("fail")))
        vm.loadLists("uid")
        vm.editList(action, "Thriller", null, null, "uid")
        assertFalse(vm.editState.value == ListMutationState.Success)
    }

    // ── resetEditState ────────────────────────────────────────────────────────

    @Test
    fun resetEditState_clearsError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.editList(action, "Drama", null, null, "uid")
        assertTrue("precondition: error should be set", vm.editState.value is ListMutationState.Error)
        vm.resetEditState()
        assertEquals(ListMutationState.Idle, vm.editState.value)
    }

    @Test
    fun resetEditState_clearsSuccess() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.editList(action, "Thriller", null, null, "uid")
        assertEquals("precondition: success should be set", ListMutationState.Success, vm.editState.value)
        vm.resetEditState()
        assertEquals(ListMutationState.Idle, vm.editState.value)
    }
}
