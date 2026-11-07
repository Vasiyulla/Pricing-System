package com.example.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.PriceBridgeRepositoryProvider
import com.example.core.data.PriceBridgeRepository
import com.example.core.model.AppNotification
import com.example.core.model.Category
import com.example.core.model.KarmaVerificationPrompt
import com.example.core.model.LocationInfo
import com.example.core.model.Product
import com.example.core.model.SmartBasketSummary
import com.example.core.model.Store
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: PriceBridgeRepository = PriceBridgeRepositoryProvider.repository
) : ViewModel() {

    private val selectedCategoryId = MutableStateFlow("all")
    private val isOfflineState = MutableStateFlow(false)
    private val errorMessageState = MutableStateFlow<String?>(null)
    private val isLocationSheetOpenState = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.OnCategorySelected -> {
                selectedCategoryId.value = event.categoryId
                _uiState.update { it.copy(selectedCategoryId = event.categoryId) }
            }
            is HomeUiEvent.OnDismissKarmaPrompt -> {
                viewModelScope.launch {
                    repository.dismissKarmaPrompt(event.promptId)
                }
            }
            is HomeUiEvent.OnLocationSelectorClicked -> {
                isLocationSheetOpenState.value = true
                _uiState.update { it.copy(isLocationSheetOpen = true) }
            }
            is HomeUiEvent.OnDismissLocationSheet -> {
                isLocationSheetOpenState.value = false
                _uiState.update { it.copy(isLocationSheetOpen = false) }
            }
            is HomeUiEvent.OnChangeLocation -> {
                viewModelScope.launch {
                    repository.updateLocation(event.location)
                    isLocationSheetOpenState.value = false
                    _uiState.update { it.copy(isLocationSheetOpen = false) }
                }
            }
            is HomeUiEvent.OnSyncOfflineQueue -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isSyncingOffline = true) }
                    repository.syncOfflineSubmissions()
                    _uiState.update { it.copy(isSyncingOffline = false, isOfflineQueueSheetOpen = false) }
                }
            }
            is HomeUiEvent.OnOpenOfflineQueueSheet -> {
                _uiState.update { it.copy(isOfflineQueueSheetOpen = true) }
            }
            is HomeUiEvent.OnDismissOfflineQueueSheet -> {
                _uiState.update { it.copy(isOfflineQueueSheetOpen = false) }
            }
            is HomeUiEvent.OnRetryQueuedSubmission -> {
                viewModelScope.launch {
                    repository.retryQueuedSubmission(event.submissionId)
                }
            }
            is HomeUiEvent.OnCancelQueuedSubmission -> {
                viewModelScope.launch {
                    repository.cancelQueuedSubmission(event.submissionId)
                }
            }
            is HomeUiEvent.OnNotificationsClicked -> {
                _uiState.update { it.copy(isNotificationsSheetOpen = true) }
            }
            is HomeUiEvent.OnDismissNotificationsSheet -> {
                _uiState.update { it.copy(isNotificationsSheetOpen = false) }
            }
            is HomeUiEvent.OnDismissProximityAlert -> {
                _uiState.update { it.copy(activeProximityStore = null, proximityDistanceMeters = null) }
            }
            is HomeUiEvent.OnNotificationClicked -> {
                viewModelScope.launch {
                    repository.markNotificationAsRead(event.notification.id)
                }
            }
            is HomeUiEvent.OnMarkAllNotificationsRead -> {
                viewModelScope.launch {
                    repository.markAllNotificationsAsRead()
                }
            }
            is HomeUiEvent.OnRetryClicked -> {
                errorMessageState.value = null
                loadData()
            }
            else -> {
                // Navigation and external click events handled by screen callbacks
            }
        }
    }

    private fun loadData() {
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
        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            combine(
                repository.getLocation(),
                repository.getSmartBasketSummary(),
                repository.getCategories(),
                selectedCategoryId.flatMapLatest { categoryId ->
                    repository.getBestDeals(categoryId)
                },
                repository.getKarmaPrompt(),
                repository.getNearbyStores(),
                repository.getNotifications()
            ) { flows ->
                val location = flows[0] as LocationInfo
                val basket = flows[1] as SmartBasketSummary
                @Suppress("UNCHECKED_CAST")
                val categories = flows[2] as List<Category>
                @Suppress("UNCHECKED_CAST")
                val deals = flows[3] as List<Product>
                val karma = flows[4] as KarmaVerificationPrompt?
                @Suppress("UNCHECKED_CAST")
                val stores = flows[5] as List<Store>
                @Suppress("UNCHECKED_CAST")
                val notifs = flows[6] as List<AppNotification>

                HomeUiState(
                    isLoading = false,
                    isOffline = isOfflineState.value,
                    errorMessage = errorMessageState.value,
                    location = location,
                    basketSummary = basket,
                    categories = categories,
                    selectedCategoryId = selectedCategoryId.value,
                    deals = deals,
                    nearbyStores = stores,
                    karmaPrompt = karma,
                    isLocationSheetOpen = isLocationSheetOpenState.value,
                    pendingOfflineCount = _uiState.value.pendingOfflineCount,
                    queuedSubmissions = _uiState.value.queuedSubmissions,
                    isOfflineQueueSheetOpen = _uiState.value.isOfflineQueueSheetOpen,
                    isSyncingOffline = _uiState.value.isSyncingOffline,
                    notifications = notifs,
                    isNotificationsSheetOpen = _uiState.value.isNotificationsSheetOpen
                )
            }.catch { exception ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = exception.localizedMessage ?: "Failed to load prices"
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    /**
     * Real-time GPS distance calculation and proximity geofencing (< 75m).
     * Re-calculates distance to all nearby stores dynamically as user moves in real-time.
     */
    fun onLiveGpsLocationUpdated(lat: Double, lon: Double, accuracyMeters: Float = 0f) {
        if (lat == 0.0 && lon == 0.0) return

        val currentStores = _uiState.value.nearbyStores
        if (currentStores.isEmpty()) return

        val results = FloatArray(1)
        val updatedStores = currentStores.map { store ->
            android.location.Location.distanceBetween(lat, lon, store.latitude, store.longitude, results)
            val distKm = (results[0] / 1000.0).let { Math.round(it * 100.0) / 100.0 }
            val formattedDist = if (results[0] < 1000f) {
                "${results[0].toInt()}m away"
            } else {
                String.format(java.util.Locale.US, "%.1f km away", distKm)
            }
            store.copy(distanceKm = distKm, distanceFormatted = formattedDist)
        }.sortedBy { it.distanceKm }

        val nearest = updatedStores.firstOrNull()
        val proximityStore = if (nearest != null) {
            android.location.Location.distanceBetween(lat, lon, nearest.latitude, nearest.longitude, results)
            val meters = results[0]
            if (meters <= 75f) Pair(nearest, meters) else null
        } else null

        _uiState.update {
            it.copy(
                nearbyStores = updatedStores,
                activeProximityStore = proximityStore?.first,
                proximityDistanceMeters = proximityStore?.second
            )
        }
    }

}
