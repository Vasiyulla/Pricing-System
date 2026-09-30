package com.example.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.FakePriceBridgeRepository
import com.example.core.data.PriceBridgeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: PriceBridgeRepository = FakePriceBridgeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEvent(event: AuthUiEvent) {
        when (event) {
            is AuthUiEvent.OnPhoneNumberChanged -> {
                val cleaned = event.value.filter { it.isDigit() }.take(10)
                _uiState.update { it.copy(phoneNumber = cleaned, errorMessage = null) }
            }
            is AuthUiEvent.OnOtpChanged -> {
                val cleaned = event.value.filter { it.isDigit() }.take(6)
                _uiState.update { it.copy(otp = cleaned, errorMessage = null) }
            }
            is AuthUiEvent.OnUseDemoPhone -> {
                _uiState.update { it.copy(phoneNumber = event.phone, errorMessage = null) }
            }
            is AuthUiEvent.OnFillDemoOtp -> {
                _uiState.update { it.copy(otp = "123456", errorMessage = null) }
            }
            is AuthUiEvent.OnRequestOtp -> {
                requestOtp()
            }
            is AuthUiEvent.OnVerifyOtp -> {
                verifyOtp()
            }
            is AuthUiEvent.OnResendOtp -> {
                requestOtp()
            }
            is AuthUiEvent.OnGoogleSignInClicked -> {
                loginWithGoogle()
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
            is AuthUiEvent.OnBackToPhone -> {
                _uiState.update { it.copy(step = AuthStep.PHONE_INPUT, otp = "", errorMessage = null) }
            }
        }
    }

    private fun requestOtp() {
        val state = _uiState.value
        if (!state.isPhoneValid) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid 10-digit Indian mobile number") }
            return
        }
        if (!state.isConsentChecked) {
            _uiState.update { it.copy(errorMessage = "Please agree to the DPDP Privacy Notice to proceed") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            delay(600)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    step = AuthStep.OTP_INPUT,
                    otp = "123456" // Pre-fill sample OTP for convenient review
                )
            }
        }
    }

    private fun verifyOtp() {
        val state = _uiState.value
        if (!state.isOtpValid) {
            _uiState.update { it.copy(errorMessage = "Please enter the 6-digit OTP") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            repository.loginWithPhone(state.phoneNumber, state.otp)
            _uiState.update { it.copy(isLoading = false, step = AuthStep.SUCCESS) }
        }
    }

    private fun loginWithGoogle() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.loginWithGoogle()
            _uiState.update { it.copy(isLoading = false, step = AuthStep.SUCCESS) }
        }
    }

    private fun loginAsGuest() {
        viewModelScope.launch {
            repository.loginAsGuest()
            _uiState.update { it.copy(step = AuthStep.SUCCESS) }
        }
    }
}
