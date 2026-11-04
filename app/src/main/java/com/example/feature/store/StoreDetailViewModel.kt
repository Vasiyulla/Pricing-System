package com.example.feature.store

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

class StoreDetailViewModel(
    private val storeId: String,
    private val repository: PriceBridgeRepository = FakePriceBridgeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoreDetailUiState(isLoading = true))
    val uiState: StateFlow<StoreDetailUiState> = _uiState.asStateFlow()

    init {
        loadStoreDetails()
    }

    fun handleEvent(event: StoreDetailUiEvent) {
        when (event) {
            is StoreDetailUiEvent.OnRequestPreVisitPriceCheck -> {
                viewModelScope.launch {
                    val success = repository.requestPreVisitPriceCheck(storeId)
                    if (success) {
                        _uiState.update { it.copy(isPreVisitRequestSent = true) }
                    }
                }
            }
            is StoreDetailUiEvent.OnDismissPreVisitSuccess -> {
                _uiState.update { it.copy(isPreVisitRequestSent = false) }
            }
            is StoreDetailUiEvent.OnOpenClaimSheet -> {
                _uiState.update { it.copy(isClaimSheetOpen = true) }
            }
            is StoreDetailUiEvent.OnDismissClaimSheet -> {
                _uiState.update { it.copy(isClaimSheetOpen = false) }
            }
            is StoreDetailUiEvent.OnSubmitClaim -> {
                viewModelScope.launch {
                    val success = repository.claimStore(storeId, event.merchantName, event.phone)
                    if (success) {
                        _uiState.update {
                            it.copy(
                                isClaimSheetOpen = false,
                                isClaimSuccess = true
                            )
                        }
                    }
                }
            }
            else -> { /* Navigation handled in UI */ }
        }
    }

    private fun loadStoreDetails() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            combine(
                repository.getStoreById(storeId),
                repository.getStorePrices(storeId)
            ) { store, prices ->
                StoreDetailUiState(
                    isLoading = false,
                    store = store,
                    storePrices = prices
                )
            }.catch { ex ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = ex.localizedMessage ?: "Failed to load store profile"
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
