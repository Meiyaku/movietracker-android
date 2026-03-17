package com.ycs.movietracker.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebaseAuthRepository : AuthRepository {

    private val auth = FirebaseAuth.getInstance()

    override val currentUser: FirebaseUser?
        get() = auth.currentUser

    override fun authStateChanges(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { a -> trySend(a.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signUp(email: String, password: String): Result<FirebaseUser> =
        suspendCancellableCoroutine { cont ->
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result -> cont.resume(Result.success(result.user!!)) }
                .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
        }

    override suspend fun signIn(email: String, password: String): Result<FirebaseUser> =
        suspendCancellableCoroutine { cont ->
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result -> cont.resume(Result.success(result.user!!)) }
                .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
        }

    override fun signOut() {
        auth.signOut()
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
        }
}
