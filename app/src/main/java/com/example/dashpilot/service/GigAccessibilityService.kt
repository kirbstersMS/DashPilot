package com.example.dashpilot.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.view.accessibility.AccessibilityEvent
import com.example.dashpilot.data.GigPrefs
import com.example.dashpilot.manager.GigFlowManager
import com.example.dashpilot.manager.GigUiState
import com.example.dashpilot.ui.overlay.OverlayController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@SuppressLint("AccessibilityPolicy")
@AndroidEntryPoint
class GigAccessibilityService : AccessibilityService() {

    @Inject lateinit var flowManager: GigFlowManager
    @Inject lateinit var overlayController: OverlayController
    @Inject lateinit var prefs: GigPrefs

    // Scope for the Service's lifecycle
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Gatekeeper to prevent event spam
    private val isScanning = AtomicBoolean(false)

    @Volatile private var isScrapingActive: Boolean = true
    private var lastScrapeTime = 0L

    // Target Package
    private val dashPackage = "com.doordash.driverapp"

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info

        // 1. Observe User Preferences
        scope.launch {
            prefs.isScrapingEnabledFlow.collectLatest { enabled ->
                isScrapingActive = enabled
                if (!enabled) {
                    overlayController.hide()
                    flowManager.clearState()
                }
            }
        }

        // 2. UI Bridge
        scope.launch {
            flowManager.uiState.collectLatest { state ->
                when (state) {
                    is GigUiState.Hidden -> overlayController.hide()
                    is GigUiState.ShowingOrder -> overlayController.show(state.order)
                    else -> {}
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isScrapingActive) return

        val eventPkg = event.packageName?.toString() ?: return

        // 1. Context Switch Detection
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val ignoredPackages = setOf(
                this.packageName,
                "com.android.systemui",
                "com.google.android.inputmethod.latin",
                "com.samsung.android.honeyboard"
            )

            if (eventPkg in ignoredPackages) return

            // If we left DoorDash, clear state immediately
            if (eventPkg != dashPackage) {
                flowManager.clearState()
                return
            }
        }

        // 2. Gatekeeper & Filter
        if (eventPkg != dashPackage) return
        if (isScanning.get()) return

        // 3. Throttle (500ms)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScrapeTime < 500L) return
        lastScrapeTime = currentTime

        // 4. Dispatch
        handleStandardScrape(eventPkg)
    }

    private fun findActiveRootNode(targetPackage: String): android.view.accessibility.AccessibilityNodeInfo? {
        val focused = rootInActiveWindow
        if (focused != null && focused.packageName == targetPackage) {
            return focused
        }
        return windows.firstOrNull { window ->
            window.root?.packageName == targetPackage
        }?.root
    }

    private fun handleStandardScrape(packageName: String) {
        val rootNode = findActiveRootNode(packageName) ?: return

        if (!isScanning.compareAndSet(false, true)) return

        // We launch on Default dispatcher to keep the service responsive
        scope.launch(Dispatchers.Default) {
            try {
                flowManager.onStandardNode(rootNode)
            } finally {
                isScanning.set(false)
            }
        }
    }

    override fun onInterrupt() {
        flowManager.clearState()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayController.onDestroy()
        scope.cancel()
    }
}