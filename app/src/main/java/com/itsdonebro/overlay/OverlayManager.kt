package com.itsdonebro.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.ViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.itsdonebro.domain.MessageEngine
import com.itsdonebro.domain.TrackingEngine
import com.itsdonebro.domain.TrackingState
import com.itsdonebro.ui.theme.ItsDoneBroTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackingEngine: TrackingEngine,
    private val messageEngine: MessageEngine
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // Floating counter
    private var counterView: View? = null
    private var counterLifecycle: OverlayLifecycleOwner? = null

    // Blocking overlay
    private var blockingView: View? = null
    private var blockingLifecycle: OverlayLifecycleOwner? = null

    // ─── Public control ────────────────────────────────────────────────────────

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    /** Show the floating counter above Instagram (if permission granted). */
    fun showFloatingCounter(stateFlow: StateFlow<TrackingState>) {
        if (!canDrawOverlays() || counterView != null) return
        counterView = createComposeOverlay(
            gravity = Gravity.TOP or Gravity.END,
            x = 16, y = 120,
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            focusable = false,
            lifecycleRef = { counterLifecycle = it }
        ) {
            val state by stateFlow.collectAsState()
            FloatingCounterContent(state = state)
        }
    }

    fun hideFloatingCounter() {
        blockingView?.let { safeRemoveView(it) }
        counterView?.let { safeRemoveView(it) }
        counterView = null
        counterLifecycle?.onDestroy()
        counterLifecycle = null
    }

    /** Show the full-screen blocking overlay when the limit is reached. */
    fun showBlockingOverlay(stateFlow: StateFlow<TrackingState>) {
        if (!canDrawOverlays() || blockingView != null) return
        // Hide the small counter while blocking
        counterView?.let { safeRemoveView(it) }

        blockingView = createComposeOverlay(
            gravity = Gravity.CENTER,
            x = 0, y = 0,
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            focusable = true,
            lifecycleRef = { blockingLifecycle = it }
        ) {
            val state by stateFlow.collectAsState()
            BlockingOverlayContent(
                state = state,
                messageEngine = messageEngine,
                onDone = { dismissAndGoHome() },
                onCloseInstagram = { closeInstagram() },
                onBlockedAttempt = { trackingEngine.onBlockedAttempt() }
            )
        }
    }

    fun hideBlockingOverlay() {
        blockingView?.let { safeRemoveView(it) }
        blockingView = null
        blockingLifecycle?.onDestroy()
        blockingLifecycle = null
    }

    // ─── Internal helpers ──────────────────────────────────────────────────────

    private fun createComposeOverlay(
        gravity: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        focusable: Boolean,
        lifecycleRef: (OverlayLifecycleOwner) -> Unit,
        content: @Composable () -> Unit
    ): View {
        val lifecycleOwner = OverlayLifecycleOwner().also { lco ->
            lco.onCreate()
            lco.onStart()
            lco.onResume()
            lifecycleRef(lco)
        }

        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            ViewTreeLifecycleOwner.set(this, lifecycleOwner)
            ViewTreeViewModelStoreOwner.set(this, lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                ItsDoneBroTheme { content() }
            }
        }

        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }

        val params = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
        }

        windowManager.addView(view, params)
        return view
    }

    private fun safeRemoveView(view: View) {
        try { windowManager.removeView(view) } catch (_: Exception) {}
    }

    private fun dismissAndGoHome() {
        hideBlockingOverlay()
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(homeIntent)
    }

    private fun closeInstagram() {
        dismissAndGoHome()   // navigating home is the cleanest user-visible way to "close" an app
    }

    fun destroy() {
        hideFloatingCounter()
        hideBlockingOverlay()
    }
}
