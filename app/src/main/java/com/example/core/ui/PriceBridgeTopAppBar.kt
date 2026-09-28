package com.example.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.model.LocationInfo
import com.example.ui.theme.spacing

@Composable
fun PriceBridgeTopAppBar(
    location: LocationInfo,
    onLocationClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onBarcodeScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.spacing.spaceMd,
                    vertical = MaterialTheme.spacing.spaceSm
                )
        ) {
            // Location Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = MaterialTheme.spacing.spaceSm)
            ) {
                // Location Icon Circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(MaterialTheme.spacing.spaceSm))

                // Location details clickable target
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onLocationClick)
                        .testTag("location_selector_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = location.neighborhood,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.cd_location_picker),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        // Radius Tag
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = location.radiusDescription,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.width(MaterialTheme.spacing.spaceSm))

                        // Live pulse dot
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alpha"
                        )

                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .alpha(alpha)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.live_prices_indicator),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Notification Bell Button
                Box(contentAlignment = Alignment.TopEnd) {
                    IconButton(
                        onClick = onNotificationsClick,
                        modifier = Modifier
                            .size(MaterialTheme.spacing.minTouchTarget)
                            .testTag("notification_button")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    CircleShape
                                )
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainer,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = stringResource(R.string.cd_notifications),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // Unread badge dot
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp, end = 8.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                    )
                }
            }

            // Search Bar affordance
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onSearchClick)
                    .testTag("home_search_bar")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(MaterialTheme.spacing.spaceSm))
                    Text(
                        text = stringResource(R.string.search_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Quick scan barcode shortcut
                    IconButton(
                        onClick = onBarcodeScanClick,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                RoundedCornerShape(8.dp)
                            )
                            .testTag("quick_barcode_scan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.cd_scan_barcode),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
