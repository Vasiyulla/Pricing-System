package com.example.feature.basket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.FakePriceBridgeRepository
import com.example.core.data.PriceBridgeRepository
import com.example.core.model.ShoppingTaskTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BasketViewModel(
    private val repository: PriceBridgeRepository = FakePriceBridgeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BasketUiState(isLoading = true))
    val uiState: StateFlow<BasketUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun onEvent(event: BasketUiEvent) {
        when (event) {
            is BasketUiEvent.OnSelectTab -> {
                _uiState.update { it.copy(selectedTab = event.tab) }
            }
            is BasketUiEvent.OnToggleItem -> {
                viewModelScope.launch {
                    repository.toggleItemChecked(event.itemId)
                }
            }
            is BasketUiEvent.OnUpdateItemQuantity -> {
                viewModelScope.launch {
                    repository.updateItemQuantity(event.itemId, event.newQuantity)
                }
            }
            is BasketUiEvent.OnDeleteItem -> {
                viewModelScope.launch {
                    repository.deleteShoppingItem(event.itemId)
                }
            }
            is BasketUiEvent.OnClearCheckedItems -> {
                viewModelScope.launch {
                    repository.clearCheckedItems()
                }
            }
            is BasketUiEvent.OnSelectRecommendation -> {
                _uiState.update { it.copy(selectedRecommendationIndex = event.index) }
            }
            is BasketUiEvent.OnLoadSavedList -> {
                viewModelScope.launch {
                    repository.loadSavedListIntoActiveBasket(event.listId)
                    _uiState.update {
                        it.copy(
                            selectedTab = ShoppingTaskTab.ActiveBasket,
                            shareSuccessMessage = "Loaded list into Active Basket!"
                        )
                    }
                }
            }
            is BasketUiEvent.OnShareListClicked -> {
                val basket = _uiState.value.basket
                if (basket != null && basket.items.isNotEmpty()) {
                    val summary = buildString {
                        append("🛒 Price Bridge Shopping Task\n")
                        append("Estimated Total: ₹${basket.optimizedEstimatedTotal.toInt()} (Save ₹${basket.estimatedSavingsAmount.toInt()})\n\n")
                        basket.items.forEachIndexed { i, item ->
                            append("${i + 1}. ${item.productName} (${item.packageSize}) x${item.quantity} — Best at ${item.bestStoreName} (₹${item.estimatedItemTotal.toInt()})\n")
                        }
                        append("\nCompare nearby prices on Price Bridge")
                    }
                    _uiState.update { it.copy(shareSuccessMessage = "Shopping list copied! Ready to share.") }
                }
            }
            is BasketUiEvent.OnDismissShareToast -> {
                _uiState.update { it.copy(shareSuccessMessage = null) }
            }
            is BasketUiEvent.OnOpenAddItemDialog -> {
                _uiState.update { it.copy(isAddItemDialogOpen = true) }
            }
            is BasketUiEvent.OnDismissAddItemDialog -> {
                _uiState.update { it.copy(isAddItemDialogOpen = false) }
            }
            is BasketUiEvent.OnConfirmAddItem -> {
                viewModelScope.launch {
                    repository.addShoppingItem(
                        name = event.name,
                        quantity = event.quantity,
                        packageSize = event.packageSize
                    )
                    _uiState.update { it.copy(isAddItemDialogOpen = false) }
                }
            }
            is BasketUiEvent.OnRetryClicked -> {
                loadData()
            }
            else -> {
                // Navigation events handled by screen callbacks
            }
        }
    }

    private fun loadData() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            combine(
                repository.getShoppingBasket(),
                repository.getSavedShoppingLists(),
                repository.getPriceWatchAlerts()
            ) { basket, savedLists, alerts ->
                _uiState.value.copy(
                    isLoading = false,
                    basket = basket,
                    savedLists = savedLists,
                    priceWatchAlerts = alerts
                )
            }.catch { ex ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = ex.localizedMessage ?: "Failed to load shopping tasks"
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
