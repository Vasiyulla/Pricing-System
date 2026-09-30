package com.example.core.model

/**
 * A nearby retail store where prices can be reported.
 */
data class Store(
    val id: String,
    val name: String,
    val address: String,
    val distanceFormatted: String,
    val isOpen: Boolean = true,
    val reliabilityScore: Int = 96,
    val isStoreConfirmedPartner: Boolean = false
)
