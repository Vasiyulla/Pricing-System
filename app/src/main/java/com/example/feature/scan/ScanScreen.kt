package com.example.feature.scan

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.R
import com.example.core.model.Product
import com.example.core.ui.BottomBarDestination
import com.example.core.ui.PriceBridgeBottomBar
import com.example.ui.theme.spacing

/**
 * Stateful entry composable for the Camera Capture & Price Reporting screen.
 */
@Composable
fun ScanScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSaved: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToProductDetail: (String) -> Unit,
    onNavigateToAddProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ScanContent(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                is ScanUiEvent.OnBackClicked -> onNavigateBack()
                is ScanUiEvent.OnViewProductClicked -> onNavigateToProductDetail(event.productId)
                is ScanUiEvent.OnAddProductClicked -> onNavigateToAddProduct(event.barcode)
                else -> viewModel.onEvent(event)
            }
        },
        onBottomNavClicked = { destination ->
            when (destination) {
                BottomBarDestination.Home -> onNavigateToHome()
                BottomBarDestination.Search -> onNavigateToSearch()
                BottomBarDestination.Scan -> Unit
                BottomBarDestination.Saved -> onNavigateToSaved()
                BottomBarDestination.Profile -> onNavigateToProfile()
            }
        },
        onNavigateToHome = onNavigateToHome,
        modifier = modifier
    )
}

/**
 * Stateless Capture & Price Reporting Content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanContent(
    uiState: ScanUiState,
    onEvent: (ScanUiEvent) -> Unit,
    onBottomNavClicked: (BottomBarDestination) -> Unit,
    onNavigateToHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isFormActive = uiState.capturedPhotoUri != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isFormActive) {
                            stringResource(R.string.capture_form_title)
                        } else if (uiState.captureMode == CaptureMode.PHOTO) {
                            stringResource(R.string.capture_title)
                        } else {
                            stringResource(R.string.scan_title)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isFormActive) {
                                onEvent(ScanUiEvent.OnRetakePhoto)
                            } else {
                                onEvent(ScanUiEvent.OnBackClicked)
                            }
                        },
                        modifier = Modifier.testTag("scan_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (!isFormActive) {
                        // Flashlight toggle in camera mode
                        IconButton(
                            onClick = { onEvent(ScanUiEvent.OnToggleTorch) },
                            modifier = Modifier.testTag("scan_torch_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = null,
                                tint = if (uiState.isTorchOn) Color(0xFFFFD700) else Color.White
                            )
                        }
                        if (uiState.captureMode == CaptureMode.BARCODE) {
                            IconButton(
                                onClick = { onEvent(ScanUiEvent.OnOpenManualDialog) },
                                modifier = Modifier.testTag("scan_manual_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Keyboard,
                                    contentDescription = stringResource(R.string.scan_manual_entry),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.85f)
                )
            )
        },
        bottomBar = {
            PriceBridgeBottomBar(
                currentDestination = BottomBarDestination.Scan,
                onDestinationClick = onBottomNavClicked
            )
        },
        containerColor = if (isFormActive) MaterialTheme.colorScheme.surface else Color.Black,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (isFormActive) {
            // Photo Captured: Add Price Details Form
            CapturedPriceFormView(
                uiState = uiState,
                onEvent = onEvent,
                onDone = onNavigateToHome,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            // Live Camera Viewfinder & Shutter
            CameraViewfinderContent(
                uiState = uiState,
                onEvent = onEvent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }

    // Manual Barcode Input Bottom Sheet (preserved for barcode mode)
    if (uiState.isManualDialogVisible) {
        ManualBarcodeBottomSheet(
            barcodeText = uiState.manualBarcodeText,
            onTextChanged = { onEvent(ScanUiEvent.OnManualBarcodeTextChanged(it)) },
            onDismiss = { onEvent(ScanUiEvent.OnDismissManualDialog) },
            onSubmit = { onEvent(ScanUiEvent.OnSubmitManualBarcode) }
        )
    }
}

/**
 * Camera Viewfinder Screen with Shutter button and Mode Switcher.
 */
