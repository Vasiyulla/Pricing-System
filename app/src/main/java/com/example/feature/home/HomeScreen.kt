package com.example.feature.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.model.Category
import com.example.core.model.KarmaVerificationPrompt
import com.example.core.model.LocationInfo
import com.example.core.model.PriceStatusBadgeType
import com.example.core.model.Product
import com.example.core.model.SmartBasketSummary
import com.example.core.model.StorePrice
import com.example.core.ui.BottomBarDestination
import com.example.core.ui.CategoryPillStrip
import com.example.core.ui.EmptyStateView
import com.example.core.ui.ErrorStateView
import com.example.core.ui.HeroSavingsCard
import com.example.core.ui.KarmaContributionCard
import com.example.core.ui.LoadingStateView
import com.example.core.ui.OfflineBanner
import com.example.core.ui.PriceBridgeBottomBar
import com.example.core.ui.PriceBridgeTopAppBar
import com.example.core.ui.ProductDealCard
import com.example.core.ui.TrustBadgeFooter
import com.example.ui.theme.PriceBridgeTheme
import com.example.ui.theme.spacing

/**
 * Stateful entry composable for the Home screen.
 */
@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToProductDetail: (String) -> Unit,
    onNavigateToBasket: () -> Unit,
    onNavigateToSaved: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeContent(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                is HomeUiEvent.OnSearchClicked -> onNavigateToSearch()
                is HomeUiEvent.OnScanClicked -> onNavigateToScan()
                is HomeUiEvent.OnProductClicked -> onNavigateToProductDetail(event.productId)
                is HomeUiEvent.OnCompareStoresClicked -> onNavigateToProductDetail(event.productId)
                is HomeUiEvent.OnViewBasketClicked -> onNavigateToBasket()
                is HomeUiEvent.OnBottomNavClicked -> {
                    when (event.destination) {
                        BottomBarDestination.Home -> Unit // Already here
                        BottomBarDestination.Search -> onNavigateToSearch()
                        BottomBarDestination.Scan -> onNavigateToScan()
                        BottomBarDestination.Saved -> onNavigateToSaved()
                        BottomBarDestination.Profile -> onNavigateToProfile()
                    }
                }
                is HomeUiEvent.OnVerifyKarmaClicked -> {
                    // Navigate to scan/camera flow for shelf tags
                    onNavigateToScan()
                }
                else -> viewModel.onEvent(event)
            }
        },
        modifier = modifier
    )
}

