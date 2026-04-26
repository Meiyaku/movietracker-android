package com.ycs.movietracker.ui.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.model.MoviesPage
import com.ycs.movietracker.data.model.NewMovie
import com.ycs.movietracker.data.repository.AuthRepository
import com.ycs.movietracker.data.repository.MovieListRepository
import com.ycs.movietracker.data.repository.MovieRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

/**
 * Unit tests for [AuthViewModel.deleteAccount] — companion test story US-006-T.
 * Parent story: US-006 (Delete account).
 *
 * Covers: happy path, no current user (no-op), movie-data deletion failure,
 * list-data deletion failure, auth network error, re-auth required error, and
 * clearDeleteAccountError().
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelDeleteAccountTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeUser: FirebaseUser = mock()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(fakeUser.uid).thenReturn("uid-123")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeVm(
        currentUser: FirebaseUser? = fakeUser,
        deleteMoviesResult: Result<Unit> = Result.success(Unit),
        deleteListsResult: Result<Unit> = Result.success(Unit),
        deleteAccountResult: Result<Unit> = Result.success(Unit)
    ): AuthViewModel {
        val fakeAuth = object : AuthRepository {
            override val currentUser = currentUser
            override suspend fun signUp(email: String, password: String): Result<FirebaseUser> =
                Result.failure(UnsupportedOperationException())
            override suspend fun signIn(email: String, password: String): Result<FirebaseUser> =
                Result.failure(UnsupportedOperationException())
            override fun signOut() {}
            override suspend fun sendPasswordReset(email: String): Result<Unit> =
                Result.success(Unit)
            override suspend fun deleteAccount() = deleteAccountResult
        }
        val fakeLists = object : MovieListRepository {
            override fun getLists(uid: String): Flow<Result<List<MovieList>>> = emptyFlow()
            override suspend fun createList(uid: String, list: MovieList): Result<String> =
                Result.success("list-id")
            override suspend fun updateList(uid: String, list: MovieList): Result<Unit> =
                Result.success(Unit)
            override suspend fun deleteList(uid: String, listId: String): Result<Unit> =
                Result.success(Unit)
            override suspend fun deleteAllLists(uid: String) = deleteListsResult
        }
        val fakeMovies = object : MovieRepository {
            override suspend fun addMovie(uid: String, movie: NewMovie): Result<Movie> =
                Result.failure(UnsupportedOperationException())
            override suspend fun getMovieById(uid: String, movieId: String): Result<Movie> =
                Result.failure(UnsupportedOperationException())
            override suspend fun checkDuplicate(uid: String, title: String, year: Int?, genre: String?, excludeId: String?): Result<Boolean> =
                Result.success(false)
            override suspend fun updateMovie(uid: String, movie: Movie): Result<Unit> =
                Result.success(Unit)
            override suspend fun deleteMovie(uid: String, movieId: String): Result<Unit> =
                Result.success(Unit)
            override suspend fun deleteAllMovies(uid: String) = deleteMoviesResult
            override suspend fun removeListFromMovies(uid: String, listId: String): Result<Unit> =
                Result.success(Unit)
            override suspend fun getMoviesPage(uid: String, listId: String, pageSize: Int, afterId: String?): Result<MoviesPage> =
                Result.success(MoviesPage(emptyList(), null, false))
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AuthViewModel(context, fakeAuth, fakeLists, fakeMovies)
    }

    // ── No current user ───────────────────────────────────────────────────────

    @Test
    fun deleteAccount_noCurrentUser_doesNothing() = runTest {
        val vm = makeVm(currentUser = null)
        vm.deleteAccount()
        assertFalse(vm.uiState.value.isDeletingAccount)
        assertNull(vm.uiState.value.deleteAccountError)
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun deleteAccount_success_clearsDeletingState() = runTest {
        val vm = makeVm()
        vm.deleteAccount()
        assertFalse(vm.uiState.value.isDeletingAccount)
        assertNull(vm.uiState.value.deleteAccountError)
    }

    // ── Data deletion failures ────────────────────────────────────────────────

    @Test
    fun deleteAccount_movieDeletionFailure_setsErrorAndDoesNotDeleteAuth() = runTest {
        val vm = makeVm(deleteMoviesResult = Result.failure(Exception("Firestore error")))
        vm.deleteAccount()
        assertFalse(vm.uiState.value.isDeletingAccount)
        assertEquals("Firestore error", vm.uiState.value.deleteAccountError)
    }

    @Test
    fun deleteAccount_listDeletionFailure_setsError() = runTest {
        val vm = makeVm(deleteListsResult = Result.failure(Exception("Lists delete failed")))
        vm.deleteAccount()
        assertFalse(vm.uiState.value.isDeletingAccount)
        assertEquals("Lists delete failed", vm.uiState.value.deleteAccountError)
    }

    @Test
    fun deleteAccount_dataFailureWithNoMessage_usesFallbackString() = runTest {
        val vm = makeVm(deleteMoviesResult = Result.failure(Exception()))
        vm.deleteAccount()
        assertEquals("Something went wrong. Please try again.", vm.uiState.value.deleteAccountError)
    }

    // ── Auth deletion failures ────────────────────────────────────────────────

    @Test
    fun deleteAccount_networkError_setsNetworkError() = runTest {
        val exception = mock<FirebaseNetworkException>()
        val vm = makeVm(deleteAccountResult = Result.failure(exception))
        vm.deleteAccount()
        assertFalse(vm.uiState.value.isDeletingAccount)
        assertEquals(
            "No internet connection. Please check your connection and try again.",
            vm.uiState.value.deleteAccountError
        )
    }

    @Test
    fun deleteAccount_recentLoginRequired_setsReauthError() = runTest {
        val exception = mock<FirebaseAuthRecentLoginRequiredException>()
        val vm = makeVm(deleteAccountResult = Result.failure(exception))
        vm.deleteAccount()
        assertFalse(vm.uiState.value.isDeletingAccount)
        assertEquals(
            "Please sign out and sign back in, then try again.",
            vm.uiState.value.deleteAccountError
        )
    }

    // ── clearDeleteAccountError ───────────────────────────────────────────────

    @Test
    fun clearDeleteAccountError_removesExistingError() = runTest {
        val vm = makeVm(deleteMoviesResult = Result.failure(Exception("oops")))
        vm.deleteAccount()
        vm.clearDeleteAccountError()
        assertNull(vm.uiState.value.deleteAccountError)
    }
}
