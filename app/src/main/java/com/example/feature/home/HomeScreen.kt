package com.example.feature.home

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import com.example.core.model.SavedAddress
import com.example.core.session.AppSessionManager
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api 
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.example.core.location.DeviceLocationManager
import com.example.core.data.PriceBridgeRepositoryProvider
import com.example.core.data.NetworkPriceBridgeRepository
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.model.AppNotification
import com.example.core.model.Category
import com.example.core.model.KarmaVerificationPrompt
import com.example.core.model.LocationInfo
import com.example.core.model.PriceStatusBadgeType
import com.example.core.model.Product
import com.example.core.model.ShoppingBasket
import com.example.core.model.ShoppingListItem
import com.example.core.model.SmartBasketSummary
import com.example.core.model.Store
import com.example.core.model.StorePrice
import com.example.core.ui.BottomBarDestination
import com.example.core.ui.CategoryPillStrip
import com.example.core.ui.EmptyStateView
import com.example.core.ui.ErrorStateView
import com.example.core.ui.LoadingStateView
import com.example.core.ui.OfflineBanner
import com.example.core.ui.PriceBridgeBottomBar
import com.example.core.ui.PriceBridgeTopAppBar
import com.example.core.ui.ProductDealCard
import com.example.core.ui.TrustBadgeFooter
import com.example.feature.notifications.NotificationsBottomSheet
import com.example.feature.offline.OfflineQueueBottomSheet
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
    onNavigateToStore: (String) -> Unit = {},
    onNavigateToTrolleyEar: () -> Unit = {},
    onNavigateToTwoStopBasket: () -> Unit = {},
    onNavigateToYellowStickers: () -> Unit = {},
    onNavigateToBounties: () -> Unit = {},
    onNavigateToCopilot: () -> Unit = {},
    onNavigateToAddProduct: () -> Unit = {},
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
                is HomeUiEvent.OnStoreClicked -> onNavigateToStore(event.storeId)
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
        onNavigateToProduct = onNavigateToProductDetail,
        onNavigateToStore = onNavigateToStore,
        onNavigateToTrolleyEar = onNavigateToTrolleyEar,
        onNavigateToTwoStopBasket = onNavigateToTwoStopBasket,
        onNavigateToYellowStickers = onNavigateToYellowStickers,
        onNavigateToBounties = onNavigateToBounties,
        onNavigateToCopilot = onNavigateToCopilot,
        onNavigateToAddProduct = onNavigateToAddProduct,
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
    onNavigateToProduct: (String) -> Unit = {},
    onNavigateToStore: (String) -> Unit = {},
    onNavigateToTrolleyEar: () -> Unit = {},
    onNavigateToTwoStopBasket: () -> Unit = {},
    onNavigateToYellowStickers: () -> Unit = {},
    onNavigateToBounties: () -> Unit = {},
    onNavigateToCopilot: () -> Unit = {},
    onNavigateToAddProduct: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            PriceBridgeTopAppBar(
                location = uiState.location,
                onLocationClick = { onEvent(HomeUiEvent.OnLocationSelectorClicked) },
                onNotificationsClick = { onEvent(HomeUiEvent.OnNotificationsClicked) },
                onSearchClick = { onEvent(HomeUiEvent.OnSearchClicked) },
                onBarcodeScanClick = { onEvent(HomeUiEvent.OnScanClicked) },
                unreadNotificationsCount = uiState.unreadNotificationsCount
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
                        // Offline Sync Queue Banner (Plan Sprint 6)
                        if (uiState.pendingOfflineCount > 0) {
                            item(key = "offline_sync_banner") {
                                Box(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.spaceMd)) {
                                    Surface(
                                        onClick = { onEvent(HomeUiEvent.OnOpenOfflineQueueSheet) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)),
                                        modifier = Modifier.fillMaxWidth().testTag("banner_offline_sync")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CloudSync,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${uiState.pendingOfflineCount} price report${if (uiState.pendingOfflineCount > 1) "s" else ""} queued offline",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                                Text(
                                                    text = "Tap to inspect • Safe to sync anytime",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { onEvent(HomeUiEvent.OnOpenOfflineQueueSheet) },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "Inspect",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Button(
                                                onClick = { onEvent(HomeUiEvent.OnSyncOfflineQueue) },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "Sync",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Categories Horizontal Filter Strip (immediately below search)
                        if (uiState.categories.isNotEmpty()) {
                            item(key = "categories_strip") {
                                CategoryPillStrip(
                                    categories = uiState.categories,
                                    selectedCategoryId = uiState.selectedCategoryId,
                                    onCategorySelect = { categoryId ->
                                        if (categoryId == "yellow_stickers" || categoryId == "clearance") {
                                            onNavigateToYellowStickers()
                                        } else {
                                            onEvent(HomeUiEvent.OnCategorySelected(categoryId))
                                        }
                                    }
                                )
                            }
                        }

                        // ── Enterprise AI Commerce Suite Card ──
                        item(key = "enterprise_ai_hub_card") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MaterialTheme.spacing.spaceMd)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "Price Bridge AI Copilot",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Grounded real-time price intelligence",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = onNavigateToCopilot,
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("Ask AI", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Quick AI Mode Chips
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            onClick = onNavigateToTrolleyEar,
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Text("🎧 In-Store Audio", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                            }
                                        }

                                        Surface(
                                            onClick = onNavigateToTwoStopBasket,
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Text("⚡ 2-Stop Split", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Nearby Stores & Kiranas Horizontal Discovery Carousel
                        if (uiState.nearbyStores.isNotEmpty()) {
                            item(key = "nearby_stores_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = MaterialTheme.spacing.spaceMd),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Nearby Stores",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.primary,
                                                        CircleShape
                                                    )
                                            )
                                        }
                                        Text(
                                            text = if (uiState.routeMode == "driving") {
                                                "Road driving routes • Reliability scores"
                                            } else {
                                                "Footpath walking routes • Reliability scores"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.surfaceContainer,
                                                RoundedCornerShape(20.dp)
                                            )
                                            .padding(3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        listOf("walking" to "Walk", "driving" to "Drive").forEach { (modeKey, label) ->
                                            val selected = uiState.routeMode == modeKey
                                            Surface(
                                                onClick = { onEvent(HomeUiEvent.OnRouteModeChanged(modeKey)) },
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item(key = "nearby_stores_carousel") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.spaceMd),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(
                                        items = uiState.nearbyStores,
                                        key = { it.id }
                                    ) { store ->
                                        NearbyStoreCard(
                                            store = store,
                                            onClick = { onEvent(HomeUiEvent.OnStoreClicked(store.id)) }
                                        )
                                    }
                                }
                            }
                        }

                        // Empty Area Coverage State (no tracked stores within 10 km)
                        if (!uiState.isLoading && uiState.errorMessage == null && uiState.nearbyStores.isEmpty()) {
                            item(key = "empty_area_coverage") {
                                EmptyAreaCoverageCard(
                                    neighborhood = uiState.location.neighborhood,
                                    onExploreLondonPilot = {
                                        val pilotLocation = LocationInfo(
                                            title = "Camden Town (Pilot)",
                                            neighborhood = "Camden Town, London NW1",
                                            addressLine = "124-126 Camden High St, London NW1 0LU",
                                            latitude = 51.5390,
                                            longitude = -0.1426,
                                            isLiveTrackingActive = false
                                        )
                                        (PriceBridgeRepositoryProvider.repository as? NetworkPriceBridgeRepository)?.setCoordinates(
                                            lat = pilotLocation.latitude,
                                            lon = pilotLocation.longitude,
                                            neighborhood = pilotLocation.neighborhood
                                        )
                                        onEvent(HomeUiEvent.OnChangeLocation(pilotLocation))
                                    },
                                    onPickLocation = { onEvent(HomeUiEvent.OnLocationSelectorClicked) }
                                )
                            }
                        }

                        // Inline Shopping Basket + Add Item directly on Home
                        item(key = "home_shopping_basket") {
                            HomeShoppingBasketCard(
                                basket = uiState.shoppingBasket,
                                onAddItemClick = { onEvent(HomeUiEvent.OnOpenAddHomeItem) },
                                onViewFullBasketClick = { onEvent(HomeUiEvent.OnViewBasketClicked) },
                                onToggleItemChecked = { onEvent(HomeUiEvent.OnToggleHomeBasketItem(it)) },
                                onUpdateItemQty = { id, qty -> onEvent(HomeUiEvent.OnUpdateHomeBasketQty(id, qty)) },
                                onDeleteItem = { onEvent(HomeUiEvent.OnDeleteHomeBasketItem(it)) }
                            )
                        }

                        // Products Added by You (strictly user-contributed, independent of nearby store deals)
                        item(key = "home_my_added_products") {
                            HomeMyAddedProductsCard(
                                products = uiState.myAddedProducts,
                                onAddProductClick = onNavigateToAddProduct,
                                onProductClick = { onEvent(HomeUiEvent.OnProductClicked(it)) },
                                onViewAllProfileClick = { onEvent(HomeUiEvent.OnBottomNavClicked(BottomBarDestination.Profile)) }
                            )
                        }

                        // Section 1 Header: Best Deals Near You (only shown when tracked nearby stores exist)
                        if (uiState.nearbyStores.isNotEmpty() && uiState.deals.isNotEmpty()) {
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
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { onEvent(HomeUiEvent.OnDismissLocationSheet) },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            LocationSelectionSheetContent(
                currentLocation = uiState.location,
                onSelectLocation = { locationInfo ->
                    onEvent(HomeUiEvent.OnChangeLocation(locationInfo))
                }
            )
        }
    }

    // In-App Notifications & Alerts Bottom Sheet
    if (uiState.isNotificationsSheetOpen) {
        NotificationsBottomSheet(
            notifications = uiState.notifications,
            onNotificationClick = { notification ->
                onEvent(HomeUiEvent.OnNotificationClicked(notification))
                onEvent(HomeUiEvent.OnDismissNotificationsSheet)
                if (notification.targetProductId != null) {
                    onNavigateToProduct(notification.targetProductId)
                } else if (notification.targetStoreId != null) {
                    onNavigateToStore(notification.targetStoreId)
                }
            },
            onMarkAllRead = { onEvent(HomeUiEvent.OnMarkAllNotificationsRead) },
            onDismiss = { onEvent(HomeUiEvent.OnDismissNotificationsSheet) }
        )
    }

    // Offline Queue Inspection Bottom Sheet
    if (uiState.isOfflineQueueSheetOpen) {
        OfflineQueueBottomSheet(
            queuedSubmissions = uiState.queuedSubmissions,
            isSyncing = uiState.isSyncingOffline,
            onSyncAll = { onEvent(HomeUiEvent.OnSyncOfflineQueue) },
            onRetrySubmission = { id -> onEvent(HomeUiEvent.OnRetryQueuedSubmission(id)) },
            onCancelSubmission = { id -> onEvent(HomeUiEvent.OnCancelQueuedSubmission(id)) },
            onDismiss = { onEvent(HomeUiEvent.OnDismissOfflineQueueSheet) }
        )
    }

    // Add Item to Shopping Basket Bottom Sheet (directly on Home)
    if (uiState.isAddItemSheetOpen) {
        HomeAddItemBottomSheet(
            onDismiss = { onEvent(HomeUiEvent.OnDismissAddHomeItem) },
            onConfirmAdd = { name, qty, packSize ->
                onEvent(HomeUiEvent.OnConfirmAddHomeItem(name, qty, packSize))
            }
        )
    }
}

@Composable
private fun LocationSelectionSheetContent(
    currentLocation: LocationInfo,
    onSelectLocation: (LocationInfo) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val locManager = remember { DeviceLocationManager(context) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SavedAddress>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var isGpsDetecting by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }

    // Saved addresses from AppSessionManager
    var savedAddresses by remember { mutableStateOf<List<SavedAddress>>(AppSessionManager.getSavedAddresses()) }

    // Add Address Form toggle & states
    var isAddAddressOpen by remember { mutableStateOf(false) }
    var flatHouseText by remember { mutableStateOf("") }
    var areaStreetText by remember { mutableStateOf("") }
    var selectedLabelType by remember { mutableStateOf("Home") } // "Home", "Work", "Other"

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            isGpsDetecting = true
            locationError = null
            coroutineScope.launch {
                val geo = locManager.getAccurateLocation(highAccuracy = true)
                withContext(Dispatchers.Main) {
                    isGpsDetecting = false
                    if (geo.latitude != 0.0 || geo.longitude != 0.0) {
                        val (locality, fullAddr) = locManager.resolveFullAddress(geo.latitude, geo.longitude)
                        (PriceBridgeRepositoryProvider.repository as? NetworkPriceBridgeRepository)?.setCoordinates(
                            lat = geo.latitude,
                            lon = geo.longitude,
                            neighborhood = locality
                        )
                        val chosenLoc = LocationInfo(
                            title = "Current Location",
                            neighborhood = locality,
                            addressLine = fullAddr,
                            latitude = geo.latitude,
                            longitude = geo.longitude,
                            isLiveTrackingActive = true
                        )
                        onSelectLocation(chosenLoc)
                    } else {
                        locationError = "Unable to acquire GPS fix. Please ensure location services are turned on."
                    }
                }
            }
        } else {
            locationError = "Location permission denied. You can search or select a saved address below."
        }
    }

    val requestCurrentGps: () -> Unit = {
        if (!locManager.hasLocationPermission()) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            isGpsDetecting = true
            locationError = null
            coroutineScope.launch {
                val geo = locManager.getAccurateLocation(highAccuracy = true)
                withContext(Dispatchers.Main) {
                    isGpsDetecting = false
                    if (geo.latitude != 0.0 || geo.longitude != 0.0) {
                        val (locality, fullAddr) = locManager.resolveFullAddress(geo.latitude, geo.longitude)
                        (PriceBridgeRepositoryProvider.repository as? NetworkPriceBridgeRepository)?.setCoordinates(
                            lat = geo.latitude,
                            lon = geo.longitude,
                            neighborhood = locality
                        )
                        val chosenLoc = LocationInfo(
                            title = "Current Location",
                            neighborhood = locality,
                            addressLine = fullAddr,
                            latitude = geo.latitude,
                            longitude = geo.longitude,
                            isLiveTrackingActive = true
                        )
                        onSelectLocation(chosenLoc)
                    } else {
                        locationError = "Unable to acquire GPS fix. Please ensure location services are turned on."
                    }
                }
            }
        }
    }

    // Debounced search when query changes
    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().length >= 3) {
            isSearching = true
            withContext(Dispatchers.IO) {
                val results = locManager.searchAddresses(searchQuery.trim(), maxResults = 5)
                withContext(Dispatchers.Main) {
                    searchResults = results
                    isSearching = false
                }
            }
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Blinkit / Zomato Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = "Select Delivery & Shopping Location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Prices & store availability are personalized to your exact address",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Live Search Bar (Area, Street, Apartment)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text("Search area, street name, apartment...", style = MaterialTheme.typography.bodyMedium)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        modifier = Modifier
                            .clickable { searchQuery = "" }
                            .padding(8.dp)
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Show search results if user searched
        if (isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Searching locations…", style = MaterialTheme.typography.bodySmall)
            }
        } else if (searchResults.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "SEARCH RESULTS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                searchResults.forEach { item ->
                    Surface(
                        onClick = {
                            val chosen = LocationInfo(
                                title = item.label,
                                neighborhood = item.locality,
                                addressLine = item.addressLine,
                                latitude = item.latitude,
                                longitude = item.longitude,
                                isLiveTrackingActive = false
                            )
                            (PriceBridgeRepositoryProvider.repository as? NetworkPriceBridgeRepository)?.setCoordinates(
                                lat = item.latitude,
                                lon = item.longitude,
                                neighborhood = item.locality
                            )
                            onSelectLocation(chosen)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = item.addressLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error message if any
        if (locationError != null) {
            Text(
                text = locationError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // "Use Current Location" (GPS) Card — 1-Tap pinpointing
        Surface(
            onClick = { requestCurrentGps() },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGpsDetecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use Current Location",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isGpsDetecting) "Pinpointing exact building via GPS…" else "Auto-detect accurate location using device GPS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // "Camden Town, London (Pilot)" 1-Tap Preset Card
        Surface(
            onClick = {
                val pilotLocation = LocationInfo(
                    title = "Camden Town (Pilot)",
                    neighborhood = "Camden Town, London NW1",
                    addressLine = "124-126 Camden High St, London NW1 0LU",
                    latitude = 51.5390,
                    longitude = -0.1426,
                    isLiveTrackingActive = false
                )
                (PriceBridgeRepositoryProvider.repository as? NetworkPriceBridgeRepository)?.setCoordinates(
                    lat = pilotLocation.latitude,
                    lon = pilotLocation.longitude,
                    neighborhood = pilotLocation.neighborhood
                )
                onSelectLocation(pilotLocation)
            },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Camden Town, London (Pilot Data)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Explore 12 real London supermarkets & live £ prices",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Saved Addresses Section (Home, Work, Other)
        if (savedAddresses.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SAVED ADDRESSES",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )

                savedAddresses.forEach { addr ->
                    val isCurrentSelected = currentLocation.title == addr.label ||
                            currentLocation.addressLine == addr.addressLine
                    Surface(
                        onClick = {
                            val chosen = LocationInfo(
                                title = addr.label,
                                neighborhood = addr.locality,
                                addressLine = addr.addressLine,
                                latitude = addr.latitude,
                                longitude = addr.longitude,
                                isLiveTrackingActive = false
                            )
                            (PriceBridgeRepositoryProvider.repository as? NetworkPriceBridgeRepository)?.setCoordinates(
                                lat = addr.latitude,
                                lon = addr.longitude,
                                neighborhood = addr.locality
                            )
                            onSelectLocation(chosen)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCurrentSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            1.dp,
                            if (isCurrentSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val addrIcon = when (addr.label.lowercase()) {
                                "home" -> Icons.Default.Home
                                "work", "office" -> Icons.Default.Business
                                else -> Icons.Default.Place
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCurrentSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = addrIcon,
                                    contentDescription = null,
                                    tint = if (isCurrentSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = addr.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isCurrentSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = addr.addressLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = addr.locality,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }

        // "+ Add Address Details" Expandable Form
        if (!isAddAddressOpen) {
            OutlinedButton(
                onClick = { isAddAddressOpen = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "+ Add New Address Details",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Add Exact Address Details",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier
                                .clickable { isAddAddressOpen = false }
                                .padding(4.dp)
                        )
                    }

                    // House / Flat / Floor No.
                    OutlinedTextField(
                        value = flatHouseText,
                        onValueChange = { flatHouseText = it },
                        label = { Text("House / Flat / Floor No.") },
                        placeholder = { Text("e.g. Flat 304, Tower B") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Area / Street / Landmark
                    OutlinedTextField(
                        value = areaStreetText,
                        onValueChange = { areaStreetText = it },
                        label = { Text("Area / Street / Landmark") },
                        placeholder = { Text("e.g. 42 Camden High Street, London") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Address Label chips: Home, Work, Other
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Home", "Work", "Other").forEach { label ->
                            val isSelected = selectedLabelType == label
                            Surface(
                                onClick = { selectedLabelType = label },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Save & Apply Button
                    Button(
                        onClick = {
                            if (areaStreetText.isNotBlank()) {
                                val fullLine = if (flatHouseText.isNotBlank()) "${flatHouseText.trim()}, ${areaStreetText.trim()}" else areaStreetText.trim()
                                val newAddr = SavedAddress(
                                    label = selectedLabelType,
                                    addressLine = fullLine,
                                    locality = areaStreetText.trim(),
                                    latitude = if (currentLocation.latitude != 0.0) currentLocation.latitude else 51.5390,
                                    longitude = if (currentLocation.longitude != 0.0) currentLocation.longitude else -0.1426
                                )
                                AppSessionManager.saveAddress(newAddr)
                                savedAddresses = AppSessionManager.getSavedAddresses()
                                isAddAddressOpen = false

                                val chosen = LocationInfo(
                                    title = newAddr.label,
                                    neighborhood = newAddr.locality,
                                    addressLine = newAddr.addressLine,
                                    latitude = newAddr.latitude,
                                    longitude = newAddr.longitude,
                                    isLiveTrackingActive = false
                                )
                                (PriceBridgeRepositoryProvider.repository as? NetworkPriceBridgeRepository)?.setCoordinates(
                                    lat = newAddr.latitude,
                                    lon = newAddr.longitude,
                                    neighborhood = newAddr.locality
                                )
                                onSelectLocation(chosen)
                            }
                        },
                        enabled = areaStreetText.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Save Address & See Nearby Prices",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Honest empty-coverage state when the user's current GPS has 0 tracked stores within 10 km.
 */
@Composable
private fun EmptyAreaCoverageCard(
    neighborhood: String,
    onExploreLondonPilot: () -> Unit,
    onPickLocation: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.spaceMd)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "No tracked stores near ${neighborhood.ifBlank { "your area" }} yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Live pilot store prices are currently seeded in Camden Town, London (UK).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onExploreLondonPilot,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Explore Camden Town Pilot", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onPickLocation,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pick Different Location")
            }
        }
    }
}

/**
 * Inline shopping basket summary & quick item management directly on HomeScreen.
 */
@Composable
private fun HomeShoppingBasketCard(
    basket: ShoppingBasket?,
    onAddItemClick: () -> Unit,
    onViewFullBasketClick: () -> Unit,
    onToggleItemChecked: (String) -> Unit,
    onUpdateItemQty: (String, Int) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    val items = basket?.items.orEmpty()
    val totalUnits = items.sumOf { it.quantity }
    val estimatedTotal = items.sumOf { it.estimatedItemTotal }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.spaceMd)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "My Shopping Basket",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val subtitle = when {
                            items.isEmpty() -> "Plan items & compare local prices"
                            estimatedTotal > 0.0 -> String.format(
                                java.util.Locale.UK,
                                "%d %s · Est. £%.2f",
                                totalUnits,
                                if (totalUnits == 1) "item" else "items",
                                estimatedTotal
                            )
                            else -> "$totalUnits ${if (totalUnits == 1) "item" else "items"} · No local store prices yet"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onAddItemClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Item", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }

            if (items.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Your basket is empty. Add staples to track prices across nearby stores.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onAddItemClick,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add First Item", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { item ->
                        HomeBasketItemRow(
                            item = item,
                            onToggleChecked = { onToggleItemChecked(item.id) },
                            onUpdateQty = { qty -> onUpdateItemQty(item.id, qty) },
                            onDelete = { onDeleteItem(item.id) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onViewFullBasketClick) {
                        Text("Open Full Basket", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeBasketItemRow(
    item: ShoppingListItem,
    onToggleChecked: () -> Unit,
    onUpdateQty: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = { onToggleChecked() }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                    color = if (item.isChecked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                val meta = if (item.bestStorePrice > 0.0) {
                    String.format(
                        java.util.Locale.UK,
                        "%s · %s · £%.2f",
                        item.packageSize,
                        item.bestStoreName,
                        item.estimatedItemTotal
                    )
                } else {
                    "${item.packageSize} · ${item.bestStoreName}"
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onUpdateQty(item.quantity - 1) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease quantity",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = item.quantity.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { onUpdateQty(item.quantity + 1) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase quantity",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove item",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeAddItemBottomSheet(
    onDismiss: () -> Unit,
    onConfirmAdd: (name: String, quantity: Int, packageSize: String) -> Unit
) {
    var itemName by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf(1) }
    var packageSize by remember { mutableStateOf("500 g") }

    val quickStaples = listOf(
        "Whole Milk",
        "Lurpak Butter",
        "Warburtons Bread",
        "Heinz Beans",
        "Cheddar",
        "Eggs (6-pack)",
        "Basmati Rice"
    )
    val packSizes = listOf("250 g", "500 g", "1 kg", "1 L", "2.27 L", "Pack", "Each")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Add Item to Basket",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = itemName,
                onValueChange = { itemName = it },
                label = { Text("Item name") },
                placeholder = { Text("e.g., Whole Milk, Heinz Beans") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Quick Staples",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickStaples) { staple ->
                    SuggestionChip(
                        onClick = { itemName = staple },
                        label = { Text(staple) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quantity",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (quantity > 1) quantity-- }) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease quantity")
                        }
                        Text(
                            text = quantity.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        IconButton(onClick = { quantity++ }) {
                            Icon(Icons.Default.Add, contentDescription = "Increase quantity")
                        }
                    }
                }
            }

            Text(
                text = "Pack Size",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(packSizes) { size ->
                    FilterChip(
                        selected = packageSize == size,
                        onClick = { packageSize = size },
                        label = { Text(size) }
                    )
                }
            }

            Button(
                onClick = {
                    if (itemName.isNotBlank()) {
                        onConfirmAdd(itemName.trim(), quantity, packageSize)
                    }
                },
                enabled = itemName.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add to Shopping Basket", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Beautiful local store discovery card with reliability score, distance, and verified price badge.
 */
@Composable
fun NearbyStoreCard(
    store: Store,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .width(220.dp)
            .testTag("nearby_store_card_${store.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (store.isStoreConfirmedPartner)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = if (store.isStoreConfirmedPartner)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Reliability pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (store.reliabilityScore >= 90) Color(0xFF10B981).copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(
                        0.5.dp,
                        if (store.reliabilityScore >= 90) Color(0xFF10B981)
                        else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = if (store.reliabilityScore >= 90) Color(0xFF10B981)
                            else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${store.reliabilityScore}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (store.reliabilityScore >= 90) Color(0xFF10B981)
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = store.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = store.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Distance tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = store.distanceFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Observations count
                Text(
                    text = "${store.verifiedPricesCount} verified",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// -------------------------------------------------------------
// PREVIEWS
// -------------------------------------------------------------

private val previewProduct = Product(
    id = "preview_milk",
    name = "British Semi-Skimmed Milk 4 Pints",
    categoryId = "Dairy & Chilled",
    quantityDescription = "Store Brand · 2.27L",
    packageSizeBadge = "4 Pints",
    imageUrl = "",
    bestStorePrice = StorePrice(
        storeId = "store_aldi_camden",
        storeName = "Aldi Camden High St",
        distanceFormatted = "450m · 5m walk",
        price = 1.45,
        mrp = 1.65,
        currencySymbol = "£",
        lastObservedRelativeTime = "2h ago",
        sourceDescription = "Community verified",
        statusBadge = PriceStatusBadgeType.RecentlyObserved,
        hasPhotoVerification = true,
        isLowestPrice = true
    ),
    storesComparedCount = 6
)

private val previewBasket = SmartBasketSummary(
    estimatedSavingsAmount = 4.20,
    storesComparedCount = 6,
    storeNamesSample = listOf("Aldi", "Lidl", "Tesco"),
    basketItemCount = 4,
    basketName = "Weekly Essentials"
)

private val previewCategories = listOf(
    Category("all", "All"),
    Category("Dairy & Chilled", "Dairy & Chilled"),
    Category("Bakery", "Bakery")
)

/**
 * Inline summary of products added by the signed-in user ("till now I had added N products").
 * Displayed independently of nearby store coverage so users can always track their own additions.
 */
@Composable
private fun HomeMyAddedProductsCard(
    products: List<Product>,
    onAddProductClick: () -> Unit,
    onProductClick: (String) -> Unit,
    onViewAllProfileClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.spaceMd)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Your Added Products (${products.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (products.isEmpty()) {
                                "Products you add on the app appear here"
                            } else {
                                "Total added by you on the app so far: ${products.size}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = onAddProductClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }

            if (products.isEmpty()) {
                Text(
                    text = "No products added by you yet. Tap Add to register a new product in the catalog.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    products.take(4).forEach { product ->
                        Surface(
                            onClick = { onProductClick(product.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = product.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subtitle = listOfNotNull(
                                        product.categoryId.takeIf { it.isNotBlank() },
                                        product.quantityDescription.takeIf { it.isNotBlank() }
                                    ).joinToString(" · ")
                                    if (subtitle.isNotBlank()) {
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                val mrp = product.bestStorePrice.mrp
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (mrp > 0.0) {
                                        Text(
                                            text = String.format(java.util.Locale.UK, "£%.2f", mrp),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (products.size > 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onViewAllProfileClick) {
                            Text("View All ${products.size} in Profile", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Home - Content Light", showBackground = true)
@Composable
fun HomeContentPreview() {
    PriceBridgeTheme(darkTheme = false) {
        HomeContent(
            uiState = HomeUiState(
                basketSummary = previewBasket,
                categories = previewCategories,
                deals = listOf(previewProduct),
                karmaPrompt = KarmaVerificationPrompt("p1", "Aldi Camden", "Verify shelf tag photo.")
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
                basketSummary = previewBasket,
                categories = previewCategories,
                deals = listOf(previewProduct)
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
                basketSummary = previewBasket,
                categories = previewCategories,
                deals = listOf(previewProduct),
                karmaPrompt = KarmaVerificationPrompt("p1", "Aldi Camden", "Verify shelf tag photo.")
            ),
            onEvent = {}
        )
    }
}
