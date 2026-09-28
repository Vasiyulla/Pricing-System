package com.example.feature.basket

import com.example.core.model.ShoppingBasket
import com.example.core.ui.BottomBarDestination

data class BasketUiState(
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val basket: ShoppingBasket? = null,
    val isAddItemDialogOpen: Boolean = false
) {
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && (basket == null || basket.items.isEmpty())
}

sealed interface BasketUiEvent {
    data class OnToggleItem(val itemId: String) : BasketUiEvent
    data class OnDeleteItem(val itemId: String) : BasketUiEvent
    data object OnOpenAddItemDialog : BasketUiEvent
    data object OnDismissAddItemDialog : BasketUiEvent
    data class OnConfirmAddItem(
        val name: String,
        val quantity: Int,
        val packageSize: String
    ) : BasketUiEvent
    data object OnRetryClicked : BasketUiEvent
    data object OnBackClicked : BasketUiEvent
    data class OnBottomNavClicked(val destination: BottomBarDestination) : BasketUiEvent
}
