package com.example.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.PriceBridgeRepositoryProvider
import com.example.core.data.PriceBridgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: PriceBridgeRepository = PriceBridgeRepositoryProvider.repository
) : ViewModel() {

    private val initialSettings = com.example.core.session.AppSessionManager.getSettings()
    private val _uiState = MutableStateFlow(
        ProfileUiState(
            isLoading = true,
            notifyPriceDrops = initialSettings.notifyPriceDrops,
            notifyFreshnessAlerts = initialSettings.notifyFreshnessAlerts,
            notifyCommunityPoints = initialSettings.notifyCommunityPoints,
            autoSyncOnWifi = initialSettings.autoSyncOnWifi,
            compressReceiptImages = initialSettings.compressReceiptImages,
            preferredRadiusKm = initialSettings.preferredRadiusKm
        )
    )
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
                    _uiState.update { it.copy(isSyncingOffline = true) }
                    repository.syncOfflineSubmissions()
                    _uiState.update { it.copy(isSyncingOffline = false, isOfflineQueueSheetOpen = false) }
                }
            }
            is ProfileUiEvent.OnOpenOfflineQueueSheet -> {
                _uiState.update { it.copy(isOfflineQueueSheetOpen = true) }
            }
            is ProfileUiEvent.OnDismissOfflineQueueSheet -> {
                _uiState.update { it.copy(isOfflineQueueSheetOpen = false) }
            }
            is ProfileUiEvent.OnSettingsClicked -> {
                _uiState.update { it.copy(isSettingsSheetOpen = true) }
            }
            is ProfileUiEvent.OnDismissSettingsSheet -> {
                _uiState.update { it.copy(isSettingsSheetOpen = false) }
            }
            is ProfileUiEvent.OnTogglePriceDropNotifs -> {
                com.example.core.session.AppSessionManager.saveSettings { it.copy(notifyPriceDrops = event.enabled) }
                _uiState.update { it.copy(notifyPriceDrops = event.enabled) }
            }
            is ProfileUiEvent.OnToggleFreshnessNotifs -> {
                com.example.core.session.AppSessionManager.saveSettings { it.copy(notifyFreshnessAlerts = event.enabled) }
                _uiState.update { it.copy(notifyFreshnessAlerts = event.enabled) }
            }
            is ProfileUiEvent.OnTogglePointsNotifs -> {
                com.example.core.session.AppSessionManager.saveSettings { it.copy(notifyCommunityPoints = event.enabled) }
                _uiState.update { it.copy(notifyCommunityPoints = event.enabled) }
            }
            is ProfileUiEvent.OnToggleAutoSyncWifi -> {
                com.example.core.session.AppSessionManager.saveSettings { it.copy(autoSyncOnWifi = event.enabled) }
                _uiState.update { it.copy(autoSyncOnWifi = event.enabled) }
            }
            is ProfileUiEvent.OnToggleCompressImages -> {
                com.example.core.session.AppSessionManager.saveSettings { it.copy(compressReceiptImages = event.enabled) }
                _uiState.update { it.copy(compressReceiptImages = event.enabled) }
            }
            is ProfileUiEvent.OnSelectPreferredRadius -> {
                com.example.core.session.AppSessionManager.saveSettings { it.copy(preferredRadiusKm = event.radiusKm) }
                _uiState.update { it.copy(preferredRadiusKm = event.radiusKm) }
            }
            is ProfileUiEvent.OnClearLocalCache -> {
                viewModelScope.launch {
                    repository.clearAllData(wipeServerDb = false)
                    _uiState.update { it.copy(isCacheClearedMessageVisible = true) }
                    kotlinx.coroutines.delay(2000)
                    _uiState.update { it.copy(isCacheClearedMessageVisible = false) }
                }
            }
            is ProfileUiEvent.OnResetAllData -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isResettingData = true, resetSuccessMessage = null) }
                    val success = repository.clearAllData(wipeServerDb = false)
                    _uiState.update {
                        it.copy(
                            isResettingData = false,
                            resetSuccessMessage = if (success) "Local cache cleared and session signed out cleanly." else "Failed to clear local app data."
                        )
                    }
                    kotlinx.coroutines.delay(2500)
                    _uiState.update { it.copy(resetSuccessMessage = null) }
                }
            }
            is ProfileUiEvent.OnRetryQueuedSubmission -> {
                viewModelScope.launch {
                    repository.retryQueuedSubmission(event.submissionId)
                }
            }
            is ProfileUiEvent.OnCancelQueuedSubmission -> {
                viewModelScope.launch {
                    repository.cancelQueuedSubmission(event.submissionId)
                }
            }
            else -> { /* Navigation events handled by screen callbacks */ }
        }
    }

    private fun loadProfile() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        // Observe offline queue count & queued items
        viewModelScope.launch {
            repository.getPendingOfflineSubmissionCount().collect { count ->
                _uiState.update { it.copy(pendingOfflineCount = count) }
            }
        }
        viewModelScope.launch {
            repository.getQueuedSubmissions().collect { queued ->
                _uiState.update { it.copy(queuedSubmissions = queued) }
            }
        }

        viewModelScope.launch {
            combine(
                repository.getUserProfile(),
                repository.getSubmissionHistory(),
                repository.getAvailableProfiles(),
                repository.getMyAddedProducts()
            ) { profile, submissions, available, addedProducts ->
                _uiState.value.copy(
                    isLoading = false,
                    profile = profile,
                    myAddedProducts = addedProducts,
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
