package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * 全部逻辑围绕一个标记展开：markedPackage 非空，表示该游戏包可能被 MaaMeow 静音过、
 * 声音尚未恢复。内存中的值是权威，DataStore 只用于进程重启后找回标记
 *
 * 状态流转：
 * - [mute]：先持久化标记，再通过 IPC 静音游戏。顺序不能反——这样即使静音生效后
 *   进程立刻崩溃，重启后仍能凭标记恢复声音。IPC 失败时同样保留标记，交给后续恢复兜底
 * - [unmute]：游戏会话关闭时调用。按标记恢复声音并清除标记；失败则保留标记，
 *   等远端下次连接时重试。
 * - [toggle]：手动开关。有标记走恢复，无标记走静音
 * - [startAutoRestore]：监听远端服务连接。连接建立说明上一个游戏会话必然已结束，
 *   此时若标记还在，说明有残留静音，恢复声音并清除标记
 *
 *  游戏被静音却没有标记会导致永久静音
 *  有标记但游戏其实没静音无害,对未静音的包执行恢复只是一次空操作
 */
class GameMuteCoordinator internal constructor(
    private val appSettingsManager: AppSettingsManager,
    private val gameAudioAdapter: GameAudioAdapter,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val mutex = Mutex()

    private val markedPackage = MutableStateFlow(appSettingsManager.initialMutedGamePackage)

    val isMuted: StateFlow<Boolean> = markedPackage
        .map { it.isNotEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, markedPackage.value.isNotEmpty())

    fun startAutoRestore() {
        scope.launch {
            gameAudioAdapter.connected.filter { it }.collect {
                mutex.withLock {
                    if (markedPackage.value.isEmpty()) return@withLock
                    Timber.i(
                        "Service connected with residual mute, restoring %s",
                        markedPackage.value,
                    )
                    unmuteLocked()
                }
            }
        }
    }

    suspend fun mute(clientType: String?): Boolean = mutex.withLock { muteLocked(clientType) }

    suspend fun unmute(): Boolean = mutex.withLock { unmuteLocked() }

    suspend fun toggle(clientType: String?): Boolean = mutex.withLock {
        val marked = markedPackage.value
        Timber.i("Toggle game mute, direction=%s", if (marked.isNotEmpty()) "unmute" else "mute")
        if (marked.isNotEmpty()) unmuteLocked() else muteLocked(clientType)
    }

    private suspend fun muteLocked(clientType: String?): Boolean {
        val pkg = clientType?.let { Packages[it] } ?: return false
        setMarker(pkg)
        val ok = gameAudioAdapter.setMuted(pkg, muted = true)
        if (ok) {
            Timber.i("Muted %s", pkg)
        } else {
            Timber.w("Mute %s failed, marker kept for self-heal", pkg)
        }
        return ok
    }

    private suspend fun unmuteLocked(): Boolean {
        val pkg = markedPackage.value
        if (pkg.isEmpty()) return true
        val ok = gameAudioAdapter.setMuted(pkg, muted = false)
        if (ok) {
            setMarker("")
            Timber.i("Restored audio for %s", pkg)
        } else {
            Timber.w("Restore audio for %s failed, marker kept for reconnect", pkg)
        }
        return ok
    }

    private suspend fun setMarker(packageName: String) {
        appSettingsManager.setMutedGamePackage(packageName)
        markedPackage.value = packageName
    }
}
