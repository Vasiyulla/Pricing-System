package com.example.feature.auth

enum class AuthStep {
    PHONE_INPUT,
    OTP_INPUT,
    SUCCESS
}

data class AuthUiState(
    val step: AuthStep = AuthStep.PHONE_INPUT,
    val phoneNumber: String = "",
    val otp: String = "",
    val isConsentChecked: Boolean = true,
    val isConsentSheetVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val resendCountdownSeconds: Int = 30
) {
    val isPhoneValid: Boolean
        get() = phoneNumber.filter { it.isDigit() }.length == 10

    val isOtpValid: Boolean
        get() = otp.trim().length == 6
}

sealed interface AuthUiEvent {
    data class OnPhoneNumberChanged(val value: String) : AuthUiEvent
    data class OnOtpChanged(val value: String) : AuthUiEvent
    data object OnRequestOtp : AuthUiEvent
    data object OnVerifyOtp : AuthUiEvent
    data object OnResendOtp : AuthUiEvent
    data object OnGoogleSignInClicked : AuthUiEvent
    data object OnContinueAsGuest : AuthUiEvent
    data object OnOpenConsentSheet : AuthUiEvent
    data object OnDismissConsentSheet : AuthUiEvent
    data class OnToggleConsent(val accepted: Boolean) : AuthUiEvent
    data object OnBackToPhone : AuthUiEvent
    data class OnUseDemoPhone(val phone: String) : AuthUiEvent
    data object OnFillDemoOtp : AuthUiEvent
}
