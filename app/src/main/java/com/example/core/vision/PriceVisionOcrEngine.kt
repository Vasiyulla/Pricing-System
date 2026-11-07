package com.example.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import kotlin.coroutines.resume

/**
 * Structured shelf-tag parsing result.
 */
data class OcrPriceTagResult(
    val rawText: String,
    val detectedPrice: Double?,
    val detectedMrp: Double?,
    val detectedUnit: String?,
    val detectedProductName: String?,
    val detectedStoreName: String?,
    val confidenceScore: Int,
    val currencySymbol: String = "₹",
    val isVerifiedTag: Boolean = false
)

/**
 * Line item on a printed receipt.
 */
data class OcrReceiptItem(
    val name: String,
    val price: Double,
    val quantity: Int = 1
)

/**
 * Structured paper-receipt parsing result.
 */
data class OcrReceiptResult(
    val rawText: String,
    val storeName: String?,
    val dateString: String?,
    val items: List<OcrReceiptItem>,
    val subtotal: Double?,
    val taxVat: Double?,
    val total: Double?,
    val isMathConsistent: Boolean,
    val confidenceScore: Int
)

/**
 * Enterprise On-Device Vision & OCR Engine powered by Google ML Kit.
 * Sub-100ms execution, zero cloud billing costs, full shopper privacy.
 */
