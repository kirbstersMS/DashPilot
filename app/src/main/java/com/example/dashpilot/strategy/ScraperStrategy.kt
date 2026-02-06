package com.example.dashpilot.strategy

import android.view.accessibility.AccessibilityNodeInfo
import com.example.dashpilot.model.GigOrder

interface ScraperStrategy {
    fun attemptScrape(root: AccessibilityNodeInfo): ScrapeResult
}

sealed class ScrapeResult {
    // 1. We found an order
    data class Success(val order: GigOrder) : ScrapeResult()

    // 2. We looked, but found nothing
    data object NotFound : ScrapeResult() // Use 'data object' here for clean output

    // 3. Something went wrong (WITH A MESSAGE)
    // 🟢 Fix: Add 'val message: String' so it can carry the error details
    data class Error(val message: String) : ScrapeResult()
}