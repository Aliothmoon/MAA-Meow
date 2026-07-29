package com.aliothmoon.maameow.remote.internal

import android.app.AppOpsManager
import com.aliothmoon.maameow.third.Ln


object AppOpsHelper {
    private const val TAG = "AppOpsHelper"

    private const val OP_PLAY_AUDIO = 28

    /** 读取当前生效 mode；读取失败返回 -1 */
    fun checkPlayAudioMode(packageName: String, uid: Int): Int = runCatching {
        RemoteUtils.appOpsService.checkOperation(OP_PLAY_AUDIO, uid, packageName)
    }.getOrElse {
        Ln.w("$TAG: checkOperation failed for $packageName: ${it.message}")
        -1
    }

    /** 设置 PLAY_AUDIO mode 并校验实际生效 */
    fun setPlayAudioMode(packageName: String, uid: Int, mode: Int): Boolean {
        val viaBinder = runCatching {
            RemoteUtils.appOpsService.setMode(OP_PLAY_AUDIO, uid, packageName, mode)
        }.onFailure {
            Ln.w("$TAG: setMode via binder failed for $packageName: ${it.message}")
        }.isSuccess

        if (!viaBinder) {
            val arg = if (mode == AppOpsManager.MODE_ALLOWED) "allow" else "ignore"
            RemoteUtils.shellExec("appops set $packageName PLAY_AUDIO $arg")
        }
        return checkPlayAudioMode(packageName, uid) == mode
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
        val mode = checkPlayAudioMode(packageName, uid)
        if (mode < 0) {
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

    internal fun isPlayAudioRestored(mode: Int): Boolean =
        mode >= 0 && mode != AppOpsManager.MODE_IGNORED
}
