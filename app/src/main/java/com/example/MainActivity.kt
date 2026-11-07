package com.example

import android.os.Bundle

import android.widget.Toast

import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent

import androidx.activity.enableEdgeToEdge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.Manifest

import android.location.Geocoder

import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.remember

import androidx.compose.ui.platform.LocalContext

import com.example.core.data.NetworkPriceBridgeRepository

import com.example.core.data.PriceBridgeRepositoryProvider

import com.example.core.location.DeviceLocationManager

import com.example.core.session.AppSessionManager

import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

import java.util.Locale

import androidx.navigation.NavHostController

import androidx.navigation.NavType

import androidx.navigation.compose.NavHost

import androidx.navigation.compose.composable

import androidx.navigation.compose.rememberNavController

import androidx.navigation.navArgument

import com.example.feature.addproduct.AddProductScreen

import com.example.feature.auth.AuthScreen

import com.example.feature.basket.BasketScreen

import com.example.feature.basket.TwoStopBasketScreen

import com.example.feature.bounty.BountyRadarScreen

import com.example.feature.home.HomeScreen

import com.example.feature.productdetail.ProductDetailScreen

import com.example.feature.profile.ProfileScreen

import com.example.feature.scan.ScanScreen

import com.example.feature.search.SearchScreen

import com.example.feature.store.StoreDetailScreen

import com.example.feature.submit.SubmitPriceScreen

import com.example.feature.trolley.TrolleyEarScreen

import com.example.feature.yellowsticker.YellowStickerScreen

import com.example.feature.copilot.PriceCopilotScreen

import com.example.ui.theme.PriceBridgeTheme

/**

 * Navigation route constants for Price Bridge.

 */

object Routes {

    const val HOME = "home"

    const val SEARCH = "search"

    const val SCAN = "scan"

    const val SAVED = "saved"

    const val PROFILE = "profile"

    const val AUTH = "auth"

    const val PRODUCT_DETAIL = "product/{productId}"

    const val STORE_DETAIL = "store/{storeId}"

    const val SUBMIT_PRICE = "submit_price?productId={productId}"

    const val ADD_PRODUCT = "add_product?barcode={barcode}"

    const val TROLLEY_EAR = "trolley_ear"

    const val TWO_STOP_BASKET = "two_stop_basket"

    const val YELLOW_STICKERS = "yellow_stickers"

    const val BOUNTIES = "bounties"

    const val COPILOT = "copilot"

    fun productDetail(productId: String) = "product/$productId"

    fun storeDetail(storeId: String) = "store/$storeId"

    fun submitPrice(productId: String? = null): String {

        return if (productId != null) "submit_price?productId=$productId" else "submit_price"

    }

    fun addProduct(barcode: String? = null): String {

        return if (barcode != null) "add_product?barcode=$barcode" else "add_product"

    }

}

