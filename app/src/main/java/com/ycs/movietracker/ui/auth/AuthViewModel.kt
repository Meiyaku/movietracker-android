package com.ycs.movietracker.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.repository.AuthRepository
import com.ycs.movietracker.data.repository.FirebaseAuthRepository
import com.ycs.movietracker.data.repository.FirebaseMovieListRepository
import com.ycs.movietracker.data.repository.MovieListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val signUpError: String? = null,
    val signInError: String? = null,
    val passwordResetSent: Boolean = false
)

class AuthViewModel(
    private val authRepository: AuthRepository = FirebaseAuthRepository(),
    private val listRepository: MovieListRepository = FirebaseMovieListRepository()
) : ViewModel() {

    val authState: StateFlow<FirebaseUser?> = authRepository.authStateChanges()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = authRepository.currentUser
        )

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signUp(email: String, password: String, confirmPassword: String) {
        if (password != confirmPassword) {
            _uiState.value = _uiState.value.copy(signUpError = "Passwords do not match")
            return
        }
        _uiState.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            authRepository.signUp(email, password).fold(
                onSuccess = { user ->
                    listRepository.createList(user.uid, MovieList(name = "My Movies"))
                    _uiState.value = AuthUiState()
                },
                onFailure = { e ->
                    val msg = when (e) {
                        is FirebaseAuthUserCollisionException -> "An account with this email already exists"
                        is FirebaseAuthWeakPasswordException -> "Password must be at least 6 characters"
                        else -> e.message ?: "Sign up failed. Please try again."
                    }
                    _uiState.value = AuthUiState(signUpError = msg)
                }
            )
        }
    }

    fun signIn(email: String, password: String) {
        _uiState.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            authRepository.signIn(email, password).fold(
                onSuccess = {
                    _uiState.value = AuthUiState()
                    // Navigation back to home is driven by the Firebase AuthStateListener
                    // in MainActivity — no explicit navigation needed here.
                },
                onFailure = { e ->
                    val msg = when (e) {
                        is FirebaseAuthInvalidCredentialsException,
                        is FirebaseAuthInvalidUserException -> "Incorrect email or password"
                        else -> e.message ?: "Sign in failed. Please try again."
                    }
                    _uiState.value = AuthUiState(signInError = msg)
                }
            )
        }
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.value = AuthUiState() // clear all in-memory auth state
        // MainActivity's AuthStateListener detects the sign-out and switches to AuthScreen
    }

    fun sendPasswordReset(email: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            // Always show the confirmation regardless of success or failure —
            // this prevents account enumeration (FR-3a).
            authRepository.sendPasswordReset(email)
            _uiState.value = _uiState.value.copy(isLoading = false, passwordResetSent = true)
        }
    }

    fun dismissPasswordReset() {
        _uiState.value = _uiState.value.copy(passwordResetSent = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(signUpError = null, signInError = null)
    }
}
