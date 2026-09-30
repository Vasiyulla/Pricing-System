package com.example.feature.productdetail

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

class ProductDetailViewModel(
    private val productId: String,
    private val repository: PriceBridgeRepository = FakePriceBridgeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductDetailUiState(isLoading = true))
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    init {
        loadProductDetail()
    }

    fun onEvent(event: ProductDetailUiEvent) {
        when (event) {
            is ProductDetailUiEvent.OnRetryClicked -> loadProductDetail()
            is ProductDetailUiEvent.OnConfirmFreshness -> {
                viewModelScope.launch {
                    repository.confirmPriceFreshness(productId, event.storeId)
                    _uiState.update { it.copy(freshnessConfirmedStoreId = event.storeId) }
                }
            }
            is ProductDetailUiEvent.OnOpenDiscrepancySheet -> {
                _uiState.update {
                    it.copy(
                        isDiscrepancySheetOpen = true,
                        selectedStoreForDiscrepancy = event.storePrice
                    )
                }
            }
            is ProductDetailUiEvent.OnDismissDiscrepancySheet -> {
                _uiState.update {
                    it.copy(
                        isDiscrepancySheetOpen = false,
                        selectedStoreForDiscrepancy = null
                    )
                }
            }
            is ProductDetailUiEvent.OnSubmitDiscrepancy -> {
                viewModelScope.launch {
                    repository.reportDiscrepancy(
                        productId = productId,
                        storeId = event.storeId,
                        reason = event.reason,
                        notes = event.notes
                    )
                    _uiState.update {
                        it.copy(
                            isDiscrepancySheetOpen = false,
                            selectedStoreForDiscrepancy = null,
                            discrepancySubmittedSuccess = true
                        )
                    }
                }
            }
            is ProductDetailUiEvent.OnDismissDiscrepancySuccess -> {
                _uiState.update { it.copy(discrepancySubmittedSuccess = false) }
            }
            is ProductDetailUiEvent.OnAddToBasket -> {
                viewModelScope.launch {
                    repository.addShoppingItem(
                        name = event.product.name,
                        quantity = 1,
                        packageSize = event.product.quantityDescription
                    )
                    _uiState.update { it.copy(isAddedToBasket = true) }
                }
            }
            else -> { /* Navigation events handled by screen callbacks */ }
        }
    }

    private fun loadProductDetail() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            combine(
                repository.getProductById(productId),
                repository.getStorePricesForProduct(productId)
            ) { product, storePrices ->
                ProductDetailUiState(
                    isLoading = false,
                    product = product,
                    storePrices = storePrices
                )
            }.catch { ex ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = ex.localizedMessage ?: "Failed to load product details"
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
