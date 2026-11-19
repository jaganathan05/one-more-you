package com.onemoreyou.blocking

import android.content.Context
import android.util.Log
import android.graphics.PixelFormat
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.onemoreyou.R
import com.onemoreyou.ui.OneMoreYouTheme
import com.onemoreyou.ui.overlay.BlockingOverlay
import com.onemoreyou.ui.overlay.CountdownBanner

sealed interface OverlayContent {
    data object Hidden : OverlayContent

    data class Blocking(val state: BlockScreenState) : OverlayContent

    data class Countdown(
        val appLabel: String,
        val remainingMillis: Long,
    ) : OverlayContent
}

class OverlayController(
    context: Context,
    private val onRequestPass: () -> Unit,
    private val onLeave: () -> Unit,
) {
    private val windowContext = ContextThemeWrapper(context, R.style.Theme_OneMoreYou)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val content = mutableStateOf<OverlayContent>(OverlayContent.Hidden)
    private var host: FrameLayout? = null
    private var owner: OverlayLifecycleOwner? = null
    private var attachedKind: Kind? = null
    private var windowParams: WindowManager.LayoutParams? = null

    fun render(next: OverlayContent) {
        if (next is OverlayContent.Hidden) {
            detach()
            content.value = OverlayContent.Hidden
            return
        }
        val kind = if (next is OverlayContent.Countdown) Kind.BANNER else Kind.BLOCK
        val current = host
        if (current == null) {
            content.value = next
            attach(kind)
            return
        }
        if (attachedKind != kind) {
            val changed = runCatching { windowManager.updateViewLayout(current, layoutParams(kind)) }
            if (changed.isSuccess) {
                attachedKind = kind
                Log.i("OMY", "overlay $kind")
            } else {
                Log.e("OMY", "overlay update failed", changed.exceptionOrNull())
            }
        }
        content.value = next
    }

    fun hide() = render(OverlayContent.Hidden)

    private fun attach(kind: Kind) {
        val lifecycleOwner = OverlayLifecycleOwner().also {
            it.create()
            it.resume()
        }
        val composeView = ComposeView(windowContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                OneMoreYouTheme {
                    when (val state = content.value) {
                        OverlayContent.Hidden -> Unit
                        is OverlayContent.Blocking -> BlockingOverlay(
                            state = state.state,
                            onConfirmPass = onRequestPass,
                            onLeave = onLeave,
                        )
                        is OverlayContent.Countdown -> CountdownBanner(
                            appLabel = state.appLabel,
                            remainingMillis = state.remainingMillis,
                        )
                    }
                }
            }
        }
        val container = FrameLayout(windowContext).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            addView(composeView)
        }
        val params = layoutParams(kind)
        try {
            windowManager.addView(container, params)
        } catch (error: RuntimeException) {
            Log.e("OMY", "overlay add failed", error)
            host = null
            attachedKind = null
            owner?.destroy()
            owner = null
            return
        }
        host = container
        owner = lifecycleOwner
        attachedKind = kind
    }

    private fun detach() {
        val current = host
        host = null
        attachedKind = null
        if (current != null) {
            runCatching { windowManager.removeView(current) }
        }
        owner?.destroy()
        owner = null
    }

    private fun layoutParams(kind: Kind): WindowManager.LayoutParams {
        val banner = kind == Kind.BANNER
        val params = windowParams ?: WindowManager.LayoutParams().also { windowParams = it }
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = if (banner) WindowManager.LayoutParams.WRAP_CONTENT else WindowManager.LayoutParams.MATCH_PARENT
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        params.flags = if (banner) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        }
        params.format = PixelFormat.TRANSLUCENT
        params.gravity = if (banner) Gravity.TOP else Gravity.CENTER
        params.y = if (banner) 150 else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return params
    }

    private enum class Kind { BLOCK, BANNER }

    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val store = ViewModelStore()
        private val savedState = SavedStateRegistryController.create(this)
        private var destroyed = false

        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun create() {
            savedState.performAttach()
            savedState.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        fun resume() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun destroy() {
            if (destroyed) return
            destroyed = true
            if (registry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            }
            if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
            if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
            store.clear()
        }
    }
}