/**
 * Stateless Home Content Composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onEvent: (HomeUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            PriceBridgeTopAppBar(
                location = uiState.location,
                onLocationClick = { onEvent(HomeUiEvent.OnLocationSelectorClicked) },
                onNotificationsClick = { onEvent(HomeUiEvent.OnNotificationsClicked) },
                onSearchClick = { onEvent(HomeUiEvent.OnSearchClicked) },
                onBarcodeScanClick = { onEvent(HomeUiEvent.OnScanClicked) }
            )
        },
        bottomBar = {
            PriceBridgeBottomBar(
                currentDestination = BottomBarDestination.Home,
                onDestinationClick = { destination ->
                    onEvent(HomeUiEvent.OnBottomNavClicked(destination))
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Offline banner indicator
            if (uiState.isOffline) {
                OfflineBanner()
            }

            when {
                uiState.isLoading -> {
                    LoadingStateView(modifier = Modifier.weight(1f))
                }
                uiState.errorMessage != null -> {
                    ErrorStateView(
                        errorMessage = uiState.errorMessage,
                        onRetryClick = { onEvent(HomeUiEvent.OnRetryClicked) },
                        modifier = Modifier.weight(1f)
                    )
                }
                uiState.isEmpty -> {
                    EmptyStateView(
                        onActionClick = { onEvent(HomeUiEvent.OnScanClicked) },
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("home_deal_list"),
                        contentPadding = PaddingValues(
                            top = MaterialTheme.spacing.spaceSm,
                            bottom = MaterialTheme.spacing.spaceXl
                        ),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.spaceMd)
                    ) {
                        // Hero Savings Card (Smart Basket Optimization)
                        uiState.basketSummary?.let { basket ->
                            item(key = "hero_savings_card") {
                                Box(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.spaceMd)) {
                                    HeroSavingsCard(
                                        basketSummary = basket,
                                        onViewBasketClick = { onEvent(HomeUiEvent.OnViewBasketClicked) }
                                    )
                                }
                            }
                        }

                        // Categories Horizontal Filter Strip
                        if (uiState.categories.isNotEmpty()) {
                            item(key = "categories_strip") {
                                CategoryPillStrip(
                                    categories = uiState.categories,
                                    selectedCategoryId = uiState.selectedCategoryId,
                                    onCategorySelect = { categoryId ->
                                        onEvent(HomeUiEvent.OnCategorySelected(categoryId))
                                    }
                                )
                            }
                        }

                        // Section 1 Header: Best Deals Near You
                        item(key = "best_deals_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MaterialTheme.spacing.spaceMd),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.section_best_deals_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.secondary,
                                                    CircleShape
                                                )
                                        )
                                    }
                                    Text(
                                        text = stringResource(
                                            R.string.section_best_deals_subtitle,
                                            uiState.location.neighborhood
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                TextButton(
                                    onClick = { onEvent(HomeUiEvent.OnSearchClicked) },
                                    modifier = Modifier.testTag("btn_view_all_deals")
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_view_all),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Deals List Items
                        items(
                            items = uiState.deals,
                            key = { it.id }
                        ) { product ->
                            Box(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.spaceMd)) {
                                ProductDealCard(
                                    product = product,
                                    onProductClick = { onEvent(HomeUiEvent.OnProductClicked(it)) },
                                    onCompareStoresClick = { onEvent(HomeUiEvent.OnCompareStoresClicked(it)) }
                                )
                            }
                        }

                        // Section 2: Help Verify Nearby Prices (Community Karma)
                        uiState.karmaPrompt?.let { prompt ->
                            item(key = "karma_verification_prompt") {
                                Box(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.spaceMd)) {
                                    KarmaContributionCard(
                                        prompt = prompt,
                                        onVerifyClick = { onEvent(HomeUiEvent.OnVerifyKarmaClicked(it)) },
                                        onMaybeLaterClick = { onEvent(HomeUiEvent.OnDismissKarmaPrompt(it)) }
                                    )
                                }
                            }
                        }

                        // Trust Badge Footer
                        item(key = "trust_badge_footer") {
                            TrustBadgeFooter()
                        }
                    }
                }
            }
        }
    }

    // Modal Location & Radius Selector Bottom Sheet
    if (uiState.isLocationSheetOpen) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { onEvent(HomeUiEvent.OnDismissLocationSheet) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            LocationSelectionSheetContent(
                currentNeighborhood = uiState.location.neighborhood,
                onSelectLocation = { neighborhood, radius ->
                    onEvent(HomeUiEvent.OnChangeLocation(neighborhood, radius))
                }
            )
        }
    }
}

@Composable
private fun LocationSelectionSheetContent(
    currentNeighborhood: String,
    onSelectLocation: (String, Double) -> Unit
) {
    var selectedLocation by remember { mutableStateOf(currentNeighborhood) }
    var selectedRadius by remember { mutableStateOf(1.5) }

    val locations = listOf(
        "Indiranagar, 100ft Rd",
        "Koramangala, 80ft Rd",
        "HSR Layout, Sector 1",
        "Jayanagar, 4th Block",
        "Whitefield, Main Rd"
    )

    val radii = listOf(1.0, 1.5, 3.0, 5.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.spaceLg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(MaterialTheme.spacing.spaceSm))
            Text(
                text = "Choose Search Location",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.spaceMd))

        Text(
            text = "Select Neighborhood",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.spaceSm))

        locations.forEach { location ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedLocation = location }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = selectedLocation == location,
                    onClick = { selectedLocation = location }
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.spaceSm))
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.spaceMd))

        Text(
            text = "Search Radius",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.spaceSm))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            radii.forEach { radius ->
                val isSelected = selectedRadius == radius
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedRadius = radius }
                ) {
                    Text(
                        text = "${radius} km",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.spaceLg))

        Button(
            onClick = { onSelectLocation(selectedLocation, selectedRadius) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_confirm_location")
        ) {
            Text(
                text = "Apply Location",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.spaceMd))
    }
}

// -------------------------------------------------------------
// PREVIEWS
// -------------------------------------------------------------

private val sampleProduct = Product(
    id = "fortune_oil_sample",
    name = "Fortune Sunlite Sunflower Oil",
    categoryId = "groceries",
    quantityDescription = "1L Pouch",
    packageSizeBadge = "1L",
    imageUrl = "",
    bestStorePrice = StorePrice(
        storeId = "nilgiris",
        storeName = "Nilgiris Supermarket",
        distanceFormatted = "0.6 km",
        price = 142.0,
        mrp = 165.0,
        lastObservedRelativeTime = "2h ago",
        sourceDescription = "Community corroborated",
        statusBadge = PriceStatusBadgeType.CommunityCorroborated,
        hasPhotoVerification = true,
        isLowestPrice = true
    ),
    storesComparedCount = 4
)

private val sampleBasket = SmartBasketSummary(
    estimatedSavingsAmount = 148.0,
    storesComparedCount = 6,
    storeNamesSample = listOf("Nilgiris", "Reliance", "Kiranas"),
    basketItemCount = 4,
    basketName = "Weekend Basket"
)

private val sampleCategories = listOf(
    Category("all", "All"),
    Category("groceries", "Groceries & Staples"),
    Category("dairy", "Dairy & Eggs")
)

@Preview(name = "Home - Content Light", showBackground = true)
@Composable
fun HomeContentPreview() {
    PriceBridgeTheme(darkTheme = false) {
        HomeContent(
            uiState = HomeUiState(
                basketSummary = sampleBasket,
                categories = sampleCategories,
                deals = listOf(sampleProduct),
                karmaPrompt = KarmaVerificationPrompt("p1", "Reliance Smart", "Verify shelf tag photo.")
            ),
            onEvent = {}
        )
    }
}

@Preview(name = "Home - Loading", showBackground = true)
@Composable
fun HomeLoadingPreview() {
    PriceBridgeTheme(darkTheme = false) {
        HomeContent(
            uiState = HomeUiState(isLoading = true),
            onEvent = {}
        )
    }
}

@Preview(name = "Home - Empty", showBackground = true)
@Composable
fun HomeEmptyPreview() {
    PriceBridgeTheme(darkTheme = false) {
        HomeContent(
            uiState = HomeUiState(deals = emptyList()),
            onEvent = {}
        )
    }
}

@Preview(name = "Home - Error", showBackground = true)
@Composable
fun HomeErrorPreview() {
    PriceBridgeTheme(darkTheme = false) {
        HomeContent(
            uiState = HomeUiState(errorMessage = "Network timeout. Could not reach crowd servers."),
            onEvent = {}
        )
    }
}

@Preview(name = "Home - Offline Mode", showBackground = true)
@Composable
fun HomeOfflinePreview() {
    PriceBridgeTheme(darkTheme = false) {
        HomeContent(
            uiState = HomeUiState(
                isOffline = true,
                basketSummary = sampleBasket,
                categories = sampleCategories,
                deals = listOf(sampleProduct)
            ),
            onEvent = {}
        )
    }
}

@Preview(name = "Home - Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun HomeDarkModePreview() {
    PriceBridgeTheme(darkTheme = true) {
        HomeContent(
            uiState = HomeUiState(
                basketSummary = sampleBasket,
                categories = sampleCategories,
                deals = listOf(sampleProduct),
                karmaPrompt = KarmaVerificationPrompt("p1", "Reliance Smart", "Verify shelf tag photo.")
            ),
            onEvent = {}
        )
    }
}
