package com.aliothmoon.maameow.remote.internal

import android.os.SystemClock
import android.view.KeyEvent
import com.aliothmoon.maameow.maa.InputControlUtils
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.ServiceManager

/**
 * 唤醒 + 解锁，整条序列在提权进程内完成，App 侧只发一次 IPC。
 */
object WakeUnlockController {

    private const val TAG = "WakeUnlock"

    private const val SCREEN_ON_TIMEOUT_MS = 5_000L
    private const val KEYGUARD_GONE_TIMEOUT_MS = 5_000L
    private const val BOUNCER_SETTLE_MS = 1_200L
    private const val POLL_INTERVAL_MS = 100L

    /** 与 AIDL 约定的返回码。 */
    object Result {
        const val OK = 0
        const val WAKE_FAILED = 1
        const val CREDENTIAL_REQUIRED = 2
        const val CREDENTIAL_REJECTED = 3
        const val BOUNCER_NOT_READY = 4
        const val UNSUPPORTED = 5
    }

    /**
     * @param credential 数字 PIN；无凭证锁屏传空串。图案锁不支持。
     */
    fun wakeAndUnlock(credential: String): Int {
        val pm = ServiceManager.getPowerManager()
        val wm = ServiceManager.getWindowManager()

        // 必须先亮屏：dismissKeyguard 只管锁屏状态，息屏下解锁会被推迟到亮屏之后
        if (!pm.isScreenOn(0)) {
            if (!pm.wakeUp()) {
                Ln.w("$TAG: wakeUp() unavailable on this ROM")
                return Result.UNSUPPORTED
            }
            if (!pollUntil(SCREEN_ON_TIMEOUT_MS) { pm.isScreenOn(0) }) {
                Ln.w("$TAG: screen did not turn on within ${SCREEN_ON_TIMEOUT_MS}ms")
                return Result.WAKE_FAILED
            }
        }
        Ln.i("$TAG: screen on")

        val locked = wm.isKeyguardLocked()
        if (locked == null) {
            Ln.w("$TAG: isKeyguardLocked unavailable")
            return Result.UNSUPPORTED
        }
        if (!locked) {
            Ln.i("$TAG: keyguard not showing, nothing to dismiss")
            return Result.OK
        }

        val secure = wm.isKeyguardSecure(0) ?: false
        Ln.i("$TAG: keyguard locked, secure=$secure")

        // 无凭证锁屏会直接解除；有凭证锁屏由系统弹出 bouncer
        if (!wm.dismissKeyguard()) {
            Ln.w("$TAG: dismissKeyguard unavailable on this ROM")
            return Result.UNSUPPORTED
        }

        if (!secure) {
            return if (pollUntil(KEYGUARD_GONE_TIMEOUT_MS) { wm.isKeyguardLocked() == false }) {
                Ln.i("$TAG: unlocked (insecure keyguard)")
                Result.OK
            } else {
                Ln.w("$TAG: insecure keyguard did not dismiss")
                Result.CREDENTIAL_REJECTED
            }
        }

        if (credential.isEmpty()) {
            Ln.w("$TAG: secure keyguard but no credential configured")
            return Result.CREDENTIAL_REQUIRED
        }
        if (credential.any { !it.isDigit() }) {
            Ln.w("$TAG: credential contains non-digit characters; only numeric PIN is supported")
            return Result.CREDENTIAL_REQUIRED
        }

        // isKeyguardLocked 在 bouncer 弹出期间仍为 true，判断不了「可以输入了」。
        // TODO 在设了 PIN 的真机上确认 bouncer 窗口名后，换成轮询真实信号
        Thread.sleep(BOUNCER_SETTLE_MS)
        Ln.i("$TAG: injecting ${credential.length} digits after ${BOUNCER_SETTLE_MS}ms settle")

        for (c in credential) {
            val keyCode = KeyEvent.KEYCODE_0 + (c - '0')
            InputControlUtils.keyDown(keyCode, 0)
            InputControlUtils.keyUp(keyCode, 0)
        }
        // 位数够时系统一般自动提交，开了「需要确认」才要回车；多按一次无害
        InputControlUtils.keyDown(KeyEvent.KEYCODE_ENTER, 0)
        InputControlUtils.keyUp(KeyEvent.KEYCODE_ENTER, 0)

        return if (pollUntil(KEYGUARD_GONE_TIMEOUT_MS) { wm.isKeyguardLocked() == false }) {
            Ln.i("$TAG: unlocked (PIN accepted)")
            Result.OK
        } else {
            // 不重试：PIN 连续输错会触发系统锁定倒计时，有 MDM 策略的设备可能触发擦除
            Ln.w("$TAG: still locked after PIN injection — wrong PIN, or this ROM's keyguard ignores injected keys")
            Result.CREDENTIAL_REJECTED
        }
    }

    /** 仅点亮屏幕，不解锁。 */
    fun wakeOnly(): Boolean {
        val pm = ServiceManager.getPowerManager()
        if (pm.isScreenOn(0)) return true
        if (!pm.wakeUp()) return false
        return pollUntil(SCREEN_ON_TIMEOUT_MS) { pm.isScreenOn(0) }
    }

    private inline fun pollUntil(timeoutMs: Long, cond: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (cond()) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return cond()
    }
}
