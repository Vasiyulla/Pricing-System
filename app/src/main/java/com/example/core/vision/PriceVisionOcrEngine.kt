package com.example.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.math.max

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
    val currencySymbol: String = "£",
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
            val image = InputImage.fromFilePath(context, uri)
            processInputImage(image)
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                processBitmap(bitmap)
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun processImagePathOrUri(uriStr: String, fallbackBitmap: Bitmap? = null): Text? =
        withContext(Dispatchers.IO) {
            try {
                when {
                    uriStr.startsWith("content://") || uriStr.startsWith("file://") -> {
                        val uri = Uri.parse(uriStr)
                        try {
                            processInputImage(InputImage.fromFilePath(context, uri))
                        } catch (_: Exception) {
                            fallbackBitmap?.let { processBitmap(it) }
                        }
                    }
                    File(uriStr).exists() -> {
                        val file = File(uriStr)
                        try {
                            // InputImage.fromFilePath reads EXIF orientation automatically
                            processInputImage(InputImage.fromFilePath(context, Uri.fromFile(file)))
                        } catch (_: Exception) {
                            val rotation = readExifRotationDegrees(file.absolutePath)
                            val bmp = fallbackBitmap ?: BitmapFactory.decodeFile(file.absolutePath)
                            bmp?.let { processBitmap(it, rotation) }
                        }
                    }
                    fallbackBitmap != null -> processBitmap(fallbackBitmap, 0)
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }

    suspend fun processBitmap(bitmap: Bitmap, rotationDegrees: Int = 0): Text? =
        processInputImage(InputImage.fromBitmap(bitmap, rotationDegrees))

    private suspend fun processInputImage(image: InputImage): Text? =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText)
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
        }

    /**
     * Decodes an image with EXIF orientation corrected, scales it down to maxDimPx,
     * and returns a base64-encoded JPEG string suitable for multimodal Vision AI APIs.
     */
    suspend fun loadUprightScaledJpegBase64(
        uriStr: String,
        fallbackBitmap: Bitmap? = null,
        maxDimPx: Int = 1024
    ): String? = withContext(Dispatchers.IO) {
        try {
            var bitmap = when {
                uriStr.startsWith("content://") || uriStr.startsWith("file://") -> {
                    val uri = Uri.parse(uriStr)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    } ?: fallbackBitmap
                }
                File(uriStr).exists() -> {
                    val raw = BitmapFactory.decodeFile(uriStr)
                    val rot = readExifRotationDegrees(uriStr)
                    if (raw != null && rot != 0) rotateBitmap(raw, rot) else raw
                }
                else -> fallbackBitmap
            } ?: return@withContext null

            val longest = max(bitmap.width, bitmap.height)
            if (longest > maxDimPx) {
                val scale = maxDimPx.toFloat() / longest.toFloat()
                val targetW = max(1, (bitmap.width * scale).toInt())
                val targetH = max(1, (bitmap.height * scale).toInt())
                bitmap = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            }

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun readExifRotationDegrees(path: String): Int = try {
        when (ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Exception) {
        0
    }

    private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Parses a supermarket shelf price tag or product listing using spatial prominence
     * (bounding box height), currency anchoring, and UK retail/loyalty heuristics.
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

        val allLines = visionText.textBlocks.flatMap { it.lines }
            .filter { it.text.isNotBlank() }

        var detectedUnit: String? = null
        var storeName: String? = null
        var currency = "£"
        if (raw.contains("₹")) {
            currency = "₹"
        } else if (raw.contains("$") && !raw.contains("£")) {
            currency = "$"
        }

        val unitRegex = Regex("""(\d+(?:\.\d+)?\s*(?:kg|g|gm|gms|l|litre|liter|ml|pack|pcs|pc|wash))\b""", RegexOption.IGNORE_CASE)
        val unitRateMaskRegex = Regex("""(?:£|₹|\$)?\s*\d+(?:\.\d{1,2})?\s*/\s*(?:kg|100g|g|litre|liter|l|100ml|ml|unit|pack|ea)\b""", RegexOption.IGNORE_CASE)
        val multiBuyMaskRegex = Regex("""(?:any\s+)?\d+\s+for\s+(?:£|₹|\$)?\s*\d+(?:\.\d{1,2})?""", RegexOption.IGNORE_CASE)
        val currencyPriceRegex = Regex("""(?:£|₹|\$)\s*(\d+(?:\.\d{1,2})?)""")
        val pencePriceRegex = Regex("""(?<!\d)(\d{1,3})\s*p\b""")
        val bareDecimalRegex = Regex("""\b(\d{1,4}\.\d{2})\b""")

        data class PriceCandidate(val amount: Double, val heightPx: Int, val isExplicitCurrency: Boolean)

        val priceCandidates = mutableListOf<PriceCandidate>()

        for (lineObj in allLines) {
            val line = lineObj.text.trim()
            val heightPx = lineObj.boundingBox?.height() ?: 12

            if (detectedUnit == null) {
                unitRegex.find(line)?.let { m ->
                    detectedUnit = m.groupValues[1].replace(Regex("\\s+"), " ").trim()
                }
            }

            if (storeName == null) {
                storeName = detectStoreOrLoyalty(line)
            }

            // Strip unit-rate denominators (e.g. "£7.50/kg") and multi-buy totals (e.g. "Any 5 for £5")
            var sanitized = unitRateMaskRegex.replace(line, " ")
            sanitized = multiBuyMaskRegex.replace(sanitized, " ")

            var foundExplicitOnLine = false
            currencyPriceRegex.findAll(sanitized).forEach { m ->
                val num = m.groupValues[1].toDoubleOrNull()
                if (num != null && num > 0.0 && num < 5000.0) {
                    priceCandidates.add(PriceCandidate(num, heightPx, isExplicitCurrency = true))
                    foundExplicitOnLine = true
                }
            }

            if (!foundExplicitOnLine) {
                pencePriceRegex.findAll(sanitized).forEach { m ->
                    val pence = m.groupValues[1].toIntOrNull()
                    if (pence != null && pence in 1..999) {
                        priceCandidates.add(PriceCandidate(pence / 100.0, heightPx, isExplicitCurrency = true))
                        foundExplicitOnLine = true
                    }
                }
            }

            // Only fall back to bare X.XX decimals on lines that are not ratings/reviews/weights
            val lower = line.lowercase()
            if (!foundExplicitOnLine &&
                !lower.contains("star") &&
                !lower.contains("review") &&
                !lower.contains("kg") &&
                !lower.contains("ml")
            ) {
                bareDecimalRegex.findAll(sanitized).forEach { m ->
                    val num = m.groupValues[1].toDoubleOrNull()
                    if (num != null && num > 0.0 && num < 1000.0) {
                        priceCandidates.add(PriceCandidate(num, heightPx, isExplicitCurrency = false))
                    }
                }
            }
        }

        val explicitPrices = priceCandidates.filter { it.isExplicitCurrency }
        val chosenPriceCandidate = if (explicitPrices.isNotEmpty()) {
            // Prefer largest font height (primary shelf price), breaking ties by first occurrence
            explicitPrices.maxByOrNull { it.heightPx }
        } else {
            priceCandidates.maxByOrNull { it.heightPx }
        }
        val bestPrice = chosenPriceCandidate?.amount
        val bestMrp = if (explicitPrices.size >= 2) {
            explicitPrices.map { it.amount }.maxOrNull()?.takeIf { bestPrice != null && it > bestPrice * 1.05 }
        } else {
            null
        }

        val (productName, titleScore) = extractBestProductTitle(visionText)

        var confidence = 0
        if (productName != null) {
            confidence += if (titleScore >= 300) 45 else 25
        }
        if (chosenPriceCandidate != null) {
            confidence += if (chosenPriceCandidate.isExplicitCurrency) 35 else 18
        }
        if (detectedUnit != null) confidence += 10
        if (storeName != null) confidence += 10
        if (productName == null || bestPrice == null) {
            confidence = confidence.coerceAtMost(55)
        }
        confidence = confidence.coerceIn(0, 98)

        return OcrPriceTagResult(
            rawText = raw,
            detectedPrice = bestPrice,
            detectedMrp = bestMrp,
            detectedUnit = detectedUnit,
            detectedProductName = productName,
            detectedStoreName = storeName,
            confidenceScore = confidence,
            currencySymbol = currency,
            isVerifiedTag = confidence >= 75
        )
    }

    private fun detectStoreOrLoyalty(line: String): String? {
        val lower = line.lowercase()
        return when {
            lower.contains("tesco") || lower.contains("clubcard") -> "Tesco"
            lower.contains("sainsbury") || lower.contains("nectar") -> "Sainsbury's"
            lower.contains("lidl plus") || lower.contains("lidl") -> "Lidl"
            lower.contains("aldi") -> "Aldi"
            lower.contains("waitrose") -> "Waitrose"
            lower.contains("asda") -> "Asda"
            lower.contains("morrison") -> "Morrisons"
            lower.contains("marks & spencer") || lower.contains("m&s") -> "M&S Foodhall"
            lower.contains("co-op") -> "Co-op"
            lower.contains("iceland") -> "Iceland"
            lower.contains("boots") -> "Boots"
            lower.contains("reliance") -> "Reliance Smart"
            lower.contains("dmart") -> "DMart"
            lower.contains("nilgiris") -> "Nilgiris"
            else -> null
        }
    }

    private fun isValidTitleLine(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 4) return false

        val lower = trimmed.lowercase()
        val noiseTokens = listOf(
            "review", "stars", "clubcard", "nectar", "lidl plus", "selected",
            "offer", "valid", "delivery", "add to", "trolley", "basket",
            "http", "www.", ".co.uk", ".com", "mrp", "price", "/kg", "/100g", "/l"
        )
        if (noiseTokens.any { lower.contains(it) }) return false
        if (lower == "add" || lower == "save" || lower == "new") return false

        val nonSpace = trimmed.filterNot { it.isWhitespace() }
        if (nonSpace.isEmpty()) return false
        val letters = nonSpace.count { it.isLetter() }
        if (letters < 5) return false
        if (letters.toFloat() / nonSpace.length.toFloat() < 0.55f) return false

        // Reject lines dominated by isolated single-character fragments like "G x 12 X"
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val singleCharWords = words.count { it.length == 1 }
        if (words.size >= 2 && singleCharWords * 2 > words.size) return false

        return true
    }

    private fun extractBestProductTitle(visionText: Text): Pair<String?, Int> {
        val unitTailRegex = Regex("""\b\d+(?:\.\d+)?\s*(?:kg|g|ml|l|pack)\b""", RegexOption.IGNORE_CASE)
        data class TitleCandidate(val text: String, val score: Int)

        val candidates = mutableListOf<TitleCandidate>()

        for (block in visionText.textBlocks) {
            val validLines = block.lines.filter { isValidTitleLine(it.text) }
            if (validLines.isEmpty()) continue

            val maxHeight = validLines.maxOf { it.boundingBox?.height() ?: 16 }

            // 1. Merged prominent lines inside a block (handles wrapped product titles)
            val prominentLines = validLines.filter { (it.boundingBox?.height() ?: 16) >= maxHeight * 0.72 }
            if (prominentLines.size in 2..3) {
                val merged = prominentLines.joinToString(" ") { it.text.trim() }
                if (merged.length in 6..90) {
                    val avgH = prominentLines.map { it.boundingBox?.height() ?: 16 }.average().toInt()
                    val words = merged.split(Regex("\\s+")).filter { it.isNotBlank() }
                    var score = avgH * 10 + 30
                    if (unitTailRegex.containsMatchIn(merged)) score += 45
                    if (words.size in 2..10) score += 25
                    candidates.add(TitleCandidate(merged, score))
                }
            }

            // 2. Individual lines inside the block
            for (lineObj in validLines) {
                val txt = lineObj.text.trim()
                val h = lineObj.boundingBox?.height() ?: 16
                val words = txt.split(Regex("\\s+")).filter { it.isNotBlank() }
                var score = h * 10
                if (unitTailRegex.containsMatchIn(txt)) score += 40
                if (words.size in 2..8) score += 20
                if (txt.length < 7) score -= 30
                candidates.add(TitleCandidate(txt, score))
            }
        }

        val best = candidates.maxByOrNull { it.score } ?: return null to 0
        return if (best.score >= 120) best.text to best.score else null to best.score
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

