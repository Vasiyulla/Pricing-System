package com.example.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.FakePriceBridgeRepository
import com.example.core.data.PriceBridgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: PriceBridgeRepository = FakePriceBridgeRepository()
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
                    repository.updateLocation(event.neighborhood, event.radiusKm)
                    isLocationSheetOpenState.value = false
                    _uiState.update { it.copy(isLocationSheetOpen = false) }
                }
            }
            is HomeUiEvent.OnSyncOfflineQueue -> {
                viewModelScope.launch {
                    repository.syncOfflineSubmissions()
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

        // Observe offline queue count
        viewModelScope.launch {
            repository.getPendingOfflineSubmissionCount().collect { count ->
                _uiState.update { it.copy(pendingOfflineCount = count) }
            }
        }
        viewModelScope.launch {
            combine(
                repository.getLocation(),
                repository.getSmartBasketSummary(),
                repository.getCategories(),
                selectedCategoryId.flatMapLatest { categoryId ->
                    repository.getBestDeals(categoryId)
                },
                repository.getKarmaPrompt()
            ) { location, basket, categories, deals, karma ->
                HomeUiState(
                    isLoading = false,
                    isOffline = isOfflineState.value,
                    errorMessage = errorMessageState.value,
                    location = location,
                    basketSummary = basket,
                    categories = categories,
                    selectedCategoryId = selectedCategoryId.value,
                    deals = deals,
                    karmaPrompt = karma,
                    isLocationSheetOpen = isLocationSheetOpenState.value
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
}
