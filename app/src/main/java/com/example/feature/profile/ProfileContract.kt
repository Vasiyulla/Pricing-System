package com.example.feature.profile

import com.example.core.data.local.entity.QueuedSubmissionEntity
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
    val pendingOfflineCount: Int = 0,
    val queuedSubmissions: List<QueuedSubmissionEntity> = emptyList(),
    val isOfflineQueueSheetOpen: Boolean = false,
    val isSyncingOffline: Boolean = false,
    val isSettingsSheetOpen: Boolean = false,
    val notifyPriceDrops: Boolean = true,
    val notifyFreshnessAlerts: Boolean = true,
    val notifyCommunityPoints: Boolean = true,
    val autoSyncOnWifi: Boolean = true,
    val compressReceiptImages: Boolean = true,
    val preferredRadiusKm: Double = 1.5,
    val isCacheClearedMessageVisible: Boolean = false
)

sealed interface ProfileUiEvent {
    data object OnBackClicked : ProfileUiEvent
    data object OnRetryClicked : ProfileUiEvent
    data class OnBottomNavClicked(val destination: BottomBarDestination) : ProfileUiEvent
    data object OnSettingsClicked : ProfileUiEvent
    data object OnDismissSettingsSheet : ProfileUiEvent
    data class OnTogglePriceDropNotifs(val enabled: Boolean) : ProfileUiEvent
    data class OnToggleFreshnessNotifs(val enabled: Boolean) : ProfileUiEvent
    data class OnTogglePointsNotifs(val enabled: Boolean) : ProfileUiEvent
    data class OnToggleAutoSyncWifi(val enabled: Boolean) : ProfileUiEvent
    data class OnToggleCompressImages(val enabled: Boolean) : ProfileUiEvent
    data class OnSelectPreferredRadius(val radiusKm: Double) : ProfileUiEvent
    data object OnClearLocalCache : ProfileUiEvent
    data object OnEditProfileClicked : ProfileUiEvent
    data object OnOpenProfileSwitcher : ProfileUiEvent
    data object OnDismissProfileSwitcher : ProfileUiEvent
    data class OnSelectProfile(val profileId: String) : ProfileUiEvent
    data object OnOpenDpdpNotice : ProfileUiEvent
    data object OnDismissDpdpNotice : ProfileUiEvent
    data object OnSignOut : ProfileUiEvent
    data object OnSyncOfflineQueue : ProfileUiEvent
    data object OnOpenOfflineQueueSheet : ProfileUiEvent
    data object OnDismissOfflineQueueSheet : ProfileUiEvent
    data class OnRetryQueuedSubmission(val submissionId: String) : ProfileUiEvent
    data class OnCancelQueuedSubmission(val submissionId: String) : ProfileUiEvent
}
