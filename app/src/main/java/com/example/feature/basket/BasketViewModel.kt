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
            is BasketUiEvent.OnOpenGoogleTasksSheet -> {
                _uiState.update { it.copy(isGoogleTasksSheetOpen = true) }
                loadGoogleTasksForSelectedList()
            }
            is BasketUiEvent.OnDismissGoogleTasksSheet -> {
                _uiState.update { it.copy(isGoogleTasksSheetOpen = false) }
            }
            is BasketUiEvent.OnConnectGoogleTasks -> {
                viewModelScope.launch {
                    repository.connectGoogleTasks(event.email)
                    loadGoogleTasksForSelectedList()
                }
            }
            is BasketUiEvent.OnDisconnectGoogleTasks -> {
                viewModelScope.launch {
                    repository.disconnectGoogleTasks()
                }
            }
            is BasketUiEvent.OnSelectGoogleTaskList -> {
                _uiState.update {
                    it.copy(
                        googleTasksAuthState = it.googleTasksAuthState.copy(selectedListId = event.listId)
                    )
                }
                loadGoogleTasksForSelectedList(event.listId)
            }
            is BasketUiEvent.OnImportGoogleTasks -> {
                viewModelScope.launch {
                    val count = repository.importGoogleTasks(event.selectedTasks)
                    _uiState.update {
                        it.copy(
                            isGoogleTasksSheetOpen = false,
                            googleTasksImportedCount = count,
                            shareSuccessMessage = "Successfully imported $count items from Google Tasks!"
                        )
                    }
                }
            }
            is BasketUiEvent.OnDismissImportSuccessToast -> {
                _uiState.update { it.copy(googleTasksImportedCount = null) }
            }
            is BasketUiEvent.OnRetryClicked -> {
                loadData()
            }
            else -> {
                // Navigation events handled by screen callbacks
            }
        }
    }

    private fun loadGoogleTasksForSelectedList(listId: String? = null) {
        val targetListId = listId
            ?: _uiState.value.googleTasksAuthState.selectedListId
            ?: _uiState.value.availableGoogleTaskLists.firstOrNull()?.id
            ?: "list_groceries"
        viewModelScope.launch {
            repository.getGoogleTasksForList(targetListId).collect { tasks ->
                _uiState.update { it.copy(googleTasksInSelectedList = tasks) }
            }
        }
    }

    private fun loadData() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            combine(
                repository.getShoppingBasket(),
                repository.getSavedShoppingLists(),
                repository.getPriceWatchAlerts(),
                repository.getGoogleTasksAuthState(),
                repository.getGoogleTaskLists()
            ) { basket, savedLists, alerts, googleAuth, googleLists ->
                _uiState.value.copy(
                    isLoading = false,
                    basket = basket,
                    savedLists = savedLists,
                    priceWatchAlerts = alerts,
                    googleTasksAuthState = googleAuth,
                    availableGoogleTaskLists = googleLists
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
                if (state.googleTasksInSelectedList.isEmpty()) {
                    loadGoogleTasksForSelectedList(state.googleTasksAuthState.selectedListId)
                }
            }
        }
    }
}

