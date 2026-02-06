package com.example.dashpilot.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.example.dashpilot.data.GigPrefs
import com.example.dashpilot.manager.GigFlowManager
import com.example.dashpilot.manager.GigSideEffect
import com.example.dashpilot.manager.GigUiState
import com.example.dashpilot.ui.overlay.OverlayController
import com.example.dashpilot.util.GigLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("AccessibilityPolicy")
@AndroidEntryPoint
class GigAccessibilityService : AccessibilityService() {

    @Inject lateinit var flowManager: GigFlowManager
    @Inject lateinit var overlayController: OverlayController
    @Inject lateinit var prefs: GigPrefs

    // Scope for the Service's lifecycle (UI updates, Prefs observation)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Scope for background tasks (Screenshotting)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // VIBE: The "Gatekeeper". Prevents spamming the Manager with useless event spam.
    // We let the Manager handle the heavy synchronization, but this saves us from
    // launching 50 dead coroutines per second.
    private val isScanning = AtomicBoolean(false)

    @Volatile private var isScrapingActive: Boolean = true
    private var lastScrapeTime = 0L

    // Supported Packages
    private val uberPkg = "com.ubercab.driver"
    private val supportedPackages = setOf(
        uberPkg,
        "com.doordash.driverapp",
        "com.grubhub.driver"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info

        // 1. Observe User Preferences (The On/Off Switch)
        scope.launch {
            prefs.isScrapingEnabledFlow.collectLatest { enabled ->
                isScrapingActive = enabled
                if (!enabled) {
                    overlayController.hide()
                    flowManager.clearState()
                }
            }
        }

        // 2. UI Bridge: Connect the Brain (Manager) to the View (Overlay)
        scope.launch {
            flowManager.uiState.collectLatest { state ->
                when (state) {
                    is GigUiState.Hidden -> overlayController.hide()
                    is GigUiState.ShowingOrder -> overlayController.show(state.order)
                    is GigUiState.Stabilizing -> {
                        // Optional: overlayController.showLoading()
                    }
                }
            }
        }

        // 3. Side Effects: Listen for "Requests" from the Brain
        // Example: Manager suspects a new Uber order but wants a 2nd screenshot to verify.
        scope.launch {
            flowManager.sideEffects.collect { effect ->
                when (effect) {
                    is GigSideEffect.RequestRescan -> {
                        // Bypass throttle for verification
                        handleUberScrape(bypassGate = true)
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isScrapingActive) return

        // This is the correct variable derived from the event
        val eventPkg = event.packageName?.toString() ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            // FIX: Allow specific system packages to "interrupt" without killing the overlay
            // This prevents the overlay from disappearing if the Status Bar or Keyboard flickers.
            val ignoredPackages = setOf(
                this.packageName,               // Our own overlay
                "com.android.systemui",         // Status bar / Notification shade
                "com.google.android.inputmethod.latin", // Gboard
                "com.samsung.android.honeyboard" // Samsung Keyboard
            )

            if (eventPkg in ignoredPackages) return

            // If it's not a supported Gig App AND not an ignored system app,
            // THEN we assume the user left and clear state.
            if (eventPkg !in supportedPackages) {
                flowManager.clearState()
                return
            }
        }

        // 2. Gatekeeper
        if (isScanning.get()) return

        // 3. Filter & Throttle
        // BUG FOUND: You were checking 'packageName' (Your App) instead of 'eventPkg' (DoorDash)
        // Since "com.example.gigpilot" isn't in supportedPackages, this ALWAYS returned.
        if (eventPkg !in supportedPackages) return

        val currentTime = System.currentTimeMillis()
        // FIX: Use eventPkg here too
        val throttle = if (eventPkg == uberPkg) 1000L else 500L
        if (currentTime - lastScrapeTime < throttle) return
        lastScrapeTime = currentTime

        // 4. Dispatch
        // FIX: Use eventPkg here too
        if (eventPkg == uberPkg) {
            handleUberScrape()
        } else {
            handleStandardScrape(eventPkg)
        }
    }

    /**
     * [VIBE FIX] Smart Window Scanner
     * Iterates through the system's window list (Z-ordered from top to bottom)
     * to find the target app, even if it doesn't currently have Input Focus.
     */
    private fun findActiveRootNode(targetPackage: String): android.view.accessibility.AccessibilityNodeInfo? {
        // 1. Fast Path: If the active window IS the target, use it (Least expensive)
        val focused = rootInActiveWindow
        if (focused != null && focused.packageName == targetPackage) {
            return focused
        }

        // 2. Deep Scan: Check all visible windows
        // 'windows' is a list provided by AccessibilityService (requires FLAG_RETRIEVE_INTERACTIVE_WINDOWS)
        return windows.firstOrNull { window ->
            window.root?.packageName == targetPackage
        }?.root
    }

    private fun handleUberScrape(bypassGate: Boolean = false) {
        if (!bypassGate && !isScanning.compareAndSet(false, true)) return

        ioScope.launch {
            try {
                // 1. The screenshot takes time (100ms - 500ms)
                val bitmap = takeScreenshotCompat()

                // [VIBE FIX] 2. The "Double Check"
                // If the user switched apps while the screenshot was happening,
                // the top window is no longer Uber. Abort.
                val currentPkg = findActiveRootNode(uberPkg)?.packageName

                // Check if we are STILL in Uber.
                // If active root is null or not Uber, we effectively switched context.
                if (currentPkg != uberPkg && !bypassGate) {
                    GigLogger.i("GigService", "Aborted: User switched apps during capture.")
                    bitmap?.recycle()
                    return@launch
                }

                if (bitmap != null) {
                    flowManager.processUberScreenshot(bitmap)
                    try { bitmap.recycle() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                GigLogger.e("GigService", "Uber Capture Failed", e)
            } finally {
                isScanning.set(false)
            }
        }
    }

    private fun handleStandardScrape(packageName: String) {
        // [VIBE FIX] Don't rely on 'rootInActiveWindow' which returns null if focus is lost.
        // Instead, hunt down the window belonging to the package.
        val rootNode = findActiveRootNode(packageName) ?: return

        if (!isScanning.compareAndSet(false, true)) return

        scope.launch(Dispatchers.Default) {
            try {
                flowManager.onStandardNode(rootNode, packageName)
            } finally {
                isScanning.set(false)
            }
        }
    }

    // --- Boilerplate ---

    private suspend fun takeScreenshotCompat(): Bitmap? = suspendCoroutine { cont ->
        // Keeps your existing implementation exactly as it was
        val executor = Dispatchers.Default.asExecutor()
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            executor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        val colorSpace = screenshot.colorSpace
                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        val softwareBitmap = bitmap?.copy(Bitmap.Config.RGB_565, false)
                        hardwareBuffer.close()
                        bitmap?.recycle()
                        cont.resume(softwareBitmap)
                    } catch (_: Exception) {
                        cont.resume(null)
                    }
                }
                override fun onFailure(errorCode: Int) {
                    cont.resume(null)
                }
            }
        )
    }

    override fun onInterrupt() {
        flowManager.clearState()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayController.onDestroy() // Ensure clean view removal
        scope.cancel()
        ioScope.cancel()
    }
}