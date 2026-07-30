package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.manager.RemoteServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/**
 * 唤醒 + 解锁引擎。通过 [RemoteService]（跑在 root 提权进程）执行 shell 命令，
 * 等价于用户在屏幕上按电源键 + 手势解锁 / 输入密码。
 *
 * 支持的解锁策略：
 *  - `swipe`  : 无锁屏，仅上滑解锁
 *  - `pin`    : PIN 码（数字）
 *  - `password`: 字母数字密码
 *  - `keyguard`: 通过 `svc keyguard disable` 强制解除（部分 ROM 需要额外权限）
 *
 * Pattern（图案）暂不支持：需要把九宫格坐标序列转成 input event，不同厂商分辨率
 * 差异大，MVP 阶段留给用户自行切换到 PIN。
 */
class WakeUnlockEngine {

    enum class UnlockType(val key: String) {
        SWIPE("swipe"),
        PIN("pin"),
        PASSWORD("password"),
        KEYGUARD("keyguard");

        companion object {
            fun fromKey(key: String): UnlockType =
                entries.firstOrNull { it.key == key } ?: SWIPE
        }
    }

    data class WakeConfig(
        val unlockType: UnlockType,
        val credential: String,
        val screenWidth: Int = 1080,
        val screenHeight: Int = 2400,
    )

    /**
     * 执行「唤醒 → 解锁」完整序列。
     * 如果 [RemoteService] 尚未连接（比如设备刚重启 / Shizuku 未启动），
     * 会通过 [RemoteServiceManager.useRemoteService] 等待连接建立（最多 12s）。
     * @return true 表示 shell 命令跑完（不保证解锁成功，需调用方自行校验）
     */
    suspend fun wakeAndUnlock(config: WakeConfig): Boolean = withContext(Dispatchers.IO) {
        val seq = buildCommandSequence(config)
        Timber.i("WakeUnlockEngine: executing %d commands, type=%s", seq.size, config.unlockType)
        // buildCommandSequence 返回的每条命令已经 shell-safe（凭证由 escapeCredential 单独处理），
        // 这里只需拼接，不再整体转义，避免双层转义引号冲突。
        val script = seq.joinToString(" ; ")
        runCatching {
            RemoteServiceManager.useRemoteService(timeoutMs = 12_000) { svc ->
                val rc = svc.executeShellCommand(script)
                Timber.i("WakeUnlockEngine: shell rc=%d", rc)
                rc == 0
            }
        }.getOrElse {
            Timber.w(it, "WakeUnlockEngine: IPC failure")
            false
        }
    }

    /**
     * 执行「息屏上锁 → 唤醒 → 解锁」完整序列，供「立即测试」按钮使用。
     *
     * 与 [wakeAndUnlock] 的区别：测试版会先主动 [justSleep] 让屏幕进入上锁状态，
     * 等待 [lockDelayMs] 让锁屏动画完成，再跑唤醒 + 解锁。
     * 这样才能验证从「屏幕锁住」到「解锁进入桌面」的全链路。
     *
     * 注意：如果用户系统设置「屏幕锁定 = 无」，`KEYCODE_SLEEP` 只会息屏不会上锁，
     * 后续 swipe 仍然会「解锁」，但相当于只验证唤醒 + 上滑，没真正测到解锁动作。
     * UI 上提醒用户在测试前给系统设个 PIN。
     */
    suspend fun lockThenWakeAndUnlock(config: WakeConfig, lockDelayMs: Long = 2_000L): Boolean =
        withContext(Dispatchers.IO) {
            Timber.i("WakeUnlockEngine: [test] locking screen first")
            // 1) 主动息屏（配合系统「按电源键立即上锁」设置 → 屏幕进入锁定状态）
            runCatching {
                RemoteServiceManager.useRemoteService(timeoutMs = 12_000) { svc ->
                    svc.executeShellCommand("input keyevent KEYCODE_SLEEP")
                }
            }
            kotlinx.coroutines.delay(lockDelayMs.milliseconds)
            // 2) 接标准唤醒 + 解锁
            wakeAndUnlock(config)
        }

    /** 仅唤醒屏幕，不做解锁动作（用于「任务完成后再次息屏」等场景）。 */
    suspend fun justWake(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            RemoteServiceManager.useRemoteService(timeoutMs = 12_000) { svc ->
                svc.executeShellCommand("input keyevent KEYCODE_WAKEUP") == 0
            }
        }.getOrDefault(false)
    }

    /** 立即息屏。 */
    suspend fun justSleep(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            RemoteServiceManager.useRemoteService(timeoutMs = 12_000) { svc ->
                svc.executeShellCommand("input keyevent KEYCODE_SLEEP") == 0
            }
        }.getOrDefault(false)
    }

    /** 探测提权进程是否为 root。 */
    suspend fun hasRoot(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            RemoteServiceManager.useRemoteService(timeoutMs = 8_000) { svc ->
                svc.hasRootPrivilege()
            }
        }.getOrDefault(false)
    }

    private fun buildCommandSequence(config: WakeConfig): List<String> = buildList {
        add("input keyevent KEYCODE_WAKEUP")
        add("sleep 1")
        when (config.unlockType) {
            UnlockType.SWIPE -> {
                // 上滑解锁：从 80% 高度滑到 30%
                val x = config.screenWidth / 2
                val y1 = config.screenHeight * 4 / 5
                val y2 = config.screenHeight * 3 / 10
                add("input swipe $x $y1 $x $y2 300")
            }
            UnlockType.PIN, UnlockType.PASSWORD -> {
                // 先上滑进入密码页
                val x = config.screenWidth / 2
                val y1 = config.screenHeight * 4 / 5
                val y2 = config.screenHeight * 3 / 10
                add("input swipe $x $y1 $x $y2 300")
                add("sleep 0.5")
                if (config.credential.isNotBlank()) {
                    // 凭证用「整体包裹单引号」的 escapeCredential 处理：
                    // 保证空格、分号、单引号等特殊字符都能被 shell 正确还原。
                    // 这里不再走外层 joinToString 的 escape（那套是为整条命令设计的，
                    // 不会包裹引号，会让空格被当成参数分隔符）。
                    val safeCredential = escapeCredential(config.credential)
                    add("input text $safeCredential")
                    add("sleep 0.2")
                    add("input keyevent KEYCODE_ENTER")
                }
            }
            UnlockType.KEYGUARD -> {
                // Android 14 (API 34) 起 `svc keyguard` 子命令被 AOSP 删除，
                // 这里加 `|| true` 是 best-effort：成功最好，失败也不阻断后续命令。
                // 用户在 Android 14+ 设备上选 KEYGUARD 解锁方式时会退化为「只唤醒，不解锁」。
                add("svc keyguard disable || true")
            }
        }
    }

    /**
     * 凭证（PIN / 密码）专用转义：用单引号整体包裹，内部单引号换成 `'\''`。
     * 返回值是一个 shell-safe 的 token，作为 `input text` 的单个参数。
     *
     * 例：
     *  - `escapeCredential("1234")`       → `'1234'`           shell 还原 `1234`
     *  - `escapeCredential("pass word")`  → `'pass word'`     shell 还原 `pass word`（含空格）
     *  - `escapeCredential("a'b")`        → `'a'\''b'`         shell 还原 `a'b`
     */
    private fun escapeCredential(s: String): String = buildString {
        append('\'')
        s.forEach { c ->
            if (c == '\'') append("'\\''") else append(c)
        }
        append('\'')
    }
}
