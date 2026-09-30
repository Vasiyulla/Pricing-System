package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.feature.addproduct.AddProductScreen
import com.example.feature.auth.AuthScreen
import com.example.feature.basket.BasketScreen
import com.example.feature.home.HomeScreen
import com.example.feature.productdetail.ProductDetailScreen
import com.example.feature.profile.ProfileScreen
import com.example.feature.scan.ScanScreen
import com.example.feature.search.SearchScreen
import com.example.feature.submit.SubmitPriceScreen
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
    const val SUBMIT_PRICE = "submit_price?productId={productId}"
    const val ADD_PRODUCT = "add_product?barcode={barcode}"

    fun productDetail(productId: String) = "product/$productId"
    fun submitPrice(productId: String? = null): String {
        return if (productId != null) "submit_price?productId=$productId" else "submit_price"
    }
    fun addProduct(barcode: String? = null): String {
        return if (barcode != null) "add_product?barcode=$barcode" else "add_product"
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
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

        // ── Auth & DPDP Onboarding ──
        composable(Routes.AUTH) {
            AuthScreen(
                onAuthSuccess = { navController.popBackStack() },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
