package com.example.feature.auth

enum class AuthStep {
    IDLE,
    AUTHENTICATING,
    SUCCESS
}

data class AuthUiState(
    val isSignUpMode: Boolean = false,
    val nameInput: String = "",
    val emailInput: String = "",
    val passwordInput: String = "",
    val isPasswordVisible: Boolean = false,
    val step: AuthStep = AuthStep.IDLE,
    val isConsentChecked: Boolean = true,
    val isConsentSheetVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface AuthUiEvent {
    data class OnNameChanged(val name: String) : AuthUiEvent
    data class OnEmailChanged(val email: String) : AuthUiEvent
    data class OnPasswordChanged(val password: String) : AuthUiEvent
    data object OnTogglePasswordVisibility : AuthUiEvent
    data object OnToggleAuthMode : AuthUiEvent
    data object OnSubmitEmailAuth : AuthUiEvent
    data object OnGoogleSignInClicked : AuthUiEvent
    data object OnContinueAsGuest : AuthUiEvent
    data object OnOpenConsentSheet : AuthUiEvent
    data object OnDismissConsentSheet : AuthUiEvent
    data class OnToggleConsent(val accepted: Boolean) : AuthUiEvent
}
