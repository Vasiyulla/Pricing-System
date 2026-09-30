package com.example.feature.addproduct

import com.example.core.model.Category
import com.example.core.model.Product

data class AddProductUiState(
    val name: String = "",
    val categoryId: String = "groceries",
    val packageSize: String = "",
    val mrpText: String = "",
    val barcode: String = "",
    val categories: List<Category> = emptyList(),
    val isSubmitting: Boolean = false,
    val createdProductId: String? = null,
    val potentialDuplicates: List<Product> = emptyList(),
    val isDuplicateDismissed: Boolean = false,
    val errorMessage: String? = null
) {
    val isValid: Boolean
        get() = name.trim().isNotBlank() &&
                packageSize.trim().isNotBlank() &&
                (mrpText.toDoubleOrNull() ?: 0.0) > 0.0
}

sealed interface AddProductUiEvent {
    data object OnBackClicked : AddProductUiEvent
    data class OnNameChanged(val value: String) : AddProductUiEvent
    data class OnCategoryChanged(val categoryId: String) : AddProductUiEvent
    data class OnPackageSizeChanged(val value: String) : AddProductUiEvent
    data class OnMrpChanged(val value: String) : AddProductUiEvent
    data class OnBarcodeChanged(val value: String) : AddProductUiEvent
    data object OnDismissDuplicateWarning : AddProductUiEvent
    data class OnSelectExistingDuplicate(val product: Product) : AddProductUiEvent
    data object OnSubmitClicked : AddProductUiEvent
}
