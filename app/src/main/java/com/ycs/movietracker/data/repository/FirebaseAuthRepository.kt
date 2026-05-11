package com.ycs.movietracker.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class FirebaseAuthRepository(
    private val auth: FirebaseAuth
) : AuthRepository {

    override val currentUser: FirebaseUser?
        get() = auth.currentUser

    override fun authStateChanges(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { a ->
            Timber.i("Auth state changed: uid=${a.currentUser?.uid ?: "signed out"}")
            trySend(a.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signUp(email: String, password: String): Result<FirebaseUser> =
        try {
            val user = auth.createUserWithEmailAndPassword(email, password).await().user
                ?: return Result.failure(IllegalStateException("Sign-up succeeded but user is null"))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }

    override suspend fun signIn(email: String, password: String): Result<FirebaseUser> =
        try {
            val user = auth.signInWithEmailAndPassword(email, password).await().user
                ?: return Result.failure(IllegalStateException("Sign-in succeeded but user is null"))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }

    override fun signOut() {
        auth.signOut()
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    override suspend fun deleteAccount(): Result<Unit> =
        try {
            val user = auth.currentUser
                ?: return Result.failure(IllegalStateException("No signed-in user"))
            user.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
}
