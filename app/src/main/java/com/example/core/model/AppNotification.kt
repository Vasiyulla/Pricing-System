package com.example.core.model

enum class NotificationType {
    PRICE_DROP,
    COMMUNITY_VERIFICATION,
    KARMA_REWARD,
    FRESHNESS_ALERT,
    SYSTEM_UPDATE
}

data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val timestampRelative: String,
    val type: NotificationType,
    val isRead: Boolean = false,
    val targetProductId: String? = null,
    val targetStoreId: String? = null
)
