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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VolumeOff
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
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

val TROLLEY_CATALOG = listOf(
    TrolleySampleItem(
        id = "item_1",
        name = "Nandini Toned Milk 500ml",
        storePrice = "₹26",
        bestAlternativePrice = "₹24",
        altStoreName = "Krishna Kirana",
        distance = "80m away",
        recommendation = "BUY",
        voiceScript = "Put it in the trolley! Nandini milk is at the best nearby price right here at ₹24."
    ),
    TrolleySampleItem(
        id = "item_2",
        name = "Fortune Sunflower Oil 1L",
        storePrice = "₹145",
        bestAlternativePrice = "₹118",
        altStoreName = "Reliance Smart",
        distance = "250m away",
        recommendation = "SKIP",
        voiceScript = "Fortune Oil is ₹145 here. Skip it! Reliance Smart 250 metres away has it for ₹118, saving you ₹27."
    ),
    TrolleySampleItem(
        id = "item_3",
        name = "Aashirvaad Superior MP Atta 5kg",
        storePrice = "₹280",
        bestAlternativePrice = "₹245",
        altStoreName = "DMart Ready",
        distance = "400m away",
        recommendation = "SKIP",
        voiceScript = "Aashirvaad Atta is ₹280 here. You can save ₹35 by getting it at DMart Ready for ₹245."
    ),
    TrolleySampleItem(
        id = "item_4",
        name = "Tata Tea Gold 500g",
        storePrice = "₹310",
        bestAlternativePrice = "₹310",
        altStoreName = "Current Store",
        distance = "0m",
        recommendation = "BUY",
        voiceScript = "Tata Tea Gold is at its 7-day lowest price right here at ₹310. Great deal!"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrolleyEarScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isListening by remember { mutableStateOf(true) }
    var currentItemIndex by remember { mutableStateOf(0) }
    val currentItem = TROLLEY_CATALOG[currentItemIndex]

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

    // Initialize Native Android TextToSpeech
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
                ttsEngine?.language = Locale.ENGLISH
                ttsEngine?.setSpeechRate(1.05f)
                isTtsReady = true
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
        if (isListening && isTtsReady) {
            ttsEngine?.stop()
            ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "trolley_advice")
        }
    }

    // Speak initial item when TTS becomes ready
    LaunchedEffect(isTtsReady) {
        if (isTtsReady) {
            speakAdvice(currentItem.voiceScript)
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
                            if (isListening) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.VolumeOff,
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
                    items(TROLLEY_CATALOG.indices.toList()) { idx ->
                        val item = TROLLEY_CATALOG[idx]
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
                            currentItemIndex = (currentItemIndex + 1) % TROLLEY_CATALOG.size
                            speakAdvice(TROLLEY_CATALOG[currentItemIndex].voiceScript)
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
