package com.example

import com.example.core.data.PriceBridgeRepository
import com.example.core.data.local.entity.QueuedSubmissionEntity
import com.example.core.model.AppNotification
import com.example.core.model.Category
import com.example.core.model.GoogleTaskItem
import com.example.core.model.GoogleTaskList
import com.example.core.model.GoogleTasksAuthState
import com.example.core.model.KarmaVerificationPrompt
import com.example.core.model.LocationInfo
import com.example.core.model.PriceStatusBadgeType
import com.example.core.model.PriceSubmission
import com.example.core.model.PriceWatchAlert
import com.example.core.model.Product
import com.example.core.model.SavedShoppingList
import com.example.core.model.ShoppingBasket
import com.example.core.model.SmartBasketSummary
import com.example.core.model.Store
import com.example.core.model.StorePrice
import com.example.core.model.UserProfile
import com.example.feature.bounty.BountyItem
import com.example.feature.copilot.CopilotUiEvent
import com.example.feature.copilot.MessageSender
import com.example.feature.copilot.PriceCopilotViewModel
import com.example.feature.home.HomeViewModel
import com.example.feature.yellowsticker.YellowStickerItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CopilotAndHomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun copilot_unknownQueryWithNoNearbyStores_neverEmitsBlankStoreListOr90Accuracy() = runTest {
        val repo = TestFakeRepository()
        val vm = PriceCopilotViewModel(repository = repo, ioDispatcher = testDispatcher)

        vm.onEvent(CopilotUiEvent.OnSendMessage("strawberry"))
        advanceUntilIdle()

        val lastMsg = vm.uiState.value.messages.last()
        assertEquals(MessageSender.COPILOT, lastMsg.sender)
        assertFalse(lastMsg.text.contains("Here are the most active stores near your location"))
        assertFalse(lastMsg.text.contains("*"))
        assertNull(lastMsg.confidencePercent)
        assertEquals("NO_LOCAL_COVERAGE", lastMsg.intentTag)
    }

    @Test
    fun copilot_myAddedProductsQuery_returnsUserAddedProducts() = runTest {
        val repo = TestFakeRepository()
        val straw = Product(
            id = "prod_straw",
            name = "Strawberry Punnet",
            categoryId = "Fresh Produce",
            quantityDescription = "400g",
            packageSizeBadge = "400g",
            imageUrl = "",
            bestStorePrice = StorePrice(
                storeId = "user_added",
                storeName = "Added by you",
                distanceFormatted = "--",
                price = 2.50,
                mrp = 2.50,
                lastObservedRelativeTime = "Just added",
                sourceDescription = "Added by you",
                statusBadge = PriceStatusBadgeType.CommunityReported
            ),
            storesComparedCount = 0,
            alternativePrices = emptyList()
        )
        repo.addProduct(straw)

        val vm = PriceCopilotViewModel(repository = repo, ioDispatcher = testDispatcher)
        vm.onEvent(CopilotUiEvent.OnSendMessage("Show my added products"))
        advanceUntilIdle()

        val lastMsg = vm.uiState.value.messages.last()
        assertEquals("MY_PRODUCTS", lastMsg.intentTag)
        assertTrue(lastMsg.text.contains("1 product"))
        assertTrue(lastMsg.text.contains("Strawberry Punnet"))
    }

    @Test
    fun homeViewModel_hidesDealsWhenNearbyStoresEmpty() = runTest {
        val repo = TestFakeRepository(
            dealsToEmit = listOf(
                Product(
                    id = "prod_leak",
                    name = "Leaked Deal",
                    categoryId = "all",
                    quantityDescription = "500g",
                    packageSizeBadge = "500g",
                    imageUrl = "",
                    bestStorePrice = StorePrice(
                        storeId = "store_1",
                        storeName = "Remote Store",
                        distanceFormatted = "50m",
                        price = 1.99,
                        mrp = 2.50,
                        lastObservedRelativeTime = "Just now",
                        sourceDescription = "Community",
                        statusBadge = PriceStatusBadgeType.RecentlyObserved
                    ),
                    storesComparedCount = 1,
                    alternativePrices = emptyList()
                )
            ),
            nearbyStoresToEmit = emptyList()
        )

        val vm = HomeViewModel(repository = repo)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.nearbyStores.isEmpty())
        assertTrue(vm.uiState.value.deals.isEmpty())
    }

    @Test
    fun homeViewModel_doesNotHangInLoadingWhenNetworkFlowsDelay() = runTest {
        val repo = HangingNetworkFakeRepository()
        val vm = HomeViewModel(repository = repo)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals("Test City", vm.uiState.value.location.neighborhood)
        assertTrue(vm.uiState.value.categories.isNotEmpty())
    }
}

