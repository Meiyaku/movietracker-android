package com.ycs.movietracker.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Integration tests for [FirebaseAuthRepository] running against the local Firebase Auth emulator.
 *
 * Prerequisites:
 *   1. Install Firebase CLI: `npm install -g firebase-tools`
 *   2. From the project root (movietracker/): `firebase emulators:start --only firestore,auth`
 *   3. Run: `./gradlew connectedAndroidTest` (requires a running Android Emulator)
 *
 * Each test uses a UUID-suffixed email address for isolation — no teardown cleanup needed
 * because the Auth emulator is ephemeral.
 *
 * Test story: US-AUTH-T
 */
@RunWith(AndroidJUnit4::class)
class FirebaseAuthRepositoryTest {

    private lateinit var repo: FirebaseAuthRepository

    @Before
    fun setUp() {
        EmulatorSetup.configure()
        repo = FirebaseAuthRepository(EmulatorSetup.auth())
        // Ensure each test starts signed out regardless of prior test state.
        EmulatorSetup.auth().signOut()
    }

    @After
    fun tearDown() {
        EmulatorSetup.auth().signOut()
    }

    private fun uniqueEmail() = "test-${UUID.randomUUID()}@example.com"

    companion object {
        private const val PASSWORD = "password123"
    }

    // ── currentUser ───────────────────────────────────────────────────────────

    @Test
    fun currentUser_isNull_whenSignedOut() {
        assertNull(repo.currentUser)
    }

    @Test
    fun currentUser_isNonNull_afterSignUp() = runBlocking {
        repo.signUp(uniqueEmail(), PASSWORD)
        assertNotNull(repo.currentUser)
    }

    // ── signUp ────────────────────────────────────────────────────────────────

    @Test
    fun signUp_returnsUserWithNonBlankUid() = runBlocking {
        val result = repo.signUp(uniqueEmail(), PASSWORD)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().uid.isNotBlank())
    }

    @Test
    fun signUp_returnsUserWithCorrectEmail() = runBlocking {
        val email = uniqueEmail()
        val result = repo.signUp(email, PASSWORD)

        assertEquals(email, result.getOrThrow().email)
    }

    @Test
    fun signUp_duplicateEmail_returnsFailure() = runBlocking {
        val email = uniqueEmail()
        repo.signUp(email, PASSWORD)
        repo.signOut()

        val result = repo.signUp(email, PASSWORD)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is FirebaseAuthUserCollisionException)
    }

    // ── signIn ────────────────────────────────────────────────────────────────

    @Test
    fun signIn_withCorrectCredentials_returnsUser() = runBlocking {
        val email = uniqueEmail()
        repo.signUp(email, PASSWORD)
        repo.signOut()

        val result = repo.signIn(email, PASSWORD)

        assertTrue(result.isSuccess)
        assertEquals(email, result.getOrThrow().email)
    }

    @Test
    fun signIn_withWrongPassword_returnsFailure() = runBlocking {
        val email = uniqueEmail()
        repo.signUp(email, PASSWORD)
        repo.signOut()

        val result = repo.signIn(email, "wrongpassword")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is FirebaseAuthInvalidCredentialsException)
    }

    @Test
    fun signIn_withUnregisteredEmail_returnsFailure() = runBlocking {
        val result = repo.signIn("nobody-${UUID.randomUUID()}@example.com", PASSWORD)

        assertTrue(result.isFailure)
    }

    // ── signOut ───────────────────────────────────────────────────────────────

    @Test
    fun signOut_currentUserIsNull_afterSignOut() = runBlocking {
        repo.signUp(uniqueEmail(), PASSWORD)
        repo.signOut()

        assertNull(repo.currentUser)
    }

    // ── sendPasswordReset ─────────────────────────────────────────────────────

    @Test
    fun sendPasswordReset_returnsSuccess_forRegisteredEmail() = runBlocking {
        val email = uniqueEmail()
        repo.signUp(email, PASSWORD)
        repo.signOut()

        val result = repo.sendPasswordReset(email)

        assertTrue(result.isSuccess)
    }

    @Test
    fun sendPasswordReset_returnsSuccess_forUnregisteredEmail() = runBlocking {
        // The Auth emulator (like production Firebase) returns success for unknown addresses
        // to prevent email enumeration — this is intentional security behaviour.
        val result = repo.sendPasswordReset("nobody-${UUID.randomUUID()}@example.com")

        assertTrue(result.isSuccess)
    }

    // ── authStateChanges ──────────────────────────────────────────────────────

    @Test
    fun authStateChanges_emitsCurrentUser_whenSignedIn() = runBlocking {
        val email = uniqueEmail()
        repo.signUp(email, PASSWORD)

        val user = withTimeout(5_000) {
            repo.authStateChanges().first { it != null }
        }

        assertNotNull(user)
        assertEquals(email, user!!.email)
    }

    @Test
    fun authStateChanges_emitsNull_whenSignedOut() = runBlocking {
        val email = uniqueEmail()
        repo.signUp(email, PASSWORD)
        repo.signOut()

        // The AuthStateListener fires immediately on subscription with the current auth state.
        val user = withTimeout(5_000) {
            repo.authStateChanges().first()
        }

        assertNull(user)
    }
}
