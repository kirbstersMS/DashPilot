package com.example.dashpilot.manager

import android.view.accessibility.AccessibilityNodeInfo
import com.example.dashpilot.data.GigDao
import com.example.dashpilot.model.GigOrder
import com.example.dashpilot.strategy.DoorDashStrategy
import com.example.dashpilot.strategy.ScrapeResult
import com.example.dashpilot.util.GigLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GigFlowManager @Inject constructor(
    private val gigDao: GigDao
) {

    // --- State & Events ---
    private val _uiState = MutableStateFlow<GigUiState>(GigUiState.Hidden)
    val uiState = _uiState.asStateFlow()

    // --- Internals ---
    // Single threaded scope is fine for node traversal, keeps it off Main/UI thread
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- Strategy ---
    private val ddStrategy = DoorDashStrategy()

    // --- History & Logic ---
    private val recentHistory = ArrayDeque<GigOrder>()
    private val maxHistorySize = 5

    // VIBE FIX: Debounce counter to prevent flickering if a frame is missed
    private var consecutiveMisses = 0
    private val missThreshold = 3

    fun onStandardNode(rootNode: AccessibilityNodeInfo?) {
        if (rootNode == null) return

        scope.launch {
            try {
                // Direct line to DoorDash Strategy
                val result = ddStrategy.attemptScrape(rootNode)
                handleResult(result)
            } catch (e: Exception) {
                GigLogger.e("GigFlow", "Scrape failed", e)
            }
        }
    }

    fun clearState() {
        _uiState.value = GigUiState.Hidden
        consecutiveMisses = 0
    }

    // --- Core Logic ---

    private fun handleResult(result: ScrapeResult) {
        when (result) {
            is ScrapeResult.Success -> {
                consecutiveMisses = 0
                val incoming = result.order

                // 1. Check History (Dedup)
                val existing = recentHistory.find { isSameOrder(incoming, it) }

                if (existing != null) {
                    // If it's in history but we are Hidden (user switched back), SHOW IT AGAIN.
                    if (_uiState.value is GigUiState.Hidden) {
                        _uiState.value = GigUiState.ShowingOrder(incoming)
                    }
                    return
                }

                // 2. Publish New Order
                publishOrder(incoming)
            }
            is ScrapeResult.NotFound -> {
                // Grace Period: Don't hide immediately on one bad frame
                consecutiveMisses++
                if (consecutiveMisses >= missThreshold) {
                    _uiState.value = GigUiState.Hidden
                }
            }
            is ScrapeResult.Error -> {
                // Do nothing, keep existing state
            }
        }
    }

    private fun publishOrder(order: GigOrder) {
        val previousVersion = recentHistory.find { isSameGig(order, it) }

        addToHistory(order)
        _uiState.value = GigUiState.ShowingOrder(order)

        dbScope.launch {
            handleDbWrites(order, previousVersion)
        }
    }

    private suspend fun handleDbWrites(order: GigOrder, previousVersion: GigOrder?) {
        try {
            val isRefinement = previousVersion != null && (
                    previousVersion.dropoffLocation.isBlank() && order.dropoffLocation.isNotBlank()
                    )

            if (isRefinement) {
                val cutoff = System.currentTimeMillis() - 300_000
                gigDao.removeRecentDuplicates(
                    platform = order.platform,
                    price = order.price,
                    miles = order.distanceMiles,
                    cutoffTime = cutoff
                )
                GigLogger.d("GigFlow", "Refinement detected - removing duplicates")
            }

            gigDao.insertOrder(order)
            GigLogger.d("GigFlow", "Saved: ${order.platform} $${order.price}")

        } catch (e: Exception) {
            GigLogger.e("GigFlow", "DB Write Failed", e)
        }
    }

    // --- Helpers ---

    private fun addToHistory(order: GigOrder) {
        recentHistory.addFirst(order)
        if (recentHistory.size > maxHistorySize) {
            recentHistory.removeLast()
        }
    }

    private fun isSameGig(newOrder: GigOrder, oldOrder: GigOrder?): Boolean {
        if (oldOrder == null) return false
        return newOrder.platform == oldOrder.platform &&
                newOrder.price == oldOrder.price &&
                newOrder.distanceMiles == oldOrder.distanceMiles
    }

    private fun isSameOrder(newOrder: GigOrder, oldOrder: GigOrder?): Boolean {
        if (oldOrder == null) return false
        return isSameGig(newOrder, oldOrder) &&
                newOrder.durationMinutes == oldOrder.durationMinutes &&
                newOrder.dropoffLocation == oldOrder.dropoffLocation
    }
}