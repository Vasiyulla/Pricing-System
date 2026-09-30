package com.example.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.FakePriceBridgeRepository
import com.example.core.data.PriceBridgeRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val repository: PriceBridgeRepository = FakePriceBridgeRepository()
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val selectedCategoryId = MutableStateFlow("all")

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
        observeSearch()
    }

    fun onEvent(event: SearchUiEvent) {
        when (event) {
            is SearchUiEvent.OnQueryChanged -> {
                queryFlow.value = event.query
                _uiState.update { it.copy(query = event.query) }
            }
            is SearchUiEvent.OnClearQuery -> {
                queryFlow.value = ""
                _uiState.update { it.copy(query = "", results = emptyList()) }
            }
            is SearchUiEvent.OnCategorySelected -> {
                selectedCategoryId.value = event.categoryId
                _uiState.update { it.copy(selectedCategoryId = event.categoryId) }
            }
            is SearchUiEvent.OnRecentSearchClicked -> {
                queryFlow.value = event.query
                _uiState.update { it.copy(query = event.query) }
            }
            is SearchUiEvent.OnProductClicked -> {
                viewModelScope.launch {
                    if (queryFlow.value.isNotBlank()) {
                        repository.addRecentSearch(queryFlow.value)
                    }
                }
            }
            is SearchUiEvent.OnCompareStoresClicked -> {
                viewModelScope.launch {
                    if (queryFlow.value.isNotBlank()) {
                        repository.addRecentSearch(queryFlow.value)
                    }
                }
            }
            else -> { /* Navigation events handled by screen callbacks */ }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            combine(
                repository.getCategories(),
                repository.getRecentSearches()
            ) { categories, recents ->
                _uiState.update {
                    it.copy(
                        categories = categories,
                        recentSearches = recents
                    )
                }
            }.catch { /* ignore initial load errors */ }
                .collect {}
        }
    }

    private fun observeSearch() {
        viewModelScope.launch {
            combine(
                queryFlow.debounce(300),
                selectedCategoryId
            ) { query, categoryId ->
                Pair(query, categoryId)
            }.flatMapLatest { (query, categoryId) ->
                _uiState.update { it.copy(isLoading = query.isNotBlank()) }
                repository.searchProducts(query, categoryId)
            }.catch { ex ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = ex.localizedMessage ?: "Search failed"
                    )
                }
            }.collect { results ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        results = results,
                        errorMessage = null
                    )
                }
            }
        }
    }
}
