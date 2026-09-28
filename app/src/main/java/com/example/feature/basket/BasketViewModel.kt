package com.example.feature.basket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.FakePriceBridgeRepository
import com.example.core.data.PriceBridgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BasketViewModel(
    private val repository: PriceBridgeRepository = FakePriceBridgeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BasketUiState(isLoading = true))
    val uiState: StateFlow<BasketUiState> = _uiState.asStateFlow()

    init {
        loadBasket()
    }

    fun onEvent(event: BasketUiEvent) {
        when (event) {
            is BasketUiEvent.OnToggleItem -> {
                viewModelScope.launch {
                    repository.toggleItemChecked(event.itemId)
                }
            }
            is BasketUiEvent.OnDeleteItem -> {
                viewModelScope.launch {
                    repository.deleteShoppingItem(event.itemId)
                }
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
                loadBasket()
            }
            else -> {
                // Navigation events handled by screen callbacks
            }
        }
    }

    private fun loadBasket() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.getShoppingBasket()
                .catch { ex ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = ex.localizedMessage ?: "Failed to load shopping basket"
                        )
                    }
                }
                .collect { basket ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            basket = basket
                        )
                    }
                }
        }
    }
}