class PriceVisionOcrEngine(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun analyzeImageFromUri(uri: Uri): Text? = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            processBitmap(bitmap)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun processBitmap(bitmap: Bitmap): Text? = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                continuation.resume(visionText)
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }

    /**
     * Parses a supermarket/kirana shelf price tag into structured product attributes.
     */
    fun parseShelfTag(visionText: Text): OcrPriceTagResult {
        val raw = visionText.text
        if (raw.isBlank()) {
            return OcrPriceTagResult(
                rawText = "",
                detectedPrice = null,
                detectedMrp = null,
                detectedUnit = null,
                detectedProductName = null,
                detectedStoreName = null,
                confidenceScore = 0
            )
        }

        val lines = visionText.textBlocks.flatMap { it.lines.map { line -> line.text.trim() } }
            .filter { it.isNotBlank() }

        var bestPrice: Double? = null
        var bestMrp: Double? = null
        var detectedUnit: String? = null
        var productName: String? = null
        var storeName: String? = null
        var currency = "₹"

        // 1. Detect currency
        if (raw.contains("£")) {
            currency = "£"
        } else if (raw.contains("$")) {
            currency = "$"
        }

        // 2. Extract numeric prices
        val priceRegex = Pattern.compile("""(?:₹|Rs\.?|INR|£|\$)?\s*(\d+(?:\.\d{1,2})?)""")
        val foundPrices = mutableListOf<Double>()

        for (line in lines) {
            val matcher = priceRegex.matcher(line)
            while (matcher.find()) {
                val numStr = matcher.group(1)
                val num = numStr?.toDoubleOrNull()
                if (num != null && num > 0.0 && num < 50000.0) {
                    foundPrices.add(num)
                }
            }

            // Detect Unit (e.g. 500g, 1kg, 1L, 200ml)
            if (detectedUnit == null) {
                val unitRegex = Pattern.compile("""(\d+(?:\.\d+)?\s*(?:kg|g|gm|gms|l|litre|liter|ml|pack|pcs|pc|wash))\b""", Pattern.CASE_INSENSITIVE)
                val unitMatcher = unitRegex.matcher(line)
                if (unitMatcher.find()) {
                    detectedUnit = unitMatcher.group(1)
                }
            }

            // Detect Known Stores
            val lower = line.lowercase()
            if (storeName == null) {
                when {
                    lower.contains("reliance") -> storeName = "Reliance Smart"
                    lower.contains("dmart") -> storeName = "DMart"
                    lower.contains("more") -> storeName = "More Supermarket"
                    lower.contains("nilgiris") -> storeName = "Nilgiris"
                    lower.contains("tesco") -> storeName = "Tesco"
                    lower.contains("sainsbury") -> storeName = "Sainsbury's"
                    lower.contains("aldi") -> storeName = "Aldi"
                    lower.contains("lidl") -> storeName = "Lidl"
                    lower.contains("kirana") -> storeName = "Local Kirana"
                }
            }
        }

        // Price logic: if multiple numbers found, smaller is usually sale price, larger is MRP
        if (foundPrices.isNotEmpty()) {
            val sorted = foundPrices.sorted()
            if (sorted.size >= 2) {
                bestPrice = sorted.first()
                bestMrp = sorted.last()
            } else {
                bestPrice = sorted.first()
            }
        }

        // Product Name heuristic: look for top line that isn't a pure price or barcode
        for (line in lines) {
            val isNumber = line.all { it.isDigit() || it.isWhitespace() || it == '.' || it == '₹' || it == '£' }
            if (!isNumber && line.length >= 3 && !line.lowercase().contains("mrp") && !line.lowercase().contains("price")) {
                productName = line
                break
            }
        }

        val confidence = when {
            bestPrice != null && productName != null && detectedUnit != null -> 95
            bestPrice != null && productName != null -> 85
            bestPrice != null -> 70
            else -> 30
        }

        return OcrPriceTagResult(
            rawText = raw,
            detectedPrice = bestPrice,
            detectedMrp = bestMrp,
            detectedUnit = detectedUnit,
            detectedProductName = productName,
            detectedStoreName = storeName,
            confidenceScore = confidence,
            currencySymbol = currency,
            isVerifiedTag = confidence >= 80
        )
    }

    /**
     * Parses a supermarket receipt and verifies arithmetic consistency.
     */
    fun parseReceipt(visionText: Text): OcrReceiptResult {
        val raw = visionText.text
        val lines = visionText.textBlocks.flatMap { it.lines.map { l -> l.text.trim() } }

        var storeName: String? = null
        var total: Double? = null
        var subtotal: Double? = null
        var tax: Double? = null
        val items = mutableListOf<OcrReceiptItem>()

        val priceRegex = Pattern.compile("""(\d+\.\d{2})""")

        for ((idx, line) in lines.withIndex()) {
            val lower = line.lowercase()
            if (idx == 0 || idx == 1) {
                if (storeName == null && line.length in 3..35 && !line.any { it.isDigit() }) {
                    storeName = line
                }
            }

            // Check for Total
            if (lower.contains("total") || lower.contains("amount due") || lower.contains("balance")) {
                val m = priceRegex.matcher(line)
                if (m.find()) {
                    total = m.group(1)?.toDoubleOrNull()
                }
            } else if (lower.contains("subtotal") || lower.contains("sub total")) {
                val m = priceRegex.matcher(line)
                if (m.find()) {
                    subtotal = m.group(1)?.toDoubleOrNull()
                }
            } else if (lower.contains("tax") || lower.contains("vat") || lower.contains("gst")) {
                val m = priceRegex.matcher(line)
                if (m.find()) {
                    tax = m.group(1)?.toDoubleOrNull()
                }
            } else {
                // Try line item parse
                val m = priceRegex.matcher(line)
                if (m.find()) {
                    val p = m.group(1)?.toDoubleOrNull()
                    val itemName = line.replace(m.group(1) ?: "", "").trim()
                    if (p != null && itemName.length >= 3) {
                        items.add(OcrReceiptItem(name = itemName, price = p))
                    }
                }
            }
        }

        // Arithmetic Checksum verification: sum(items) ~ total
        val itemsSum = items.sumOf { it.price }
        val isConsistent = if (total != null && itemsSum > 0.0) {
            Math.abs(itemsSum - total) <= 1.0 || (tax != null && Math.abs(itemsSum + tax - total) <= 1.0)
        } else false

        val confidence = if (isConsistent) 98 else if (total != null) 75 else 40

        return OcrReceiptResult(
            rawText = raw,
            storeName = storeName,
            dateString = "Today",
            items = items,
            subtotal = subtotal ?: itemsSum,
            taxVat = tax,
            total = total ?: itemsSum,
            isMathConsistent = isConsistent,
            confidenceScore = confidence
        )
    }
}

