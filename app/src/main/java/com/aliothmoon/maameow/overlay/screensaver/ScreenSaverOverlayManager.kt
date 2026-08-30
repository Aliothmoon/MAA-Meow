package com.aliothmoon.maameow.overlay.screensaver

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.domain.service.ScreenSaverController
import com.aliothmoon.maameow.theme.MaaMeowTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

class ScreenSaverOverlayManager(
    private val context: Context,
    private val sessionLogger: MaaSessionLogger
) : LifecycleOwner, SavedStateRegistryOwner, ScreenSaverController {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    private val _showing = MutableStateFlow(false)
    val showing: StateFlow<Boolean> = _showing.asStateFlow()

    private var lifecycleReady = false

    /** Lifecycle 须主线程；Koin 可能在 IO 构造，推迟到 show */
    private fun ensureLifecycleReady() {
        if (lifecycleReady) return
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleReady = true
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun isShowing(): Boolean = _showing.value

    override suspend fun show(): Boolean =
        withContext(Dispatchers.Main.immediate) { showInternal() }

    override suspend fun hide() = withContext(Dispatchers.Main.immediate) { hideInternal() }

    private fun showInternal(): Boolean {
        if (_showing.value) return true
        ensureLifecycleReady()

        composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@ScreenSaverOverlayManager)
            setViewTreeSavedStateRegistryOwner(this@ScreenSaverOverlayManager)

            setContent {
                MaaMeowTheme(themeMode = AppSettingsManager.ThemeMode.DARK) {
                    val baseDensity = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            density = baseDensity.density,
                            fontScale = baseDensity.fontScale.coerceIn(0.85f, 1.3f)
                        )
                    ) {
                        ScreenSaverView(
                            sessionLogger = sessionLogger,
                            onUnlock = { hideInternal() }
                        )
                    }
                }
            }
        }

        val layoutParams = createLayoutParams()
        try {
            windowManager.addView(composeView, layoutParams)
            // 排除底部手势区域，防止系统后退手势拦截横向滑动
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                composeView?.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    val barHeightPx = (v.resources.displayMetrics.density * 120).toInt()
                    v.systemGestureExclusionRects =
                        listOf(Rect(0, v.height - barHeightPx, v.width, v.height))
                }
            }
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            _showing.value = true
        } catch (e: Exception) {
            Timber.e(e, "Failed to show screen saver overlay")
            hideInternal()
        }
        return _showing.value
    }

    private fun hideInternal() {
        val view = composeView ?: return
        composeView = null
        _showing.value = false

        try {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            windowManager.removeView(view)
        } catch (e: Exception) {
            Timber.e(e, "Failed to hide screen saver overlay")
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        @Suppress("DEPRECATION")
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            screenBrightness = 0.01f // 设置屏幕亮度极暗以省电和防烧屏
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
