package com.example.core.model

enum class UserRole {
    CommunityShopper,
    ActiveContributor,
    StorePartner,
    AreaModerator
}

/**
 * User profile with karma, contribution stats, role, and DPDP consent status.
 */
data class UserProfile(
    val id: String,
    val displayName: String,
    val email: String,
    val phoneNumber: String? = null,
    val karmaPoints: Int,
    val pricesReported: Int,
    val pricesVerified: Int,
    val accuracyPercent: Int,
    val memberSince: String,
    val reputationLevel: String, // e.g. "Trusted Contributor", "Price Explorer"
    val role: UserRole = UserRole.ActiveContributor,
    val isDpdpConsentGiven: Boolean = true,
    val isGuest: Boolean = false
)
