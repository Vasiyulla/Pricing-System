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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.core.model.Product
import com.example.ui.theme.spacing

@Composable
fun ProductDealCard(
    product: Product,
    onProductClick: (String) -> Unit,
    onCompareStoresClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onProductClick(product.id) }
            .testTag("product_card_${product.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.spaceMd)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.spaceMd)
            ) {
                // Product Thumbnail with size badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(product.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = product.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(68.dp)
                            .padding(4.dp)
                    )

                    // Fallback icon in background if image takes time
                    if (product.imageUrl.isEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Size Tag Pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 4.dp, start = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = product.packageSizeBadge,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Details Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(76.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        // Storefront & Distance
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Store,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = product.bestStorePrice.storeName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "(${product.bestStorePrice.distanceFormatted})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Product Title
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        // Quantity Subtext
                        Text(
                            text = product.quantityDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Price & Savings Pill
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.price_format, product.bestStorePrice.price),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (product.bestStorePrice.mrp > product.bestStorePrice.price) {
                            Text(
                                text = stringResource(R.string.mrp_format, product.bestStorePrice.mrp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = TextDecoration.LineThrough,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .padding(bottom = 2.dp)
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.save_amount_percent,
                                        product.bestStorePrice.savingsAmount,
                                        product.bestStorePrice.discountPercent
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Meta & Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Verification status pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    PriceStatusBadgeComponent(status = product.bestStorePrice.statusBadge)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${stringResource(R.string.meta_dot)} ${product.bestStorePrice.lastObservedRelativeTime}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(MaterialTheme.spacing.spaceSm))

                // Compare Stores or View Store Action
                if (product.storesComparedCount > 1) {
                    Button(
                        onClick = { onCompareStoresClick(product.id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 6.dp
                        ),
                        modifier = Modifier.testTag("btn_compare_stores_${product.id}")
                    ) {
                        Text(
                            text = stringResource(R.string.compare_stores_action, product.storesComparedCount),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { onCompareStoresClick(product.id) },
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 6.dp
                        ),
                        modifier = Modifier.testTag("btn_view_store_${product.id}")
                    ) {
                        Text(
                            text = stringResource(R.string.btn_view_store),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
