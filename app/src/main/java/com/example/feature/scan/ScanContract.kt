package com.example.feature.scan

import com.example.core.model.Product

enum class CaptureMode {
    PHOTO,
    BARCODE
}

data class ScanUiState(
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val isTorchOn: Boolean = false,
    val isScanning: Boolean = true,
    val isSearching: Boolean = false,
    val isCameraPermissionGranted: Boolean = false,
    val isProcessingOcr: Boolean = false,

    // Photo capture & price reporting state
    val capturedPhotoUri: String? = null,
    val rawOcrText: String? = null,
    val productNameInput: String = "",
    val storeNameInput: String = "",
    val priceInput: String = "",
    val mrpInput: String = "",
    val unitInput: String = "200 g",
    val evidenceType: String = "Shelf Tag",
    val ocrConfidenceScore: Int? = null,
    val ocrSourceLabel: String = "On-Device OCR",
    val isOcrDetected: Boolean = false,
    val isLowConfidenceOcr: Boolean = false,
    val isSubmittingPrice: Boolean = false,
    val isSubmitSuccess: Boolean = false,
    val nearbyStoreSuggestions: List<String> = listOf(
        "Tesco Express",
        "Sainsbury's Local",
        "Aldi",
        "Lidl",
        "M&S Foodhall"
    ),
    val popularProductSuggestions: List<String> = listOf(
        "Arla Protein Strawberry Yogurt Pouch 200g",
        "Lurpak Slightly Salted Butter 500g",
        "Warburtons Toastie White 800g",
        "British Whole Milk 2.27L",
        "Cathedral City Mature Cheddar 350g"
    ),

    // Barcode scanning state (backward-compatible)
    val scannedBarcode: String? = null,
    val matchedProduct: Product? = null,
    val notFoundBarcode: String? = null,
    val isManualDialogVisible: Boolean = false,
    val manualBarcodeText: String = "",
    val errorMessage: String? = null
)

sealed interface ScanUiEvent {
    data object OnBackClicked : ScanUiEvent
    data object OnToggleTorch : ScanUiEvent
    data class OnChangeCaptureMode(val mode: CaptureMode) : ScanUiEvent
    data class OnCameraPermissionResult(val isGranted: Boolean) : ScanUiEvent

    // Photo Capture & Form Events
    data class OnPhotoCaptured(val photoUri: String = "captured_product_photo", val bitmap: android.graphics.Bitmap? = null) : ScanUiEvent
    data object OnRetakePhoto : ScanUiEvent
    data class OnProductNameChanged(val name: String) : ScanUiEvent
    data class OnStoreNameChanged(val store: String) : ScanUiEvent
    data class OnPriceChanged(val price: String) : ScanUiEvent
    data class OnUnitChanged(val unit: String) : ScanUiEvent
    data class OnEvidenceTypeChanged(val type: String) : ScanUiEvent
    data object OnSubmitPriceWithPhoto : ScanUiEvent
    data object OnDismissSuccess : ScanUiEvent

    // Barcode events
    data object OnOpenManualDialog : ScanUiEvent
    data object OnDismissManualDialog : ScanUiEvent
    data class OnManualBarcodeTextChanged(val text: String) : ScanUiEvent
    data object OnSubmitManualBarcode : ScanUiEvent
    data class OnBarcodeScanned(val barcode: String) : ScanUiEvent
    data class OnSelectSampleBarcode(val barcode: String) : ScanUiEvent
    data object OnClearDetection : ScanUiEvent
    data class OnViewProductClicked(val productId: String) : ScanUiEvent
    data class OnAddProductClicked(val barcode: String) : ScanUiEvent
}