class MainActivity : ComponentActivity() {

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchLiveDeviceLocation()
        }
    }

    private fun fetchLiveDeviceLocation() {
        val locManager = DeviceLocationManager(this)
        CoroutineScope(Dispatchers.IO).launch {
            val geo = locManager.getAccurateLocation(highAccuracy = true)
            withContext(Dispatchers.Main) {
                (PriceBridgeRepositoryProvider.repository as? NetworkPriceBridgeRepository)?.setCoordinates(
                    lat = geo.latitude,
                    lon = geo.longitude,
                    neighborhood = geo.areaName
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        AppSessionManager.init(this)
        PriceBridgeRepositoryProvider.init(this)

        val locManager = DeviceLocationManager(this)
        if (locManager.hasLocationPermission()) {
            fetchLiveDeviceLocation()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        enableEdgeToEdge()

        setContent {

            PriceBridgeTheme {

                val navController = rememberNavController()

                PriceBridgeNavHost(navController = navController)

            }

        }

    }

}

@Composable

fun PriceBridgeNavHost(

    navController: NavHostController

) {

    val isLoggedIn by AppSessionManager.isLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    val startDestination = if (isLoggedIn) Routes.HOME else Routes.AUTH

    NavHost(

        navController = navController,

        startDestination = startDestination

    ) {

        // ── Home ──

        composable(Routes.HOME) {

            HomeScreen(

                onNavigateToSearch = {

                    navController.navigate(Routes.SEARCH) {

                        launchSingleTop = true

                    }

                },

                onNavigateToScan = {

                    navController.navigate(Routes.SCAN) {

                        launchSingleTop = true

                    }

                },

                onNavigateToProductDetail = { productId ->

                    navController.navigate(Routes.productDetail(productId))

                },

                onNavigateToBasket = {

                    navController.navigate(Routes.SAVED) {

                        launchSingleTop = true

                    }

                },

                onNavigateToSaved = {

                    navController.navigate(Routes.SAVED) {

                        launchSingleTop = true

                    }

                },

                onNavigateToProfile = {

                    navController.navigate(Routes.PROFILE) {

                        launchSingleTop = true

                    }

                },

                onNavigateToStore = { storeId ->

                    navController.navigate(Routes.storeDetail(storeId))

                },

                onNavigateToTrolleyEar = {

                    navController.navigate(Routes.TROLLEY_EAR)

                },

                onNavigateToTwoStopBasket = {

                    navController.navigate(Routes.TWO_STOP_BASKET)

                },

                onNavigateToYellowStickers = {

                    navController.navigate(Routes.YELLOW_STICKERS)

                },

                onNavigateToBounties = {

                    navController.navigate(Routes.BOUNTIES)

                },

                onNavigateToCopilot = {

                    navController.navigate(Routes.COPILOT)

                }

            )

        }

        // ── Search ──

        composable(Routes.SEARCH) {

            SearchScreen(

                onNavigateBack = { navController.popBackStack() },

                onNavigateToProductDetail = { productId ->

                    navController.navigate(Routes.productDetail(productId))

                },

                onNavigateToHome = {

                    navController.navigate(Routes.HOME) {

                        popUpTo(Routes.HOME) { inclusive = true }

                        launchSingleTop = true

                    }

                },

                onNavigateToScan = {

                    navController.navigate(Routes.SCAN) {

                        launchSingleTop = true

                    }

                },

                onNavigateToSaved = {

                    navController.navigate(Routes.SAVED) {

                        launchSingleTop = true

                    }

                },

                onNavigateToProfile = {

                    navController.navigate(Routes.PROFILE) {

                        launchSingleTop = true

                    }

                }

            )

        }

        // ── Saved / Basket ──

        composable(Routes.SAVED) {

            BasketScreen(

                onNavigateBack = { navController.popBackStack() },

                onNavigateToHome = {

                    navController.navigate(Routes.HOME) {

                        popUpTo(Routes.HOME) { inclusive = true }

                        launchSingleTop = true

                    }

                },

                onNavigateToSearch = {

                    navController.navigate(Routes.SEARCH) {

                        launchSingleTop = true

                    }

                },

                onNavigateToScan = {

                    navController.navigate(Routes.SCAN) {

                        launchSingleTop = true

                    }

                },

                onNavigateToProfile = {

                    navController.navigate(Routes.PROFILE) {

                        launchSingleTop = true

                    }

                },

                onNavigateToTwoStopBasket = {

                    navController.navigate(Routes.TWO_STOP_BASKET)

                }

            )

        }

        // ── Profile ──

        composable(Routes.PROFILE) {

            ProfileScreen(

                onNavigateToHome = {

                    navController.navigate(Routes.HOME) {

                        popUpTo(Routes.HOME) { inclusive = true }

                        launchSingleTop = true

                    }

                },

                onNavigateToSearch = {

                    navController.navigate(Routes.SEARCH) {

                        launchSingleTop = true

                    }

                },

                onNavigateToScan = {

                    navController.navigate(Routes.SCAN) {

                        launchSingleTop = true

                    }

                },

                onNavigateToSaved = {

                    navController.navigate(Routes.SAVED) {

                        launchSingleTop = true

                    }

                },

                onNavigateToAuth = {

                    navController.navigate(Routes.AUTH)

                },

                onNavigateToBounties = {

                    navController.navigate(Routes.BOUNTIES)

                }

            )

        }

        // ── Scan ──

        composable(Routes.SCAN) {

            ScanScreen(

                onNavigateBack = { navController.popBackStack() },

                onNavigateToHome = {

                    navController.navigate(Routes.HOME) {

                        popUpTo(Routes.HOME) { inclusive = true }

                        launchSingleTop = true

                    }

                },

                onNavigateToSearch = {

                    navController.navigate(Routes.SEARCH) {

                        launchSingleTop = true

                    }

                },

                onNavigateToSaved = {

                    navController.navigate(Routes.SAVED) {

                        launchSingleTop = true

                    }

                },

                onNavigateToProfile = {

                    navController.navigate(Routes.PROFILE) {

                        launchSingleTop = true

                    }

                },

                onNavigateToProductDetail = { productId ->

                    navController.navigate(Routes.productDetail(productId))

                },

                onNavigateToAddProduct = { barcode ->

                    navController.navigate(Routes.addProduct(barcode))

                }

            )

        }

        // ── Product Detail ──

        composable(

            route = Routes.PRODUCT_DETAIL,

            arguments = listOf(navArgument("productId") { type = NavType.StringType })

        ) { backStackEntry ->

            val productId = backStackEntry.arguments?.getString("productId") ?: return@composable

            ProductDetailScreen(

                productId = productId,

                onNavigateBack = { navController.popBackStack() },

                onNavigateToSubmitPrice = { id ->

                    navController.navigate(Routes.submitPrice(id))

                },

                onNavigateToStore = { storeId ->

                    navController.navigate(Routes.storeDetail(storeId))

                }

            )

        }

        // ── Submit Price ──

        composable(

            route = Routes.SUBMIT_PRICE,

            arguments = listOf(

                navArgument("productId") {

                    type = NavType.StringType

                    defaultValue = ""

                    nullable = true

                }

            )

        ) { backStackEntry ->

            val productId = backStackEntry.arguments?.getString("productId")

                ?.takeIf { it.isNotBlank() }

            SubmitPriceScreen(

                onNavigateBack = { navController.popBackStack() },

                preselectedProductId = productId,

                onNavigateToAddProduct = {

                    navController.navigate(Routes.addProduct())

                }

            )

        }

        // ── Add Product ──

        composable(

            route = Routes.ADD_PRODUCT,

            arguments = listOf(

                navArgument("barcode") {

                    type = NavType.StringType

                    defaultValue = ""

                    nullable = true

                }

            )

        ) { backStackEntry ->

            val barcode = backStackEntry.arguments?.getString("barcode")

                ?.takeIf { it.isNotBlank() }

            AddProductScreen(

                onNavigateBack = { navController.popBackStack() },

                onProductCreated = { newProductId ->

                    navController.navigate(Routes.submitPrice(newProductId)) {

                        popUpTo(Routes.ADD_PRODUCT) { inclusive = true }

                    }

                },

                prefilledBarcode = barcode

            )

        }

        // ── Store Detail (Phase 4) ──

        composable(

            route = Routes.STORE_DETAIL,

            arguments = listOf(navArgument("storeId") { type = NavType.StringType })

        ) { backStackEntry ->

            val storeId = backStackEntry.arguments?.getString("storeId") ?: return@composable

            StoreDetailScreen(

                storeId = storeId,

                onNavigateBack = { navController.popBackStack() },

                onNavigateToProductDetail = { productId ->

                    navController.navigate(Routes.productDetail(productId))

                }

            )

        }

        // ── Auth & DPDP Onboarding ──

        composable(Routes.AUTH) {

            AuthScreen(

                onAuthSuccess = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                        }
                    }
                },

                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                        }
                    }
                }

            )

        }

        // ── The Trolley Ear (Audio Whisperer) ──

        composable(Routes.TROLLEY_EAR) {

            TrolleyEarScreen(onNavigateBack = { navController.popBackStack() })

        }

        // ── 2-Stop Pareto Basket Optimizer ──

        composable(Routes.TWO_STOP_BASKET) {

            TwoStopBasketScreen(onNavigateBack = { navController.popBackStack() })

        }

        // ── Yellow Sticker Clearance Radar ──

        composable(Routes.YELLOW_STICKERS) {

            YellowStickerScreen(onNavigateBack = { navController.popBackStack() })

        }

        // ── AI Bounty Hunter ──

        composable(Routes.BOUNTIES) {

            BountyRadarScreen(onNavigateBack = { navController.popBackStack() })

        }

        // ── Enterprise AI Shopping Copilot ──

        composable(Routes.COPILOT) {

            PriceCopilotScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProduct = { productId ->
                    navController.navigate(Routes.productDetail(productId))
                },
                onNavigateToStore = { storeId ->
                    navController.navigate(Routes.storeDetail(storeId))
                }
            )

        }

    }

}

private fun resolveNeighborhood(context: android.content.Context, lat: Double, lon: Double): String {

    return try {

        val geocoder = Geocoder(context, Locale.UK)

        @Suppress("DEPRECATION")

        val addresses = geocoder.getFromLocation(lat, lon, 1)

        if (!addresses.isNullOrEmpty()) {

            val addr = addresses[0]

            val borough = addr.subLocality ?: addr.locality ?: addr.subAdminArea ?: "London"

            "$borough, London"

        } else {

            "London, UK"

        }

    } catch (_: Exception) {

        "London, UK"

    }

}

