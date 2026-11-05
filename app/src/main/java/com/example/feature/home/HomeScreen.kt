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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.example.core.model.SmartBasketSummary
import com.example.core.model.Store
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

                        // "What are you purchasing today?" Reactive Shopping Intent Hero
                        item(key = "shopping_intent_hero") {
                            Box(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.spaceMd)) {
                                ShoppingIntentHero(
                                    onNavigateToTwoStopBasket = onNavigateToTwoStopBasket,
                                    onNavigateToYellowStickers = onNavigateToYellowStickers,
                                    onNavigateToSearch = { _ ->
                                        onEvent(HomeUiEvent.OnSearchClicked)
                                    }
                                )
                            }
                        }

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

                        // ── AI Superpowers Quick Access Strip ──
                        item(key = "ai_superpowers_strip") {
                            Column(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.spaceMd)) {
                                Text(
                                    text = "AI Smart Tools",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    item {
                                        Surface(
                                            onClick = onNavigateToTrolleyEar,
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFE8F5E9),
                                            border = BorderStroke(1.dp, Color(0xFF81C784))
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Headphones, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text("Trolley Ear", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1B5E20))
                                                    Text("In-Store Audio", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                                }
                                            }
                                        }
                                    }
                                    item {
                                        Surface(
                                            onClick = onNavigateToTwoStopBasket,
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFE3F2FD),
                                            border = BorderStroke(1.dp, Color(0xFF90CAF9))
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Savings, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text("2-Stop Split", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0D47A1))
                                                    Text("Pareto Basket", fontSize = 11.sp, color = Color(0xFF1565C0))
                                                }
                                            }
                                        }
                                    }
                                    item {
                                        Surface(
                                            onClick = onNavigateToYellowStickers,
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFFFF9C4),
                                            border = BorderStroke(1.dp, Color(0xFFFFF176))
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.LocalOffer, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text("Yellow Stickers", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFBF360C))
                                                    Text("Clearance Deals", fontSize = 11.sp, color = Color(0xFFE65100))
                                                }
                                            }
                                        }
                                    }
                                    item {
                                        Surface(
                                            onClick = onNavigateToBounties,
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFF3E5F5),
                                            border = BorderStroke(1.dp, Color(0xFFCE93D8))
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = Color(0xFF6A1B9A), modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text("AI Bounties", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF4A148C))
                                                    Text("Earn Points", fontSize = 11.sp, color = Color(0xFF6A1B9A))
                                                }
                                            }
                                        }
                                    }
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
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Nearby Stores & Kiranas",
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
                                            text = "Verified local shops • Reliability scores",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { onEvent(HomeUiEvent.OnDismissLocationSheet) },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            LocationSelectionSheetContent(
                currentNeighborhood = uiState.location.neighborhood,
                onSelectLocation = { neighborhood, radius ->
                    onEvent(HomeUiEvent.OnChangeLocation(neighborhood, radius))
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
}

private data class NeighborhoodOption(
    val name: String,
    val zone: String,
    val storeCount: Int
)

@Composable
private fun LocationSelectionSheetContent(
    currentNeighborhood: String,
    onSelectLocation: (String, Double) -> Unit
) {
    var selectedLocation by remember { mutableStateOf(currentNeighborhood) }
    var selectedRadius by remember { mutableStateOf(1.5) }

    val locations = listOf(
        NeighborhoodOption("Indiranagar, 100ft Rd", "East Bangalore", 18),
        NeighborhoodOption("Koramangala, 80ft Rd", "South-East Zone", 24),
        NeighborhoodOption("HSR Layout, Sector 1", "South Bangalore", 15),
        NeighborhoodOption("Jayanagar, 4th Block", "Central South", 21),
        NeighborhoodOption("Whitefield, Main Rd", "IT Corridor Zone", 16)
    )

    val radii = listOf(1.0, 1.5, 3.0, 5.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
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
                    imageVector = Icons.Default.NearMe,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = "Choose Shopping Area",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Compare prices across local kiranas & supermarkets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Neighborhood Selection Tiles
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Nearby Pilot Hubs",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.outline
            )

            locations.forEach { item ->
                val isSelected = selectedLocation.startsWith(item.name.substringBefore(","))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedLocation = item.name }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${item.zone} • ${item.storeCount} verified stores",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // Search Radius Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Shopping Radius",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.outline
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                radii.forEach { radius ->
                    val isSelected = selectedRadius == radius
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedRadius = radius }
                    ) {
                        Text(
                            text = "${radius} km",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = { onSelectLocation(selectedLocation, selectedRadius) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_confirm_location")
        ) {
            Text(
                text = "Apply & Explore Nearby Prices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
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
