package com.ycs.movietracker.ui.auth

import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.repository.AuthRepository
import com.ycs.movietracker.data.repository.MovieListRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for [AuthViewModel.signUp] — companion test story US-002-T.
 * Parent story: US-002 (Sign up with email and password).
 *
 * Covers: happy path, password mismatch, Firebase error mapping (collision,
 * weak password, generic), and clearError().
 *
 * Run with: ./gradlew test
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    // Reused across tests; Mockito stubs uid on each setUp
    private val fakeUser: FirebaseUser = mock()

    // Captures createList calls so tests can assert on them
    private val createdLists = mutableListOf<Pair<String, MovieList>>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(fakeUser.uid).thenReturn("uid-123")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        createdLists.clear()
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun makeVm(signUpResult: Result<FirebaseUser>): AuthViewModel {
        val fakeAuth = object : AuthRepository {
            override val currentUser: FirebaseUser? = null
            override suspend fun signUp(email: String, password: String) = signUpResult
            override suspend fun signIn(email: String, password: String): Result<FirebaseUser> =
                Result.failure(UnsupportedOperationException())
            override fun signOut() {}
            override suspend fun sendPasswordReset(email: String): Result<Unit> =
                Result.success(Unit)
        }
        val fakeLists = object : MovieListRepository {
            override fun getLists(uid: String): Flow<List<MovieList>> = emptyFlow()
            override suspend fun createList(uid: String, list: MovieList): Result<String> {
                createdLists += uid to list
                return Result.success("list-id")
            }
            override suspend fun updateList(uid: String, list: MovieList): Result<Unit> =
                Result.success(Unit)
            override suspend fun deleteList(uid: String, listId: String): Result<Unit> =
                Result.success(Unit)
        }
        return AuthViewModel(fakeAuth, fakeLists)
    }

    // ── Password mismatch ────────────────────────────────────────────────────

    @Test
    fun signUp_passwordMismatch_setsPasswordsDoNotMatchError() = runTest {
        val vm = makeVm(Result.success(fakeUser))
        vm.signUp("user@example.com", "password1", "password2")
        assertEquals("Passwords do not match", vm.uiState.value.signUpError)
    }

    @Test
    fun signUp_passwordMismatch_doesNotCallAuthRepository() = runTest {
        // If the fake auth were called and returned success, createdLists would be populated.
        // Verifying it stays empty proves signUp() returned early on mismatch.
        val vm = makeVm(Result.success(fakeUser))
        vm.signUp("user@example.com", "password1", "password2")
        assertTrue("Repository must not be called on password mismatch", createdLists.isEmpty())
    }

    // ── Success ──────────────────────────────────────────────────────────────

    @Test
    fun signUp_success_clearsLoadingAndError() = runTest {
        val vm = makeVm(Result.success(fakeUser))
        vm.signUp("user@example.com", "password1", "password1")
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.signUpError)
    }

    @Test
    fun signUp_success_createsMyMoviesListForCorrectUser() = runTest {
        val vm = makeVm(Result.success(fakeUser))
        vm.signUp("user@example.com", "password1", "password1")
        assertEquals("Expected exactly one list to be created", 1, createdLists.size)
        assertEquals("uid-123", createdLists[0].first)
        assertEquals("My Movies", createdLists[0].second.name)
    }

    // ── Firebase error mapping ───────────────────────────────────────────────

    @Test
    fun signUp_emailAlreadyInUse_setsCorrectErrorMessage() = runTest {
        val exception = mock<FirebaseAuthUserCollisionException>()
        val vm = makeVm(Result.failure(exception))
        vm.signUp("taken@example.com", "password1", "password1")
        assertEquals(
            "An account with this email already exists",
            vm.uiState.value.signUpError
        )
    }

    @Test
    fun signUp_weakPassword_setsCorrectErrorMessage() = runTest {
        val exception = mock<FirebaseAuthWeakPasswordException>()
        val vm = makeVm(Result.failure(exception))
        vm.signUp("user@example.com", "weak", "weak")
        assertEquals(
            "Password must be at least 6 characters",
            vm.uiState.value.signUpError
        )
    }

    @Test
    fun signUp_genericError_usesExceptionMessage() = runTest {
        val vm = makeVm(Result.failure(Exception("Network timeout")))
        vm.signUp("user@example.com", "password1", "password1")
        assertEquals("Network timeout", vm.uiState.value.signUpError)
    }

    @Test
    fun signUp_genericErrorWithNoMessage_usesFallbackString() = runTest {
        val vm = makeVm(Result.failure(Exception()))
        vm.signUp("user@example.com", "password1", "password1")
        assertEquals("Sign up failed. Please try again.", vm.uiState.value.signUpError)
    }

    // ── clearError ───────────────────────────────────────────────────────────

    @Test
    fun clearError_removesExistingSignUpError() = runTest {
        val vm = makeVm(Result.success(fakeUser))
        vm.signUp("user@example.com", "pass1", "pass2") // sets mismatch error
        vm.clearError()
        assertNull(vm.uiState.value.signUpError)
    }
}
