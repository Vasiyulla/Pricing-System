package com.example.feature.scan

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.PriceBridgeRepositoryProvider
import com.example.core.data.PriceBridgeRepository
import com.example.core.model.PriceStatusBadgeType
import com.example.core.model.Product
import com.example.core.model.StorePrice
import com.example.core.vision.PriceVisionOcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class ScanViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: PriceBridgeRepository = PriceBridgeRepositoryProvider.repository
) : AndroidViewModel(application) {

    private val ocrEngine = PriceVisionOcrEngine(application.applicationContext)

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun onEvent(event: ScanUiEvent) {
        when (event) {
            is ScanUiEvent.OnCameraPermissionResult -> {
                _uiState.update { it.copy(isCameraPermissionGranted = event.isGranted) }
            }
            is ScanUiEvent.OnChangeCaptureMode -> {
                _uiState.update { it.copy(captureMode = event.mode) }
            }
            is ScanUiEvent.OnPhotoCaptured -> {
                handlePhotoCaptured(event)
            }
            is ScanUiEvent.OnRetakePhoto -> {
                _uiState.update {
                    it.copy(
                        capturedPhotoUri = null,
                        rawOcrText = null,
                        isProcessingOcr = false,
                        isScanning = true,
                        isSubmitSuccess = false
                    )
                }
            }
            is ScanUiEvent.OnProductNameChanged -> {
                _uiState.update { it.copy(productNameInput = event.name) }
            }
            is ScanUiEvent.OnStoreNameChanged -> {
                _uiState.update { it.copy(storeNameInput = event.store) }
            }
            is ScanUiEvent.OnPriceChanged -> {
                val cleanPrice = event.price.filter { it.isDigit() || it == '.' }
                _uiState.update { it.copy(priceInput = cleanPrice) }
            }
            is ScanUiEvent.OnUnitChanged -> {
                _uiState.update { it.copy(unitInput = event.unit) }
            }
            is ScanUiEvent.OnEvidenceTypeChanged -> {
                _uiState.update { it.copy(evidenceType = event.type) }
            }
            is ScanUiEvent.OnSubmitPriceWithPhoto -> {
                submitPriceWithPhoto()
            }
            is ScanUiEvent.OnDismissSuccess -> {
                _uiState.update {
                    it.copy(
                        capturedPhotoUri = null,
                        productNameInput = "",
                        priceInput = "",
                        isSubmitSuccess = false,
                        isScanning = true
                    )
                }
            }
            is ScanUiEvent.OnToggleTorch -> {
                _uiState.update { it.copy(isTorchOn = !it.isTorchOn) }
            }
            is ScanUiEvent.OnOpenManualDialog -> {
                _uiState.update { it.copy(isManualDialogVisible = true, manualBarcodeText = "") }
            }
            is ScanUiEvent.OnDismissManualDialog -> {
                _uiState.update { it.copy(isManualDialogVisible = false) }
            }
            is ScanUiEvent.OnManualBarcodeTextChanged -> {
                _uiState.update { it.copy(manualBarcodeText = event.text) }
            }
            is ScanUiEvent.OnSubmitManualBarcode -> {
                val code = _uiState.value.manualBarcodeText.trim()
                if (code.isNotBlank()) {
                    _uiState.update { it.copy(isManualDialogVisible = false) }
                    lookupBarcode(code)
                }
            }
            is ScanUiEvent.OnBarcodeScanned -> {
                lookupBarcode(event.barcode)
            }
            is ScanUiEvent.OnSelectSampleBarcode -> {
                lookupBarcode(event.barcode)
            }
            is ScanUiEvent.OnClearDetection -> {
                _uiState.update {
                    it.copy(
                        scannedBarcode = null,
                        matchedProduct = null,
                        notFoundBarcode = null,
                        isScanning = true
                    )
                }
            }
            else -> {
                // Navigation events handled by screen callbacks
            }
        }
    }

    private fun submitPriceWithPhoto() {
        val state = _uiState.value
        val price = state.priceInput.toDoubleOrNull() ?: 0.0
        val productName = state.productNameInput.trim()
        val storeName = state.storeNameInput.trim()

        if (price <= 0.0 || productName.isBlank() || storeName.isBlank()) {
            return
        }

        _uiState.update { it.copy(isSubmittingPrice = true) }

        viewModelScope.launch {
            var resolvedProductId: String? = null
            var resolvedStoreId = "store_tesco_camden"
            try {
                // 1. Resolve or create a real Product ID in PostgreSQL
                val existingProduct = repository.searchProducts(productName, null).firstOrNull()?.firstOrNull()
                resolvedProductId = if (existingProduct != null) {
                    existingProduct.id
                } else {
                    val newId = "prod_${UUID.randomUUID().toString().take(12)}"
                    repository.addProduct(
                        Product(
                            id = newId,
                            name = productName,
                            categoryId = "Pantry & Staples",
                            quantityDescription = state.unitInput.ifBlank { "1 unit" },
                            packageSizeBadge = state.unitInput.ifBlank { "1 unit" }.take(8),
                            imageUrl = "",
                            bestStorePrice = StorePrice(
                                storeId = resolvedStoreId,
                                storeName = storeName,
                                distanceFormatted = "--",
                                price = price,
                                mrp = price * 1.15,
                                lastObservedRelativeTime = "Just now",
                                sourceDescription = "Photo scan",
                                statusBadge = PriceStatusBadgeType.RecentlyObserved,
                                isLowestPrice = true
                            ),
                            storesComparedCount = 1,
                            alternativePrices = emptyList()
                        )
                    )
                }

                // 2. Resolve real Store ID from nearby stores
                val nearbyStores = repository.getNearbyStores().firstOrNull() ?: emptyList()
                val matchedStore = nearbyStores.find {
                    it.name.contains(storeName, ignoreCase = true) ||
                        storeName.contains(it.name, ignoreCase = true)
                } ?: nearbyStores.firstOrNull()
                if (matchedStore != null) {
                    resolvedStoreId = matchedStore.id
                }

                // 3. Upload EXIF-stripped photo evidence if real file exists
                val photoUri = state.capturedPhotoUri
                val evidenceId = if (!photoUri.isNullOrBlank() &&
                    !photoUri.startsWith("sample_") &&
                    !photoUri.startsWith("photo_captured_")
                ) {
                    repository.uploadEvidence(
                        productId = resolvedProductId,
                        storeId = resolvedStoreId,
                        sourceType = state.evidenceType,
                        imagePath = photoUri,
                        ocrRawText = state.rawOcrText ?: ""
                    )
                } else {
                    null
                }

                // 4. Submit price to append-only ledger
                repository.submitPrice(
                    productId = resolvedProductId,
                    storeId = resolvedStoreId,
                    price = price,
                    mrp = price * 1.15,
                    sourceType = state.evidenceType,
                    evidenceId = evidenceId
                )
            } catch (_: Exception) {
                // Offline fallback: enqueue into Room WorkManager sync queue
                repository.enqueueOfflinePrice(
                    productId = resolvedProductId ?: productName.lowercase().replace(" ", "_").take(30),
                    storeId = resolvedStoreId,
                    price = price,
                    mrp = price * 1.15,
                    sourceType = state.evidenceType
                )
            }

            _uiState.update {
                it.copy(
                    isSubmittingPrice = false,
                    isSubmitSuccess = true
                )
            }
        }
    }

    private fun lookupBarcode(barcode: String) {
        _uiState.update {
            it.copy(
                isSearching = true,
                scannedBarcode = barcode,
                isScanning = false,
                matchedProduct = null,
                notFoundBarcode = null
            )
        }

        viewModelScope.launch {
            val product = repository.getProductByBarcode(barcode).firstOrNull()
            if (product != null) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        matchedProduct = product,
                        notFoundBarcode = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        matchedProduct = null,
                        notFoundBarcode = barcode
                    )
                }
            }
        }
    }

    private fun handlePhotoCaptured(event: ScanUiEvent.OnPhotoCaptured) {
        val uriStr = event.photoUri
        _uiState.update {
            it.copy(
                capturedPhotoUri = uriStr,
                isScanning = false,
                isProcessingOcr = true
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Resolve bitmap from direct bitmap or file path / Uri
                val bitmap = when {
                    event.bitmap != null -> event.bitmap
                    uriStr.startsWith("content://") || uriStr.startsWith("file://") -> {
                        val parsedUri = Uri.parse(uriStr)
                        @Suppress("DEPRECATION")
                        android.provider.MediaStore.Images.Media.getBitmap(
                            getApplication<Application>().contentResolver,
                            parsedUri
                        )
                    }
                    File(uriStr).exists() -> {
                        BitmapFactory.decodeFile(uriStr)
                    }
                    else -> null
                }

                if (bitmap != null) {
                    val visionText = ocrEngine.processBitmap(bitmap)
                    if (visionText != null && visionText.text.isNotBlank()) {
                        val tagResult = ocrEngine.parseShelfTag(visionText)
                        val detectedPriceStr = tagResult.detectedPrice?.let { p ->
                            if (p % 1.0 == 0.0) p.toInt().toString() else p.toString()
                        } ?: ""
                        val detectedMrpStr = tagResult.detectedMrp?.let { m ->
                            if (m % 1.0 == 0.0) m.toInt().toString() else m.toString()
                        } ?: ""

                        _uiState.update { current ->
                            current.copy(
                                isProcessingOcr = false,
                                isOcrDetected = detectedPriceStr.isNotBlank() || !tagResult.detectedProductName.isNullOrBlank(),
                                ocrConfidenceScore = tagResult.confidenceScore,
                                rawOcrText = tagResult.rawText,
                                priceInput = detectedPriceStr.ifBlank { current.priceInput.ifBlank { "135" } },
                                mrpInput = detectedMrpStr.ifBlank { current.mrpInput.ifBlank { "150" } },
                                productNameInput = tagResult.detectedProductName ?: current.productNameInput.ifBlank { "Supermarket Shelf Product" },
                                unitInput = tagResult.detectedUnit ?: current.unitInput,
                                storeNameInput = tagResult.detectedStoreName ?: current.storeNameInput
                            )
                        }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fallback if OCR returned empty
            _uiState.update { current ->
                current.copy(
                    isProcessingOcr = false,
                    isOcrDetected = false,
                    ocrConfidenceScore = null,
                    priceInput = current.priceInput.ifBlank { "135" },
                    mrpInput = current.mrpInput.ifBlank { "150" },
                    productNameInput = current.productNameInput.ifBlank { "Supermarket Shelf Product" }
                )
            }
        }
    }
}
