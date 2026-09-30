package com.example.feature.basket

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.model.PriceStatusBadgeType
import com.example.core.model.PriceWatchAlert
import com.example.core.model.SavedShoppingList
import com.example.core.model.ShoppingBasket
import com.example.core.model.ShoppingListItem
import com.example.core.model.ShoppingTaskTab
import com.example.core.model.StoreRecommendation
import com.example.core.model.StoreSplitSummary
import com.example.core.ui.BottomBarDestination
import com.example.core.ui.EmptyStateView
import com.example.core.ui.ErrorStateView
import com.example.core.ui.LoadingStateView
import com.example.core.ui.OfflineBanner
import com.example.core.ui.PriceBridgeBottomBar
import com.example.core.ui.PriceStatusBadgeComponent
import com.example.core.ui.TrustBadgeFooter
import com.example.ui.theme.PriceBridgeTheme
import com.example.ui.theme.spacing

/**
 * Stateful entry composable for the Smart Basket / Shopping List Screen.
 */
@Composable
fun BasketScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BasketViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onNavigateBack)

    BasketContent(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                is BasketUiEvent.OnBackClicked -> onNavigateBack()
                is BasketUiEvent.OnBottomNavClicked -> {
                    when (event.destination) {
                        BottomBarDestination.Home -> onNavigateToHome()
                        BottomBarDestination.Search -> onNavigateToSearch()
                        BottomBarDestination.Scan -> onNavigateToScan()
                        BottomBarDestination.Saved -> Unit // Current screen
                        BottomBarDestination.Profile -> onNavigateToProfile()
                    }
                }
                else -> viewModel.onEvent(event)
            }
        },
        modifier = modifier
    )
}

