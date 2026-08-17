package com.aliothmoon.maameow.presentation.pip

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.staticCompositionLocalOf
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/** [sourceRect] 是预览区在 window 中的位置，给进入小窗的过渡动画用，拿不到传 null */
data class PipRequest(
    val resolution: DefaultDisplayConfig.Resolution,
    val sourceRect: Rect? = null,
)

/** 后台任务页写 [pipRequest]，API 28~30 的 onUserLeaveHint 兜底读它；只存参数不存 Activity 引用 */
interface PipHost {
    var pipRequest: PipRequest?

    val isInPictureInPicture: StateFlow<Boolean>
}

/** 不直接用 [Rational]：它在纯 JVM 单测里是抛异常的 stub，clamp 逻辑就没法测了 */
data class PipAspectRatio(val numerator: Int, val denominator: Int)

/** 小窗里只该有预览画面，其余 UI 一律不渲染；由 MainActivity 在最外层提供，判断 PIP 的唯一来源 */
val LocalIsInPip = staticCompositionLocalOf { false }

/**
 * 系统画中画：后台模式任务运行中，回桌面时自动缩为小窗继续显示虚拟显示器画面
 *
 * 纯画面，不带 RemoteAction——PIP 窗口的点击被系统消费为「展开控制条」，无法转发触摸到虚拟显示器
 */
object PipController {

    /** 系统对画中画宽高比的硬性区间 2.39:1，越界 setAspectRatio 会抛 IllegalArgumentException */
    private const val MAX_RATIO_NUM = 239
    private const val MAX_RATIO_DEN = 100

    internal const val MAX_RATIO = MAX_RATIO_NUM.toFloat() / MAX_RATIO_DEN
    internal const val MIN_RATIO = 1f / MAX_RATIO

    fun isSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /**
     * 现有 [DefaultDisplayConfig] 只产出 720p/1080p 两档 16:9，走不到边界；
     * 但 setVirtualDisplayResolution 收任意宽高，越界会在 setAspectRatio 直接崩，故留此防线
     */
    fun clampAspectRatio(width: Int, height: Int): PipAspectRatio {
        if (width <= 0 || height <= 0) {
            return PipAspectRatio(
                DefaultDisplayConfig.ASPECT_RATIO_WIDTH,
                DefaultDisplayConfig.ASPECT_RATIO_HEIGHT
            )
        }
        val ratio = width.toFloat() / height
        return when {
            ratio > MAX_RATIO -> PipAspectRatio(MAX_RATIO_NUM, MAX_RATIO_DEN)
            ratio < MIN_RATIO -> PipAspectRatio(MAX_RATIO_DEN, MAX_RATIO_NUM)
            else -> PipAspectRatio(width, height)
        }
    }

    /** API 31+ 顺带开合 auto-enter；低版本只更新参数，进入靠 [enterNow] 兜底 */
    fun updateParams(activity: Activity, autoEnter: Boolean, request: PipRequest) {
        if (!isSupported(activity)) return
        runCatching {
            activity.setPictureInPictureParams(buildParams(request, autoEnter))
        }.onFailure {
            Timber.w(it, "setPictureInPictureParams failed")
        }
    }

    /**
     * API 28~30 没有 auto-enter，只能在 onUserLeaveHint 里手动进入
     *
     * 手势导航下 onUserLeaveHint 不保证触发，进不去就算了，不影响回桌面
     */
    fun enterNow(activity: Activity, request: PipRequest): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return false
        if (!isSupported(activity)) return false
        if (activity.isInPictureInPictureMode) return false
        return runCatching {
            activity.enterPictureInPictureMode(buildParams(request, autoEnter = false))
        }.onFailure {
            Timber.w(it, "enterPictureInPictureMode failed")
        }.getOrDefault(false)
    }

    private fun buildParams(request: PipRequest, autoEnter: Boolean): PictureInPictureParams {
        val ratio = clampAspectRatio(request.resolution.width, request.resolution.height)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(ratio.numerator, ratio.denominator))
        request.sourceRect?.let { builder.setSourceRectHint(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }
}
