package com.example.feature.auth

enum class AuthStep {
    PHONE_INPUT,
    OTP_INPUT,
    SUCCESS
}

data class CountryCode(
    val code: String, // e.g. "+91"
    val countryName: String, // e.g. "India"
    val flagEmoji: String, // e.g. "🇮🇳"
    val minDigits: Int = 10,
    val maxDigits: Int = 10,
    val placeholder: String = "98765 43210"
)

val DEFAULT_COUNTRIES = listOf(
    CountryCode(code = "+91", countryName = "India", flagEmoji = "🇮🇳", minDigits = 10, maxDigits = 10, placeholder = "98765 43210"),
    CountryCode(code = "+1", countryName = "United States", flagEmoji = "🇺🇸", minDigits = 10, maxDigits = 10, placeholder = "415 555 2671"),
    CountryCode(code = "+44", countryName = "United Kingdom", flagEmoji = "🇬🇧", minDigits = 10, maxDigits = 11, placeholder = "7911 123456"),
    CountryCode(code = "+971", countryName = "United Arab Emirates", flagEmoji = "🇦🇪", minDigits = 9, maxDigits = 9, placeholder = "50 123 4567"),
    CountryCode(code = "+966", countryName = "Saudi Arabia", flagEmoji = "🇸🇦", minDigits = 9, maxDigits = 9, placeholder = "50 123 4567"),
    CountryCode(code = "+65", countryName = "Singapore", flagEmoji = "🇸🇬", minDigits = 8, maxDigits = 8, placeholder = "8123 4567"),
    CountryCode(code = "+60", countryName = "Malaysia", flagEmoji = "🇲🇾", minDigits = 9, maxDigits = 10, placeholder = "12 345 6789"),
    CountryCode(code = "+61", countryName = "Australia", flagEmoji = "🇦🇺", minDigits = 9, maxDigits = 9, placeholder = "412 345 678"),
    CountryCode(code = "+49", countryName = "Germany", flagEmoji = "🇩🇪", minDigits = 10, maxDigits = 11, placeholder = "151 23456789"),
    CountryCode(code = "+1", countryName = "Canada", flagEmoji = "🇨🇦", minDigits = 10, maxDigits = 10, placeholder = "604 555 1234")
)

data class AuthUiState(
    val step: AuthStep = AuthStep.PHONE_INPUT,
    val selectedCountry: CountryCode = DEFAULT_COUNTRIES[0],
    val isCountryPickerOpen: Boolean = false,
    val countrySearchQuery: String = "",
    val availableCountries: List<CountryCode> = DEFAULT_COUNTRIES,
    val phoneNumber: String = "",
    val otp: String = "",
    val isConsentChecked: Boolean = true,
    val isConsentSheetVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val resendCountdownSeconds: Int = 30
) {
    val filteredCountries: List<CountryCode>
        get() = if (countrySearchQuery.isBlank()) {
            availableCountries
        } else {
            availableCountries.filter {
                it.countryName.contains(countrySearchQuery, ignoreCase = true) ||
                    it.code.contains(countrySearchQuery, ignoreCase = true)
            }
        }

    val isPhoneValid: Boolean
        get() {
            val digits = phoneNumber.filter { it.isDigit() }.length
            return digits in selectedCountry.minDigits..selectedCountry.maxDigits
        }

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
    data object OnOpenCountryPicker : AuthUiEvent
    data object OnDismissCountryPicker : AuthUiEvent
    data class OnSelectCountry(val country: CountryCode) : AuthUiEvent
    data class OnCountrySearchQueryChanged(val query: String) : AuthUiEvent
}
