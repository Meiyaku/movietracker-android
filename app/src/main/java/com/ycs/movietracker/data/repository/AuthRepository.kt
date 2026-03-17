package com.ycs.movietracker.data.repository

import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AuthRepository {
    val currentUser: FirebaseUser?
    fun authStateChanges(): Flow<FirebaseUser?> = emptyFlow()
    suspend fun signUp(email: String, password: String): Result<FirebaseUser>
    suspend fun signIn(email: String, password: String): Result<FirebaseUser>
    fun signOut()
    suspend fun sendPasswordReset(email: String): Result<Unit>
}
