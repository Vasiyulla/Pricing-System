package com.example.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.data.PriceBridgeRepositoryProvider
import com.example.core.network.ApiClient
import com.example.core.network.NetworkProduct
import com.example.core.network.NetworkStorePriceComparison
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Trip Preset definition for curated London shopping baskets.
 */
data class QuickTripPack(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val items: List<String>
)

val LONDON_QUICK_TRIPS = listOf(
    QuickTripPack(
        id = "trip_staples",
        emoji = "🥛",
        title = "Daily Staples",
        description = "Milk, Bread, Butter & Eggs",
        items = listOf("Milk", "Bread", "Lurpak", "Eggs")
    ),
    QuickTripPack(
        id = "trip_dinner",
        emoji = "🍗",
        title = "Dinner Tonight",
        description = "Chicken, Pasta & Cheddar",
        items = listOf("Chicken", "Cheddar", "Pasta")
    ),
    QuickTripPack(
        id = "trip_breakfast",
        emoji = "☕",
        title = "Breakfast & Brew",
        description = "Nescafe Gold & Fresh Loaf",
        items = listOf("Nescafe", "Bread", "Milk")
    ),
    QuickTripPack(
        id = "trip_clearance",
        emoji = "🏷️",
        title = "Yellow Stickers",
        description = "Clearance deals near me",
        items = listOf("Clearance")
    )
)

data class StoreTripEstimate(
    val storeId: String,
    val storeName: String,
    val distanceFormatted: String,
    val totalPence: Int,
    val matchedCount: Int,
    val totalItems: Int,
    val loyaltyScheme: String
)

