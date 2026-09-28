package com.example.feature.basket

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.core.model.ShoppingBasket
import com.example.core.model.ShoppingListItem
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
                            text = stringResource(R.string.shopping_list_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        uiState.basket?.let { basket ->
                            Text(
                                text = stringResource(R.string.shopping_list_subtitle, basket.items.size),
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
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

                        // Shopping List Items
                        items(
                            items = basket.items,
                            key = { it.id }
                        ) { item ->
                            Box(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.spaceMd)) {
                                ShoppingListItemCard(
                                    item = item,
                                    onToggleChecked = { onEvent(BasketUiEvent.OnToggleItem(item.id)) },
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

    // Add Item Dialog (No receipt/barcode mandatory)
    if (uiState.isAddItemDialogOpen) {
        AddItemDialog(
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
private fun ShoppingListItemCard(
    item: ShoppingListItem,
    onToggleChecked: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val itemAlpha = if (item.isChecked) 0.55f else 1f

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .alpha(itemAlpha)
            .testTag("basket_item_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
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
                    .size(MaterialTheme.spacing.minTouchTarget)
                    .testTag("checkbox_${item.id}")
            )

            Spacer(modifier = Modifier.width(4.dp))

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
                        text = "${item.quantity}x (${item.packageSize})",
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
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("delete_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete_item),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableIntStateOf(1) }
    var packageSize by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_add_item_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.input_item_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_add_item_name")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.input_quantity),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null)
                        }
                        Text(
                            text = quantity.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(
                            onClick = { quantity++ },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                    }
                }

                OutlinedTextField(
                    value = packageSize,
                    onValueChange = { packageSize = it },
                    label = { Text(stringResource(R.string.input_package_size)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), quantity, packageSize.trim())
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("btn_confirm_add_item")
            ) {
                Text(stringResource(R.string.btn_confirm_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
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
