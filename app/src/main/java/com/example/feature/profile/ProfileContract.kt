package com.example.feature.profile

import com.example.core.model.PriceSubmission
import com.example.core.model.UserProfile
import com.example.core.ui.BottomBarDestination

data class ProfileUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val profile: UserProfile? = null,
    val recentSubmissions: List<PriceSubmission> = emptyList(),
    val availableProfiles: List<UserProfile> = emptyList(),
    val isProfileSwitcherOpen: Boolean = false,
    val isDpdpNoticeOpen: Boolean = false,
    val pendingOfflineCount: Int = 0
)

sealed interface ProfileUiEvent {
    data object OnBackClicked : ProfileUiEvent
    data object OnRetryClicked : ProfileUiEvent
    data class OnBottomNavClicked(val destination: BottomBarDestination) : ProfileUiEvent
    data object OnSettingsClicked : ProfileUiEvent
    data object OnEditProfileClicked : ProfileUiEvent
    data object OnOpenProfileSwitcher : ProfileUiEvent
    data object OnDismissProfileSwitcher : ProfileUiEvent
    data class OnSelectProfile(val profileId: String) : ProfileUiEvent
    data object OnOpenDpdpNotice : ProfileUiEvent
    data object OnDismissDpdpNotice : ProfileUiEvent
    data object OnSignOut : ProfileUiEvent
    data object OnSyncOfflineQueue : ProfileUiEvent
}
