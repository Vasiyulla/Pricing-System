package com.example.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.FakePriceBridgeRepository
import com.example.core.data.PriceBridgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScanViewModel(
    private val repository: PriceBridgeRepository = FakePriceBridgeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun onEvent(event: ScanUiEvent) {
        when (event) {
            is ScanUiEvent.OnChangeCaptureMode -> {
                _uiState.update { it.copy(captureMode = event.mode) }
            }
            is ScanUiEvent.OnPhotoCaptured -> {
                _uiState.update {
                    it.copy(
                        capturedPhotoUri = event.photoUri,
                        isScanning = false
                    )
                }
            }
            is ScanUiEvent.OnRetakePhoto -> {
                _uiState.update {
                    it.copy(
                        capturedPhotoUri = null,
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
        val product = state.productNameInput.trim()
        val store = state.storeNameInput.trim()

        if (price <= 0.0 || product.isBlank() || store.isBlank()) {
            return
        }

        _uiState.update { it.copy(isSubmittingPrice = true) }

        viewModelScope.launch {
            // Find or use a realistic product ID
            val productId = product.lowercase().replace(" ", "_").take(30)
            val storeId = store.lowercase().replace(" ", "_").take(30)

            repository.submitPrice(
                productId = productId,
                storeId = storeId,
                price = price,
                mrp = price * 1.15,
                sourceType = state.evidenceType
            )

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
}
