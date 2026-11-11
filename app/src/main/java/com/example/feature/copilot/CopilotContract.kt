package com.example.feature.copilot

import com.example.core.model.Product
import com.example.core.model.Store
import com.example.core.model.StorePrice

enum class MessageSender {
    USER, COPILOT
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val productCard: Product? = null,
    val storeCard: Store? = null,
    val storePrices: List<StorePrice> = emptyList(),
    val intentTag: String? = null,
    val confidencePercent: Int? = null
)

data class CopilotUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            sender = MessageSender.COPILOT,
            text = "Hello! I am your Price Bridge AI Copilot. I analyze live nearby supermarkets and local stores in real-time. Ask me to compare products, find cheap staples, or evaluate grocery deals!",
            intentTag = "WELCOME"
        )
    ),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val suggestedPrompts: List<String> = listOf(
        "Show my added products",
        "Summarize my basket",
        "Compare milk prices nearby",
        "Where is cooking oil cheapest?"
    )
)

sealed interface CopilotUiEvent {
    data class OnInputTextChanged(val text: String) : CopilotUiEvent
    data class OnSendMessage(val customText: String? = null) : CopilotUiEvent
    data class OnPromptChipClicked(val promptText: String) : CopilotUiEvent
    data class OnProductActionClicked(val productId: String) : CopilotUiEvent
    data class OnStoreActionClicked(val storeId: String) : CopilotUiEvent
    data object OnClearChat : CopilotUiEvent
}

