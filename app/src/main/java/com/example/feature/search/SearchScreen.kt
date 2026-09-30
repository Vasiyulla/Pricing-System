package com.example.feature.search

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.ui.BottomBarDestination
import com.example.core.ui.CategoryPillStrip
import com.example.core.ui.EmptyStateView
import com.example.core.ui.ErrorStateView
import com.example.core.ui.LoadingStateView
import com.example.core.ui.PriceBridgeBottomBar
import com.example.core.ui.ProductDealCard
import com.example.core.ui.TrustBadgeFooter
import com.example.ui.theme.spacing

/**
 * Stateful entry composable for the Search screen.
 */
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProductDetail: (String) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToSaved: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SearchContent(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                is SearchUiEvent.OnBackClicked -> onNavigateBack()
                is SearchUiEvent.OnProductClicked -> {
                    viewModel.onEvent(event)
                    onNavigateToProductDetail(event.productId)
                }
                is SearchUiEvent.OnCompareStoresClicked -> {
                    viewModel.onEvent(event)
                    onNavigateToProductDetail(event.productId)
                }
                is SearchUiEvent.OnBottomNavClicked -> {
                    when (event.destination) {
                        BottomBarDestination.Home -> onNavigateToHome()
                        BottomBarDestination.Search -> Unit // Already here
                        BottomBarDestination.Scan -> onNavigateToScan()
                        BottomBarDestination.Saved -> onNavigateToSaved()
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
 * Stateless Search Content Composable.
 */
@Composable
fun SearchContent(
    uiState: SearchUiState,
    onEvent: (SearchUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            SearchTopBar(
                query = uiState.query,
                onQueryChanged = { onEvent(SearchUiEvent.OnQueryChanged(it)) },
                onClearQuery = { onEvent(SearchUiEvent.OnClearQuery) },
                onBackClick = { onEvent(SearchUiEvent.OnBackClicked) },
                focusRequester = focusRequester
            )
        },
        bottomBar = {
            PriceBridgeBottomBar(
                currentDestination = BottomBarDestination.Search,
                onDestinationClick = { onEvent(SearchUiEvent.OnBottomNavClicked(it)) }
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
            // Category filter strip
            if (uiState.categories.isNotEmpty()) {
                CategoryPillStrip(
                    categories = uiState.categories,
                    selectedCategoryId = uiState.selectedCategoryId,
                    onCategorySelect = { onEvent(SearchUiEvent.OnCategorySelected(it)) }
                )
            }

            when {
                uiState.isLoading -> {
                    LoadingStateView(
                        message = stringResource(R.string.search_loading),
                        modifier = Modifier.weight(1f)
                    )
                }
                uiState.errorMessage != null -> {
                    ErrorStateView(
                        errorMessage = uiState.errorMessage,
                        onRetryClick = { onEvent(SearchUiEvent.OnQueryChanged(uiState.query)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                uiState.isInitial -> {
                    // Show recent searches
                    RecentSearchesSection(
                        recentSearches = uiState.recentSearches,
                        onRecentSearchClick = { onEvent(SearchUiEvent.OnRecentSearchClicked(it)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                uiState.isEmpty -> {
                    SearchEmptyView(
                        query = uiState.query,
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {
                    // Search results
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("search_results_list"),
                        contentPadding = PaddingValues(
                            top = MaterialTheme.spacing.spaceSm,
                            bottom = MaterialTheme.spacing.spaceXl
                        ),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.spaceMd)
                    ) {
                        item(key = "results_header") {
                            Text(
                                text = stringResource(R.string.search_results_count, uiState.results.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.spaceMd)
                            )
                        }

                        items(
                            items = uiState.results,
                            key = { it.id }
                        ) { product ->
                            Box(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.spaceMd)) {
                                ProductDealCard(
                                    product = product,
                                    onProductClick = { onEvent(SearchUiEvent.OnProductClicked(it)) },
                                    onCompareStoresClick = { onEvent(SearchUiEvent.OnCompareStoresClicked(it)) }
                                )
                            }
                        }

                        item(key = "trust_footer") {
                            TrustBadgeFooter()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onClearQuery: () -> Unit,
    onBackClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    horizontal = MaterialTheme.spacing.spaceSm,
                    vertical = MaterialTheme.spacing.spaceSm
                )
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.testTag("search_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Search text field
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(MaterialTheme.spacing.spaceSm))

                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChanged,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .testTag("search_text_field")
                        )
                    }

                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = onClearQuery,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("search_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_clear_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(MaterialTheme.spacing.spaceSm))
        }
    }
}

@Composable
private fun RecentSearchesSection(
    recentSearches: List<String>,
    onRecentSearchClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.spaceMd)
    ) {
        if (recentSearches.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.spaceSm)
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.spaceSm))
                Text(
                    text = stringResource(R.string.search_recent_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            recentSearches.forEach { search ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRecentSearchClick(search) }
                        .testTag("recent_search_$search")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = MaterialTheme.spacing.spaceSm,
                                vertical = 12.dp
                            )
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = search,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyView(
    query: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.spaceMd))
        Text(
            text = stringResource(R.string.search_no_results_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.search_no_results_description, query),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
