package com.aliothmoon.maameow.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.system.exitProcess
import androidx.core.net.toUri
import timber.log.Timber

object Misc {

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
        require(physicalWidth > 0 && physicalHeight > 0) {
            "物理尺寸必须为正数"
        }

        // 16:9 的最小单位
        val unitW = 16
        val unitH = 9

        // 是否是横屏
        val isLandscape = physicalWidth >= physicalHeight
        val maxW = if (isLandscape) physicalWidth else physicalHeight
        val maxH = if (isLandscape) physicalHeight else physicalWidth

        require(maxW >= 1280 && maxH >= 720) {
            "屏幕尺寸 ${maxW}x${maxH} 小于最低要求 1280x720"
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

    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.let { context.startActivity(it) }
        Process.killProcess(Process.myPid())
        exitProcess(0)
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
