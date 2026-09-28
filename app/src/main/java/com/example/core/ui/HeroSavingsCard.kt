package com.example.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.model.SmartBasketSummary
import com.example.ui.theme.spacing

@Composable
fun HeroSavingsCard(
    basketSummary: SmartBasketSummary,
    onViewBasketClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onViewBasketClick)
            .testTag("hero_savings_card")
    ) {
        Box(
            modifier = Modifier
                .background(gradientBrush)
                .padding(MaterialTheme.spacing.spaceMd)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Badge: Smart Basket Optimization
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    CircleShape
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Savings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.hero_badge_label),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(MaterialTheme.spacing.spaceSm))

                        // Savings headline
                        val titleText = buildAnnotatedString {
                            append(stringResource(R.string.hero_title_prefix))
                            append(" ")
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            ) {
                                append(stringResource(R.string.hero_savings_amount, basketSummary.estimatedSavingsAmount))
                            }
                            append(" ")
                            append(stringResource(R.string.hero_title_suffix))
                        }

                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = MaterialTheme.typography.titleLarge.lineHeight
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Subtext with stores compared
                        val storesSample = basketSummary.storeNamesSample.joinToString(", ")
                        Text(
                            text = stringResource(
                                R.string.hero_compared_info,
                                basketSummary.storesComparedCount,
                                storesSample,
                                basketSummary.basketItemCount,
                                basketSummary.basketName
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(MaterialTheme.spacing.spaceSm))

                    // Receipt icon in soft container
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp)
                            )
                            .background(
                                MaterialTheme.colorScheme.surfaceBright,
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.spaceMd))

                // Bottom Meta & Action Row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                )

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.spaceSm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.secondary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.hero_crowd_tags, basketSummary.storesComparedCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onViewBasketClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            contentColor = MaterialTheme.colorScheme.surfaceBright
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 14.dp,
                            vertical = 8.dp
                        ),
                        modifier = Modifier.testTag("btn_view_basket_deals")
                    ) {
                        Text(
                            text = stringResource(R.string.btn_view_basket_deals),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
