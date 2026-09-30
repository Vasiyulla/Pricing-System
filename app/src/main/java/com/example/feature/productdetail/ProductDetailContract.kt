package com.example.feature.productdetail

import com.example.core.model.Product
import com.example.core.model.StorePrice

data class ProductDetailUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val product: Product? = null,
    val storePrices: List<StorePrice> = emptyList(),
    val isDiscrepancySheetOpen: Boolean = false,
    val selectedStoreForDiscrepancy: StorePrice? = null,
    val discrepancySubmittedSuccess: Boolean = false,
    val freshnessConfirmedStoreId: String? = null,
    val isAddedToBasket: Boolean = false
) {
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && product == null
}

sealed interface ProductDetailUiEvent {
    data object OnBackClicked : ProductDetailUiEvent
    data object OnSubmitPriceClicked : ProductDetailUiEvent
    data class OnStoreClicked(val storeId: String) : ProductDetailUiEvent
    data object OnShareClicked : ProductDetailUiEvent
    data object OnRetryClicked : ProductDetailUiEvent
    data class OnConfirmFreshness(val storeId: String) : ProductDetailUiEvent
    data class OnOpenDiscrepancySheet(val storePrice: StorePrice) : ProductDetailUiEvent
    data object OnDismissDiscrepancySheet : ProductDetailUiEvent
    data class OnSubmitDiscrepancy(val storeId: String, val reason: String, val notes: String?) : ProductDetailUiEvent
    data object OnDismissDiscrepancySuccess : ProductDetailUiEvent
    data class OnAddToBasket(val product: Product) : ProductDetailUiEvent
}
