package com.example.dashpilot.strategy

import android.graphics.Bitmap
import android.graphics.Matrix
import com.example.dashpilot.model.GigOrder
import com.example.dashpilot.util.GigLogger
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class UberOcrStrategy : ScraperStrategy {

    // --- REGEX CONFIG ---
    // 1. Time: Strict digits only to avoid "L" -> "1"
    private val timeChunkRegex = Regex("""(?:(\d+)\s*hr)?\s*(\d+)\s*min""")

    // 2. Price: Standard dollar format
    private val priceRegex = Regex("""\$(\d+\.?\d{0,2})""")

    // 3. Distance: Added (?!\s*n) to ensure 'min' isn't read as 'mi'
    private val distRegex = Regex("""(\d+\.?\d*)\s*(?:mi|miles)(?!\s*n)""")

    // 4. Order Count header
    private val deliveryHeaderRegex = Regex("""Del[a-z]+\s*\(\s*(\d+)\s*\)""", RegexOption.IGNORE_CASE)

    // Noise Filter: These lines are part of the UI, not the address
    private val uiNoiseKeywords = setOf(
        "delivery", "exclusive", "match", "accept", "decline", "go offline",
        "includes expected tip", "finding trips", "later today", "unlock",
        "verify", "pin", "total", "fare", "trip"
    )

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Tracking for duplicates
    private var lastLoggedSignature: String = ""
    private var lastLogTime: Long = 0

    // --- GEOGRAPHY CONFIG ---
    // Add every city in your 30-mile radius here. Lowercase for easier matching.
    private val targetCities = setOf(
        "tualatin", "tigard", "portland", "wilsonville", "oregon city",
        "lake oswego", "sherwood", "beaverton", "west linn", "gladstone",
        "milwaukie", "canby", "aurora", "hubbard", "woodburn", "aloha",
        "hillsboro", "gresham", "happy valley", "clackamas", "durham",
        "king city", "rivergrove", "vancouver" // Added Vancouver just in case
    )

    // Add this property to your class
    private val poisonPills = setOf(
        "daily log", "gigpilot", "weekly recap", "earnings", "shift tracker"
    )

    override fun attemptScrape(root: android.view.accessibility.AccessibilityNodeInfo): ScrapeResult {
        return ScrapeResult.NotFound
    }

    suspend fun processScreenshot(bitmap: Bitmap): ScrapeResult = withContext(Dispatchers.Default) {
        val height = bitmap.height
        val width = bitmap.width

        // 1. Crop: Bottom 45%
        val cropHeight = (height * 0.45).toInt()
        val startY = height - cropHeight

        // 2. SCALE UP: Keep this, it helps ML Kit read small text clearly
        val matrix = Matrix()
        matrix.postScale(2f, 2f)

        val croppedBitmap = Bitmap.createBitmap(
            bitmap, 0, startY, width, cropHeight, matrix, true
        )

        val image = InputImage.fromBitmap(croppedBitmap, 0)

        return@withContext suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val result = parseRawText(visionText.text)
                    continuation.resume(result)
                    croppedBitmap.recycle()
                }
                .addOnFailureListener { e ->
                    GigLogger.e("GigPilot_Uber", "OCR Failed", e)
                    continuation.resume(ScrapeResult.NotFound)
                    croppedBitmap.recycle()
                }
        }
    }

    private fun parseRawText(rawString: String): ScrapeResult {
        val lowerRaw = rawString.lowercase()

        // [VIBE FIX] Poison Pill: If we see our own app's headers, stop immediately.
        // This prevents the "Infinite Loop" where we scrape our own log.
        if (poisonPills.any { lowerRaw.contains(it) }) {
            GigLogger.w("GigPilot_Uber", "Self-Scrape detected (Poison Pill). Aborting.")
            return ScrapeResult.NotFound
        }

        val lines = rawString.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // --- PARSE METRICS ---
        var price = 0.0
        var miles = 0.0
        var minutes = 0
        var orderCount = 1

        val fullText = lines.joinToString(" ").lowercase()

        // Price
        val priceMatch = priceRegex.find(rawString)
        if (priceMatch != null) {
            price = priceMatch.groupValues[1].toDoubleOrNull() ?: 0.0
        }

        // Time
        val timeMatch = timeChunkRegex.find(fullText)
        if (timeMatch != null) {
            val hr = timeMatch.groupValues[1].toIntOrNull() ?: 0
            val min = timeMatch.groupValues[2].toIntOrNull() ?: 0
            minutes = (hr * 60) + min
        }

        // Distance (Fixed regex prevents 'min' from matching 'mi')
        val distMatches = distRegex.findAll(fullText)
        val maxDist = distMatches.mapNotNull { it.groupValues[1].toDoubleOrNull() }.maxOrNull()
        if (maxDist != null) {
            miles = maxDist
        }

        // Order Count
        val countMatch = deliveryHeaderRegex.find(rawString)
        if (countMatch != null) {
            orderCount = countMatch.groupValues[1].toIntOrNull() ?: 1
        }

        if (price == 0.0 && miles == 0.0) return ScrapeResult.NotFound

        // --- PARSE LOCATION (THE FIX) ---
        val location = extractLocation(lines)

        // --- SIGNATURE CHECK ---
        val currentSignature = "$price|$miles|$minutes|$orderCount"

        val now = System.currentTimeMillis()
        if (currentSignature == lastLoggedSignature && (now - lastLogTime) < 30_000) {
            lastLogTime = now
            return ScrapeResult.Success(
                GigOrder(platform = "Uber Eats",
                    price = price,
                    distanceMiles = miles,
                    durationMinutes = minutes,
                    orderCount = orderCount,
                    dropoffLocation = location)
            )
        }

        // New Valid Order
        lastLoggedSignature = currentSignature
        lastLogTime = now

        // --- RESTORED FULL LOGGING ---
        GigLogger.i("GigPilot_Uber", "NEW VALID ORDER: $currentSignature\nParsed Loc: $location\nParsing Analysis:\n$rawString")

        return ScrapeResult.Success(
            GigOrder(
                platform = "Uber Eats",
                price = price,
                distanceMiles = miles,
                durationMinutes = minutes,
                orderCount = orderCount,
                dropoffLocation = location
            )
        )
    }

    private fun extractLocation(lines: List<String>): String {
        // Step A: Filter out known Noise (Same as before)
        val cleanLines = lines.filter { line ->
            val lower = line.lowercase()

            // Filter UI keywords
            if (uiNoiseKeywords.any { lower.contains(it) }) return@filter false

            // Filter Metrics lines ($ or "min" AND "mi")
            if (line.contains("$")) return@filter false
            if (lower.contains("min") && lower.contains("mi")) return@filter false

            // Filter Short Artifacts
            if (line.length < 3) return@filter false // Bumped to 3 to allow "St" etc

            true
        }.map {
            // Cleanup trailing 'X' artifact
            if (it.endsWith(" X") || it.endsWith(" x")) it.dropLast(2).trim() else it
        }

        // Step B: The "City Anchor" Strategy
        val cityCandidates = mutableListOf<String>()

        for (i in cleanLines.indices) {
            val line = cleanLines[i]
            val lowerLine = line.lowercase()

            // Does this line contain a target city?
            if (targetCities.any { lowerLine.contains(it) }) {

                // FOUND ANCHOR. Now, check if we need to merge up.
                // If the line is short (just "Tualatin, OR"), we likely missed the street above.
                // Threshold: 30 chars is usually enough to hold "123 Main St, Tualatin"
                if (line.length < 30 && i > 0) {
                    // Merge with previous line
                    val merged = "${cleanLines[i - 1]} $line"
                    cityCandidates.add(merged)
                } else {
                    // Line seems complete enough, or it's the first line
                    cityCandidates.add(line)
                }
            }
        }

        // Step C: Selection Logic
        return when {
            // 1. Ideal: We found city matches.
            // Uber lists Pickup then Dropoff. We want the LAST one.
            cityCandidates.isNotEmpty() -> cityCandidates.last()

            // 2. Fallback: No known city found (e.g., Salem run).
            // Revert to old logic: Take the very last clean line.
            cleanLines.isNotEmpty() -> cleanLines.last()

            // 3. Failure
            else -> "Unknown Location"
        }
    }
}