@Composable
private fun CameraViewfinderContent(
    uiState: ScanUiState,
    onEvent: (ScanUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode Switcher Pill (Photo Camera vs Barcode Scan)
        Row(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = uiState.captureMode == CaptureMode.PHOTO,
                onClick = { onEvent(ScanUiEvent.OnChangeCaptureMode(CaptureMode.PHOTO)) },
                label = { Text(stringResource(R.string.capture_mode_photo)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    labelColor = Color.White,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = null
            )

            FilterChip(
                selected = uiState.captureMode == CaptureMode.BARCODE,
                onClick = { onEvent(ScanUiEvent.OnChangeCaptureMode(CaptureMode.BARCODE)) },
                label = { Text(stringResource(R.string.capture_mode_barcode)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    labelColor = Color.White,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = null
            )
        }

        // Viewfinder Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.captureMode == CaptureMode.PHOTO) {
                // Photo Camera Framing Box
                CameraFramingViewfinder()

                // Guidance pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.70f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.capture_instruction),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // Barcode Laser Viewfinder
                ScannerViewfinder(isScanning = uiState.isScanning)

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.scan_instruction),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Bottom Controls: Shutter for Photo OR Barcode Results
        if (uiState.captureMode == CaptureMode.PHOTO) {
            CameraShutterBar(
                onCaptureClick = {
                    onEvent(ScanUiEvent.OnPhotoCaptured("photo_captured_${System.currentTimeMillis()}"))
                },
                onSampleTagClick = { tag ->
                    when (tag) {
                        "Fortune Oil" -> {
                            onEvent(ScanUiEvent.OnProductNameChanged("Fortune Sunlite Sunflower Oil 1L"))
                            onEvent(ScanUiEvent.OnPriceChanged("142"))
                        }
                        "Atta 5kg" -> {
                            onEvent(ScanUiEvent.OnProductNameChanged("Aashirvaad Shubh Chakki Atta 5kg"))
                            onEvent(ScanUiEvent.OnPriceChanged("245"))
                        }
                        else -> {
                            onEvent(ScanUiEvent.OnProductNameChanged("Amul Butter 500g"))
                            onEvent(ScanUiEvent.OnPriceChanged("275"))
                        }
                    }
                    onEvent(ScanUiEvent.OnPhotoCaptured("sample_shelf_label"))
                }
            )
        } else {
            // Barcode sample row & lookup result
            BarcodeControlsArea(
                uiState = uiState,
                onEvent = onEvent
            )
        }
    }
}

/**
 * Camera Viewfinder Framing Reticle.
 */
@Composable
private fun CameraFramingViewfinder(modifier: Modifier = Modifier) {
    val frameWidth = 300.dp
    val frameHeight = 260.dp

    Box(
        modifier = modifier
            .size(width = frameWidth, height = frameHeight)
            .border(
                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(24.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        CornerBrackets(size = frameHeight)

        // Center reticle crosshair
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(Color.White.copy(alpha = 0.2f), CircleShape)
        )
    }
}

/**
 * Camera Shutter Bar with large capture button and quick test samples.
 */
@Composable
private fun CameraShutterBar(
    onCaptureClick: () -> Unit,
    onSampleTagClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.spaceMd, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Quick Sample Tags for rapid demonstration
        Text(
            text = "Tap sample or click shutter to photograph:",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { onSampleTagClick("Fortune Oil") },
                label = { Text("📸 Fortune Oil Tag") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    labelColor = Color.White
                )
            )
            AssistChip(
                onClick = { onSampleTagClick("Atta 5kg") },
                label = { Text("🧾 Atta 5kg Receipt") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    labelColor = Color.White
                )
            )
            AssistChip(
                onClick = { onSampleTagClick("Amul Butter") },
                label = { Text("🏷️ Amul Butter Shelf") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    labelColor = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Shutter Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(4.dp, Color.White, CircleShape)
                .padding(5.dp)
                .background(Color.White, CircleShape)
                .clickable(onClick = onCaptureClick)
                .testTag("camera_shutter_button")
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = stringResource(R.string.capture_shutter_hint),
                tint = Color.Black,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

/**
 * The Form shown after snapping a photo: User enters Store Name, Product Name, Price, and Pack Size.
 */
@Composable
private fun CapturedPriceFormView(
    uiState: ScanUiState,
    onEvent: (ScanUiEvent) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isSubmitSuccess) {
        // Success celebration view
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.capture_success_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = stringResource(R.string.capture_success_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = uiState.productNameInput,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "At: ${uiState.storeNameInput}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Reported Price: ₹${uiState.priceInput} (${uiState.unitInput})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onEvent(ScanUiEvent.OnDismissSuccess) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.capture_btn_another))
                        }

                        Button(
                            onClick = onDone,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.btn_done))
                        }
                    }
                }
            }
        }
        return
    }

    // Scrollable Form
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.spacing.spaceMd),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. Photo Evidence Preview Card ──
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Photo Evidence",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Product & Shelf Tag Captured",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { onEvent(ScanUiEvent.OnRetakePhoto) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.capture_retake))
                    }
                }
            }
        }

        // ── 2. Product Name Input ──
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.capture_product_name_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = uiState.productNameInput,
                onValueChange = { onEvent(ScanUiEvent.OnProductNameChanged(it)) },
                placeholder = { Text(stringResource(R.string.capture_product_name_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (uiState.productNameInput.isNotBlank()) {
                        IconButton(onClick = { onEvent(ScanUiEvent.OnProductNameChanged("")) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Quick Product suggestions chips
            Text(
                text = "Quick Suggestion:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                uiState.popularProductSuggestions.forEach { suggestion ->
                    AssistChip(
                        onClick = { onEvent(ScanUiEvent.OnProductNameChanged(suggestion)) },
                        label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                }
            }
        }

        // ── 3. Store Name Input ──
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.capture_store_name_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = uiState.storeNameInput,
                onValueChange = { onEvent(ScanUiEvent.OnStoreNameChanged(it)) },
                placeholder = { Text(stringResource(R.string.capture_store_name_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = {
                    Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Nearby Store quick-picks
            Text(
                text = "Nearby Stores:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                uiState.nearbyStoreSuggestions.forEach { store ->
                    AssistChip(
                        onClick = { onEvent(ScanUiEvent.OnStoreNameChanged(store)) },
                        label = { Text(store, style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                }
            }
        }

        // ── 4. Selling Price Input ──
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.capture_price_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = uiState.priceInput,
                onValueChange = { onEvent(ScanUiEvent.OnPriceChanged(it)) },
                placeholder = { Text("e.g. 142") },
                singleLine = true,
                leadingIcon = {
                    Text(
                        text = "₹",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── 5. Pack Size / Unit ──
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.capture_unit_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val units = listOf("1 L", "1 kg", "500 g", "250 g", "100 g", "1 pc")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                units.forEach { unit ->
                    FilterChip(
                        selected = uiState.unitInput == unit,
                        onClick = { onEvent(ScanUiEvent.OnUnitChanged(unit)) },
                        label = { Text(unit) }
                    )
                }
            }
        }

        // ── 6. Evidence Type ──
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.capture_evidence_type_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val evidenceOptions = listOf(
                stringResource(R.string.capture_evidence_shelf),
                stringResource(R.string.capture_evidence_receipt),
                stringResource(R.string.capture_evidence_item)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                evidenceOptions.forEach { type ->
                    FilterChip(
                        selected = uiState.evidenceType == type,
                        onClick = { onEvent(ScanUiEvent.OnEvidenceTypeChanged(type)) },
                        label = { Text(type) }
                    )
                }
            }
        }

        // ── 7. Privacy & Karma Card ──
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Camera EXIF Location Stripped for Privacy",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Evidence is reviewed by community moderators. You earn +20 Karma Points for submitting photo evidence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── 8. Submit Button ──
        val isFormValid = uiState.priceInput.isNotBlank() &&
                uiState.productNameInput.isNotBlank() &&
                uiState.storeNameInput.isNotBlank() &&
                !uiState.isSubmittingPrice

        Button(
            onClick = { onEvent(ScanUiEvent.OnSubmitPriceWithPhoto) },
            enabled = isFormValid,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("btn_submit_captured_price")
        ) {
            if (uiState.isSubmittingPrice) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.capture_submit_btn),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Barcode Scanner controls area (preserved when user toggles to barcode mode).
 */
@Composable
private fun BarcodeControlsArea(
    uiState: ScanUiState,
    onEvent: (ScanUiEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.spaceMd, vertical = 6.dp)
    ) {
        Text(
            text = stringResource(R.string.scan_sample_barcodes),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        val testBarcodes = listOf(
            "Fortune Oil" to "8901030383709",
            "Amul Milk" to "8901262010058",
            "Atta 5kg" to "8901725181222",
            "Maggi" to "8901058852881",
            "Dettol" to "8901030825315",
            "New Item" to "8909999000001"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            testBarcodes.forEach { (name, barcode) ->
                FilterChip(
                    selected = uiState.scannedBarcode == barcode,
                    onClick = { onEvent(ScanUiEvent.OnSelectSampleBarcode(barcode)) },
                    label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        labelColor = Color.White,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Result Card Area
        when {
            uiState.isSearching -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Looking up barcode ${uiState.scannedBarcode ?: ""}…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            uiState.matchedProduct != null -> {
                MatchedProductCard(
                    product = uiState.matchedProduct,
                    barcode = uiState.scannedBarcode ?: "",
                    onViewProduct = { onEvent(ScanUiEvent.OnViewProductClicked(uiState.matchedProduct.id)) },
                    onScanAgain = { onEvent(ScanUiEvent.OnClearDetection) }
                )
            }

            uiState.notFoundBarcode != null -> {
                ProductNotFoundCard(
                    barcode = uiState.notFoundBarcode,
                    onAddProduct = { onEvent(ScanUiEvent.OnAddProductClicked(uiState.notFoundBarcode)) },
                    onScanAgain = { onEvent(ScanUiEvent.OnClearDetection) }
                )
            }
        }
    }
}

/**
 * Animated Viewfinder Frame with laser scanning line.
 */
@Composable
private fun ScannerViewfinder(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val frameSize = 250.dp
    val transition = rememberInfiniteTransition(label = "scan_laser")
    val laserProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Box(
        modifier = modifier
            .size(frameSize)
            .border(
                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(20.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        CornerBrackets(size = frameSize)

        if (isScanning) {
            val laserOffset = (frameSize * laserProgress) - (frameSize / 2)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .offset(y = laserOffset)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.primary,
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun CornerBrackets(
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val bracketLength = 24.dp
    val strokeWidth = 4.dp
    val color = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.size(size)) {
        Box(modifier = Modifier.align(Alignment.TopStart).size(bracketLength, strokeWidth).background(color))
        Box(modifier = Modifier.align(Alignment.TopStart).size(strokeWidth, bracketLength).background(color))
        Box(modifier = Modifier.align(Alignment.TopEnd).size(bracketLength, strokeWidth).background(color))
        Box(modifier = Modifier.align(Alignment.TopEnd).size(strokeWidth, bracketLength).background(color))
        Box(modifier = Modifier.align(Alignment.BottomStart).size(bracketLength, strokeWidth).background(color))
        Box(modifier = Modifier.align(Alignment.BottomStart).size(strokeWidth, bracketLength).background(color))
        Box(modifier = Modifier.align(Alignment.BottomEnd).size(bracketLength, strokeWidth).background(color))
        Box(modifier = Modifier.align(Alignment.BottomEnd).size(strokeWidth, bracketLength).background(color))
    }
}

@Composable
private fun MatchedProductCard(
    product: Product,
    barcode: String,
    onViewProduct: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("scan_matched_product_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.scan_product_found),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onScanAgain, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.size(60.dp)
                ) {
                    if (product.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = product.imageUrl,
                            contentDescription = product.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${product.quantityDescription} · Barcode: $barcode",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Lowest: ${product.bestStorePrice.currencySymbol}${String.format("%.0f", product.bestStorePrice.price)} at ${product.bestStorePrice.storeName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onViewProduct,
                modifier = Modifier.fillMaxWidth().testTag("scan_view_product_btn")
            ) {
                Text(stringResource(R.string.btn_view_product))
            }
        }
    }
}

@Composable
private fun ProductNotFoundCard(
    barcode: String,
    onAddProduct: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("scan_product_not_found_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.scan_product_not_found),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onScanAgain, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Barcode $barcode is not registered yet. Add this product to help your neighborhood compare its price!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onAddProduct,
                modifier = Modifier.fillMaxWidth().testTag("scan_add_new_product_btn")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.btn_add_new_product))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualBarcodeBottomSheet(
    barcodeText: String,
    onTextChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.scan_manual_entry),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Enter 8 to 14 digit EAN/UPC barcode number",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = barcodeText,
                onValueChange = { input ->
                    if (input.all { it.isDigit() } && input.length <= 14) {
                        onTextChanged(input)
                    }
                },
                label = { Text("Barcode Digits") },
                placeholder = { Text("e.g. 8901030383742") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (barcodeText.isNotEmpty()) {
                        IconButton(onClick = { onTextChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (barcodeText.isNotBlank()) onSubmit()
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Quick Sample Staples:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { onTextChanged("8901030383742") },
                        label = { Text("Tata Salt (1kg)") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                    AssistChip(
                        onClick = { onTextChanged("8901262010052") },
                        label = { Text("Amul Butter (500g)") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                    AssistChip(
                        onClick = { onTextChanged("8901725181222") },
                        label = { Text("Fortune Oil (1L)") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onSubmit,
                enabled = barcodeText.length in 8..14,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Look Up Barcode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
