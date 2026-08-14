package com.aliothmoon.maameow.schedule.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import timber.log.Timber

/**
 * 拉起系统的精确闹钟开关页
 *
 * 不走 XXPermissions：这一项没有运行时授权流程，只有一个系统设置页，跳过去就完了
 * API 31 以下没有这个开关，调用方不该走到这里（见 [ScheduleAlarmManager.canScheduleExact]）
 */
object ExactAlarmSettings {

    fun open(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "Failed to open exact-alarm settings") }
    }

    /** API 31 起用户可单独关掉精确闹钟；关了仍能定时，只是状态栏多个图标 */
    fun isAllowed(sdkInt: Int, canScheduleExactAlarms: Boolean): Boolean =
        sdkInt < Build.VERSION_CODES.S || canScheduleExactAlarms

    /** 31 以下没有那个系统开关页，入口要藏掉 */
    fun hasToggle(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.S
}
