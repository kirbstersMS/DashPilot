package com.example.dashpilot.strategy

import android.view.accessibility.AccessibilityNodeInfo
import com.example.dashpilot.model.GigOrder
import com.example.dashpilot.util.GigLogger
import kotlin.math.max

class DoorDashStrategy : ScraperStrategy {

    // --- REGEX CONFIG ---
    private val priceRegex = Regex("""[+$]*\$(\d+\.?\d{2})""")
    private val milesRegex = Regex("""(\d+\.?\d*)\s*mi""")
    private val minRegex = Regex("""(\d+)\s*min""")
    private val explicitCountRegex = Regex("""\(\s*(\d+)\s*orders?\s*\)""", RegexOption.IGNORE_CASE)

    // --- ADDRESS HEURISTICS ---
    // 1. Starts with digits (house number) followed by text: "25604 Canyon..."
    private val addressStartsWithNumRegex = Regex("""^\d+\s+[a-zA-Z].*""")
    // 2. Fallback: Standard street suffixes (expanded to include full words)
    private val addressSuffixRegex = Regex("""(?i).*\b(St|Street|Rd|Road|Ave|Avenue|Blvd|Boulevard|Dr|Drive|Ln|Lane|Ct|Court|Pl|Place|Way|Hwy|Highway)\b.*""")

    // Noise Filter: Strictly ignore these generic UI terms
    private val uiNoiseRegex = Regex("""(?i)^(Accept|Decline|Return to Dash|Customer dropoff|Restaurant|Store|Pickup|Drop off|Guaranteed|incl\. tips)$""")

    // --- STATE MANAGEMENT ---
    private var lastLoggedSignature: String = ""

    // The "Skeleton" (Price/Miles/Time)
    private var cachedPrice: Double = 0.0
    private var cachedMiles: Double = 0.0
    private var cachedMinutes: Int = 0
    private var cachedCount: Int = 0

    // The "Meat" (Address - can arrive late)
    private var cachedAddress: String = ""

    override fun attemptScrape(root: AccessibilityNodeInfo): ScrapeResult {

        val allText = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        var isPoisoned = false
        var hasAcceptButton = false

        // 1. SCAN & FLATTEN
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            // Check content
            if (!node.text.isNullOrEmpty()) {
                val text = node.text.toString().trim()
                if (text.isNotEmpty()) {
                    allText.add(text)

                    // Poison check (Not on Shift / Summary Screens)
                    if (text.contains("Looking for offers", ignoreCase = true) ||
                        text.contains("This dash so far", ignoreCase = true) ||
                        text.contains("End Dash", ignoreCase = true)) {
                        isPoisoned = true
                    }

                    // Button Check
                    if (text.equals("Accept", ignoreCase = true)) {
                        hasAcceptButton = true
                    }
                }
            }

            // Standard BFS traversal
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        // --- GUARDRAILS ---
        if (isPoisoned) {
            clearCache()
            return ScrapeResult.NotFound
        }

        // If we have no active order cached, we MUST see "Accept" to start a new one.
        // If we DO have a cache, we allow "Accept" to disappear (Map Pin interaction mode).
        if (cachedPrice == 0.0 && !hasAcceptButton) {
            return ScrapeResult.NotFound
        }

        // 2. PARSE CURRENT FRAME
        var foundPrice = 0.0
        var foundMiles = 0.0
        var foundMinutes = 0
        var explicitCount = 0
        var pickupLabelCount = 0
        var dropoffLabelCount = 0
        var frameAddressCandidate: String? = null

        for (text in allText) {
            // A. Extract Core Metrics
            if (foundPrice == 0.0 && text.contains("$")) {
                priceRegex.find(text)?.let { foundPrice = it.groupValues[1].toDoubleOrNull() ?: 0.0 }
            }
            if (foundMiles == 0.0 && text.contains("mi")) {
                milesRegex.find(text)?.let { foundMiles = it.groupValues[1].toDoubleOrNull() ?: 0.0 }
            }
            if (foundMinutes == 0 && text.contains("min")) {
                minRegex.find(text)?.let { foundMinutes = it.groupValues[1].toIntOrNull() ?: 0 }
            }
            if (explicitCount == 0) {
                explicitCountRegex.find(text)?.let { explicitCount = it.groupValues[1].toIntOrNull() ?: 0 }
            }

            // B. Count Stops
            if (text.equals("Pickup", ignoreCase = true)) pickupLabelCount++
            if (text.equals("Customer dropoff", ignoreCase = true)) dropoffLabelCount++

            // C. Address Detection (The Fix)
            // We only look for an address if we haven't found a strong candidate in this frame yet
            if (frameAddressCandidate == null) {
                // FILTER: Ignore noise, exact price strings, or distance strings
                if (!uiNoiseRegex.containsMatchIn(text) &&
                    !text.contains("$") &&
                    !text.endsWith("mi") &&
                    !text.endsWith("min")) {

                    // STRATEGY 1: Starts with a number? (e.g. "25604 SW...")
                    // This is the strongest signal for the specific node in your log.
                    if (addressStartsWithNumRegex.matches(text)) {
                        frameAddressCandidate = text
                    }
                    // STRATEGY 2: Contains "Street/Road/Ave" (Fallback)
                    else if (addressSuffixRegex.matches(text)) {
                        frameAddressCandidate = text
                    }
                }
            }
        }

        // 3. CACHE LOGIC
        // We only update the core cache if we found a valid Price/Distance pair
        if (foundPrice > 0.0 && foundMiles > 0.0) {

            // Sanity Check: Ignore garbage values
            if (foundPrice > 200.0 || foundMiles > 100.0 || foundPrice < 2.00) return ScrapeResult.NotFound

            // New Order Detection
            if (foundPrice != cachedPrice || foundMiles != cachedMiles) {
                clearCache()
                cachedPrice = foundPrice
                cachedMiles = foundMiles
                cachedMinutes = foundMinutes
                cachedCount = if (explicitCount > 0) explicitCount else max(pickupLabelCount, dropoffLabelCount).coerceAtLeast(1)
            }
        }

        // Address "Pop-in" Update
        // If we have a valid order cached, but no address yet, and we just found one... capture it.
        if (cachedPrice > 0.0 && frameAddressCandidate != null) {
            if (cachedAddress != frameAddressCandidate) {
                cachedAddress = frameAddressCandidate
                GigLogger.i("DoorDash", ">>> ADDRESS LOCKED: $cachedAddress <<<")
            }
        }

        // 4. FINAL ASSEMBLY
        if (cachedPrice > 0.0 && cachedMiles > 0.0) {
            val finalOrder = GigOrder(
                platform = "DoorDash",
                price = cachedPrice,
                distanceMiles = cachedMiles,
                durationMinutes = if (foundMinutes > 0) foundMinutes else cachedMinutes,
                orderCount = cachedCount,
                dropoffLocation = cachedAddress // Returns empty string until detected
            )

            // Logging Signature
            val currentSignature = "${finalOrder.price}|${finalOrder.distanceMiles}|${finalOrder.dropoffLocation}"
            if (currentSignature != lastLoggedSignature) {
                GigLogger.i("DoorDash", "Order State Update ($currentSignature)")
                lastLoggedSignature = currentSignature
            }

            return ScrapeResult.Success(finalOrder)
        }

        return ScrapeResult.NotFound
    }

    private fun clearCache() {
        cachedPrice = 0.0
        cachedMiles = 0.0
        cachedMinutes = 0
        cachedCount = 0
        cachedAddress = ""
        lastLoggedSignature = ""
    }
}