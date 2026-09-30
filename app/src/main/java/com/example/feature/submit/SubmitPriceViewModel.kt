package com.example.feature.submit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.FakePriceBridgeRepository
import com.example.core.data.PriceBridgeRepository
import com.example.core.model.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SubmitPriceViewModel(
    private val preselectedProductId: String? = null,
    private val repository: PriceBridgeRepository = FakePriceBridgeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubmitPriceUiState(isLoading = true))
    val uiState: StateFlow<SubmitPriceUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    fun onEvent(event: SubmitPriceUiEvent) {
        when (event) {
            is SubmitPriceUiEvent.OnProductQueryChanged -> {
                _uiState.update {
                    it.copy(
                        productQuery = event.query,
                        showProductSuggestions = event.query.isNotBlank()
                    )
                }
                filterProducts(event.query)
            }
            is SubmitPriceUiEvent.OnProductSelected -> {
                _uiState.update {
                    it.copy(
                        selectedProduct = event.product,
                        productQuery = event.product.name,
                        showProductSuggestions = false
                    )
                }
            }
            is SubmitPriceUiEvent.OnClearProduct -> {
                _uiState.update {
                    it.copy(
                        selectedProduct = null,
                        productQuery = "",
                        showProductSuggestions = false
                    )
                }
            }
            is SubmitPriceUiEvent.OnStoreSelected -> {
                _uiState.update { it.copy(selectedStore = event.store) }
            }
            is SubmitPriceUiEvent.OnPriceChanged -> {
                _uiState.update { it.copy(priceText = event.price, validationError = null) }
            }
            is SubmitPriceUiEvent.OnMrpChanged -> {
                _uiState.update { it.copy(mrpText = event.mrp, validationError = null) }
            }
            is SubmitPriceUiEvent.OnSourceTypeSelected -> {
                _uiState.update { it.copy(selectedSourceType = event.sourceType) }
            }
            is SubmitPriceUiEvent.OnSubmitClicked -> submitPrice()
            is SubmitPriceUiEvent.OnDismissSuccess -> {
                _uiState.update { it.copy(isSubmitted = false) }
            }
            is SubmitPriceUiEvent.OnToggleOfflineMode -> {
                _uiState.update { it.copy(isOfflineMode = event.enabled) }
            }
            is SubmitPriceUiEvent.OnSyncOfflineQueue -> {
                viewModelScope.launch {
                    repository.syncOfflineSubmissions()
                }
            }
            else -> { /* Navigation events handled by screen callbacks */ }
        }
    }

    private fun loadInitialData() {
        // Observe offline queue count
        viewModelScope.launch {
            repository.getPendingOfflineSubmissionCount().collect { count ->
                _uiState.update { it.copy(pendingOfflineCount = count) }
            }
        }

        viewModelScope.launch {
            combine(
                repository.getAllProducts(),
                repository.getNearbyStores()
            ) { products, stores ->
                val preselected = if (preselectedProductId != null) {
                    products.find { it.id == preselectedProductId }
                } else null

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        productSuggestions = products,
                        nearbyStores = stores,
                        selectedProduct = preselected,
                        productQuery = preselected?.name ?: ""
                    )
                }
            }.catch { ex ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = ex.localizedMessage ?: "Failed to load data"
                    )
                }
            }.collect {}
        }
    }

    private fun filterProducts(query: String) {
        val q = query.trim().lowercase()
        viewModelScope.launch {
            repository.getAllProducts()
                .catch { }
                .collect { all ->
                    val filtered = if (q.isBlank()) all else {
                        all.filter { it.name.lowercase().contains(q) }
                    }
                    _uiState.update { it.copy(productSuggestions = filtered) }
                }
        }
    }

    private fun submitPrice() {
        val state = _uiState.value
        val product = state.selectedProduct
        val store = state.selectedStore
        val price = state.priceText.toDoubleOrNull()
        val mrp = state.mrpText.toDoubleOrNull()

        // Validation
        if (product == null) {
            _uiState.update { it.copy(validationError = "Please select a product") }
            return
        }
        if (store == null) {
            _uiState.update { it.copy(validationError = "Please select a store") }
            return
        }
        if (price == null || price <= 0) {
            _uiState.update { it.copy(validationError = "Please enter a valid price") }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, validationError = null) }

        if (state.isOfflineMode) {
            viewModelScope.launch {
                repository.enqueueOfflinePrice(
                    productId = product.id,
                    storeId = store.id,
                    price = price,
                    mrp = mrp ?: 0.0,
                    sourceType = state.selectedSourceType.label
                )
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        isSubmitted = true,
                        // Reset form
                        selectedProduct = null,
                        productQuery = "",
                        selectedStore = null,
                        priceText = "",
                        mrpText = "",
                        selectedSourceType = SourceType.PersonalObservation
                    )
                }
            }
            return
        }

        viewModelScope.launch {
            try {
                repository.submitPrice(
                    productId = product.id,
                    storeId = store.id,
                    price = price,
                    mrp = mrp ?: 0.0,
                    sourceType = state.selectedSourceType.label
                )
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        isSubmitted = true,
                        // Reset form
                        selectedProduct = null,
                        productQuery = "",
                        selectedStore = null,
                        priceText = "",
                        mrpText = "",
                        selectedSourceType = SourceType.PersonalObservation
                    )
                }
            } catch (ex: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = ex.localizedMessage ?: "Submission failed"
                    )
                }
            }
        }
    }
}
