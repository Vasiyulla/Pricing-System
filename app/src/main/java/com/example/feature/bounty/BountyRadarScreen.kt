package com.example.feature.bounty

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
import androidx.compose.material.icons.filled.MilitaryTech
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BountyItem(
    val id: String,
    val storeName: String,
    val productName: String,
    val rewardPoints: Int,
    val distance: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BountyRadarScreen(
    onNavigateBack: () -> Unit
) {
    val sampleBounties = listOf(
        BountyItem(
            id = "b1",
            storeName = "Morrisons Chalk Farm",
            productName = "British Fresh Chicken Breast Fillets 650g",
            rewardPoints = 50,
            distance = "0.4 miles away"
        ),
        BountyItem(
            id = "b2",
            storeName = "M&S Foodhall Covent Garden",
            productName = "Warburtons Toastie Thick Sliced White Bread",
            rewardPoints = 75,
            distance = "0.8 miles away"
        ),
        BountyItem(
            id = "b3",
            storeName = "Asda Old Kent Road Superstore",
            productName = "Filippo Berio Extra Virgin Olive Oil 500ml",
            rewardPoints = 100,
            distance = "1.2 miles away"
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Bounty Hunter") },
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
            // Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.MilitaryTech,
                        contentDescription = null,
                        tint = Color(0xFF283593),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Fill Price Gaps & Earn Reputation", fontWeight = FontWeight.Bold, color = Color(0xFF283593), fontSize = 15.sp)
                        Text("Snap shelf labels for missing prices nearby. On-device AI validates your photo instantly!", fontSize = 12.sp, color = Color(0xFF3949AB))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Nearby High-Demand Bounties", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sampleBounties) { bounty ->
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
                                Text(bounty.storeName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("+${bounty.rewardPoints} Points", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            Text(bounty.productName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(bounty.distance, fontSize = 12.sp, color = Color.Gray)
                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = { /* Camera snap bounty */ },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Claim & Snap Shelf Tag")
                            }
                        }
                    }
                }
            }
        }
    }
}

