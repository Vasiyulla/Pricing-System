package com.example.feature.search

import com.example.core.model.Category
import com.example.core.model.Product
import com.example.core.ui.BottomBarDestination

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<Product> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String = "all",
    val errorMessage: String? = null
) {
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && results.isEmpty() && query.isNotBlank()
    val isInitial: Boolean get() = query.isBlank() && results.isEmpty()
}

sealed interface SearchUiEvent {
    data class OnQueryChanged(val query: String) : SearchUiEvent
    data object OnClearQuery : SearchUiEvent
    data class OnCategorySelected(val categoryId: String) : SearchUiEvent
    data class OnProductClicked(val productId: String) : SearchUiEvent
    data class OnCompareStoresClicked(val productId: String) : SearchUiEvent
    data class OnRecentSearchClicked(val query: String) : SearchUiEvent
    data object OnBackClicked : SearchUiEvent
    data class OnBottomNavClicked(val destination: BottomBarDestination) : SearchUiEvent
}
