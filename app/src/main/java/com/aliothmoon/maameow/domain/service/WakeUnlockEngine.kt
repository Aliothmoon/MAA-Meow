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
        /** 用户校准的滑动起点（屏幕百分比 0.0–1.0）。null 表示未校准，引擎回退到默认坐标。 */
        val swipeStartCalibration: SwipeCalibration? = null,
        /** swipe 后等待秒数（给 PIN 键盘弹出 + 密码框获焦预留时间）。默认 1.5s。 */
        val pinWaitSec: Float = 1.5f,
        /** 解锁失败最大重试次数（「shell 成功但仍锁屏」时重试）。0 表示不重试。 */
        val maxRetries: Int = 2,
    )

    /**
     * 用户校准的滑动起点坐标（屏幕百分比）。
     * 通过「设置 → 校准滑动起点」功能让用户在真机上触摸一次得到。
     */
    data class SwipeCalibration(
        val xPercent: Float,  // 0.0–1.0，相对屏幕宽度
        val yPercent: Float,  // 0.0–1.0，相对屏幕高度
    )

    // 屏幕尺寸缓存：同一进程生命周期内分辨率不会变，避免每次 wakeAndUnlock 都跑 wm size。
    @Volatile
    private var cachedScreenWidth: Int? = null
    @Volatile
    private var cachedScreenHeight: Int? = null

    /**
     * 执行「唤醒 → 解锁」完整序列。
     * 如果 [RemoteService] 尚未连接（比如设备刚重启 / Shizuku 未启动），
     * 会通过 [RemoteServiceManager.useRemoteService] 等待连接建立（最多 12s）。
     *
     * 内部流程：
     *  1) 通过 `wm size` 动态查询屏幕分辨率（首次查询后缓存，失败回退默认值）
     *  2) 按分辨率计算 swipe 坐标（起点 90% 高度，终点 30% 高度）
     *  3) 执行唤醒 + 解锁 shell 序列
     *  4) 通过 `dumpsys window` 校验是否真的解锁
     *  5) 若「shell 成功但仍锁屏」且未达重试上限，等待 1s 后重新执行整个序列
     *
     * @return true 表示 shell 命令跑完（不保证解锁成功，需看日志确认）
     */
    suspend fun wakeAndUnlock(config: WakeConfig): Boolean = withContext(Dispatchers.IO) {
        // 1) 动态查询屏幕分辨率（优先用缓存，避免每次都跑 wm size）
        val (screenW, screenH) = queryScreenSize(
            fallbackW = config.screenWidth,
            fallbackH = config.screenHeight,
        )
        Timber.i(
            "WakeUnlockEngine: screen=%dx%d, type=%s, calibrated=%s, " +
                "pinWait=%.1fs, maxRetries=%d",
            screenW, screenH, config.unlockType,
            config.swipeStartCalibration?.let { "(%.2f,%.2f)".format(it.xPercent, it.yPercent) }
                ?: "no (fallback 50%,90%)",
            config.pinWaitSec,
            config.maxRetries,
        )

        val effectiveConfig = config.copy(screenWidth = screenW, screenHeight = screenH)
        // PIN/PASSWORD/SWIPE 需要解锁校验，失败才重试；KEYGUARD 直接相信 shell rc
        val shouldVerify = config.unlockType != UnlockType.KEYGUARD

        var lastShellOk = false
        // 最多跑 1 + maxRetries 次（首次 + 重试）
        val maxAttempts = 1 + config.maxRetries.coerceAtLeast(0)
        for (attempt in 1..maxAttempts) {
            if (attempt > 1) {
                Timber.w("WakeUnlockEngine: retry attempt %d/%d", attempt, maxAttempts)
                kotlinx.coroutines.delay(1000)
            }

            // 2) 构建命令序列（swipe 坐标基于实际分辨率计算）
            val seq = buildCommandSequence(effectiveConfig)
            Timber.i("WakeUnlockEngine: executing %d commands (attempt %d)", seq.size, attempt)
            val script = seq.joinToString(" ; ")

            // 3) 执行主序列
            lastShellOk = runCatching {
                RemoteServiceManager.useRemoteService(timeoutMs = 12_000) { svc ->
                    val rc = svc.executeShellCommand(script)
                    Timber.i("WakeUnlockEngine: shell rc=%d", rc)
                    rc == 0
                }
            }.getOrElse {
                Timber.w(it, "WakeUnlockEngine: IPC failure")
                false
            }

            // 4) 解锁校验
            if (!lastShellOk || !shouldVerify) break
            val unlocked = verifyUnlocked()
            if (unlocked == true) {
                Timber.i("WakeUnlockEngine: unlock verified ✓ (attempt %d)", attempt)
                break
            } else if (unlocked == false) {
                Timber.w(
                    "WakeUnlockEngine: still locked after attempt %d ✗ — " +
                        "possible causes: swipe coordinate missed, PIN rejected, " +
                        "PIN box not focused, or ROM blocked input injection",
                    attempt,
                )
                // 若还有重试机会就 continue，否则 fall through
                if (attempt < maxAttempts) continue
            } else {
                Timber.w("WakeUnlockEngine: unlock state unknown (dumpsys parse failed)")
                break
            }
        }

        lastShellOk
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
            UnlockType.SWIPE, UnlockType.PIN, UnlockType.PASSWORD -> {
                // 滑动起点：优先用用户校准值，否则回退到屏幕宽度 50% / 高度 90%
                // （MIUI/ColorOS/AOSP 手势区一般在底部 ~15%，90% 高度能覆盖绝大多数设备）
                val (x, y1) = config.swipeStartCalibration?.let { cal ->
                    (config.screenWidth * cal.xPercent).toInt() to
                        (config.screenHeight * cal.yPercent).toInt()
                } ?: (config.screenWidth / 2) to (config.screenHeight * 9 / 10)
                val y2 = config.screenHeight * 3 / 10
                add("input swipe $x $y1 $x $y2 300")
                if (config.unlockType == UnlockType.PIN || config.unlockType == UnlockType.PASSWORD) {
                    // 等待 PIN 键盘弹出 + 密码框获焦（MIUI/ColorOS 动画较慢，默认 1.5s）
                    add("sleep ${config.pinWaitSec}")
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
     * 通过 `wm size` 查询当前屏幕分辨率。首次查询后缓存，后续调用直接返回缓存值。
     * 查询失败（比如提权进程未连上、wm 命令不可用）返回 [fallbackW] × [fallbackH]。
     *
     * 输出示例：`Physical size: 1440x3200`（MIUI 可能带 `Override size: ...` 行，取 Physical）
     */
    private suspend fun queryScreenSize(fallbackW: Int = 1080, fallbackH: Int = 2400): Pair<Int, Int> {
        cachedScreenWidth?.let { w ->
            cachedScreenHeight?.let { h -> return w to h }
        }
        val (w, h) = runCatching {
            RemoteServiceManager.useRemoteService(timeoutMs = 6_000) { svc ->
                val out = svc.executeShellCommandCaptureOutput("wm size")
                parseWmSize(out)
            }
        }.getOrNull() ?: (fallbackW to fallbackH)
        cachedScreenWidth = w
        cachedScreenHeight = h
        if (w != fallbackW || h != fallbackH) {
            Timber.i("WakeUnlockEngine: wm size resolved to %dx%d", w, h)
        } else {
            Timber.w("WakeUnlockEngine: wm size failed, fallback to %dx%d", fallbackW, fallbackH)
        }
        return w to h
    }

    /**
     * 解析 `wm size` 输出。支持多行（Physical / Override），优先取 Physical。
     * 示例输入：
     * ```
     * Physical size: 1440x3200
     * Override size: 1080x2400
     * ```
     */
    private fun parseWmSize(output: String): Pair<Int, Int>? {
        val physical = Regex("""Physical size:\s*(\d+)x(\d+)""").find(output)
            ?: Regex("""(\d+)x(\d+)""").find(output)
            ?: return null
        val w = physical.groupValues[1].toIntOrNull() ?: return null
        val h = physical.groupValues[2].toIntOrNull() ?: return null
        if (w <= 0 || h <= 0) return null
        return w to h
    }

    /**
     * 通过 `dumpsys window` 校验当前是否已解锁。
     * @return true=已解锁, false=仍在锁屏, null=无法解析（输出为空或关键字缺失）
     *
     * 判定逻辑：扫描输出中的 `mDreamingLockscreen=true` / `mShowingLockscreen=true`，
     * 任意一个为 true 即认为仍锁屏。不同 ROM 字段命名有差异（MIUI/ColorOS/AOSP）
     * 但这两个字段在绝大多数 ROM 都存在，作为 MVP 判定基准。
     */
    private suspend fun verifyUnlocked(): Boolean? {
        val output = runCatching {
            RemoteServiceManager.useRemoteService(timeoutMs = 6_000) { svc ->
                svc.executeShellCommandCaptureOutput("dumpsys window")
            }
        }.getOrNull() ?: return null
        if (output.isBlank()) return null
        val stillLocked = output.lineSequence().any { line ->
            line.contains("mDreamingLockscreen=true") ||
                line.contains("mShowingLockscreen=true")
        }
        return !stillLocked
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
