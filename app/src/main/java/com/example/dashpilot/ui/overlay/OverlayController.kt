package com.example.dashpilot.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.dashpilot.data.GigPrefs
import com.example.dashpilot.model.GigOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayController @Inject constructor(
    @param: ApplicationContext private val context: Context,
    private val prefs: GigPrefs
) : LifecycleOwner, SavedStateRegistryOwner {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null

    // VIBE: This is the reactive heart. When we change this, the UI redraws.
    private val currentOrder = mutableStateOf<GigOrder?>(null)

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    /**
     * VIBE: Reactive Update
     * If the view doesn't exist, we build it.
     * If it DOES exist, we just update the state, and Compose handles the rest.
     */
    fun show(order: GigOrder) {
        // 1. Always update the source of truth first
        currentOrder.value = order

        // 2. If already showing, we are done! Compose will recompose.
        if (composeView != null) {
            return
        }

        // 3. First time setup (Cold Start)
        val layoutParams = getLayoutParams()

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@OverlayController)
            setViewTreeSavedStateRegistryOwner(this@OverlayController)

            setContent {
                // VIBE: We read the value inside the composable scope.
                // When 'currentOrder.value' changes, this block re-runs.
                val orderState = currentOrder.value
                if (orderState != null) {
                    OverlayHUD(order = orderState)
                }
            }
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        try {
            windowManager.addView(view, layoutParams)
            composeView = view
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hide() {
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
                // View might already be gone
            }
            composeView = null
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    // --- Window Positioning Helpers ---

    fun updatePosition() {
        composeView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            params.x = prefs.overlayX
            params.y = prefs.overlayY
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun getLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.overlayX
            y = prefs.overlayY
        }
    }

    fun onDestroy() {
        hide()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}