private class HangingNetworkFakeRepository : PriceBridgeRepository by TestFakeRepository() {
    override fun getNearbyStores(): Flow<List<Store>> = flow { awaitCancellation() }
    override fun getBestDeals(categoryId: String?): Flow<List<Product>> = flow { awaitCancellation() }
    override fun getKarmaPrompt(): Flow<KarmaVerificationPrompt?> = flow { awaitCancellation() }
    override fun getCategories(): Flow<List<Category>> = flow { awaitCancellation() }
}

private class TestFakeRepository(
    private val dealsToEmit: List<Product> = emptyList(),
    private val nearbyStoresToEmit: List<Store> = emptyList()
) : PriceBridgeRepository {
    private val addedProducts = MutableStateFlow<List<Product>>(emptyList())
    private val loc = MutableStateFlow(
        LocationInfo(
            title = "Current Location",
            neighborhood = "Test City",
            addressLine = "Live GPS",
            latitude = 20.82,
            longitude = 72.98
        )
    )

    override fun getLocation(): Flow<LocationInfo> = loc
    override fun getSmartBasketSummary(): Flow<SmartBasketSummary> =
        flowOf(
            SmartBasketSummary(
                estimatedSavingsAmount = 0.0,
                storesComparedCount = 0,
                storeNamesSample = emptyList(),
                basketItemCount = 0,
                basketName = "Basket"
            )
        )
    override fun getCategories(): Flow<List<Category>> = flowOf(emptyList())
    override fun getBestDeals(categoryId: String?): Flow<List<Product>> = flowOf(dealsToEmit)
    override fun getKarmaPrompt(): Flow<KarmaVerificationPrompt?> = flowOf(null)
    override fun searchProducts(query: String, categoryId: String?): Flow<List<Product>> = flowOf(emptyList())
    override fun getProductByBarcode(barcode: String): Flow<Product?> = flowOf(null)
    override fun getRecentSearches(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun addRecentSearch(query: String) {}
    override fun getProductById(productId: String): Flow<Product?> = flowOf(null)
    override fun getStorePricesForProduct(productId: String): Flow<List<StorePrice>> = flowOf(emptyList())
    override suspend fun addProduct(product: Product): String {
        addedProducts.value = listOf(product) + addedProducts.value
        return product.id
    }
    override suspend fun confirmPriceFreshness(productId: String, storeId: String) {}
    override suspend fun reportDiscrepancy(productId: String, storeId: String, reason: String, notes: String?) {}
    override fun getShoppingBasket(): Flow<ShoppingBasket> = flowOf(
        ShoppingBasket(
            id = "b1",
            title = "Basket",
            items = emptyList(),
            singleStoreEstimatedTotal = 0.0,
            optimizedEstimatedTotal = 0.0,
            estimatedSavingsAmount = 0.0,
            storeSplits = emptyList()
        )
    )
    override suspend fun toggleItemChecked(itemId: String) {}
    override suspend fun updateItemQuantity(itemId: String, newQuantity: Int) {}
    override suspend fun addShoppingItem(name: String, quantity: Int, packageSize: String) {}
    override suspend fun deleteShoppingItem(itemId: String) {}
    override suspend fun clearCheckedItems() {}
    override fun getSavedShoppingLists(): Flow<List<SavedShoppingList>> = flowOf(emptyList())
    override fun getPriceWatchAlerts(): Flow<List<PriceWatchAlert>> = flowOf(emptyList())
    override suspend fun loadSavedListIntoActiveBasket(listId: String) {}
    override fun getNearbyStores(): Flow<List<Store>> = flowOf(nearbyStoresToEmit)
    override fun getAllProducts(): Flow<List<Product>> = flowOf(emptyList())
    override suspend fun submitPrice(productId: String, storeId: String, price: Double, mrp: Double, sourceType: String, evidenceId: String?) {}
    override fun getPendingOfflineSubmissionCount(): Flow<Int> = flowOf(0)
    override fun getQueuedSubmissions(): Flow<List<QueuedSubmissionEntity>> = flowOf(emptyList())
    override suspend fun enqueueOfflinePrice(productId: String, storeId: String, price: Double, mrp: Double, sourceType: String): String = "id"
    override suspend fun syncOfflineSubmissions(): Int = 0
    override suspend fun retryQueuedSubmission(submissionId: String): Boolean = false
    override suspend fun cancelQueuedSubmission(submissionId: String) {}
    override fun getUserProfile(): Flow<UserProfile> = flowOf(
        UserProfile("u1", "Tester", "test@test.com", null, 50, 0, 0, 0, 100, "Today", "Scout")
    )
    override fun getSubmissionHistory(): Flow<List<PriceSubmission>> = flowOf(emptyList())
    override fun getMyAddedProducts(): Flow<List<Product>> = addedProducts
    override fun isUserLoggedIn(): Flow<Boolean> = flowOf(true)
    override suspend fun loginWithGoogle(idToken: String?, email: String?, displayName: String?, photoUrl: String?): Boolean = true
    override suspend fun signInWithEmail(email: String, password: String): Boolean = true
    override suspend fun signUpWithEmail(name: String, email: String, password: String): Boolean = true
    override suspend fun loginAsGuest() {}
    override suspend fun logout() {}
    override fun getAvailableProfiles(): Flow<List<UserProfile>> = flowOf(emptyList())
    override suspend fun switchProfile(profileId: String) {}
    override fun getStoreById(storeId: String): Flow<Store?> = flowOf(null)
    override fun getStorePrices(storeId: String): Flow<List<StorePrice>> = flowOf(emptyList())
    override suspend fun requestPreVisitPriceCheck(storeId: String, productId: String?): Boolean = false
    override suspend fun claimStore(storeId: String, merchantName: String, contactPhone: String): Boolean = false
    override fun getGoogleTasksAuthState(): Flow<GoogleTasksAuthState> = flowOf(GoogleTasksAuthState(false))
    override suspend fun connectGoogleTasks(email: String): Boolean = false
    override suspend fun disconnectGoogleTasks(): Boolean = true
    override fun getGoogleTaskLists(): Flow<List<GoogleTaskList>> = flowOf(emptyList())
    override fun getGoogleTasksForList(listId: String): Flow<List<GoogleTaskItem>> = flowOf(emptyList())
    override suspend fun importGoogleTasks(selectedTasks: List<GoogleTaskItem>): Int = 0
    override fun getNotifications(): Flow<List<AppNotification>> = flowOf(emptyList())
    override suspend fun markNotificationAsRead(notificationId: String) {}
    override suspend fun markAllNotificationsAsRead() {}
    override fun getActiveYellowStickers(): Flow<List<YellowStickerItem>> = flowOf(emptyList())
    override fun getNearbyBounties(): Flow<List<BountyItem>> = flowOf(emptyList())
    override suspend fun claimBounty(bountyId: String): Boolean = false
    override suspend fun updateLocation(location: LocationInfo) {}
    override suspend fun dismissKarmaPrompt(promptId: String) {}
    override suspend fun clearAllData(wipeServerDb: Boolean): Boolean = true
}

