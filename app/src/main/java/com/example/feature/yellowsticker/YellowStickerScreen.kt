package com.example.feature.yellowsticker

import androidx.compose.material.icons.automirrored.filled.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class YellowStickerItem(
    val storeName: String,
    val productName: String,
    val wasPrice: String,
    val nowPrice: String,
    val discountPercent: Int,
    val expiryNote: String,
    val hoursRemaining: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YellowStickerScreen(
    onNavigateBack: () -> Unit
) {
    val sampleDeals = listOf(
        YellowStickerItem(
            storeName = "M&S Foodhall Covent Garden",
            productName = "M&S British Fresh Chicken Breast Fillets 650g",
            wasPrice = "£6.50",
            nowPrice = "£1.95",
            discountPercent = 70,
            expiryNote = "Use by midnight tonight",
            hoursRemaining = 3
        ),
        YellowStickerItem(
            storeName = "Sainsbury's Camden Superstore",
            productName = "Taste the Difference Sourdough Boule 400g",
            wasPrice = "£2.50",
            nowPrice = "£0.60",
            discountPercent = 76,
            expiryNote = "Fresh bakery clearance",
            hoursRemaining = 2
        ),
        YellowStickerItem(
            storeName = "Tesco Extra Surrey Quays",
            productName = "Tesco Finest Ribeye Steak 227g",
            wasPrice = "£5.75",
            nowPrice = "£2.10",
            discountPercent = 63,
            expiryNote = "Best before end of today",
            hoursRemaining = 4
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yellow Sticker Radar") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Header Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)) // Yellow tint
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocalOffer, contentDescription = null, tint = Color(0xFFF57F17), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Live Clearance Deals (Reduced to Clear)", fontWeight = FontWeight.Bold, color = Color(0xFFE65100), fontSize = 14.sp)
                        Text("Crowdsourced within 2 miles in the last 2 hours. First come, first served!", fontSize = 12.sp, color = Color(0xFF5D4037))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action Button
            Button(
                onClick = { /* Scan yellow sticker camera flow */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57F17)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan Yellow Sticker with Camera", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            // Feed of clearance deals
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sampleDeals) { deal ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(deal.storeName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFC62828))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("${deal.discountPercent}% OFF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            Text(deal.productName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(deal.nowPrice, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFC62828))
                                    Spacer(Modifier.width(8.dp))
                                    Text(deal.wasPrice, textDecoration = TextDecoration.LineThrough, color = Color.Gray, fontSize = 14.sp)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Expires in ${deal.hoursRemaining}h", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE65100))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

