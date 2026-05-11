package com.ycs.movietracker.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.model.MoviesPage
import com.ycs.movietracker.data.model.NewMovie
import com.ycs.movietracker.data.repository.MovieListRepository
import com.ycs.movietracker.data.repository.MovieRepository
import com.ycs.movietracker.ui.home.ListMutationState
import com.ycs.movietracker.ui.home.MovieListViewModel
import com.ycs.movietracker.util.ConnectivityMonitor
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
 * Integration tests for [MovieListViewModel] list management flows — companion test story US-022-T.
 *
 * Covers: list creation uniqueness, rename self-conflict exclusion, delete active list fallback,
 * and offline guard propagation across all mutation operations.
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class ListManagementIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val defaultList = MovieList(id = "1", name = MovieList.DEFAULT_LIST_NAME)
    private val actionList = MovieList(id = "2", name = "Action")
    private val sciFiList = MovieList(id = "3", name = "Sci-Fi")

    private class FakeListRepo(
        private val initial: List<MovieList> = emptyList(),
        private val createResult: Result<String> = Result.success("new-id"),
        private val updateResult: Result<Unit> = Result.success(Unit),
        private val deleteResult: Result<Unit> = Result.success(Unit)
    ) : MovieListRepository {
        var createCallCount = 0
        var lastCreatedList: MovieList? = null
        var deleteCallCount = 0

        override fun getLists(uid: String): Flow<Result<List<MovieList>>> =
            flowOf(Result.success(initial))
        override suspend fun createList(uid: String, list: MovieList): Result<String> {
            createCallCount++
            lastCreatedList = list
            return createResult
        }
        override suspend fun updateList(uid: String, list: MovieList): Result<Unit> = updateResult
        override suspend fun deleteList(uid: String, listId: String): Result<Unit> {
            deleteCallCount++
            return deleteResult
        }
        override suspend fun deleteAllLists(uid: String): Result<Unit> = Result.success(Unit)
    }

    private val noopMovieRepo = object : MovieRepository {
        override suspend fun getMoviesPage(uid: String, listId: String, pageSize: Int, afterId: String?) =
            Result.success(MoviesPage(emptyList(), null, false))
        override suspend fun addMovie(uid: String, movie: NewMovie) =
            Result.success(movie.toMovie(id = ""))
        override suspend fun updateMovie(uid: String, movie: Movie) = Result.success(Unit)
        override suspend fun deleteMovie(uid: String, movieId: String) = Result.success(Unit)
        override suspend fun removeListFromMovies(uid: String, listId: String) = Result.success(Unit)
        override suspend fun getMovieById(uid: String, movieId: String) =
            Result.failure<Movie>(UnsupportedOperationException())
        override suspend fun checkDuplicate(uid: String, title: String, year: Int?, genre: String?, excludeId: String?) =
            Result.success(false)
        override suspend fun deleteAllMovies(uid: String) = Result.success(Unit)
    }

    private fun makeVm(
        initial: List<MovieList> = listOf(defaultList, actionList, sciFiList),
        createResult: Result<String> = Result.success("new-id"),
        deleteResult: Result<Unit> = Result.success(Unit),
        isOnline: Boolean = true
    ): Pair<MovieListViewModel, FakeListRepo> {
        val repo = FakeListRepo(initial, createResult = createResult, deleteResult = deleteResult)
        val monitor = object : ConnectivityMonitor { override val isOnline = isOnline }
        val vm = MovieListViewModel(repo, noopMovieRepo, context, monitor)
        return vm to repo
    }

    // ── List creation ────────────────────────────────────────────────────────

    @Test
    fun createList_uniqueName_callsRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.loadLists("uid")
        vm.createList("Horror", null, null, "uid")
        assertEquals(1, repo.createCallCount)
    }

    @Test
    fun createList_duplicateNameCaseInsensitive_doesNotCallRepository() = runTest {
        val (vm, repo) = makeVm()
        vm.loadLists("uid")
        vm.createList("action", null, null, "uid")
        assertEquals(0, repo.createCallCount)
    }

    @Test
    fun createList_duplicateName_setsError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.createList("Action", null, null, "uid")
        assertTrue(vm.createState.value is ListMutationState.Error)
    }

    @Test
    fun createList_success_setsSuccessState() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.createList("Horror", null, null, "uid")
        assertEquals(ListMutationState.Success, vm.createState.value)
    }

    // ── List edit ────────────────────────────────────────────────────────────

    @Test
    fun editList_toSameName_doesNotSetDuplicateError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.editList(actionList, "Action", null, null, "uid")
        assertFalse(vm.editState.value is ListMutationState.Error)
    }

    @Test
    fun editList_toOtherExistingName_setsError() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.editList(actionList, "Sci-Fi", null, null, "uid")
        assertTrue(vm.editState.value is ListMutationState.Error)
    }

    @Test
    fun editList_updatesActiveListNameWhenActive() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.selectList(actionList)
        vm.editList(actionList, "Thriller", null, null, "uid")
        assertEquals("Thriller", vm.activeList.value?.name)
    }

    // ── List selection ───────────────────────────────────────────────────────

    @Test
    fun selectList_updatesActiveList() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.selectList(sciFiList)
        assertEquals("Sci-Fi", vm.activeList.value?.name)
    }

    @Test
    fun selectList_defaultList_isReflectedInActiveList() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.selectList(sciFiList)
        vm.selectList(defaultList)
        assertEquals(defaultList.name, vm.activeList.value?.name)
    }

    // ── List deletion ────────────────────────────────────────────────────────

    @Test
    fun deleteList_activeList_fallsBackToDefaultList() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.selectList(actionList)
        vm.deleteList(actionList, "uid")
        assertEquals(defaultList.name, vm.activeList.value?.name)
    }

    @Test
    fun deleteList_nonActiveList_doesNotChangeActiveList() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.selectList(defaultList)
        vm.deleteList(actionList, "uid")
        assertEquals(defaultList.name, vm.activeList.value?.name)
    }

    @Test
    fun deleteList_success_setsSuccessState() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.deleteList(actionList, "uid")
        assertEquals(ListMutationState.Success, vm.deleteState.value)
    }

    @Test
    fun deleteList_repositoryFailure_setsErrorState() = runTest {
        val (vm, _) = makeVm(deleteResult = Result.failure(RuntimeException("fail")))
        vm.loadLists("uid")
        vm.deleteList(actionList, "uid")
        assertTrue(vm.deleteState.value is ListMutationState.Error)
    }

    // ── Offline guard ────────────────────────────────────────────────────────

    @Test
    fun createList_offline_setsError_doesNotCallRepository() = runTest {
        val (vm, repo) = makeVm(isOnline = false)
        vm.loadLists("uid")
        vm.createList("Horror", null, null, "uid")
        assertTrue(vm.createState.value is ListMutationState.Error)
        assertEquals(0, repo.createCallCount)
    }

    @Test
    fun deleteList_offline_setsError_doesNotCallRepository() = runTest {
        val (vm, repo) = makeVm(isOnline = false)
        vm.loadLists("uid")
        vm.deleteList(actionList, "uid")
        assertTrue(vm.deleteState.value is ListMutationState.Error)
        assertEquals(0, repo.deleteCallCount)
    }

    // ── State reset ──────────────────────────────────────────────────────────

    @Test
    fun resetCreateState_resetsToIdle() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.createList("Horror", null, null, "uid")
        assertEquals(ListMutationState.Success, vm.createState.value)
        vm.resetCreateState()
        assertEquals(ListMutationState.Idle, vm.createState.value)
    }

    @Test
    fun resetDeleteState_resetsToIdle() = runTest {
        val (vm, _) = makeVm()
        vm.loadLists("uid")
        vm.deleteList(actionList, "uid")
        assertEquals(ListMutationState.Success, vm.deleteState.value)
        vm.resetDeleteState()
        assertEquals(ListMutationState.Idle, vm.deleteState.value)
    }
}
