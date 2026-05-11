package com.ycs.movietracker.ui.auth

import android.content.Context
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.ycs.movietracker.R
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.repository.AuthRepository
import com.ycs.movietracker.data.repository.MovieListRepository
import com.ycs.movietracker.data.repository.MovieRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val passwordResetSent: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val deleteAccountError: String? = null
)

class AuthViewModel(
    private val context: Context,
    private val authRepository: AuthRepository,
    private val listRepository: MovieListRepository,
    private val movieRepository: MovieRepository
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
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            _uiState.value = _uiState.value.copy(signUpError = context.getString(R.string.error_invalid_email))
            return
        }
        if (password != confirmPassword) {
            _uiState.value = _uiState.value.copy(signUpError = context.getString(R.string.error_passwords_do_not_match))
            return
        }
        _uiState.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            authRepository.signUp(email, password).fold(
                onSuccess = { user ->
                    val listResult = listRepository.createList(
                        user.uid, MovieList(name = MovieList.DEFAULT_LIST_NAME)
                    )
                    if (listResult.isFailure) {
                        val msg = listResult.exceptionOrNull()?.message
                            ?: context.getString(R.string.error_sign_up_failed)
                        _uiState.value = AuthUiState(signUpError = msg)
                        return@launch
                    }
                    _uiState.value = AuthUiState()
                },
                onFailure = { e ->
                    val msg = when (e) {
                        is FirebaseAuthUserCollisionException -> context.getString(R.string.error_email_already_exists)
                        is FirebaseAuthWeakPasswordException -> context.getString(R.string.error_password_too_short)
                        is FirebaseAuthInvalidCredentialsException -> context.getString(R.string.error_invalid_email)
                        is FirebaseNetworkException -> context.getString(R.string.error_auth_network)
                        else -> e.message ?: context.getString(R.string.error_sign_up_failed)
                    }
                    _uiState.value = AuthUiState(signUpError = msg)
                }
            )
        }
    }

    fun signIn(email: String, password: String) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            _uiState.value = _uiState.value.copy(signInError = context.getString(R.string.error_invalid_email))
            return
        }
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
                        is FirebaseAuthInvalidUserException -> context.getString(R.string.error_invalid_credentials)
                        is FirebaseNetworkException -> context.getString(R.string.error_auth_network)
                        else -> e.message ?: context.getString(R.string.error_sign_in_failed)
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

    fun deleteAccount() {
        val uid = authRepository.currentUser?.uid ?: return
        _uiState.value = _uiState.value.copy(isDeletingAccount = true, deleteAccountError = null)
        viewModelScope.launch {
            // Delete Firestore data in parallel first. Auth deletion is intentionally
            // last — if data deletion fails the account still exists and the user can retry.
            val (moviesResult, listsResult) = coroutineScope {
                val m = async { movieRepository.deleteAllMovies(uid) }
                val l = async { listRepository.deleteAllLists(uid) }
                m.await() to l.await()
            }
            val dataError = moviesResult.exceptionOrNull() ?: listsResult.exceptionOrNull()
            if (dataError != null) {
                _uiState.value = _uiState.value.copy(
                    isDeletingAccount = false,
                    deleteAccountError = dataError.message ?: context.getString(R.string.error_generic)
                )
                return@launch
            }
            authRepository.deleteAccount().fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isDeletingAccount = false)
                    // AuthStateListener in MainActivity detects account deletion and navigates to auth screen
                },
                onFailure = { e ->
                    val msg = when (e) {
                        is FirebaseAuthRecentLoginRequiredException -> context.getString(R.string.error_reauth_required)
                        is FirebaseNetworkException -> context.getString(R.string.error_auth_network)
                        else -> e.message ?: context.getString(R.string.error_generic)
                    }
                    _uiState.value = _uiState.value.copy(
                        isDeletingAccount = false,
                        deleteAccountError = msg
                    )
                }
            )
        }
    }

    fun clearDeleteAccountError() {
        _uiState.value = _uiState.value.copy(deleteAccountError = null)
    }
}
