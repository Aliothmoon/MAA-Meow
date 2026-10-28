package com.aliothmoon.maameow.remote.internal

import android.app.AppOpsManager
import android.os.Build
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.utils.device.RomDetector
import java.lang.reflect.InvocationTargetException

/** appop 定义：AOSP 按名解析，ROM 私有的只有编号 */
class AppOp(
    val label: String,
    /** AOSP 的 APP_OP_PERMISSION_UID_OPS：非默认的 uid 模式会盖过包模式，两级都写 */
    val uidScoped: Boolean = false,
    resolve: () -> Int?,
) {
    /** 本机没有这个 op（SDK 不对、不是对应 ROM）时为 null */
    val code: Int? by lazy(resolve)
}

object AppOps {
    val SYSTEM_ALERT_WINDOW = aosp("android:system_alert_window")
    val POST_NOTIFICATION = aosp("android:post_notification")
    val PLAY_AUDIO = aosp("android:play_audio")
    val RUN_IN_BACKGROUND = aosp("android:run_in_background")
    val RUN_ANY_IN_BACKGROUND = aosp("android:run_any_in_background")
    val MANAGE_EXTERNAL_STORAGE = aosp("android:manage_external_storage", uidScoped = true)
    val FOREGROUND_SERVICE_SPECIAL_USE =
        aosp("android:foreground_service_special_use", uidScoped = true)

    // 13+ 靠清单里的 USE_EXACT_ALARM，只有 12/12L 要它
    val SCHEDULE_EXACT_ALARM = aosp(
        "android:schedule_exact_alarm", uidScoped = true, maxSdk = Build.VERSION_CODES.S_V2
    )

    // 安全中心的「自启动」「后台弹出界面」
    val MIUI_AUTO_START = xiaomi("miui:auto_start", 10008)
    val MIUI_BACKGROUND_START_ACTIVITY = xiaomi("miui:background_start_activity", 10021)

    val SCHEDULE = arrayOf(SCHEDULE_EXACT_ALARM, MIUI_AUTO_START, MIUI_BACKGROUND_START_ACTIVITY)

    private val strOpToOp by lazy {
        runCatching { AppOpsManager::class.java.getMethod("strOpToOp", String::class.java) }
            .onFailure { Ln.w("AppOps: strOpToOp unavailable: $it") }
            .getOrNull()
    }

    private fun aosp(opStr: String, uidScoped: Boolean = false, maxSdk: Int = Int.MAX_VALUE) =
        AppOp(opStr.removePrefix("android:"), uidScoped) {
            if (Build.VERSION.SDK_INT > maxSdk) return@AppOp null
            try {
                strOpToOp?.invoke(null, opStr) as Int?
            } catch (e: InvocationTargetException) {
                // 名字不认识即本机没有，其他异常要报
                if (e.cause !is IllegalArgumentException) Ln.w("AppOps: resolve $opStr failed: ${e.cause}")
                null
            }
        }

    private fun xiaomi(label: String, code: Int) = AppOp(label) { code.takeIf { RomDetector.current.isXiaomi } }
}
