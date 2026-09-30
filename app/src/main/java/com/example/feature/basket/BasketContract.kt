package com.example.feature.basket

import androidx.compose.runtime.Immutable
import com.example.core.model.GoogleTaskItem
import com.example.core.model.GoogleTaskList
import com.example.core.model.GoogleTasksAuthState
import com.example.core.model.PriceWatchAlert
import com.example.core.model.SavedShoppingList
import com.example.core.model.ShoppingBasket
import com.example.core.model.ShoppingTaskTab
import com.example.core.ui.BottomBarDestination

/**
 * UI State for the Shopping Basket & Tasks screen.
 */
@Immutable
data class BasketUiState(
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val basket: ShoppingBasket? = null,
    val isAddItemDialogOpen: Boolean = false,
    val selectedTab: ShoppingTaskTab = ShoppingTaskTab.ActiveBasket,
    val savedLists: List<SavedShoppingList> = emptyList(),
    val priceWatchAlerts: List<PriceWatchAlert> = emptyList(),
    val selectedRecommendationIndex: Int = 0, // 0 = 2-Store Split (Max Savings), 1 = 1-Store Run (Fastest)
    val shareSuccessMessage: String? = null,
    val isGoogleTasksSheetOpen: Boolean = false,
    val googleTasksAuthState: GoogleTasksAuthState = GoogleTasksAuthState(),
    val availableGoogleTaskLists: List<GoogleTaskList> = emptyList(),
    val googleTasksInSelectedList: List<GoogleTaskItem> = emptyList(),
    val googleTasksImportedCount: Int? = null
) {
    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && (basket == null || basket.items.isEmpty())
}

/**
 * User actions and UI events for the Shopping Basket screen.
 */
@Immutable
sealed interface BasketUiEvent {
    data class OnSelectTab(val tab: ShoppingTaskTab) : BasketUiEvent
    data class OnToggleItem(val itemId: String) : BasketUiEvent
    data class OnUpdateItemQuantity(val itemId: String, val newQuantity: Int) : BasketUiEvent
    data class OnDeleteItem(val itemId: String) : BasketUiEvent
    data object OnClearCheckedItems : BasketUiEvent
    data object OnOpenAddItemDialog : BasketUiEvent
    data object OnDismissAddItemDialog : BasketUiEvent
    data class OnConfirmAddItem(
        val name: String,
        val quantity: Int = 1,
        val packageSize: String = ""
    ) : BasketUiEvent
    data class OnSelectRecommendation(val index: Int) : BasketUiEvent
    data class OnLoadSavedList(val listId: String) : BasketUiEvent
    data object OnShareListClicked : BasketUiEvent
    data object OnDismissShareToast : BasketUiEvent
    data object OnOpenGoogleTasksSheet : BasketUiEvent
    data object OnDismissGoogleTasksSheet : BasketUiEvent
    data class OnConnectGoogleTasks(val email: String) : BasketUiEvent
    data object OnDisconnectGoogleTasks : BasketUiEvent
    data class OnSelectGoogleTaskList(val listId: String) : BasketUiEvent
    data class OnImportGoogleTasks(val selectedTasks: List<GoogleTaskItem>) : BasketUiEvent
    data object OnDismissImportSuccessToast : BasketUiEvent
    data object OnRetryClicked : BasketUiEvent
    data object OnBackClicked : BasketUiEvent
    data class OnBottomNavClicked(val destination: BottomBarDestination) : BasketUiEvent
}


