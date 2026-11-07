package com.aliothmoon.maameow.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.OpenableColumns
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.net.toUri
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.utils.i18n.LocalizedException
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import timber.log.Timber
import kotlin.math.abs

object Misc {

    /** 16:9 容差（相对目标比例），与首页悬浮层启动校验一致 */
    private const val ASPECT_16X9_TOLERANCE = 0.05f

    private const val RESTART_FALLBACK_KILL_MS = 3_000L

    /**
     * 物理尺寸非法（≤0）时抛出；消息走资源，调用方展示前 resolve。
     * 用 [LocalizedException] 而非 require：这是开发期兜底，理论上不该触发。
     */
    class InvalidPhysicalSizeException : LocalizedException(
        uiTextOf(R.string.resolution_invalid_physical_size)
    )

    fun getScreenSize(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            displayMetrics.widthPixels to displayMetrics.heightPixels
        }
    }

    /** 是否接近 16:9（长短边比，与横竖屏无关） */
    fun isAspectRatio16x9(
        width: Int,
        height: Int,
        tolerance: Float = ASPECT_16X9_TOLERANCE
    ): Boolean {
        val longSide = maxOf(width, height)
        val shortSide = minOf(width, height)
        if (shortSide <= 0) return false
        val ratio = longSide.toFloat() / shortSide.toFloat()
        val target = 16f / 9f
        return abs(ratio - target) <= target * tolerance
    }

    fun getPhysicalSize(context: Context): Pair<Int, Int> {
        val display = if (Build.VERSION.SDK_INT >= 30) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        }

        val mode = display?.mode
        val physicalWidth = mode?.physicalWidth ?: 0
        val physicalHeight = mode?.physicalHeight ?: 0

        return physicalWidth to physicalHeight
    }

    fun calculate16x9Resolution(physicalWidth: Int, physicalHeight: Int): Pair<Int, Int> {
        if (physicalWidth <= 0 || physicalHeight <= 0) throw InvalidPhysicalSizeException()

        // 16:9 的最小单位
        val unitW = 16
        val unitH = 9

        // 是否是横屏
        val isLandscape = physicalWidth >= physicalHeight
        val maxW = if (isLandscape) physicalWidth else physicalHeight
        val maxH = if (isLandscape) physicalHeight else physicalWidth

        if (maxW < 1280 || maxH < 720) {
            throw LocalizedException(
                uiTextOf(R.string.resolution_below_minimum, maxW, maxH)
            )
        }

        val scaleByWidth = maxW / unitW
        val scaleByHeight = maxH / unitH
        val scale = minOf(scaleByWidth, scaleByHeight)

        val width = unitW * scale
        val height = unitH * scale

        return if (isLandscape) {
            width to height
        } else {
            height to width
        }
    }

    fun bringAppToFront(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent?.let { context.startActivity(it) }
    }

    // RestartActivity 没起来时兜底自杀
    fun restartApp(context: Context) {
        context.startActivity(
            Intent(context, RestartActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(RestartActivity.EXTRA_MAIN_PID, Process.myPid())
        )
        Handler(Looper.getMainLooper()).postDelayed(
            { Process.killProcess(Process.myPid()) },
            RESTART_FALLBACK_KILL_MS
        )
    }

    fun queryFileName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    }

    fun openUriSafely(context: Context, uriString: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uriString.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open URI: $uriString")
        }
    }
}
