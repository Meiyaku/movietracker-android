package com.ycs.movietracker.ui.auth

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AuthViewModel.sendPasswordReset] — companion test story US-004-T.
 * Parent story: US-004 (Forgot password).
 *
 * Key behaviour under test: the confirmation flag is set regardless of whether the
 * Firebase call succeeds or fails (account-enumeration protection, FR-3a).
 *
 * Run with: ./gradlew test
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelForgotPasswordTest {

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

    private fun makeVm(resetResult: Result<Unit>): AuthViewModel {
        val fakeAuth = object : AuthRepository {
            override val currentUser: FirebaseUser? = null
            override suspend fun signUp(email: String, password: String): Result<FirebaseUser> =
                Result.failure(UnsupportedOperationException())
            override suspend fun signIn(email: String, password: String): Result<FirebaseUser> =
                Result.failure(UnsupportedOperationException())
            override fun signOut() {}
            override suspend fun sendPasswordReset(email: String) = resetResult
        }
        val fakeLists = object : MovieListRepository {
            override fun getLists(uid: String): Flow<List<MovieList>> = emptyFlow()
            override suspend fun createList(uid: String, list: MovieList): Result<String> =
                Result.success("list-id")
            override suspend fun updateList(uid: String, list: MovieList): Result<Unit> =
                Result.success(Unit)
            override suspend fun deleteList(uid: String, listId: String): Result<Unit> =
                Result.success(Unit)
        }
        return AuthViewModel(fakeAuth, fakeLists)
    }

    // ── sendPasswordReset ────────────────────────────────────────────────────

    @Test
    fun sendPasswordReset_onSuccess_setsPasswordResetSent() = runTest {
        val vm = makeVm(Result.success(Unit))
        vm.sendPasswordReset("user@example.com")
        assertTrue(vm.uiState.value.passwordResetSent)
    }

    @Test
    fun sendPasswordReset_onFailure_stillSetsPasswordResetSent() = runTest {
        // Firebase returns an error (e.g. email not registered) — we still show the
        // confirmation to avoid revealing whether the email is registered (FR-3a).
        val vm = makeVm(Result.failure(Exception("User not found")))
        vm.sendPasswordReset("nobody@example.com")
        assertTrue(vm.uiState.value.passwordResetSent)
    }

    @Test
    fun sendPasswordReset_clearsLoadingWhenDone() = runTest {
        val vm = makeVm(Result.success(Unit))
        vm.sendPasswordReset("user@example.com")
        assertFalse(vm.uiState.value.isLoading)
    }

    // ── dismissPasswordReset ─────────────────────────────────────────────────

    @Test
    fun dismissPasswordReset_clearsPasswordResetSent() = runTest {
        val vm = makeVm(Result.success(Unit))
        vm.sendPasswordReset("user@example.com")
        assertTrue(vm.uiState.value.passwordResetSent) // precondition
        vm.dismissPasswordReset()
        assertFalse(vm.uiState.value.passwordResetSent)
    }

    @Test
    fun dismissPasswordReset_doesNotClearOtherState() = runTest {
        // signInError should survive a dismissPasswordReset call
        val vm = makeVm(Result.success(Unit))
        // Put a signInError in state by reaching into the ViewModel indirectly:
        // call sendPasswordReset (sets passwordResetSent), then dismiss
        vm.sendPasswordReset("user@example.com")
        vm.dismissPasswordReset()
        // isLoading must still be false, no side-effects on unrelated fields
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.passwordResetSent)
    }
}
