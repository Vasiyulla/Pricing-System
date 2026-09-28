package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.feature.basket.BasketScreen
import com.example.feature.home.HomeScreen
import com.example.ui.theme.PriceBridgeTheme

enum class ScreenRoute {
    Home,
    Basket,
    Search,
    Scan,
    Profile
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PriceBridgeTheme {
                var currentScreen by remember { mutableStateOf(ScreenRoute.Home) }

                Crossfade(targetState = currentScreen, label = "screen_navigation") { screen ->
                    when (screen) {
                        ScreenRoute.Home -> {
                            HomeScreen(
                                onNavigateToSearch = {
                                    Toast.makeText(this@MainActivity, "Opening Search & Discovery…", Toast.LENGTH_SHORT).show()
                                },
                                onNavigateToScan = {
                                    Toast.makeText(this@MainActivity, "Opening Shelf Tag & Barcode Scanner…", Toast.LENGTH_SHORT).show()
                                },
                                onNavigateToProductDetail = { productId ->
                                    Toast.makeText(this@MainActivity, "Opening Comparison for $productId…", Toast.LENGTH_SHORT).show()
                                },
                                onNavigateToBasket = {
                                    currentScreen = ScreenRoute.Basket
                                },
                                onNavigateToSaved = {
                                    currentScreen = ScreenRoute.Basket
                                },
                                onNavigateToProfile = {
                                    Toast.makeText(this@MainActivity, "Opening Karma Profile…", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        ScreenRoute.Basket -> {
                            BasketScreen(
                                onNavigateBack = {
                                    currentScreen = ScreenRoute.Home
                                },
                                onNavigateToHome = {
                                    currentScreen = ScreenRoute.Home
                                },
                                onNavigateToSearch = {
                                    Toast.makeText(this@MainActivity, "Opening Search & Discovery…", Toast.LENGTH_SHORT).show()
                                },
                                onNavigateToScan = {
                                    Toast.makeText(this@MainActivity, "Opening Scanner to add item…", Toast.LENGTH_SHORT).show()
                                },
                                onNavigateToProfile = {
                                    Toast.makeText(this@MainActivity, "Opening Karma Profile…", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        else -> {
                            HomeScreen(
                                onNavigateToSearch = {},
                                onNavigateToScan = {},
                                onNavigateToProductDetail = {},
                                onNavigateToBasket = { currentScreen = ScreenRoute.Basket },
                                onNavigateToSaved = { currentScreen = ScreenRoute.Basket },
                                onNavigateToProfile = {}
                            )
                        }
                    }
                }
            }
        }
    }
}
