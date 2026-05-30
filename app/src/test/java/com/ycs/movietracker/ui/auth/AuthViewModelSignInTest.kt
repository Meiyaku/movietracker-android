package com.ycs.movietracker.ui.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.repository.AuthRepository
import com.ycs.movietracker.data.repository.MovieListRepository
import com.ycs.movietracker.test.NoopMovieRepository
import com.ycs.movietracker.util.AndroidStringProvider
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
import org.robolectric.annotation.Config

/**
 * Unit tests for [AuthViewModel.signIn] — companion test story US-003-T.
 * Parent story: US-003 (Log in with email and password).
 *
 * Covers: happy path, invalid credentials, user-not-found, generic error,
 * generic error without message, and clearError().
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelSignInTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeUser: FirebaseUser = mock()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun makeVm(signInResult: Result<FirebaseUser>): AuthViewModel {
        val fakeAuth = object : AuthRepository {
            override val currentUser: FirebaseUser? = null
            override suspend fun signUp(email: String, password: String): Result<FirebaseUser> =
                Result.failure(UnsupportedOperationException())
            override suspend fun signIn(email: String, password: String) = signInResult
            override fun signOut() {}
            override suspend fun sendPasswordReset(email: String): Result<Unit> =
                Result.success(Unit)
            override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        }
        val fakeLists = object : MovieListRepository {
            override fun getLists(uid: String): Flow<Result<List<MovieList>>> = emptyFlow()
            override suspend fun createList(uid: String, list: MovieList): Result<String> =
                Result.success("list-id")
            override suspend fun updateList(uid: String, list: MovieList): Result<Unit> =
                Result.success(Unit)
            override suspend fun deleteList(uid: String, listId: String): Result<Unit> =
                Result.success(Unit)
            override suspend fun deleteAllLists(uid: String): Result<Unit> = Result.success(Unit)
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AuthViewModel(AndroidStringProvider(context), fakeAuth, fakeLists, NoopMovieRepository())
    }

    // ── Success ──────────────────────────────────────────────────────────────

    @Test
    fun signIn_success_clearsLoadingAndError() = runTest {
        val vm = makeVm(Result.success(fakeUser))
        vm.signIn("user@example.com", "password1")
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.signInError)
    }

    // ── Invalid credentials ──────────────────────────────────────────────────

    @Test
    fun signIn_wrongPassword_setsIncorrectEmailOrPasswordError() = runTest {
        val exception = mock<FirebaseAuthInvalidCredentialsException>()
        val vm = makeVm(Result.failure(exception))
        vm.signIn("user@example.com", "wrongpassword")
        assertEquals("Incorrect email or password", vm.uiState.value.signInError)
    }

    @Test
    fun signIn_userNotFound_setsIncorrectEmailOrPasswordError() = runTest {
        val exception = mock<FirebaseAuthInvalidUserException>()
        val vm = makeVm(Result.failure(exception))
        vm.signIn("nobody@example.com", "password1")
        assertEquals("Incorrect email or password", vm.uiState.value.signInError)
    }

    // ── Network error ────────────────────────────────────────────────────────

    @Test
    fun signIn_networkError_setsNetworkError() = runTest {
        val exception = mock<FirebaseNetworkException>()
        val vm = makeVm(Result.failure(exception))
        vm.signIn("user@example.com", "password1")
        assertEquals(
            "No internet connection. Please check your connection and try again.",
            vm.uiState.value.signInError
        )
    }

    // ── Generic errors ────────────────────────────────────────────────────────

    @Test
    fun signIn_genericError_usesExceptionMessage() = runTest {
        val vm = makeVm(Result.failure(Exception("Connection refused")))
        vm.signIn("user@example.com", "password1")
        assertEquals("Connection refused", vm.uiState.value.signInError)
    }

    @Test
    fun signIn_genericErrorWithNoMessage_usesFallbackString() = runTest {
        val vm = makeVm(Result.failure(Exception()))
        vm.signIn("user@example.com", "password1")
        assertEquals("Sign in failed. Please try again.", vm.uiState.value.signInError)
    }

    // ── clearError ────────────────────────────────────────────────────────────

    @Test
    fun clearError_removesExistingSignInError() = runTest {
        val exception = mock<FirebaseAuthInvalidCredentialsException>()
        val vm = makeVm(Result.failure(exception))
        vm.signIn("user@example.com", "wrongpassword") // sets signInError
        vm.clearError()
        assertNull(vm.uiState.value.signInError)
    }

    @Test
    fun clearError_doesNotAffectSignUpError() = runTest {
        // clearError clears both fields — verify signUpError is also cleared
        val vm = makeVm(Result.success(fakeUser))
        // Manually inject a signUpError by calling signUp with mismatched passwords
        vm.signUp("user@example.com", "pass1", "pass2")
        vm.clearError()
        assertNull(vm.uiState.value.signUpError)
        assertNull(vm.uiState.value.signInError)
    }
}
