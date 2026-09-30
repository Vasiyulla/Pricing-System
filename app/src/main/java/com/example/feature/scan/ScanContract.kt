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

    // Photo capture & price reporting state
    val capturedPhotoUri: String? = null,
    val productNameInput: String = "",
    val storeNameInput: String = "More Supermarket, Indiranagar",
    val priceInput: String = "",
    val mrpInput: String = "",
    val unitInput: String = "1 L",
    val evidenceType: String = "Shelf Tag",
    val isSubmittingPrice: Boolean = false,
    val isSubmitSuccess: Boolean = false,
    val nearbyStoreSuggestions: List<String> = listOf(
        "More Supermarket, Indiranagar",
        "Reliance Smart, 100ft Rd",
        "Nilgiris Supermarket",
        "Apna Bazar Kirana",
        "MK Ahmed Supermarket"
    ),
    val popularProductSuggestions: List<String> = listOf(
        "Fortune Sunlite Sunflower Oil 1L",
        "Aashirvaad Shubh Chakki Atta 5kg",
        "Amul Butter 500g",
        "Tata Salt 1kg",
        "Maggi 2-Minute Noodles 4-Pack"
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

    // Photo Capture & Form Events
    data class OnPhotoCaptured(val photoUri: String = "captured_product_photo") : ScanUiEvent
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
