package com.aliothmoon.maameow.remote.internal

import android.app.AppOpsManager
import com.aliothmoon.maameow.third.Ln


object GameAudioMuteController {
    private const val TAG = "GameAudioMute"

    private data class MuteRecord(
        val uid: Int,
        val playerFallback: Boolean,
    )

    // 所有访问均在 @Synchronized 入口内，无需并发容器
    private val muted = mutableMapOf<String, MuteRecord>()

    @Synchronized
    fun setMuted(packageName: String, muted: Boolean): Boolean {
        return if (muted) mute(packageName) else unmute(packageName)
    }

    @Synchronized
    fun restoreAll() {
        muted.keys.toList().forEach { unmute(it) }
    }

    private fun mute(pkg: String): Boolean {
        muted[pkg]?.let {
            Ln.i("$TAG: $pkg already muted")
            return true
        }

        val uid = RemoteUtils.getAppUid(pkg)
        if (uid < 0) {
            Ln.w("$TAG: mute $pkg failed - cannot resolve uid")
            return false
        }

        if (AppOpsHelper.setPlayAudioMode(pkg, uid, AppOpsManager.MODE_IGNORED)) {
            muted[pkg] = MuteRecord(uid, playerFallback = false)
            Ln.i("$TAG: muted $pkg via appops (uid=$uid)")
            return true
        }

        Ln.w("$TAG: appops mute failed for $pkg, falling back to player volume")
        if (PlayerVolumeFallback.engage(uid)) {
            muted[pkg] = MuteRecord(uid, playerFallback = true)
            return true
        }
        // 两条路径都失败：reset 清掉可能半生效的 appops
        AppOpsHelper.resetPackage(pkg)
        return false
    }

    private fun unmute(pkg: String): Boolean {
        val record = muted[pkg]
        val uid = record?.uid ?: RemoteUtils.getAppUid(pkg)

        if (record?.playerFallback == true && uid >= 0) {
            PlayerVolumeFallback.disengage(uid)
        }

        val ok = AppOpsHelper.resetPackage(pkg)
        if (ok) {
            muted.remove(pkg)
            Ln.i("$TAG: unmuted $pkg via appops reset")
            return true
        }

        // player 音量路径已 disengage，即使 reset 失败也视为本进程侧恢复完成
        if (record?.playerFallback == true) {
            muted.remove(pkg)
            Ln.w("$TAG: unmute $pkg appops reset failed after player disengage")
            return true
        }

        Ln.w("$TAG: unmute $pkg failed (appops reset)")
        return false
    }
}
