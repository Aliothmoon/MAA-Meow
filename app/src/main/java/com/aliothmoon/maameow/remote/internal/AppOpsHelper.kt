package com.aliothmoon.maameow.remote.internal

import android.app.AppOpsManager
import android.os.Process
import com.aliothmoon.maameow.third.Ln
import com.android.internal.app.IAppOpsService

/** 走 binder：`appops set` 底层是同一个 setMode，还拿不到结果 */
object AppOpsHelper {
    private const val TAG = "AppOpsHelper"

    /** 读当前生效 mode；本机没有该 op 或读取失败返回 null */
    fun check(
        op: AppOp,
        packageName: String,
        uid: Int,
        service: () -> IAppOpsService = RemoteUtils::appOpsService,
    ): Int? {
        val code = op.code ?: return null
        return runCatching { service().checkOperation(code, uid, packageName) }
            .onFailure { Ln.w("$TAG: check ${op.label} failed for $packageName: ${it.message}") }
            .getOrNull()
    }

    /** 写完读回才算成功；uid 无效时 setMode 静默丢弃且读回未必分得出，先挡掉 */
    fun set(
        op: AppOp,
        packageName: String,
        uid: Int,
        mode: Int,
        service: () -> IAppOpsService = RemoteUtils::appOpsService,
    ): Boolean {
        val code = op.code ?: run {
            Ln.i("$TAG: ${op.label} unavailable, skip")
            return false
        }
        if (uid < Process.FIRST_APPLICATION_UID) {
            Ln.w("$TAG: ${op.label} $packageName invalid uid=$uid, skip")
            return false
        }
        val before = check(op, packageName, uid, service)
        if (before == mode) return true
        // 分开写，uid 级失败不连累包级
        if (op.uidScoped) {
            runCatching { service().setUidMode(code, uid, mode) }
                .onFailure { Ln.w("$TAG: setUidMode ${op.label} failed for $packageName: ${it.message}") }
        }
        runCatching { service().setMode(code, uid, packageName, mode) }
            .onFailure { Ln.w("$TAG: setMode ${op.label} failed for $packageName: ${it.message}") }
        val after = check(op, packageName, uid, service)
        if (after == mode) {
            Ln.i("$TAG: ${op.label} $packageName $before -> $after")
            return true
        }
        Ln.w("$TAG: ${op.label} $packageName $before -> $after, want $mode")
        return false
    }

    /**
     * 将该包 AppOps 恢复为系统默认（`appops reset <package>`）。
     * 成功条件：PLAY_AUDIO 不再是 IGNORED；读不到 mode 时以 shell exitCode 为准。
     */
    fun resetPackage(packageName: String): Boolean {
        val exitCode = RemoteUtils.shellExec("appops reset $packageName")
        val uid = RemoteUtils.getAppUid(packageName)
        if (uid < 0) {
            Ln.w("$TAG: reset $packageName exit=$exitCode, cannot verify mode (no uid)")
            return exitCode == 0
        }
        val mode = check(AppOps.PLAY_AUDIO, packageName, uid)
        if (mode == null) {
            Ln.w("$TAG: reset $packageName exit=$exitCode, checkOperation failed")
            return exitCode == 0
        }
        val restored = isPlayAudioRestored(mode)
        if (restored) {
            Ln.i("$TAG: reset $packageName ok (mode=$mode, exit=$exitCode)")
        } else {
            Ln.w("$TAG: reset $packageName still muted (mode=$mode, exit=$exitCode)")
        }
        return restored
    }

    internal fun isPlayAudioRestored(mode: Int): Boolean = mode != AppOpsManager.MODE_IGNORED
}