/**
 * Interactive Hero Component answering:
 * "What are you purchasing today?"
 *
 * Prompts the user upon entering the app, queries the live backend comparison API,
 * and shows immediate nearby store rankings with walking distances and split savings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShoppingIntentHero(
    onNavigateToTwoStopBasket: () -> Unit,
    onNavigateToYellowStickers: () -> Unit,
    onNavigateToSearch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedPackId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var comparisonResults by remember { mutableStateOf<List<StoreTripEstimate>>(emptyList()) }
    var activeTripName by remember { mutableStateOf("") }
    var activeTripItemNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var addedToBasketSuccess by remember { mutableStateOf(false) }

    fun runTripSearch(pack: QuickTripPack) {
        if (pack.id == "trip_clearance") {
            onNavigateToYellowStickers()
            return
        }

        selectedPackId = pack.id
        activeTripName = pack.title
        activeTripItemNames = pack.items
        isLoading = true
        comparisonResults = emptyList()
        addedToBasketSuccess = false

        scope.launch {
            try {
                val estimates = withContext(Dispatchers.IO) {
                    val storeTotals = mutableMapOf<String, Triple<String, String, Int>>() // storeId -> (name, dist, totalPence)
                    val storeSchemes = mutableMapOf<String, String>()
                    val storeCounts = mutableMapOf<String, Int>()

                    val activeLoc = runCatching { PriceBridgeRepositoryProvider.repository.getLocation().firstOrNull() }.getOrNull()
                    val activeLat = activeLoc?.latitude?.takeIf { it != 0.0 } ?: 51.5390
                    val activeLon = activeLoc?.longitude?.takeIf { it != 0.0 } ?: -0.1426

                    for (term in pack.items) {
                        val prods = ApiClient.service.searchProducts(term)
                        val prod = prods.firstOrNull() ?: continue
                        val comps = ApiClient.service.compareProductPrices(prod.id, activeLat, activeLon)
                        for (comp in comps) {
                            val dist = if (comp.store.distanceKm != null) {
                                val km = comp.store.distanceKm
                                val mins = (km * 12).toInt().coerceAtLeast(1)
                                if (km < 1.0) "${(km * 1000).toInt()}m · ${mins}m walk" else "%.1f km · ${mins}m walk".format(km)
                            } else {
                                "Nearby"
                            }
                            val prev = storeTotals[comp.store.id]
                            val newTotal = (prev?.third ?: 0) + comp.effectivePricePence
                            storeTotals[comp.store.id] = Triple(comp.store.name, dist, newTotal)
                            storeCounts[comp.store.id] = (storeCounts[comp.store.id] ?: 0) + 1
                            if (comp.loyaltyScheme != "NONE") {
                                storeSchemes[comp.store.id] = comp.loyaltyScheme
                            }
                        }
                    }

                    storeTotals.map { (id, trip) ->
                        StoreTripEstimate(
                            storeId = id,
                            storeName = trip.first,
                            distanceFormatted = trip.second,
                            totalPence = trip.third,
                            matchedCount = storeCounts[id] ?: 0,
                            totalItems = pack.items.size,
                            loyaltyScheme = storeSchemes[id] ?: "NONE"
                        )
                    }.sortedBy { it.totalPence }
                }
                comparisonResults = estimates
            } catch (_: Exception) {
                comparisonResults = getFallbackTripEstimates(pack.id)
            } finally {
                isLoading = false
            }
        }
    }

    fun runSingleSearch(query: String) {
        if (query.isBlank()) return
        focusManager.clearFocus()
        selectedPackId = null
        activeTripName = query.trim()
        activeTripItemNames = listOf(query.trim())
        isLoading = true
        comparisonResults = emptyList()
        addedToBasketSuccess = false

        scope.launch {
            try {
                val estimates = withContext(Dispatchers.IO) {
                    val activeLoc = runCatching { PriceBridgeRepositoryProvider.repository.getLocation().firstOrNull() }.getOrNull()
                    val activeLat = activeLoc?.latitude?.takeIf { it != 0.0 } ?: 51.5390
                    val activeLon = activeLoc?.longitude?.takeIf { it != 0.0 } ?: -0.1426
                    val prods = ApiClient.service.searchProducts(query)
                    val prod = prods.firstOrNull()
                    if (prod != null) {
                        val comps = ApiClient.service.compareProductPrices(prod.id, activeLat, activeLon)
                        comps.map { comp ->
                            val dist = if (comp.store.distanceKm != null) {
                                val km = comp.store.distanceKm
                                val mins = (km * 12).toInt().coerceAtLeast(1)
                                if (km < 1.0) "${(km * 1000).toInt()}m · ${mins}m walk" else "%.1f km · ${mins}m walk".format(km)
                            } else {
                                "Nearby"
                            }
                            StoreTripEstimate(
                                storeId = comp.store.id,
                                storeName = comp.store.name,
                                distanceFormatted = dist,
                                totalPence = comp.effectivePricePence,
                                matchedCount = 1,
                                totalItems = 1,
                                loyaltyScheme = comp.loyaltyScheme
                            )
                        }.sortedBy { it.totalPence }
                    } else {
                        emptyList()
                    }
                }
                comparisonResults = estimates
            } catch (_: Exception) {
                comparisonResults = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Prompt
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "What are you purchasing today?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "New in London? Tell us what you need & see who's cheaper nearby.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text("e.g. Milk, Lurpak, Chicken, Bread...", style = MaterialTheme.typography.bodyMedium)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSingleSearch(searchQuery) }),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Trip Presets Chips
            Text(
                text = "Popular London Trips",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LONDON_QUICK_TRIPS.forEach { pack ->
                    val isSelected = selectedPackId == pack.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { runTripSearch(pack) },
                        label = {
                            Text("${pack.emoji} ${pack.title}", style = MaterialTheme.typography.labelSmall)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            // Results Section (Animated)
            AnimatedVisibility(visible = isLoading, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Scanning nearby London stores (Aldi, Lidl, Tesco, Sainsbury's)...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(visible = !isLoading && comparisonResults.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cheapest nearby for \"$activeTripName\":",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${comparisonResults.size} stores compared",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Top 3 Stores Comparison Cards
                    comparisonResults.take(3).forEachIndexed { index, est ->
                        val isBest = index == 0
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isBest) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Rank Badge
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .background(
                                            if (isBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBest) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = est.storeName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isBest) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "CHEAPEST",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = est.distanceFormatted,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (est.loyaltyScheme != "NONE") {
                                            Text(
                                                text = " • with ${est.loyaltyScheme}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                // Total Price
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "£%.2f".format(est.totalPence / 100.0),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "est. total",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Buttons (Add to Basket / 2-Stop Split Optimizer)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    for (item in activeTripItemNames) {
                                        PriceBridgeRepositoryProvider.repository.addShoppingItem(item, 1, "1 unit")
                                    }
                                    addedToBasketSuccess = true
                                }
                            },
                            enabled = !addedToBasketSuccess,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (addedToBasketSuccess) Icons.Default.Check else Icons.Default.AddShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (addedToBasketSuccess) "Added to Basket!" else "Add to Basket",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onNavigateToTwoStopBasket,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CompareArrows,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "2-Stop Split",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getFallbackTripEstimates(packId: String): List<StoreTripEstimate> {
    return when (packId) {
        "trip_staples" -> listOf(
            StoreTripEstimate(
                storeId = "store_aldi_camden",
                storeName = "Aldi Camden High St",
                distanceFormatted = "450m · 5m walk",
                totalPence = 645,
                matchedCount = 4,
                totalItems = 4,
                loyaltyScheme = "NONE"
            ),
            StoreTripEstimate(
                storeId = "store_lidl_camden",
                storeName = "Lidl Camden Town",
                distanceFormatted = "650m · 7m walk",
                totalPence = 659,
                matchedCount = 4,
                totalItems = 4,
                loyaltyScheme = "NONE"
            ),
            StoreTripEstimate(
                storeId = "store_tesco_express_nw1",
                storeName = "Tesco Express Camden",
                distanceFormatted = "250m · 3m walk",
                totalPence = 685,
                matchedCount = 4,
                totalItems = 4,
                loyaltyScheme = "CLUBCARD"
            ),
            StoreTripEstimate(
                storeId = "store_sainsburys_camden",
                storeName = "Sainsbury's Camden",
                distanceFormatted = "380m · 4m walk",
                totalPence = 695,
                matchedCount = 4,
                totalItems = 4,
                loyaltyScheme = "NECTAR"
            )
        )
        "trip_dinner" -> listOf(
            StoreTripEstimate(
                storeId = "store_aldi_camden",
                storeName = "Aldi Camden High St",
                distanceFormatted = "450m · 5m walk",
                totalPence = 720,
                matchedCount = 3,
                totalItems = 3,
                loyaltyScheme = "NONE"
            ),
            StoreTripEstimate(
                storeId = "store_asda_park_royal",
                storeName = "Asda Park Royal",
                distanceFormatted = "1.2 km · 14m walk",
                totalPence = 745,
                matchedCount = 3,
                totalItems = 3,
                loyaltyScheme = "ASDA_REWARDS"
            ),
            StoreTripEstimate(
                storeId = "store_sainsburys_camden",
                storeName = "Sainsbury's Camden",
                distanceFormatted = "380m · 4m walk",
                totalPence = 780,
                matchedCount = 3,
                totalItems = 3,
                loyaltyScheme = "NECTAR"
            )
        )
        else -> listOf(
            StoreTripEstimate(
                storeId = "store_lidl_camden",
                storeName = "Lidl Camden Town",
                distanceFormatted = "650m · 7m walk",
                totalPence = 710,
                matchedCount = 3,
                totalItems = 3,
                loyaltyScheme = "NONE"
            ),
            StoreTripEstimate(
                storeId = "store_tesco_express_nw1",
                storeName = "Tesco Express Camden",
                distanceFormatted = "250m · 3m walk",
                totalPence = 765,
                matchedCount = 3,
                totalItems = 3,
                loyaltyScheme = "CLUBCARD"
            )
        )
    }
}

