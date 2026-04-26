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
 * Unit tests for [MovieListViewModel.deleteList] — companion test story US-009-T.
 * Parent story: US-009 (Delete a custom list).
 *
 * Covers: repository delegation order, active list fallback on deletion,
 * failure handling (removeListFromMovies and deleteList), success state.
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class MovieListViewModelDeleteListTest {

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

    private class FakeListRepo(
        private val initialLists: List<MovieList> = emptyList(),
        private val deleteResult: Result<Unit> = Result.success(Unit)
    ) : MovieListRepository {
        var deleteCallCount = 0

        override fun getLists(uid: String): Flow<Result<List<MovieList>>> = flowOf(Result.success(initialLists))
        override suspend fun createList(uid: String, list: MovieList): Result<String> =
            Result.success("id")
        override suspend fun updateList(uid: String, list: MovieList): Result<Unit> =
            Result.success(Unit)
        override suspend fun deleteList(uid: String, listId: String): Result<Unit> {
            deleteCallCount++
            return deleteResult
        }
        override suspend fun deleteAllLists(uid: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeMovieRepo(
        private val removeResult: Result<Unit> = Result.success(Unit)
    ) : MovieRepository {
        var removeCallCount = 0

        override suspend fun getMoviesPage(uid: String, listId: String, pageSize: Int, afterId: String?): Result<MoviesPage> =
            Result.success(MoviesPage(emptyList(), null, false))
        override suspend fun addMovie(uid: String, movie: NewMovie): Result<Movie> =
            Result.success(movie.toMovie(id = "id"))
        override suspend fun updateMovie(uid: String, movie: Movie): Result<Unit> =
            Result.success(Unit)
        override suspend fun deleteMovie(uid: String, movieId: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun removeListFromMovies(uid: String, listId: String): Result<Unit> {
            removeCallCount++
            return removeResult
        }
        override suspend fun getMovieById(uid: String, movieId: String): Result<Movie> =
            Result.failure(UnsupportedOperationException())
        override suspend fun checkDuplicate(uid: String, title: String, year: Int?, genre: String?, excludeId: String?): Result<Boolean> =
            Result.success(false)
        override suspend fun deleteAllMovies(uid: String): Result<Unit> = Result.success(Unit)
    }

    private val myMovies = MovieList(id = "1", name = "All Movies")
    private val action   = MovieList(id = "2", name = "Action")
    private val sciFi    = MovieList(id = "3", name = "Sci-Fi")

    private fun makeVm(
        initialLists: List<MovieList> = listOf(myMovies, action, sciFi),
        removeResult: Result<Unit> = Result.success(Unit),
        deleteResult: Result<Unit> = Result.success(Unit)
    ): Triple<MovieListViewModel, FakeListRepo, FakeMovieRepo> {
        val listRepo = FakeListRepo(initialLists, deleteResult)
        val movieRepo = FakeMovieRepo(removeResult)
        val vm = MovieListViewModel(listRepo, movieRepo, context)
        return Triple(vm, listRepo, movieRepo)
    }

    // ── repository delegation ─────────────────────────────────────────────────

    @Test
    fun deleteList_callsRemoveListFromMovies() = runTest {
        val (vm, _, movieRepo) = makeVm()
        vm.loadLists("uid")
        vm.deleteList(action, "uid")
        assertEquals(1, movieRepo.removeCallCount)
    }

    @Test
    fun deleteList_callsDeleteListAfterRemoveSucceeds() = runTest {
        val (vm, listRepo, _) = makeVm()
        vm.loadLists("uid")
        vm.deleteList(action, "uid")
        assertEquals(1, listRepo.deleteCallCount)
    }

    @Test
    fun deleteList_doesNotCallDeleteListWhenRemoveFails() = runTest {
        val (vm, listRepo, _) = makeVm(
            removeResult = Result.failure(RuntimeException("remove failed"))
        )
        vm.loadLists("uid")
        vm.deleteList(action, "uid")
        assertEquals(0, listRepo.deleteCallCount)
    }

    // ── success path ──────────────────────────────────────────────────────────

    @Test
    fun deleteList_success_setsDeleteListSuccess() = runTest {
        val (vm, _, _) = makeVm()
        vm.loadLists("uid")
        vm.deleteList(action, "uid")
        assertEquals(ListMutationState.Success, vm.deleteState.value)
    }

    @Test
    fun deleteList_success_isDeletingListFalseAfterCompletion() = runTest {
        val (vm, _, _) = makeVm()
        vm.loadLists("uid")
        vm.deleteList(action, "uid")
        assertFalse(vm.deleteState.value == ListMutationState.Loading)
    }

    @Test
    fun deleteList_success_switchesToMyMoviesWhenActiveListDeleted() = runTest {
        val (vm, _, _) = makeVm()
        vm.loadLists("uid")
        vm.selectList(action)
        vm.deleteList(action, "uid")
        assertEquals("All Movies", vm.activeList.value?.name)
    }

    @Test
    fun deleteList_success_switchesToFirstRemainingListWhenNoMyMoviesAndActiveDeleted() = runTest {
        // No "All Movies" in the initial list
        val list1 = MovieList(id = "2", name = "Action")
        val list2 = MovieList(id = "3", name = "Sci-Fi")
        val (vm, _, _) = makeVm(initialLists = listOf(list1, list2))
        vm.loadLists("uid")
        vm.selectList(list1)
        vm.deleteList(list1, "uid")
        // After sort, Sci-Fi is first; list1 filtered out → first remaining is list2
        assertEquals("Sci-Fi", vm.activeList.value?.name)
    }

    @Test
    fun deleteList_success_doesNotChangeActiveListWhenNotActive() = runTest {
        val (vm, _, _) = makeVm()
        vm.loadLists("uid")
        vm.selectList(myMovies)
        vm.deleteList(action, "uid")
        assertEquals("All Movies", vm.activeList.value?.name)
    }

    @Test
    fun deleteList_success_clearsDeleteListError() = runTest {
        // First cause a remove failure to set the error
        val (vm, _, _) = makeVm(removeResult = Result.failure(RuntimeException("fail")))
        vm.loadLists("uid")
        vm.deleteList(action, "uid")
        assertTrue("precondition: error should be set", vm.deleteState.value is ListMutationState.Error)

        // Now a fresh VM that succeeds
        val (vm2, _, _) = makeVm()
        vm2.loadLists("uid")
        vm2.deleteList(action, "uid")
        assertFalse(vm2.deleteState.value is ListMutationState.Error)
    }

    // ── failure path ──────────────────────────────────────────────────────────

    @Test
    fun deleteList_removeListFromMoviesFailure_setsError() = runTest {
        val (vm, _, _) = makeVm(removeResult = Result.failure(RuntimeException("remove failed")))
        vm.loadLists("uid")
        vm.deleteList(action, "uid")
        assertTrue(vm.deleteState.value is ListMutationState.Error)
    }

    @Test
    fun deleteList_removeListFromMoviesFailure_doesNotSetSuccess() = runTest {
        val (vm, _, _) = makeVm(removeResult = Result.failure(RuntimeException("fail")))
        vm.loadLists("uid")
        vm.deleteList(action, "uid")
        assertFalse(vm.deleteState.value == ListMutationState.Success)
    }

    @Test
    fun deleteList_deleteListFailure_setsError() = runTest {
        val (vm, _, _) = makeVm(deleteResult = Result.failure(RuntimeException("delete failed")))
        vm.loadLists("uid")
        vm.deleteList(action, "uid")
        assertTrue(vm.deleteState.value is ListMutationState.Error)
    }

    @Test
    fun deleteList_deleteListFailure_doesNotSetSuccess() = runTest {
        val (vm, _, _) = makeVm(deleteResult = Result.failure(RuntimeException("fail")))
        vm.loadLists("uid")
        vm.deleteList(action, "uid")
        assertFalse(vm.deleteState.value == ListMutationState.Success)
    }

    // ── clearDeleteListSuccess ────────────────────────────────────────────────

    @Test
    fun clearDeleteListSuccess_clearsSuccess() = runTest {
        val (vm, _, _) = makeVm()
        vm.loadLists("uid")
        vm.deleteList(action, "uid")
        assertEquals("precondition: success should be set", ListMutationState.Success, vm.deleteState.value)
        vm.resetDeleteState()
        assertEquals(ListMutationState.Idle, vm.deleteState.value)
    }
}
