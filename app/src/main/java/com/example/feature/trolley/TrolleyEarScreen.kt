package com.example.feature.trolley

import androidx.compose.material.icons.automirrored.filled.*

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrolleyEarScreen(
    onNavigateBack: () -> Unit
) {
    var isListening by remember { mutableStateOf(true) }
    var currentItem by remember { mutableStateOf("Lurpak Slightly Salted Spreadable Butter 500g") }
    var currentPrice by remember { mutableStateOf("£4.50") }
    var whisperMessage by remember {
        mutableStateOf("Lurpak is £4.50 here at Tesco Express. Skip it — Aldi on Camden High St has it for £2.99, saving you £1.51.")
    }
    var recommendation by remember { mutableStateOf("SKIP") } // "BUY", "SKIP", "CHECK_LOYALTY"

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("The Trolley Ear (Audio Copilot)") },
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Status Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isListening) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = null,
                        tint = if (isListening) Color(0xFF2E7D32) else Color(0xFFC62828),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isListening) "Audio Copilot Active (Earphones On)" else "Muted",
                        color = if (isListening) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(30.dp))

                // Pulsing Audio Ring
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .scale(if (isListening) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32).copy(alpha = 0.15f))
                    )
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Spacer(Modifier.height(30.dp))

                // Recommendation Badge
                val (badgeColor, badgeText) = when (recommendation) {
                    "SKIP" -> Color(0xFFC62828) to "⚠️ SKIP HERE (CHEAPER NEARBY)"
                    "BUY" -> Color(0xFF2E7D32) to "✓ PUT IN TROLLEY (BEST DEAL)"
                    else -> Color(0xFFE65100) to "🏷️ SCAN CLUBCARD AT TILL"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(badgeText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(Modifier.height(16.dp))

                // Speech Bubble Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Current Scanned Item:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(currentItem, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Store Shelf Price: $currentPrice", fontWeight = FontWeight.SemiBold, color = Color(0xFFC62828), fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Whisper Advice to Shopper:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "\"$whisperMessage\"",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Controls
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        // Demo toggling next simulated product
                        if (currentItem.contains("Lurpak")) {
                            currentItem = "Yorkshire Tea 160 Bags"
                            currentPrice = "£5.50"
                            recommendation = "CHECK_LOYALTY"
                            whisperMessage = "Great deal on Yorkshire Tea with your Tesco Clubcard! Regular is £5.50, but you will pay £3.75 at the checkout."
                        } else {
                            currentItem = "British Whole Milk 4 Pints"
                            currentPrice = "£1.45"
                            recommendation = "BUY"
                            whisperMessage = "Put it in the trolley! Whole milk is at the best nearby price right here at £1.45."
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Simulate Barcode")
                }

                Button(
                    onClick = { isListening = !isListening },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) Color(0xFFC62828) else Color(0xFF2E7D32)
                    )
                ) {
                    Text(if (isListening) "Mute Copilot" else "Resume")
                }
            }
        }
    }
}

