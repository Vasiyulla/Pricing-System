package com.example.feature.trolley

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.speech.tts.TextToSpeech
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.data.PriceBridgeRepositoryProvider
import com.example.core.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class TrolleySampleItem(
    val id: String,
    val name: String,
    val storePrice: String,
    val bestAlternativePrice: String,
    val altStoreName: String,
    val distance: String,
    val recommendation: String, // "BUY", "SKIP", "CHECK_LOYALTY"
    val voiceScript: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrolleyEarScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = PriceBridgeRepositoryProvider.repository

    var trolleyItems by remember { mutableStateOf<List<TrolleySampleItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isListening by remember { mutableStateOf(true) }
    var currentItemIndex by remember { mutableStateOf(0) }
    val currentItem = trolleyItems.getOrNull(currentItemIndex)

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    // Check headphone connectivity
    var hasHeadphones by remember { mutableStateOf(false) }

    // Pulse animation
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

    // Load live product whispers from Go backend + initialize TTS
    LaunchedEffect(Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            hasHeadphones = devices.any { dev ->
                dev.type in listOf(
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLE_HEADSET
                )
            }
        }

        ttsEngine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale.UK
                ttsEngine?.setSpeechRate(1.05f)
                isTtsReady = true
            }
        }

        withContext(Dispatchers.IO) {
            try {
                val loc = repository.getLocation().firstOrNull()
                val lat = loc?.latitude ?: 0.0
                val lon = loc?.longitude ?: 0.0
                val stores = repository.getNearbyStores().firstOrNull().orEmpty()
                val currentStoreId = stores.firstOrNull()?.id

                if ((lat != 0.0 || lon != 0.0) && currentStoreId != null) {
                    val products = repository.getAllProducts().firstOrNull().orEmpty().take(6)
                    val fetched = products.mapNotNull { prod ->
                        try {
                            val whisper = ApiClient.service.getTrolleyWhisper(prod.id, currentStoreId, lat, lon)
                            val curPrice = String.format(Locale.UK, "£%.2f", whisper.currentPricePence / 100.0)
                            val altStore = whisper.alternativeStore?.name ?: "Current Store"
                            val altPricePence = (whisper.currentPricePence - (whisper.potentialSavingsPence ?: 0)).coerceAtLeast(10)
                            val altPrice = String.format(Locale.UK, "£%.2f", altPricePence / 100.0)
                            val distStr = whisper.alternativeStore?.distanceKm?.let {
                                if (it < 1.0) "${(it * 1000).toInt()}m away" else String.format(Locale.UK, "%.1f km away", it)
                            } ?: "Nearby"
                            TrolleySampleItem(
                                id = prod.id,
                                name = whisper.productName.ifBlank { prod.name },
                                storePrice = curPrice,
                                bestAlternativePrice = altPrice,
                                altStoreName = altStore,
                                distance = distStr,
                                recommendation = whisper.recommendation,
                                voiceScript = whisper.whisperText
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                    trolleyItems = fetched
                    currentItemIndex = 0
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsEngine?.stop()
            ttsEngine?.shutdown()
        }
    }

    fun speakAdvice(text: String) {
        if (isListening && isTtsReady && text.isNotBlank()) {
            ttsEngine?.stop()
            ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "trolley_advice")
        }
    }

    // Speak initial item when TTS and live items are ready
    LaunchedEffect(isTtsReady, trolleyItems) {
        val script = currentItem?.voiceScript
        if (isTtsReady && !script.isNullOrBlank()) {
            speakAdvice(script)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("The Trolley Ear (Audio Copilot)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (currentItem == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (isLoading) {
                            "Checking tracked stores near your location…"
                        } else {
                            "No nearby tracked stores for live audio coaching yet"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isLoading) {
                            "Gathering nearby shelf prices for real-time comparison."
                        } else {
                            "Audio coaching activates automatically when tracked stores and live price reports exist near your selected location."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onNavigateBack) {
                        Text("Go Back")
                    }
                }
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Audio Status Pill
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
                        text = if (isListening) {
                            if (hasHeadphones) "Earphones Connected • Audio Copilot Active" else "Audio Copilot Active (Speaker / Earbuds)"
                        } else "Muted",
                        color = if (isListening) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Pulsing Audio Ring
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .scale(if (isListening) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32).copy(alpha = 0.15f))
                    )
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(if (isListening) Color(0xFF2E7D32) else Color(0xFF757575)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isListening) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Recommendation Badge
                val (badgeColor, badgeText) = when (currentItem.recommendation) {
                    "SKIP" -> Color(0xFFC62828) to "⚠️ SKIP HERE (CHEAPER NEARBY)"
                    "BUY" -> Color(0xFF2E7D32) to "✓ PUT IN TROLLEY (BEST DEAL)"
                    else -> Color(0xFFE65100) to "🏷️ CHECK STORE PROMO"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(badgeText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(Modifier.height(14.dp))

                // Speech Bubble Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Current Item in Aisle:", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            IconButton(onClick = { speakAdvice(currentItem.voiceScript) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Replay, contentDescription = "Replay", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Text(currentItem.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = "Shelf Price: ${currentItem.storePrice}  •  Best Nearby: ${currentItem.bestAlternativePrice} (${currentItem.altStoreName})",
                            fontWeight = FontWeight.SemiBold,
                            color = if (currentItem.recommendation == "SKIP") Color(0xFFC62828) else Color(0xFF2E7D32),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(Modifier.height(8.dp))
                        Text("Spoken Copilot Advice:", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = "\"${currentItem.voiceScript}\"",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Bottom Carousel & Controls
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Aisle Barcode Simulator (Tap to Test Next Item):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(trolleyItems.indices.toList()) { idx ->
                        val item = trolleyItems[idx]
                        val isSelected = currentItemIndex == idx
                        Surface(
                            onClick = {
                                currentItemIndex = idx
                                speakAdvice(item.voiceScript)
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Text(
                                text = item.name.take(18) + "…",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (trolleyItems.isNotEmpty()) {
                                currentItemIndex = (currentItemIndex + 1) % trolleyItems.size
                                speakAdvice(trolleyItems[currentItemIndex].voiceScript)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Next Barcode")
                    }

                    Button(
                        onClick = {
                            isListening = !isListening
                            if (!isListening) ttsEngine?.stop() else speakAdvice(currentItem.voiceScript)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isListening) Color(0xFFC62828) else Color(0xFF2E7D32)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isListening) "Mute Voice" else "Resume Voice")
                    }
                }
            }
        }
        }
    }
}
