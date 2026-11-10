package com.example.core.camera

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.feature.scan.CaptureMode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Production-ready CameraX Composable View with real-time ML Kit Barcode scanning
 * and high-resolution ImageCapture for shelf price tag OCR.
 * Binds mode-specific use cases (max 2 concurrent streams) for full hardware compatibility.
 */
@Composable
fun CameraPreviewView(
    captureMode: CaptureMode,
    isTorchOn: Boolean,
    isScanningActive: Boolean,
    onBarcodeScanned: (String) -> Unit,
    onImageCaptureReady: (ImageCapture) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentScanningActive by rememberUpdatedState(isScanningActive)
    val currentCaptureMode by rememberUpdatedState(captureMode)
    val currentOnBarcodeScanned by rememberUpdatedState(onBarcodeScanned)
    val currentOnImageCaptureReady by rememberUpdatedState(onImageCaptureReady)

    var camera by remember { mutableStateOf<Camera?>(null) }
    var lastScannedBarcode by remember { mutableStateOf<String?>(null) }
    var lastScanTimestamp by remember { mutableStateOf(0L) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Bind mode-specific CameraX use cases safely whenever captureMode or lifecycleOwner changes
    LaunchedEffect(captureMode, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                cameraProvider.unbindAll()

                if (captureMode == CaptureMode.PHOTO) {
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    currentOnImageCaptureReady(imageCapture)

                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } else {
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        @OptIn(ExperimentalGetImage::class)
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && currentScanningActive && currentCaptureMode == CaptureMode.BARCODE && !cameraExecutor.isShutdown) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            barcodeScanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val raw = barcode.rawValue ?: continue
                                        val now = System.currentTimeMillis()
                                        if (raw != lastScannedBarcode || (now - lastScanTimestamp > 1500)) {
                                            lastScannedBarcode = raw
                                            lastScanTimestamp = now
                                            triggerHapticFeedback(context)
                                            currentOnBarcodeScanned(raw)
                                            break
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                }
            } catch (e: Exception) {
                Log.e("CameraPreviewView", "CameraX initialization or binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Toggle hardware torch / flashlight
    LaunchedEffect(isTorchOn, camera) {
        try {
            val cam = camera ?: return@LaunchedEffect
            if (cam.cameraInfo.hasFlashUnit()) {
                cam.cameraControl.enableTorch(isTorchOn)
            }
        } catch (e: Exception) {
            Log.w("CameraPreviewView", "Failed to toggle torch", e)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (_: Exception) {
            }
            if (!cameraExecutor.isShutdown) {
                cameraExecutor.shutdown()
            }
            try {
                barcodeScanner.close()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize()
    )
}

/**
 * Triggers a short tactile vibration buzz when a barcode is successfully decoded.
 */
private fun triggerHapticFeedback(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = vibratorManager?.defaultVibrator
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        }
    } catch (_: Exception) {
        // Fallback gracefully on devices without vibration motor
    }
}

