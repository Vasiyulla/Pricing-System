package com.example.feature.store

import com.example.core.model.Store
import com.example.core.model.StorePrice

data class StoreDetailUiState(
    val isLoading: Boolean = false,
    val store: Store? = null,
    val storePrices: List<StorePrice> = emptyList(),
    val isPreVisitRequestSent: Boolean = false,
    val isClaimSheetOpen: Boolean = false,
    val isClaimSuccess: Boolean = false,
    val errorMessage: String? = null
) {
    val isEmpty: Boolean get() = !isLoading && store == null && errorMessage == null
}

sealed interface StoreDetailUiEvent {
    data object OnBackClicked : StoreDetailUiEvent
    data object OnRequestPreVisitPriceCheck : StoreDetailUiEvent
    data object OnDismissPreVisitSuccess : StoreDetailUiEvent
    data object OnOpenClaimSheet : StoreDetailUiEvent
    data object OnDismissClaimSheet : StoreDetailUiEvent
    data class OnSubmitClaim(val merchantName: String, val phone: String) : StoreDetailUiEvent
    data class OnProductClicked(val productId: String) : StoreDetailUiEvent
    data class OnReportDiscrepancy(val storePrice: StorePrice) : StoreDetailUiEvent
}
