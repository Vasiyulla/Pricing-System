package com.example.feature.copilot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.PriceBridgeRepository
import com.example.core.data.PriceBridgeRepositoryProvider
import com.example.core.model.Product
import com.example.core.model.Store
import com.example.core.model.StorePrice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PriceCopilotViewModel(
    private val repository: PriceBridgeRepository = PriceBridgeRepositoryProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CopilotUiState())
    val uiState: StateFlow<CopilotUiState> = _uiState.asStateFlow()

    fun onEvent(event: CopilotUiEvent) {
        when (event) {
            is CopilotUiEvent.OnInputTextChanged -> {
                _uiState.update { it.copy(inputText = event.text) }
            }
            is CopilotUiEvent.OnSendMessage -> {
                val query = event.customText ?: _uiState.value.inputText
                if (query.isNotBlank()) {
                    sendMessage(query.trim())
                }
            }
            is CopilotUiEvent.OnPromptChipClicked -> {
                val clean = event.promptText.replace("""^[^\w]+""".toRegex(), "").trim()
                sendMessage(clean)
            }
            is CopilotUiEvent.OnClearChat -> {
                _uiState.update {
                    it.copy(
                        messages = listOf(
                            ChatMessage(
                                sender = MessageSender.COPILOT,
                                text = "Chat cleared. What products would you like to compare?",
                                intentTag = "RESET"
                            )
                        )
                    )
                }
            }
            else -> {}
        }
    }

    private fun sendMessage(query: String) {
        val userMsg = ChatMessage(
            sender = MessageSender.USER,
            text = query
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                inputText = "",
                isGenerating = true
            )
        }

        viewModelScope.launch {
            val response = processGroundedQuery(query)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        messages = it.messages + response,
                        isGenerating = false
                    )
                }
            }
        }
    }

    /**
     * Grounded RAG Query Processor.
     * Answers strictly from catalog, user contributions, basket state, and nearby store prices.
     */
    private suspend fun processGroundedQuery(query: String): ChatMessage = withContext(Dispatchers.IO) {
        val lower = query.lowercase().trim()

        // 1. User-added products / contributions query
        if (lower.contains("my product") ||
            lower.contains("added") ||
            lower.contains("contributed") ||
            lower.contains("submitted")
        ) {
            val myProducts = repository.getMyAddedProducts().firstOrNull().orEmpty()
            return@withContext if (myProducts.isEmpty()) {
                ChatMessage(
                    sender = MessageSender.COPILOT,
                    text = "You haven't added any products yet. Tap '+ Add Product' on Home or Profile to add one to the catalog.",
                    intentTag = "MY_PRODUCTS",
                    confidencePercent = null
                )
            } else {
                val listText = myProducts.take(8).joinToString("\n") { p ->
                    val mrp = p.bestStorePrice.mrp
                    val mrpSuffix = if (mrp > 0.0) String.format(java.util.Locale.UK, " · MRP £%.2f", mrp) else ""
                    "• ${p.name} (${p.quantityDescription}$mrpSuffix)"
                }
                val extra = if (myProducts.size > 8) "\n...and ${myProducts.size - 8} more in your Profile." else ""
                ChatMessage(
                    sender = MessageSender.COPILOT,
                    text = "You have added ${myProducts.size} ${if (myProducts.size == 1) "product" else "products"} on Price Bridge so far:\n\n$listText$extra",
                    productCard = myProducts.firstOrNull(),
                    intentTag = "MY_PRODUCTS",
                    confidencePercent = 100
                )
            }
        }

        // 2. Shopping basket summary query
        if (lower.contains("basket") || lower.contains("shopping list") || lower.contains("my list")) {
            val basket = repository.getShoppingBasket().firstOrNull()
            val items = basket?.items.orEmpty()
            val totalQty = items.sumOf { it.quantity }
            val totalEst = items.sumOf { it.estimatedItemTotal }
            val summaryText = if (items.isEmpty()) {
                "Your shopping basket is currently empty. Tap '+ Add Item' on Home to add groceries."
            } else if (totalEst > 0.0) {
                val lines = items.take(5).joinToString("\n") { item ->
                    "• ${item.productName} ×${item.quantity} (${item.packageSize})"
                }
                "Your basket has $totalQty ${if (totalQty == 1) "item" else "items"} with an estimated total of £${String.format(java.util.Locale.UK, "%.2f", totalEst)}:\n\n$lines"
            } else {
                val lines = items.take(5).joinToString("\n") { item ->
                    "• ${item.productName} ×${item.quantity} (${item.packageSize})"
                }
                "Your basket has $totalQty ${if (totalQty == 1) "item" else "items"} (no local store prices matched near your current location yet):\n\n$lines"
            }
            return@withContext ChatMessage(
                sender = MessageSender.COPILOT,
                text = summaryText,
                intentTag = "BASKET_SUMMARY",
                confidencePercent = if (items.isNotEmpty() && totalEst > 0.0) 95 else null
            )
        }

        // 3. Extract product keyword & query catalog + user-added products
        val keyword = when {
            lower.contains("milk") -> "milk"
            lower.contains("bread") -> "bread"
            lower.contains("butter") -> "butter"
            lower.contains("oil") -> "oil"
            lower.contains("egg") -> "eggs"
            lower.contains("tea") -> "tea"
            lower.contains("flour") || lower.contains("atta") -> "atta"
            lower.contains("rice") -> "rice"
            lower.contains("chicken") -> "chicken"
            lower.contains("sugar") -> "sugar"
            else -> query.split(" ").firstOrNull { it.length > 2 } ?: query.trim()
        }

        val searchResults = if (keyword.isNotBlank()) {
            repository.searchProducts(keyword, null).firstOrNull().orEmpty()
        } else emptyList()
        val myAddedMatch = repository.getMyAddedProducts().firstOrNull().orEmpty()
            .firstOrNull { it.name.contains(keyword, ignoreCase = true) }
        val matchedProduct: Product? = searchResults.firstOrNull() ?: myAddedMatch
        val nearbyStores: List<Store> = repository.getNearbyStores().firstOrNull().orEmpty()

        if (matchedProduct != null) {
            val storePrices: List<StorePrice> = repository.getStorePricesForProduct(matchedProduct.id).firstOrNull().orEmpty()

            if (storePrices.isEmpty()) {
                val mrp = matchedProduct.bestStorePrice.mrp
                val mrpNote = if (mrp > 0.0) String.format(java.util.Locale.UK, " (Target MRP £%.2f)", mrp) else ""
                ChatMessage(
                    sender = MessageSender.COPILOT,
                    text = "Found ${matchedProduct.name} (${matchedProduct.quantityDescription})$mrpNote in the catalog, but there are no tracked store prices near your current location yet.",
                    productCard = matchedProduct,
                    storePrices = emptyList(),
                    intentTag = "CATALOG_ONLY",
                    confidencePercent = null
                )
            } else {
                val bestDeal = storePrices.minByOrNull { it.price }
                val worstDeal = storePrices.maxByOrNull { it.price }
                val savings = if (bestDeal != null && worstDeal != null && worstDeal.price > bestDeal.price) {
                    worstDeal.price - bestDeal.price
                } else 0.0

                val formattedText = buildString {
                    append("Prices for ${matchedProduct.name} across ${storePrices.size} nearby ${if (storePrices.size == 1) "store" else "stores"}:\n\n")
                    if (bestDeal != null) {
                        append(
                            String.format(
                                java.util.Locale.UK,
                                "Best Deal: %s%.2f at %s (%s)\n",
                                bestDeal.currencySymbol,
                                bestDeal.price,
                                bestDeal.storeName,
                                bestDeal.distanceFormatted
                            )
                        )
                    }
                    if (savings > 0.0) {
                        append(
                            String.format(
                                java.util.Locale.UK,
                                "Potential Savings: %s%.2f compared to %s\n\n",
                                bestDeal?.currencySymbol ?: "£",
                                savings,
                                worstDeal?.storeName ?: "other shops"
                            )
                        )
                    }
                    append("Based on recent community price observations.")
                }

                ChatMessage(
                    sender = MessageSender.COPILOT,
                    text = formattedText,
                    productCard = matchedProduct,
                    storePrices = storePrices,
                    intentTag = "PRICE_COMPARISON",
                    confidencePercent = 96
                )
            }
        } else if (nearbyStores.isNotEmpty()) {
            val storeList = nearbyStores.take(3).joinToString("\n") { "• ${it.name} (${it.distanceFormatted})" }
            ChatMessage(
                sender = MessageSender.COPILOT,
                text = "I couldn't find any product matching \"$query\" in the catalog.\n\nNearby active stores:\n$storeList\n\nTry adding \"$query\" from Catalog or ask: 'Compare milk prices' or 'Show my added products'.",
                intentTag = "NOT_FOUND_WITH_STORES",
                confidencePercent = null
            )
        } else {
            ChatMessage(
                sender = MessageSender.COPILOT,
                text = "I couldn't find \"$query\" in the catalog yet, and there are no tracked stores within range of your current location.\n\nYou can add \"$query\" using '+ Add Product' on Home or Profile, or ask me: 'Show my added products'.",
                intentTag = "NO_LOCAL_COVERAGE",
                confidencePercent = null
            )
        }
    }
}

