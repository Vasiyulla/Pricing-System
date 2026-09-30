package com.example.core.model

/**
 * Represents a user list fetched from Google Tasks via read-only scope.
 */
data class GoogleTaskList(
    val id: String,
    val title: String,
    val taskCount: Int,
    val updatedRelativeTime: String = "Today"
)

/**
 * A single task from Google Tasks, paired with AI/fuzzy product matching.
 */
data class GoogleTaskItem(
    val id: String,
    val rawTitle: String,
    val isSelected: Boolean = true,
    val matchedProduct: Product? = null,
    val matchConfidence: Float = 0.92f, // 0.0 to 1.0 scale
    val suggestedQuantity: Int = 1,
    val suggestedUnit: String = "pack"
)

/**
 * Google Tasks OAuth integration connection state.
 */
data class GoogleTasksAuthState(
    val isConnected: Boolean = false,
    val connectedAccountEmail: String? = null,
    val selectedListId: String? = null
)
