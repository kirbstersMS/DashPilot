package com.example.dashpilot.strategy

import android.view.accessibility.AccessibilityNodeInfo
import com.example.dashpilot.model.GigOrder
import com.example.dashpilot.util.GigLogger
import java.text.SimpleDateFormat
import java.util.*

class GrubhubStrategy : ScraperStrategy {

    // --- STATE GUARD ---
    private var lastLoggedBaseSig: String = ""
    private var hasLoggedDetails: Boolean = false

    // --- DEBUG STATE ---
    private var bundleDebugEndTime: Long = 0
    private var lastDebugLogTime: Long = 0

    override fun attemptScrape(root: AccessibilityNodeInfo): ScrapeResult {
        val scanState = ScanState()
        recursiveScan(root, scanState)

        // --- BUNDLE DEBUG LOGIC START ---
        val now = System.currentTimeMillis()

        // 1. Arm/Refresh the timer if we see a Bundle (Order Count > 1)
        if (scanState.orderCount > 1) {
            // Keep the window open for 8 seconds from the last time we saw "Bundle"
            bundleDebugEndTime = now + 8000
        }

        // 2. Check if we are inside the Debug Window
        if (now < bundleDebugEndTime) {
            // 3. Throttle: Only log every 500ms
            if (now - lastDebugLogTime > 500) {
                GigLogger.i("Grubhub", "BUNDLE_DEBUG_WINDOW [Active]: ${(bundleDebugEndTime - now)}ms remaining. Dumping Tree...")
                GigLogger.logTree(root)
                lastDebugLogTime = now
            }
        }
        // --- BUNDLE DEBUG LOGIC END ---

        val bestPrice = scanState.prices.maxOrNull()
        val bestMiles = scanState.miles.maxOrNull()

        // 1. BASIC VALIDITY CHECK
        if (bestPrice != null && bestMiles != null && bestPrice > 1.0) {

            // 2. GENERATE SIGNATURE
            // Note: We don't include address in the signature to keep the "New Order" detection simple.
            // If the address appears later, the 'hasDropoffInfo' check handles the update.
            val currentBaseSig = "$bestPrice|$bestMiles|${scanState.orderCount}"
            val hasDropoffInfo = scanState.dropoffTimes.isNotEmpty()

            // 3. LOGGING LOGIC (Discovery Based)
            if (currentBaseSig != lastLoggedBaseSig) {
                // Event: New Order
                GigLogger.i("Grubhub", "New Order Detected ($currentBaseSig) - Initial Scrape")
                GigLogger.logTree(root)

                lastLoggedBaseSig = currentBaseSig
                hasLoggedDetails = hasDropoffInfo // If we have details instantly, mark as done

            } else {
                // Event: Details Revealed (The "More Info" screen)
                if (hasDropoffInfo && !hasLoggedDetails) {
                    val locLog = scanState.dropoffAddr ?: "Unknown Loc"
                    GigLogger.i("Grubhub", "Details Revealed ($currentBaseSig -> $locLog) - Secondary Scrape")
                    GigLogger.logTree(root)

                    hasLoggedDetails = true
                }
            }

            // 4. DURATION LOGIC
            var minutes = 0
            var isEstimate = true

            if (scanState.dropoffTimes.isNotEmpty()) {
                val lastDropoffTime = scanState.dropoffTimes.maxOrNull() ?: 0L
                if (lastDropoffTime > now) {
                    val diffMs = lastDropoffTime - now
                    minutes = (diffMs / 60000).toInt()
                    if (minutes > 0) isEstimate = false
                }
            }

            // Fallback
            if (minutes == 0) {
                minutes = (bestMiles * 4).toInt() + (7 * scanState.orderCount)
            }

            // 5. SANITY CHECK
            if (bestMiles < 0.1 || bestPrice > 400.0) {
                GigLogger.w("Grubhub", "Suspicious Data ($currentBaseSig). Dumping Tree.")
                GigLogger.logTree(root)
                return ScrapeResult.Error("Suspicious Data Values")
            }

            return ScrapeResult.Success(
                GigOrder(
                    platform = "Grubhub",
                    price = bestPrice,
                    distanceMiles = bestMiles,
                    durationMinutes = minutes,
                    isEstimate = isEstimate,
                    orderCount = scanState.orderCount,
                    dropoffLocation = scanState.dropoffAddr ?: ""
                )
            )
        }

        return ScrapeResult.NotFound
    }

    // --- HELPER CLASSES & METHODS ---

    private data class ScanState(
        val prices: MutableList<Double> = mutableListOf(),
        val miles: MutableList<Double> = mutableListOf(),
        val dropoffTimes: MutableList<Long> = mutableListOf(),
        var dropoffAddr: String? = null,
        var orderCount: Int = 1
    )

    private fun recursiveScan(node: AccessibilityNodeInfo?, state: ScanState) {
        if (node == null) return

        // 1. Process the Node itself for standard data (Price/Miles)
        if (!node.text.isNullOrEmpty()) {
            val text = node.text.toString()

            // PRICE
            if (text.contains("$")) {
                val clean = text.replace("$", "").trim()
                val price = clean.toDoubleOrNull()
                if (price != null) state.prices.add(price)
            }

            // MILES
            if (text.lowercase().contains("mi")) {
                val match = Regex("""(\d+(\.\d+)?)\s*mi""").find(text)
                if (match != null) {
                    val m = match.groupValues[1].toDoubleOrNull()
                    if (m != null) state.miles.add(m)
                }
            } else {
                val rawNumber = text.toDoubleOrNull()
                if (rawNumber != null && !text.contains("$")) {
                    if (rawNumber in 0.1..60.0 && text.contains(".")) {
                        state.miles.add(rawNumber)
                    }
                }
            }

            // BUNDLE
            if (text.contains("orders", ignoreCase = true)) {
                val match = Regex("""(\d+)\s*orders""").find(text)
                if (match != null) {
                    val count = match.groupValues[1].toIntOrNull()
                    if (count != null && count > 1) {
                        state.orderCount = count
                    }
                }
            }
        }

        // 2. Process Children (Context-Aware Scanning)
        var lastSeenText = ""

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childText = child.text?.toString()

            if (!childText.isNullOrEmpty()) {
                // PATTERN: "Dropoff by..." follows the Address
                if (childText.contains("Dropoff by", ignoreCase = true)) {

                    // A. Capture Time
                    val timePart = childText.replace("Dropoff by", "", ignoreCase = true).trim()
                    val parsedTime = parseTime(timePart)
                    if (parsedTime != null) {
                        state.dropoffTimes.add(parsedTime)
                    }

                    // B. Capture Address (The previous sibling's text)
                    if (lastSeenText.isNotEmpty() && !lastSeenText.contains("$")) {
                        if (lastSeenText.length > 5 && lastSeenText.contains(" ")) {
                            state.dropoffAddr = lastSeenText
                        }
                    }
                }

                lastSeenText = childText
            }

            recursiveScan(child, state)
        }
    }

    private fun parseTime(timeStr: String): Long? {
        return try {
            val fmt = SimpleDateFormat("h:mm a", Locale.US)
            val date = fmt.parse(timeStr) ?: return null
            val cal = Calendar.getInstance()
            val today = Calendar.getInstance()
            cal.time = date
            cal.set(Calendar.YEAR, today.get(Calendar.YEAR))
            cal.set(Calendar.MONTH, today.get(Calendar.MONTH))
            cal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))

            if (cal.timeInMillis < System.currentTimeMillis() - 43200000) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            cal.timeInMillis
        } catch (_: Exception) { null }
    }
}