package com.example.core.model

import androidx.annotation.StringRes
import com.example.R

/**
 * Strict product status badges as per crowdsourced guidelines.
 * Never use wording like "guaranteed" or "verified price".
 */
enum class PriceStatusBadgeType(
    @StringRes val labelRes: Int,
    val isWarning: Boolean = false
) {
    CommunityReported(R.string.status_community_reported),
    RecentlyObserved(R.string.status_recently_observed),
    CommunityCorroborated(R.string.status_community_corroborated),
    StoreConfirmed(R.string.status_store_confirmed),
    PriceMayBeOutdated(R.string.status_price_may_be_outdated, isWarning = true),
    FlaggedForReview(R.string.status_flagged_for_review, isWarning = true)
}