/**
 * Stateless Basket Content Composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasketContent(
    uiState: BasketUiState,
    onEvent: (BasketUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Shopping Tasks",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        uiState.basket?.let { basket ->
                            Text(
                                text = "Optimized across Indiranagar stores",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onEvent(BasketUiEvent.OnBackClicked) },
                        modifier = Modifier
                            .size(MaterialTheme.spacing.minTouchTarget)
                            .testTag("basket_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_to_home),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (uiState.selectedTab == ShoppingTaskTab.ActiveBasket && uiState.basket?.items?.any { it.isChecked } == true) {
                        IconButton(
                            onClick = { onEvent(BasketUiEvent.OnClearCheckedItems) },
                            modifier = Modifier.testTag("btn_clear_checked")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear checked items",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { onEvent(BasketUiEvent.OnShareListClicked) },
                        modifier = Modifier.testTag("btn_share_list")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share shopping list",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (uiState.selectedTab == ShoppingTaskTab.ActiveBasket) {
                ExtendedFloatingActionButton(
                    onClick = { onEvent(BasketUiEvent.OnOpenAddItemDialog) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.btn_add_item),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentColor = MaterialTheme.colorScheme.surfaceBright,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(bottom = 72.dp)
                        .testTag("fab_add_item")
                )
            }
        },
        bottomBar = {
            PriceBridgeBottomBar(
                currentDestination = BottomBarDestination.Saved,
                onDestinationClick = { destination ->
                    onEvent(BasketUiEvent.OnBottomNavClicked(destination))
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
            if (uiState.isOffline) {
                OfflineBanner()
            }

            // ── Phase 3: Task Type Switcher (Active Basket / Saved & Recurring / Price Watch) ──
            TabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab.ordinal]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                ShoppingTaskTab.entries.forEach { tab ->
                    val badgeCount = when (tab) {
                        ShoppingTaskTab.ActiveBasket -> uiState.basket?.items?.size ?: 0
                        ShoppingTaskTab.SavedLists -> uiState.savedLists.size
                        ShoppingTaskTab.PriceWatch -> uiState.priceWatchAlerts.size
                    }
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { onEvent(BasketUiEvent.OnSelectTab(tab)) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Medium
                                )
                                if (badgeCount > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = if (uiState.selectedTab == tab) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Text(
                                            text = "$badgeCount",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (uiState.selectedTab == tab) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Share confirmation toast banner
            if (uiState.shareSuccessMessage != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.shareSuccessMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onEvent(BasketUiEvent.OnDismissShareToast) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Text("✕", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    LoadingStateView(modifier = Modifier.weight(1f))
                }
                uiState.errorMessage != null -> {
                    ErrorStateView(
                        errorMessage = uiState.errorMessage,
                        onRetryClick = { onEvent(BasketUiEvent.OnRetryClicked) },
                        modifier = Modifier.weight(1f)
                    )
                }
                uiState.selectedTab == ShoppingTaskTab.SavedLists -> {
                    SavedListsSection(
                        savedLists = uiState.savedLists,
                        onLoadList = { onEvent(BasketUiEvent.OnLoadSavedList(it)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                uiState.selectedTab == ShoppingTaskTab.PriceWatch -> {
                    PriceWatchSection(
                        alerts = uiState.priceWatchAlerts,
                        onAddToCart = { name, size -> onEvent(BasketUiEvent.OnConfirmAddItem(name, 1, size)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                uiState.isEmpty -> {
                    EmptyStateView(
                        modifier = Modifier.weight(1f),
                        onActionClick = { onEvent(BasketUiEvent.OnOpenAddItemDialog) }
                    )
                }
                else -> {
                    val basket = uiState.basket!!
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("basket_item_list"),
                        contentPadding = PaddingValues(
                            top = MaterialTheme.spacing.spaceSm,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.spaceMd)
                    ) {
                        // Estimated Total Hero Summary (Product Rule: Labelled "Estimated total")
                        item(key = "estimated_total_summary") {
                            Box(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.spaceMd)) {
                                EstimatedTotalSummaryCard(basket = basket)
                            }
                        }

                        // Phase 3: 1-Store vs. 2-Store Recommendation Selector Card
                        item(key = "recommendation_selector") {
                            Box(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.spaceMd)) {
                                RecommendationSelectorCard(
                                    basket = basket,
                                    selectedIndex = uiState.selectedRecommendationIndex,
                                    onSelectIndex = { onEvent(BasketUiEvent.OnSelectRecommendation(it)) }
                                )
                            }
                        }

                        // Store Split Breakdown Segment
                        if (basket.storeSplits.isNotEmpty()) {
                            item(key = "store_splits_segment") {
                                Column(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.spaceMd)) {
                                    Text(
                                        text = stringResource(R.string.section_store_splits),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    StoreSplitsRow(splits = basket.storeSplits)
                                }
                            }
                        }

                        // Section Header: Items
                        item(key = "items_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MaterialTheme.spacing.spaceMd),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Items in Basket (${basket.items.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Quantities & Alternatives",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Shopping List Items
                        items(
                            items = basket.items,
                            key = { it.id }
                        ) { item ->
                            Box(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.spaceMd)) {
                                ShoppingListItemCard(
                                    item = item,
                                    onToggleChecked = { onEvent(BasketUiEvent.OnToggleItem(item.id)) },
                                    onQuantityChanged = { newQty -> onEvent(BasketUiEvent.OnUpdateItemQuantity(item.id, newQty)) },
                                    onDeleteClick = { onEvent(BasketUiEvent.OnDeleteItem(item.id)) }
                                )
                            }
                        }

                        // Trust Footer
                        item(key = "trust_footer") {
                            TrustBadgeFooter()
                        }
                    }
                }
            }
        }
    }

    // Add Item Bottom Sheet (No receipt/barcode mandatory)
    if (uiState.isAddItemDialogOpen) {
        AddItemBottomSheet(
            onDismiss = { onEvent(BasketUiEvent.OnDismissAddItemDialog) },
            onConfirm = { name, quantity, size ->
                onEvent(BasketUiEvent.OnConfirmAddItem(name, quantity, size))
            }
        )
    }
}

/**
 * Summary card highlighting the product-rule mandated "Estimated total".
 */
@Composable
private fun EstimatedTotalSummaryCard(
    basket: ShoppingBasket,
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
            .testTag("estimated_total_summary_card")
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
                        // Strict Product Rule: Labelled "Estimated total"
                        Text(
                            text = stringResource(R.string.label_estimated_total),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.price_format, basket.optimizedEstimatedTotal),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.label_savings_optimized, basket.estimatedSavingsAmount),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.spaceSm))

                Text(
                    text = stringResource(R.string.label_single_store_total, basket.singleStoreEstimatedTotal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough
                )
            }
        }
    }
}

