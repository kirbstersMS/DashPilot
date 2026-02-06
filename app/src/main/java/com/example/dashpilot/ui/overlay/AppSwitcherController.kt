package com.example.dashpilot.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class AppSwitcherController @Inject constructor(
    @param:ApplicationContext private val context: Context
) : LifecycleOwner, SavedStateRegistryOwner {

    // ... [Rest of code remains exactly the same] ...

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null

    private var posX = 0
    private var posY = 300

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun show() {
        if (composeView != null) return

        val displayMetrics = context.resources.displayMetrics
        if (posX == 0) {
            posX = displayMetrics.widthPixels - 200
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = posX
            y = posY
        }

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@AppSwitcherController)
            setViewTreeSavedStateRegistryOwner(this@AppSwitcherController)
            setContent {
                AppSwitcherUI(
                    onDrag = { dx, dy ->
                        posX += dx.roundToInt()
                        posY += dy.roundToInt()

                        layoutParams.x = posX
                        layoutParams.y = posY
                        windowManager.updateViewLayout(this, layoutParams)
                    },
                    onClose = { hide() }
                )
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
            windowManager.removeView(it)
            composeView = null
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    fun toggle() {
        if (composeView == null) show() else hide()
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}