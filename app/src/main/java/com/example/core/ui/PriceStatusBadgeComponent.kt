package com.example.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.PriceStatusBadgeType
import com.example.ui.theme.spacing

/**
 * Reusable badge that adheres to community rules:
 * Shows relative status without claiming "guaranteed" or "verified price".
 */
@Composable
fun PriceStatusBadgeComponent(
    status: PriceStatusBadgeType,
    modifier: Modifier = Modifier
) {
    val (icon, backgroundColor, contentColor) = when (status) {
        PriceStatusBadgeType.CommunityCorroborated -> Triple(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        PriceStatusBadgeType.StoreConfirmed -> Triple(
            Icons.Default.Store,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        PriceStatusBadgeType.RecentlyObserved -> Triple(
            Icons.Default.Schedule,
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        PriceStatusBadgeType.CommunityReported -> Triple(
            Icons.Default.VerifiedUser,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        PriceStatusBadgeType.PriceMayBeOutdated -> Triple(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        PriceStatusBadgeType.FlaggedForReview -> Triple(
            Icons.Default.Info,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .testTag("status_badge_${status.name}")
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(MaterialTheme.spacing.spaceXs))
        Text(
            text = stringResource(id = status.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

/**
 * Store Reliability Badge (Phase 2 Data Quality):
 * Shows store pricing consistency percentage and warns about community reported discrepancies.
 */
@Composable
fun StoreReliabilityBadge(
    reliabilityScore: Int,
    discrepancyWarning: String? = null,
    modifier: Modifier = Modifier
) {
    if (discrepancyWarning != null) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Disputed",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    } else {
        val (tintColor, containerColor) = when {
            reliabilityScore >= 90 -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            reliabilityScore >= 75 -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = containerColor,
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "$reliabilityScore% Reliable",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tintColor
                )
            }
        }
    }
}