@Composable
private fun StoreSplitsRow(
    splits: List<StoreSplitSummary>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        splits.forEach { split ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = split.storeName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.store_split_item_count, split.itemCount, split.subtotal),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationSelectorCard(
    basket: ShoppingBasket,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Store Optimization Recommendation",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Phase 3 Smart Engine",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Option 0: Best 2-Store Split (Max Savings)
                basket.twoStoreRecommendation?.let { rec ->
                    RecommendationChoiceChip(
                        title = "2-Store Split",
                        badge = "Save ₹${rec.savingsVsSingleStore.toInt()}",
                        subtitle = "${rec.primaryStoreName} + ${rec.secondaryStoreName}",
                        cost = "₹${rec.totalEstimatedCost.toInt()}",
                        isSelected = selectedIndex == 0,
                        onClick = { onSelectIndex(0) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Option 1: Best 1-Store Trip (Fastest)
                basket.oneStoreRecommendation?.let { rec ->
                    RecommendationChoiceChip(
                        title = "1-Store Run",
                        badge = "Single Stop",
                        subtitle = "${rec.primaryStoreName} (${rec.primaryStoreDistance})",
                        cost = "₹${rec.totalEstimatedCost.toInt()}",
                        isSelected = selectedIndex == 1,
                        onClick = { onSelectIndex(1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Plan Section 3 Legal disclaimer: Totals are estimates, never guarantees
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Estimated total based on nearby community reports • Never guaranteed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecommendationChoiceChip(
    title: String,
    badge: String,
    subtitle: String,
    cost: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = cost,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ShoppingListItemCard(
    item: ShoppingListItem,
    onToggleChecked: () -> Unit,
    onQuantityChanged: (Int) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val itemAlpha = if (item.isChecked) 0.55f else 1f

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .alpha(itemAlpha)
            .testTag("basket_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.isChecked,
                    onCheckedChange = { onToggleChecked() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.secondary,
                        uncheckedColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("checkbox_${item.id}")
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.productName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "${item.packageSize} • ₹${item.bestStorePrice.toInt()}/unit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.bestStoreName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        PriceStatusBadgeComponent(status = item.statusBadge)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "· ${item.lastObservedRelativeTime}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.price_format, item.estimatedItemTotal),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Quantity Stepper: [-] [qty] [+]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = { onQuantityChanged(item.quantity - 1) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = "${item.quantity}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = { onQuantityChanged(item.quantity + 1) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Alternative / Substitute recommendation (Plan Section 3)
            if (item.alternativeName != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Alternative: ${item.alternativeName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedListsSection(
    savedLists: List<SavedShoppingList>,
    onLoadList: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.spacing.spaceMd, vertical = MaterialTheme.spacing.spaceSm),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.spaceSm)
    ) {
        item {
            Text(
                text = "Saved & Recurring Shopping Lists",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Load pre-configured grocery tasks with one tap to check current live prices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(savedLists, key = { it.id }) { list ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = list.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (list.isRecurring) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = "${list.recurringInterval} Recurring",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${list.itemCount} items • Est. total ₹${list.estimatedTotal.toInt()} • Run: ${list.lastRunDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { onLoadList(list.id) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(text = "Load", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceWatchSection(
    alerts: List<PriceWatchAlert>,
    onAddToCart: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.spacing.spaceMd, vertical = MaterialTheme.spacing.spaceSm),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.spaceSm)
    ) {
        item {
            Text(
                text = "Monitored Price Watch Alerts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Receive notifications whenever nearby stores drop prices below your target.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(alerts, key = { it.id }) { alert ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    1.dp,
                    if (alert.isTargetMet) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = alert.productName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (alert.isTargetMet) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "Target Met!",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${alert.packageSize} • Target: ₹${alert.targetPrice.toInt()} • Current: ₹${alert.currentBestPrice.toInt()} at ${alert.currentBestStore}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { onAddToCart(alert.productName, alert.packageSize) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = "Add to Cart", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemBottomSheet(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableIntStateOf(1) }
    var packageSize by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
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
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.dialog_add_item_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Add grocery or staple to calculate cross-store totals",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Item Name Field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.input_item_name)) },
                placeholder = { Text("e.g. Aashirvaad Shudh Chakki Atta") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (name.isNotEmpty()) {
                        IconButton(onClick = { name = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_add_item_name")
            )

            // Staple item suggestions
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Common Essentials:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickItems = listOf("Atta", "Basmati Rice", "Toned Milk", "Sugar", "Toor Dal", "Sunflower Oil", "Eggs (6-pack)")
                    quickItems.forEach { item ->
                        AssistChip(
                            onClick = { name = item },
                            label = { Text(item) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            // Quantity Stepper with card container
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.input_quantity),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Units to buy",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.size(36.dp)
                        ) {
                            IconButton(
                                onClick = { if (quantity > 1) quantity-- },
                                enabled = quantity > 1,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                            }
                        }

                        Text(
                            text = quantity.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            IconButton(
                                onClick = { quantity++ },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Increase",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Package Size / Unit Field + Quick Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = packageSize,
                    onValueChange = { packageSize = it },
                    label = { Text(stringResource(R.string.input_package_size)) },
                    placeholder = { Text("e.g. 1 kg, 500 g, 1 L") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (packageSize.isNotEmpty()) {
                            IconButton(onClick = { packageSize = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sizes = listOf("500 g", "1 kg", "2 kg", "5 kg", "500 ml", "1 L", "1 pack")
                    sizes.forEach { size ->
                        AssistChip(
                            onClick = { packageSize = size },
                            label = { Text(size) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Actions: Full-width Primary Add Button + Cancel
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), quantity, packageSize.trim())
                    }
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_confirm_add_item")
            ) {
                Icon(
                    imageVector = Icons.Default.AddShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_confirm_add),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// -------------------------------------------------------------
// PREVIEWS
// -------------------------------------------------------------

private val previewBasket = ShoppingBasket(
    id = "sample_basket",
    title = "Weekend Grocery List",
    items = listOf(
        ShoppingListItem(
            id = "1",
            productName = "Fortune Sunlite Sunflower Oil",
            quantity = 1,
            packageSize = "1L Pouch",
            isChecked = false,
            bestStoreName = "Nilgiris Supermarket",
            bestStorePrice = 142.0,
            lastObservedRelativeTime = "2h ago",
            statusBadge = PriceStatusBadgeType.CommunityCorroborated
        ),
        ShoppingListItem(
            id = "2",
            productName = "Amul Taaza Milk",
            quantity = 2,
            packageSize = "1L Tetra Pack",
            isChecked = true,
            bestStoreName = "Apna Bazar Kirana",
            bestStorePrice = 72.0,
            lastObservedRelativeTime = "3h ago",
            statusBadge = PriceStatusBadgeType.CommunityCorroborated
        )
    ),
    singleStoreEstimatedTotal = 360.0,
    optimizedEstimatedTotal = 286.0,
    estimatedSavingsAmount = 74.0,
    storeSplits = listOf(
        StoreSplitSummary("Nilgiris Supermarket", 1, 142.0),
        StoreSplitSummary("Apna Bazar Kirana", 2, 144.0)
    ),
    oneStoreRecommendation = StoreRecommendation(
        title = "Fastest: 1-Store Run",
        isTwoStoreSplit = false,
        primaryStoreName = "Nilgiris Supermarket",
        primaryStoreDistance = "0.6 km",
        primaryItemCount = 2,
        totalEstimatedCost = 360.0,
        badgeLabel = "Fastest • Single Stop"
    ),
    twoStoreRecommendation = StoreRecommendation(
        title = "Max Savings: 2-Store Split",
        isTwoStoreSplit = true,
        primaryStoreName = "Apna Bazar",
        primaryStoreDistance = "0.3 km",
        primaryItemCount = 1,
        secondaryStoreName = "Nilgiris",
        secondaryStoreDistance = "0.6 km",
        secondaryItemCount = 1,
        totalEstimatedCost = 286.0,
        savingsVsSingleStore = 74.0,
        badgeLabel = "Max Savings • Save ₹74"
    )
)

@Preview(name = "Basket - Content Light", showBackground = true)
@Composable
fun BasketContentPreview() {
    PriceBridgeTheme(darkTheme = false) {
        BasketContent(
            uiState = BasketUiState(basket = previewBasket),
            onEvent = {}
        )
    }
}

@Preview(name = "Basket - Loading", showBackground = true)
@Composable
fun BasketLoadingPreview() {
    PriceBridgeTheme(darkTheme = false) {
        BasketContent(
            uiState = BasketUiState(isLoading = true),
            onEvent = {}
        )
    }
}

@Preview(name = "Basket - Empty", showBackground = true)
@Composable
fun BasketEmptyPreview() {
    PriceBridgeTheme(darkTheme = false) {
        BasketContent(
            uiState = BasketUiState(basket = null),
            onEvent = {}
        )
    }
}

@Preview(name = "Basket - Error", showBackground = true)
@Composable
fun BasketErrorPreview() {
    PriceBridgeTheme(darkTheme = false) {
        BasketContent(
            uiState = BasketUiState(errorMessage = "Could not sync shopping list"),
            onEvent = {}
        )
    }
}

@Preview(name = "Basket - Offline", showBackground = true)
@Composable
fun BasketOfflinePreview() {
    PriceBridgeTheme(darkTheme = false) {
        BasketContent(
            uiState = BasketUiState(isOffline = true, basket = previewBasket),
            onEvent = {}
        )
    }
}

@Preview(name = "Basket - Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun BasketDarkModePreview() {
    PriceBridgeTheme(darkTheme = true) {
        BasketContent(
            uiState = BasketUiState(basket = previewBasket),
            onEvent = {}
        )
    }
}
