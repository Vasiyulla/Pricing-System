package com.example.feature.home

import com.example.core.data.local.entity.QueuedSubmissionEntity
import com.example.core.model.AppNotification
import com.example.core.model.Category
import com.example.core.model.KarmaVerificationPrompt
import com.example.core.model.LocationInfo
import com.example.core.model.Product
import com.example.core.model.SmartBasketSummary
import com.example.core.model.Store
import com.example.core.ui.BottomBarDestination

data class HomeUiState(
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val location: LocationInfo = LocationInfo(
        neighborhood = "Indiranagar, 100ft Rd",
        addressLine = "Bangalore, Karnataka",
        radiusDescription = "1.5 km radius",
        isLiveTrackingActive = true
    ),
    val basketSummary: SmartBasketSummary? = null,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String = "all",
    val deals: List<Product> = emptyList(),
    val nearbyStores: List<Store> = emptyList(),
    val karmaPrompt: KarmaVerificationPrompt? = null,
    val isLocationSheetOpen: Boolean = false,
    val pendingOfflineCount: Int = 0,
    val queuedSubmissions: List<QueuedSubmissionEntity> = emptyList(),
    val isOfflineQueueSheetOpen: Boolean = false,
    val isSyncingOffline: Boolean = false,
    val notifications: List<AppNotification> = emptyList(),
    val isNotificationsSheetOpen: Boolean = false
) {
    val unreadNotificationsCount: Int get() = notifications.count { !it.isRead }
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && deals.isEmpty()
}

sealed interface HomeUiEvent {
    data class OnCategorySelected(val categoryId: String) : HomeUiEvent
    data object OnSearchClicked : HomeUiEvent
    data object OnScanClicked : HomeUiEvent
    data object OnViewBasketClicked : HomeUiEvent
    data class OnProductClicked(val productId: String) : HomeUiEvent
    data class OnCompareStoresClicked(val productId: String) : HomeUiEvent
    data class OnStoreClicked(val storeId: String) : HomeUiEvent
    data class OnVerifyKarmaClicked(val promptId: String) : HomeUiEvent
    data class OnDismissKarmaPrompt(val promptId: String) : HomeUiEvent
    data object OnLocationSelectorClicked : HomeUiEvent
    data object OnNotificationsClicked : HomeUiEvent
    data object OnDismissNotificationsSheet : HomeUiEvent
    data class OnNotificationClicked(val notification: AppNotification) : HomeUiEvent
    data object OnMarkAllNotificationsRead : HomeUiEvent
    data object OnRetryClicked : HomeUiEvent
    data class OnBottomNavClicked(val destination: BottomBarDestination) : HomeUiEvent
    data class OnChangeLocation(val neighborhood: String, val radiusKm: Double) : HomeUiEvent
    data object OnDismissLocationSheet : HomeUiEvent
    data object OnSyncOfflineQueue : HomeUiEvent
    data object OnOpenOfflineQueueSheet : HomeUiEvent
    data object OnDismissOfflineQueueSheet : HomeUiEvent
    data class OnRetryQueuedSubmission(val submissionId: String) : HomeUiEvent
    data class OnCancelQueuedSubmission(val submissionId: String) : HomeUiEvent
}
