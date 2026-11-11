package com.example.feature.basket

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.data.PriceBridgeRepositoryProvider
import com.example.core.network.ApiClient
import com.example.core.network.NetworkBasketItem
import com.example.core.network.NetworkBasketOptimizeRequest
import com.example.core.network.NetworkTwoStopBasketResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoStopBasketScreen(
    onNavigateBack: () -> Unit
) {
    val repository = PriceBridgeRepositoryProvider.repository
    val basket by repository.getShoppingBasket()
        .collectAsState(initial = null)

    var optimizedResult by remember { mutableStateOf<NetworkTwoStopBasketResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var emptyReason by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(basket?.items) {
        withContext(Dispatchers.IO) {
            isLoading = true
            try {
                val loc = repository.getLocation().firstOrNull()
                val lat = loc?.latitude ?: 0.0
                val lon = loc?.longitude ?: 0.0
                val basketItems = basket?.items.orEmpty()

                if (basketItems.isEmpty()) {
                    optimizedResult = null
                    emptyReason = "Your shopping basket is empty. Add items on Home first to compare nearby store totals."
                    return@withContext
                }
                if (lat == 0.0 && lon == 0.0) {
                    optimizedResult = null
                    emptyReason = "Waiting for GPS or a selected area with tracked nearby stores."
                    return@withContext
                }

                val reqItems = basketItems.mapNotNull { item ->
                    val matchedProd = repository.searchProducts(item.productName, null).firstOrNull()?.firstOrNull()
                    matchedProd?.let {
                        NetworkBasketItem(
                            productId = it.id,
                            productName = item.productName,
                            quantity = item.quantity
                        )
                    }
                }

                if (reqItems.isEmpty()) {
                    optimizedResult = null
                    emptyReason = "None of your basket items match tracked store price records near this location yet."
                } else {
                    optimizedResult = ApiClient.service.optimizeBasket(
                        NetworkBasketOptimizeRequest(
                            items = reqItems,
                            userLat = lat,
                            userLon = lon
                        )
                    )
                    emptyReason = if (optimizedResult == null) {
                        "Not enough nearby tracked store coverage to calculate a multi-store split here yet."
                    } else null
                }
            } catch (_: Exception) {
                optimizedResult = null
                emptyReason = "Unable to compute nearby store comparison at your current location."
            } finally {
                isLoading = false
            }
        }
    }

    val result = optimizedResult
    val singleStoreTotal = result?.let { it.singleStoreBestPence / 100.0 } ?: 0.0
    val optimizedTotal = result?.let { it.combinedTotalPence / 100.0 } ?: 0.0
    val rawSavings = result?.let { it.netSavingsPence / 100.0 } ?: 0.0
    val extraWalkMeters = result?.walkingDistanceMeters ?: 0
    val extraWalkMins = (result?.estimatedExtraMinutes ?: 1).coerceAtLeast(1)
    val hourlySavingsRate = if (extraWalkMins > 0 && rawSavings > 0.0) (rawSavings / (extraWalkMins / 60.0)) else 0.0

    val store1Name = result?.store1?.name ?: "Store 1"
    val store1Total = result?.let { it.store1TotalPence / 100.0 } ?: 0.0
    val store1ItemsCount = result?.store1Items?.size ?: 0
    val store2Name = result?.store2?.name ?: "Store 2"
    val store2Total = result?.let { it.store2TotalPence / 100.0 } ?: 0.0
    val store2ItemsCount = result?.store2Items?.size ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("2-Stop Store Comparison", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (result == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = if (isLoading) "Checking nearby stores…" else "No store comparison available here",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = emptyReason ?: "Add basket items in an area with tracked stores to compare store totals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Net Savings Hero Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Save £%.2f on this Shop!".format(rawSavings),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "By splitting items across 2 nearby stores instead of paying single-store premium.",
                            color = Color(0xFFE8F5E9),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Walking detour & Hourly Savings Metric Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Walking Detour", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("+${extraWalkMeters}m (${extraWalkMins} mins)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ElectricBolt,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Effective Hourly Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("£%.2f / hr".format(hourlySavingsRate), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        }
                    }
                }
            }

            // Single Store vs 2-Store Split Comparison
            Text(
                text = "PARETO FRONTIER COMPARISON",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )

            // Option 1: Single Store (Convenience)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("1-Store Convenience", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("Buy all items at primary store", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Text("£%.2f".format(singleStoreTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Option 2: 2-Store Pareto Split (Recommended)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("2-Store Pareto Split", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text("RECOMMENDED", color = MaterialTheme.colorScheme.onPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                            Text("Optimized across 2 nearby stores", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("£%.2f".format(optimizedTotal), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Save £%.2f".format(rawSavings), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Store A Split Breakdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (store1ItemsCount > 0) "Stop 1: $store1Name ($store1ItemsCount items)" else "Stop 1: $store1Name",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text("£%.2f".format(store1Total), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(6.dp))

                    // Store B Split Breakdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (store2ItemsCount > 0) "Stop 2: $store2Name ($store2ItemsCount items)" else "Stop 2: $store2Name",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text("£%.2f".format(store2Total), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }
    }
}
