package com.example.dashpilot.manager

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import com.example.dashpilot.data.GigDao
import com.example.dashpilot.model.GigOrder
import com.example.dashpilot.strategy.DoorDashStrategy
import com.example.dashpilot.strategy.GrubhubStrategy
import com.example.dashpilot.strategy.ScrapeResult
import com.example.dashpilot.strategy.UberOcrStrategy
import com.example.dashpilot.util.GigLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GigFlowManager @Inject constructor(
    private val gigDao: GigDao
) {

    // --- State & Events ---
    private val _uiState = MutableStateFlow<GigUiState>(GigUiState.Hidden)
    val uiState = _uiState.asStateFlow()

    private val _sideEffects = Channel<GigSideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    // --- Internals ---
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processingMutex = Mutex()

    // --- Strategies ---
    private val uberStrategy = UberOcrStrategy()
    private val ddStrategy = DoorDashStrategy()
    private val ghStrategy = GrubhubStrategy()

    // --- History & Logic ---
    private val recentHistory = ArrayDeque<GigOrder>()
    private val maxHistorySize = 5
    private var potentialOrder: GigOrder? = null

    // VIBE FIX: Debounce counter to prevent flickering
    private var consecutiveMisses = 0
    private val missThreshold = 3

    fun onStandardNode(rootNode: AccessibilityNodeInfo?, packageName: String) {
        if (rootNode == null) return

        scope.launch {
            if (processingMutex.isLocked) return@launch
            processingMutex.withLock {
                try {
                    val strategy = when (packageName) {
                        "com.doordash.driverapp" -> ddStrategy
                        else -> ghStrategy
                    }
                    val result = strategy.attemptScrape(rootNode)
                    handleResult(result)
                } catch (e: Exception) {
                    GigLogger.e("GigFlow", "Standard scrape failed", e)
                }
            }
        }
    }

    suspend fun processUberScreenshot(bitmap: Bitmap) {
        processingMutex.withLock {
            try {
                val result = uberStrategy.processScreenshot(bitmap)
                handleResult(result)
            } catch (e: Exception) {
                GigLogger.e("GigFlow", "Uber scrape failed", e)
            }
        }
    }

    fun clearState() {
        _uiState.value = GigUiState.Hidden
        potentialOrder = null
        consecutiveMisses = 0 // Reset misses on manual clear
    }

    // --- Core Logic ---

    private suspend fun handleResult(result: ScrapeResult) {
        when (result) {
            is ScrapeResult.Success -> {
                // VIBE FIX: Reset misses immediately on success
                consecutiveMisses = 0
                val incoming = result.order

                // 1. Check History (Dedup)
                val existing = recentHistory.find { isSameOrder(incoming, it) }

                if (existing != null) {
                    potentialOrder = null

                    // VIBE FIX: The "Recall" Logic
                    // If it's in history but we are Hidden (e.g., switched back to app), SHOW IT AGAIN.
                    // We do NOT add to history or DB again.
                    if (_uiState.value is GigUiState.Hidden) {
                        _uiState.value = GigUiState.ShowingOrder(incoming)
                    }
                    return
                }

                // 2. Routing
                if (incoming.platform == "Uber Eats") {
                    handleUberStabilization(incoming)
                } else {
                    publishOrder(incoming)
                }
            }
            is ScrapeResult.NotFound -> {
                // VIBE FIX: The "Grace Period"
                // Don't hide immediately. Wait for several misses.
                consecutiveMisses++

                if (consecutiveMisses >= missThreshold) {
                    potentialOrder = null
                    _uiState.value = GigUiState.Hidden
                }
            }
            is ScrapeResult.Error -> {
                potentialOrder = null
            }
        }
    }

    private suspend fun handleUberStabilization(incoming: GigOrder) {
        if (isSameOrder(incoming, potentialOrder)) {
            publishOrder(incoming)
            potentialOrder = null
        } else {
            potentialOrder = incoming
            _sideEffects.send(GigSideEffect.RequestRescan)
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
                    (previousVersion.isEstimate && !order.isEstimate) ||
                            (previousVersion.dropoffLocation.isBlank() && order.dropoffLocation.isNotBlank())
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
                newOrder.isEstimate == oldOrder.isEstimate &&
                newOrder.dropoffLocation == oldOrder.dropoffLocation
    }
}