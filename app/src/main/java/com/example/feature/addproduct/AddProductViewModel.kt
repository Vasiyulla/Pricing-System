package com.example.feature.addproduct

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.FakePriceBridgeRepository
import com.example.core.data.PriceBridgeRepository
import com.example.core.model.PriceStatusBadgeType
import com.example.core.model.Product
import com.example.core.model.StorePrice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class AddProductViewModel(
    initialBarcode: String? = null,
    private val repository: PriceBridgeRepository = FakePriceBridgeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddProductUiState(barcode = initialBarcode ?: "")
    )
    val uiState: StateFlow<AddProductUiState> = _uiState.asStateFlow()

    private var allProducts: List<Product> = emptyList()

    init {
        loadCategories()
        loadProducts()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val cats = repository.getCategories().firstOrNull() ?: emptyList()
            _uiState.update { it.copy(categories = cats.filter { c -> c.id != "all" }) }
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            allProducts = repository.getAllProducts().firstOrNull() ?: emptyList()
        }
    }

    fun onEvent(event: AddProductUiEvent) {
        when (event) {
            is AddProductUiEvent.OnNameChanged -> {
                val query = event.value.trim()
                val duplicates = if (query.length >= 3 && !_uiState.value.isDuplicateDismissed) {
                    allProducts.filter { prod ->
                        val prodTokens = prod.name.lowercase().split(" ").filter { it.length > 2 }
                        val queryTokens = query.lowercase().split(" ").filter { it.length > 2 }
                        prod.name.contains(query, ignoreCase = true) ||
                                prodTokens.any { pt -> queryTokens.any { qt -> pt.contains(qt) || qt.contains(pt) } }
                    }.take(3)
                } else {
                    emptyList()
                }
                _uiState.update { it.copy(name = event.value, potentialDuplicates = duplicates) }
            }
            is AddProductUiEvent.OnCategoryChanged -> _uiState.update { it.copy(categoryId = event.categoryId) }
            is AddProductUiEvent.OnPackageSizeChanged -> _uiState.update { it.copy(packageSize = event.value) }
            is AddProductUiEvent.OnMrpChanged -> _uiState.update { it.copy(mrpText = event.value) }
            is AddProductUiEvent.OnBarcodeChanged -> _uiState.update { it.copy(barcode = event.value) }
            is AddProductUiEvent.OnDismissDuplicateWarning -> {
                _uiState.update { it.copy(potentialDuplicates = emptyList(), isDuplicateDismissed = true) }
            }
            is AddProductUiEvent.OnSelectExistingDuplicate -> {
                _uiState.update { it.copy(createdProductId = event.product.id) }
            }
            is AddProductUiEvent.OnSubmitClicked -> submitProduct()
            else -> {}
        }
    }

    private fun submitProduct() {
        val state = _uiState.value
        if (!state.isValid) return

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val mrpVal = state.mrpText.toDoubleOrNull() ?: 0.0
                val productId = "prod_${UUID.randomUUID()}"
                val newProduct = Product(
                    id = productId,
                    name = state.name.trim(),
                    categoryId = state.categoryId,
                    quantityDescription = state.packageSize.trim(),
                    packageSizeBadge = state.packageSize.trim().take(8),
                    imageUrl = "",
                    bestStorePrice = StorePrice(
                        storeId = "nilgiris_indiranagar",
                        storeName = "Awaiting first submission",
                        distanceFormatted = "--",
                        price = mrpVal,
                        mrp = mrpVal,
                        lastObservedRelativeTime = "Just added",
                        sourceDescription = "Catalog entry",
                        statusBadge = PriceStatusBadgeType.CommunityReported,
                        isLowestPrice = true
                    ),
                    storesComparedCount = 1,
                    alternativePrices = emptyList(),
                    barcode = state.barcode.trim().takeIf { it.isNotBlank() }
                )

                repository.addProduct(newProduct)
                _uiState.update { it.copy(isSubmitting = false, createdProductId = productId) }
            } catch (ex: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = ex.localizedMessage ?: "Failed to save product"
                    )
                }
            }
        }
    }
}
