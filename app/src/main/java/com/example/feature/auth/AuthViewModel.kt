package com.example.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.auth.FirebaseAuthManager
import com.example.core.data.PriceBridgeRepository
import com.example.core.data.PriceBridgeRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: PriceBridgeRepository = PriceBridgeRepositoryProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEvent(event: AuthUiEvent) {
        when (event) {
            is AuthUiEvent.OnNameChanged -> {
                _uiState.update { it.copy(nameInput = event.name, errorMessage = null) }
            }
            is AuthUiEvent.OnEmailChanged -> {
                _uiState.update { it.copy(emailInput = event.email, errorMessage = null) }
            }
            is AuthUiEvent.OnPasswordChanged -> {
                _uiState.update { it.copy(passwordInput = event.password, errorMessage = null) }
            }
            is AuthUiEvent.OnTogglePasswordVisibility -> {
                _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
            }
            is AuthUiEvent.OnToggleAuthMode -> {
                _uiState.update { it.copy(isSignUpMode = !it.isSignUpMode, errorMessage = null) }
            }
            is AuthUiEvent.OnSubmitEmailAuth -> {
                submitEmailAuth()
            }
            is AuthUiEvent.OnGoogleSignInClicked -> {
                // Handled in AuthScreen via FirebaseAuthManager, but triggers loading state
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            is AuthUiEvent.OnContinueAsGuest -> {
                loginAsGuest()
            }
            is AuthUiEvent.OnOpenConsentSheet -> {
                _uiState.update { it.copy(isConsentSheetVisible = true) }
            }
            is AuthUiEvent.OnDismissConsentSheet -> {
                _uiState.update { it.copy(isConsentSheetVisible = false) }
            }
            is AuthUiEvent.OnToggleConsent -> {
                _uiState.update { it.copy(isConsentChecked = event.accepted) }
            }
        }
    }

    private fun submitEmailAuth() {
        val state = _uiState.value
        val email = state.emailInput.trim()
        val password = state.passwordInput
        val name = state.nameInput.trim()

        if (email.isBlank() || !email.contains("@")) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address") }
            return
        }
        if (password.length < 8) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters") }
            return
        }
        if (state.isSignUpMode && name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your name") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                if (state.isSignUpMode) {
                    repository.signUpWithEmail(name = name, email = email, password = password)
                } else {
                    repository.signInWithEmail(email = email, password = password)
                }
                _uiState.update { it.copy(isLoading = false, step = AuthStep.SUCCESS) }
            } catch (e: Exception) {
                var errorMsg = e.localizedMessage ?: "Authentication failed. Please verify credentials."
                if (e is retrofit2.HttpException) {
                    try {
                        val body = e.response()?.errorBody()?.string()
                        if (!body.isNullOrBlank()) {
                            val json = org.json.JSONObject(body)
                            if (json.has("error")) {
                                errorMsg = json.getString("error")
                            }
                        }
                    } catch (_: Exception) {}
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = if (errorMsg.contains("HTTP 401") || errorMsg.contains("401")) {
                            "Invalid email or password"
                        } else {
                            errorMsg
                        }
                    )
                }
            }
        }
    }

    fun handleFirebaseSignInResult(userData: FirebaseAuthManager.FirebaseUserData) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.loginWithGoogle(
                    idToken = userData.firebaseIdToken,
                    email = userData.email,
                    displayName = userData.displayName,
                    photoUrl = userData.photoUrl
                )
                _uiState.update { it.copy(isLoading = false, step = AuthStep.SUCCESS) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Sign-in synchronization failed: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun handleFirebaseSignInError(error: Throwable) {
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = "Google Sign-In failed: ${error.localizedMessage ?: "User cancelled or network error"}"
            )
        }
    }

    fun handleFirebaseSignInCancelled() {
        _uiState.update { it.copy(isLoading = false, errorMessage = null) }
    }

    private fun loginAsGuest() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.loginAsGuest()
            _uiState.update { it.copy(isLoading = false, step = AuthStep.SUCCESS) }
        }
    }
}
