package com.aliothmoon.maameow.manager

import android.os.ParcelFileDescriptor
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import timber.log.Timber

/**
 * user-service 启动失败的 logcat 归因
 *
 * 起服务进程的代码在 Shizuku manager APK 里（ServiceStarter / UserService），
 * 崩溃只出现在系统 logcat，App 侧唯一的失败表象是超时
 * 超时瞬间借 Shizuku shell 身份抓一段 starter 日志做定性，写入错误 message 供日志导出定位
 *
 * Shizuku.newProcess 已 private（API 14 计划移除），这里直接走 IShizukuService AIDL
 */
object ShizukuFailureDiagnostics {

    /** manager APK 的 ServiceStarter 硬编码 tag */
    private const val STARTER_TAG = "ShizukuServiceStarter"

    /** 归因仅看结尾段，整 buffer 按 tag 过滤后在本地截尾 */
    private const val RECENT_LINES = 60

    /**
     * 抓不到（server 已死/无日志）返回 null，调用方降级为普通超时文案
     */
    fun collectUserServiceTimeoutHint(): String? = runCatching {
        val serverBinder = Shizuku.getBinder()
        checkNotNull(serverBinder) { "Shizuku binder unavailable" }
        val server = IShizukuService.Stub.asInterface(serverBinder)
        // 不用 -t：它按全量日志截尾，吵闹设备上 35s 前的崩溃会被冲掉；
        // -s 只在客户端过滤，整 buffer 取该 tag 后本地截尾
        val remote = server.newProcess(
            arrayOf("logcat", "-d", "-s", "$STARTER_TAG:W"),
            null, null
        )
        val output = ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream)
            .use { it.readBytes().decodeToString() }
        runCatching { remote.destroy() }
        classify(output.lines().takeLast(RECENT_LINES).joinToString("\n"))
    }.onFailure {
        Timber.w(it, "collect user service timeout hint failed")
    }.getOrNull()

    internal fun classify(log: String): String? {
        // 大小写不敏感：manager 混淆/版本更新可能改变日志大小写形态
        val lower = log.lowercase()
        return when {
            // 联发科/MIUI HyperOS 改过 LoadedApk.makeApplicationInner，进程内 NPE 后直接退出
            // Shizuku 13.6.0 引入的回归（13.5.4 正常），修复 PR#299 至今未合并上游
            // RikkaApps/Shizuku#1198
            lower.contains("makeapplicationinner") && lower.contains("unable to start service") ->
                "starter crashed: OEM LoadedApk.makeApplication NPE (MediaTek/MIUI HyperOS, " +
                    "Shizuku 13.6.0 regression, see Shizuku issue 1198); " +
                    "downgrade Shizuku to 13.5.4 or switch to Root backend"

            lower.contains("unable to start service") ->
                "starter crashed before attaching binder, check logcat tag $STARTER_TAG"

            else -> null
        }
    }
}
