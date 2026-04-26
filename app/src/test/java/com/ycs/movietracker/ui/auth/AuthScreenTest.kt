package com.ycs.movietracker.ui.auth

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseUser
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.repository.AuthRepository
import com.ycs.movietracker.data.repository.MovieListRepository
import com.ycs.movietracker.test.NoopMovieRepository
import com.ycs.movietracker.ui.theme.MovietrackerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [AuthScreen] — companion test story US-013-T.
 * Parent story: US-013 (Auth screen Sign Up and Log In tabs).
 *
 * Covers: Sign Up button disabled states, Log In button disabled states,
 * Forgot Password dialog (open, input validation, confirmation, cancel).
 *
 * Node ordering: In TabRow + content layout (Log In is tab 0, Sign Up is tab 1):
 *   onAllNodesWithText("Log In")  → [0]=Tab, [1]=Button (when Log In tab is active — the default)
 *   onAllNodesWithText("Sign Up") → [0]=Tab, [1]=Button (when Sign Up tab is active)
 *   onAllNodes(hasSetTextAction()) on Log In tab  → [0]=Email, [1]=Password
 *   onAllNodes(hasSetTextAction()) on Sign Up tab → [0]=Email, [1]=Password, [2]=ConfirmPassword
 *   onAllNodes(hasSetTextAction()) on Log In tab + dialog open → [0]=Email, [1]=Password, [2]=DialogEmail
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AuthScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun makeVm(fakeAuthRepo: FakeAuthRepo = FakeAuthRepo()) =
        AuthViewModel(ApplicationProvider.getApplicationContext(), fakeAuthRepo, FakeMovieListRepo(), NoopMovieRepository())

    private fun setContent(vm: AuthViewModel = makeVm()) {
        composeTestRule.setContent {
            MovietrackerTheme {
                AuthScreen(viewModel = vm)
            }
        }
    }

    // ── Sign Up tab ──────────────────────────────────────────────────────────

    @Test
    fun signUpButton_isDisabled_whenNoInputEntered() {
        setContent()
        composeTestRule.onNodeWithText("Sign Up").performClick()
        // [0]=Sign Up Tab, [1]=Sign Up Button
        composeTestRule.onAllNodesWithText("Sign Up")[1].assertIsNotEnabled()
    }

    @Test
    fun signUpButton_isDisabled_whenEmailEmpty() {
        setContent()
        composeTestRule.onNodeWithText("Sign Up").performClick()
        // Fill password and confirm password but leave email empty
        composeTestRule.onAllNodes(hasSetTextAction())[1].performTextInput("password123")
        composeTestRule.onAllNodes(hasSetTextAction())[2].performTextInput("password123")
        composeTestRule.onAllNodesWithText("Sign Up")[1].assertIsNotEnabled()
    }

    @Test
    fun signUpButton_isDisabled_whenPasswordsMismatch() {
        setContent()
        composeTestRule.onNodeWithText("Sign Up").performClick()
        composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("a@b.com")
        composeTestRule.onAllNodes(hasSetTextAction())[1].performTextInput("pass1")
        composeTestRule.onAllNodes(hasSetTextAction())[2].performTextInput("pass2")
        composeTestRule.onAllNodesWithText("Sign Up")[1].assertIsNotEnabled()
    }

    @Test
    fun signUpButton_isEnabled_whenAllFieldsFilledAndPasswordsMatch() {
        setContent()
        composeTestRule.onNodeWithText("Sign Up").performClick()
        composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("a@b.com")
        composeTestRule.onAllNodes(hasSetTextAction())[1].performTextInput("pass1234")
        composeTestRule.onAllNodes(hasSetTextAction())[2].performTextInput("pass1234")
        composeTestRule.onAllNodesWithText("Sign Up")[1].assertIsEnabled()
    }

    @Test
    fun signUpTab_showsPasswordMismatchError_whenPasswordsDontMatch() {
        setContent()
        composeTestRule.onNodeWithText("Sign Up").performClick()
        composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("a@b.com")
        composeTestRule.onAllNodes(hasSetTextAction())[1].performTextInput("pass1")
        composeTestRule.onAllNodes(hasSetTextAction())[2].performTextInput("pass2")
        composeTestRule.onNodeWithText("Passwords do not match").assertExists()
    }

    // ── Log In tab ───────────────────────────────────────────────────────────

    @Test
    fun logInButton_isDisabled_whenNoInputEntered() {
        setContent()
        // Log In is the default tab: [0]=Log In Tab, [1]=Log In Button
        composeTestRule.onAllNodesWithText("Log In")[1].assertIsNotEnabled()
    }

    @Test
    fun logInButton_isEnabled_whenBothFieldsFilled() {
        setContent()
        // Log In tab: [0]=Email field, [1]=Password field
        composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("a@b.com")
        composeTestRule.onAllNodes(hasSetTextAction())[1].performTextInput("password")
        composeTestRule.onAllNodesWithText("Log In")[1].assertIsEnabled()
    }

    // ── Forgot Password dialog ───────────────────────────────────────────────

    @Test
    fun forgotPasswordButton_isVisible_onLogInTab() {
        setContent()
        composeTestRule.onNodeWithText("Forgot Password?").assertExists()
    }

    @Test
    fun tappingForgotPassword_opensDialogWithResetPasswordTitle() {
        setContent()
        composeTestRule.onNodeWithText("Forgot Password?").performClick()
        composeTestRule.onNodeWithText("Reset Password").assertExists()
    }

    @Test
    fun sendResetEmailButton_isDisabled_whenEmailFieldEmpty() {
        setContent()
        composeTestRule.onNodeWithText("Forgot Password?").performClick()
        composeTestRule.onNodeWithText("Send Reset Email").assertIsNotEnabled()
    }

    @Test
    fun submittingResetEmail_showsConfirmationMessage() {
        setContent()
        composeTestRule.onNodeWithText("Forgot Password?").performClick()
        // Log In tab + dialog open: [0]=email(LogIn), [1]=password(LogIn), [2]=dialog email
        composeTestRule.onAllNodes(hasSetTextAction())[2].performTextInput("a@b.com")
        composeTestRule.onNodeWithText("Send Reset Email").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(
            "If an account exists for that email, a reset link has been sent."
        ).assertExists()
    }

    @Test
    fun cancellingForgotPasswordDialog_doesNotCallSendPasswordReset() {
        val fakeAuth = FakeAuthRepo()
        setContent(makeVm(fakeAuth))
        composeTestRule.onNodeWithText("Forgot Password?").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertFalse("sendPasswordReset must not be called when Cancel is tapped",
            fakeAuth.passwordResetCalled)
    }
}

// ── Fakes ─────────────────────────────────────────────────────────────────────

private class FakeAuthRepo : AuthRepository {
    var passwordResetCalled = false

    override val currentUser: FirebaseUser? = null

    override suspend fun signUp(email: String, password: String): Result<FirebaseUser> =
        Result.failure(Exception("unused"))

    override suspend fun signIn(email: String, password: String): Result<FirebaseUser> =
        Result.failure(Exception("unused"))

    override fun signOut() {}

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        passwordResetCalled = true
        return Result.success(Unit)
    }
    override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
}

private class FakeMovieListRepo : MovieListRepository {
    override fun getLists(uid: String): Flow<Result<List<MovieList>>> = emptyFlow()
    override suspend fun createList(uid: String, list: MovieList): Result<String> =
        Result.success("id")
    override suspend fun updateList(uid: String, list: MovieList): Result<Unit> =
        Result.success(Unit)
    override suspend fun deleteList(uid: String, listId: String): Result<Unit> =
        Result.success(Unit)
    override suspend fun deleteAllLists(uid: String): Result<Unit> = Result.success(Unit)
}
