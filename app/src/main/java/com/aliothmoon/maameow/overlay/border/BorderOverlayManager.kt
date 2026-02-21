package com.aliothmoon.maameow.overlay.border

import android.content.Context
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber


class BorderOverlayManager(private val context: Context) {
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: BorderOverlayView? = null
    private var currentStyle: BorderStyle = BorderStyle()


    @Suppress("DEPRECATION")
    suspend fun show(style: BorderStyle = BorderStyle()) {
        if (overlayView != null) {
            if (currentStyle == style) {
                Timber.d("Border overlay already showing with same style")
                return
            }
            hide()
        }

        currentStyle = style

        try {
            overlayView = BorderOverlayView(context, style).apply {
                systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
            }

            val params = createLayoutParams()
            withContext(Dispatchers.Main) {
                windowManager.addView(overlayView, params)
            }
            Timber.d("Border overlay shown")
        } catch (e: Exception) {
            Timber.e(e, "Failed to show border overlay")
            overlayView = null
        }
    }

    suspend fun hide() {
        overlayView?.let { view ->
            try {
                withContext(Dispatchers.Main) {
                    windowManager.removeView(view)
                }
                Timber.d("Border overlay hidden")
            } catch (e: Exception) {
                Timber.e(e, "Failed to hide border overlay")
            }
            overlayView = null
        }
    }


    suspend fun toggle(style: BorderStyle = BorderStyle()) {
        if (isShowing()) {
            hide()
        } else {
            show(style)
        }
    }

    fun isShowing(): Boolean = overlayView != null

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type =
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        // 不可聚焦、不可触摸、全屏布局、可超出屏幕边界
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
