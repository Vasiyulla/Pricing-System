package com.example.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.FakePriceBridgeRepository
import com.example.core.data.PriceBridgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: PriceBridgeRepository = FakePriceBridgeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(isLoading = true))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun onEvent(event: ProfileUiEvent) {
        when (event) {
            is ProfileUiEvent.OnRetryClicked -> loadProfile()
            is ProfileUiEvent.OnOpenProfileSwitcher -> {
                _uiState.update { it.copy(isProfileSwitcherOpen = true) }
            }
            is ProfileUiEvent.OnDismissProfileSwitcher -> {
                _uiState.update { it.copy(isProfileSwitcherOpen = false) }
            }
            is ProfileUiEvent.OnSelectProfile -> {
                viewModelScope.launch {
                    repository.switchProfile(event.profileId)
                    _uiState.update { it.copy(isProfileSwitcherOpen = false) }
                }
            }
            is ProfileUiEvent.OnOpenDpdpNotice -> {
                _uiState.update { it.copy(isDpdpNoticeOpen = true) }
            }
            is ProfileUiEvent.OnDismissDpdpNotice -> {
                _uiState.update { it.copy(isDpdpNoticeOpen = false) }
            }
            is ProfileUiEvent.OnSignOut -> {
                viewModelScope.launch {
                    repository.logout()
                }
            }
            is ProfileUiEvent.OnSyncOfflineQueue -> {
                viewModelScope.launch {
                    repository.syncOfflineSubmissions()
                }
            }
            else -> { /* Navigation events handled by screen callbacks */ }
        }
    }

    private fun loadProfile() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        // Observe offline queue count
        viewModelScope.launch {
            repository.getPendingOfflineSubmissionCount().collect { count ->
                _uiState.update { it.copy(pendingOfflineCount = count) }
            }
        }

        viewModelScope.launch {
            combine(
                repository.getUserProfile(),
                repository.getSubmissionHistory(),
                repository.getAvailableProfiles()
            ) { profile, submissions, available ->
                _uiState.value.copy(
                    isLoading = false,
                    profile = profile,
                    recentSubmissions = submissions,
                    availableProfiles = available
                )
            }.catch { ex ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = ex.localizedMessage ?: "Failed to load profile"
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
