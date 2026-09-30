package com.example.feature.submit

import com.example.core.model.Product
import com.example.core.model.SourceType
import com.example.core.model.Store

data class SubmitPriceUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,

    // Product selection
    val productQuery: String = "",
    val selectedProduct: Product? = null,
    val productSuggestions: List<Product> = emptyList(),
    val showProductSuggestions: Boolean = false,

    // Store selection
    val nearbyStores: List<Store> = emptyList(),
    val selectedStore: Store? = null,

    // Price fields
    val priceText: String = "",
    val mrpText: String = "",

    // Source
    val selectedSourceType: SourceType = SourceType.PersonalObservation,

    // Validation
    val validationError: String? = null,

    // Offline Resilience (Plan Sprint 6)
    val pendingOfflineCount: Int = 0,
    val isOfflineMode: Boolean = false
) {
    val canSubmit: Boolean
        get() = selectedProduct != null &&
                selectedStore != null &&
                priceText.toDoubleOrNull() != null &&
                priceText.toDoubleOrNull()!! > 0 &&
                !isSubmitting
}

sealed interface SubmitPriceUiEvent {
    data class OnProductQueryChanged(val query: String) : SubmitPriceUiEvent
    data class OnProductSelected(val product: Product) : SubmitPriceUiEvent
    data object OnClearProduct : SubmitPriceUiEvent
    data class OnStoreSelected(val store: Store) : SubmitPriceUiEvent
    data class OnPriceChanged(val price: String) : SubmitPriceUiEvent
    data class OnMrpChanged(val mrp: String) : SubmitPriceUiEvent
    data class OnSourceTypeSelected(val sourceType: SourceType) : SubmitPriceUiEvent
    data object OnSubmitClicked : SubmitPriceUiEvent
    data object OnBackClicked : SubmitPriceUiEvent
    data object OnDismissSuccess : SubmitPriceUiEvent
    data class OnToggleOfflineMode(val enabled: Boolean) : SubmitPriceUiEvent
    data object OnSyncOfflineQueue : SubmitPriceUiEvent
}
