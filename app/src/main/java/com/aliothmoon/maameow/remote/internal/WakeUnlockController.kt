package com.aliothmoon.maameow.remote.internal

import android.view.KeyEvent
import com.aliothmoon.maameow.constant.WakeUnlockResult
import com.aliothmoon.maameow.domain.models.UnlockGesture
import com.aliothmoon.maameow.maa.InputControlUtils
import com.aliothmoon.maameow.remote.internal.ScreenPowerAttempts.SleepAction
import com.aliothmoon.maameow.remote.internal.ScreenPowerAttempts.WakeAction
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.PowerManager
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import com.aliothmoon.maameow.third.wrappers.WindowManager

/** 唤醒/解锁/锁屏；提权进程内完成，凭证支持纯数字 PIN 与录制手势 */
object WakeUnlockController {

    private const val TAG = "WakeUnlock"

    private const val SCREEN_ON_TIMEOUT_MS = 5_000L
    private const val KEY_WAKE_TIMEOUT_MS = 2_000L
    private const val KEYGUARD_GONE_TIMEOUT_MS = 5_000L
    private const val BOUNCER_SETTLE_MS = 1_200L
    private const val DIGIT_GAP_MS = 50L

    /** 手势回放前等锁屏首屏稳定；不弹 bouncer，比 PIN 那条路要短 */
    private const val GESTURE_SETTLE_MS = 800L

    /** 测试：上锁/息屏后等待系统稳定再解锁 */
    private const val LOCK_SETTLE_MS = 500L
    private const val SCREEN_OFF_TIMEOUT_MS = 3_000L

    /**
     * 设置页自测：先 [lockAndSleep]，等待 [LOCK_SETTLE_MS] 后再 [unlock]
     * 整段在提权进程内完成，避免息屏后 App 侧协程被挂起
     */
    fun testUnlock(credential: String): Int {
        val lockCode = lockAndSleep()
        if (lockCode != WakeUnlockResult.OK) return lockCode
        Ln.i("$TAG: locked for test, settle ${LOCK_SETTLE_MS}ms")
        Thread.sleep(LOCK_SETTLE_MS)
        return unlock(credential)
    }

    /** 设置页自测：手势版 */
    fun testUnlockGesture(gestureJson: String): Int {
        val lockCode = lockAndSleep()
        if (lockCode != WakeUnlockResult.OK) return lockCode
        Ln.i("$TAG: locked for gesture test, settle ${LOCK_SETTLE_MS}ms")
        Thread.sleep(LOCK_SETTLE_MS)
        return unlockWithGesture(gestureJson)
    }

    /** lockNow 上锁并 goToSleep 息屏 */
    fun lockAndSleep(): Int {
        val pm = ServiceManager.getPowerManager()
        val wm = ServiceManager.getWindowManager()

        if (!wm.lockNow()) {
            Ln.w("$TAG: lockNow unavailable")
            return WakeUnlockResult.UNSUPPORTED
        }
        if (!pollUntil(KEYGUARD_GONE_TIMEOUT_MS) { wm.isKeyguardLocked == true }) {
            // 锁屏方式为「无」时 lockNow 后 keyguard 永不出现；滑动/密码锁屏均会出现，
            // 超时且非 secure 即视为未设置锁屏，此时也无需息屏验证
            if (wm.isKeyguardSecure(0) != true) {
                Ln.i("$TAG: keyguard never appeared and not secure — no lock screen configured")
                return WakeUnlockResult.NO_KEYGUARD
            }
            Ln.w("$TAG: keyguard did not lock after lockNow")
            return WakeUnlockResult.LOCK_FAILED
        }

        if (!ensureScreenOff(pm)) {
            Ln.w("$TAG: screen still on after sleep attempts (keyguard already locked)")
        } else {
            Ln.i("$TAG: screen locked and off")
        }
        return WakeUnlockResult.OK
    }

    /**
     * 亮屏并确认 keyguard 还在
     * @return 非 null 即调用方应当直接返回的结果码
     */
    private fun wakeAndRequireKeyguard(pm: PowerManager, wm: WindowManager): Int? {
        if (!ensureScreenOn(pm)) {
            Ln.w("$TAG: screen did not turn on after wakeUp and key fallback")
            return WakeUnlockResult.WAKE_FAILED
        }
        Ln.i("$TAG: screen on")

        val locked = wm.isKeyguardLocked
        if (locked == null) {
            Ln.w("$TAG: isKeyguardLocked unavailable")
            return WakeUnlockResult.UNSUPPORTED
        }
        if (!locked) {
            Ln.i("$TAG: keyguard not showing, nothing to do")
            return WakeUnlockResult.OK
        }
        return null
    }

    /** 亮屏并解除锁屏；@param credential 纯数字 PIN，无凭证锁屏传空串 */
    fun unlock(credential: String): Int {
        val pm = ServiceManager.getPowerManager()
        val wm = ServiceManager.getWindowManager()

        wakeAndRequireKeyguard(pm, wm)?.let { return it }

        val secure = wm.isKeyguardSecure(0) ?: false
        Ln.i("$TAG: keyguard locked, secure=$secure")

        if (!wm.dismissKeyguard()) {
            Ln.w("$TAG: dismissKeyguard unavailable on this ROM")
            return WakeUnlockResult.UNSUPPORTED
        }

        if (!secure) {
            return if (pollUntil(KEYGUARD_GONE_TIMEOUT_MS) { wm.isKeyguardLocked() == false }) {
                Ln.i("$TAG: unlocked (insecure keyguard)")
                WakeUnlockResult.OK
            } else {
                Ln.w("$TAG: insecure keyguard did not dismiss")
                WakeUnlockResult.CREDENTIAL_REJECTED
            }
        }

        if (credential.isEmpty()) {
            Ln.w("$TAG: secure keyguard but no credential configured")
            return WakeUnlockResult.CREDENTIAL_REQUIRED
        }
        if (credential.any { !it.isDigit() }) {
            Ln.w("$TAG: only numeric PIN is supported")
            return WakeUnlockResult.CREDENTIAL_REQUIRED
        }

        // bouncer 弹出期间 isKeyguardLocked 仍为 true，先 settle
        Thread.sleep(BOUNCER_SETTLE_MS)
        Ln.i("$TAG: injecting ${credential.length} PIN digits after ${BOUNCER_SETTLE_MS}ms settle")

        for (c in credential) {
            val keyCode = KeyEvent.KEYCODE_0 + (c - '0')
            InputControlUtils.keyDown(keyCode, 0)
            InputControlUtils.keyUp(keyCode, 0)
            Thread.sleep(DIGIT_GAP_MS)
        }
        // 部分 ROM 会自动提交；补 ENTER 兼容需确认的 PIN
        InputControlUtils.keyDown(KeyEvent.KEYCODE_ENTER, 0)
        InputControlUtils.keyUp(KeyEvent.KEYCODE_ENTER, 0)

        return if (pollUntil(KEYGUARD_GONE_TIMEOUT_MS) { wm.isKeyguardLocked() == false }) {
            Ln.i("$TAG: unlocked (PIN accepted)")
            WakeUnlockResult.OK
        } else {
            // 不重试，避免连续输错触发系统锁定
            Ln.w("$TAG: still locked after PIN injection — wrong PIN, or keyguard ignores injected keys")
            WakeUnlockResult.CREDENTIAL_REJECTED
        }
    }

    /**
     * 亮屏并回放录制的解锁手势
     * @param gestureJson 空串表示未录制
     */
    fun unlockWithGesture(gestureJson: String): Int {
        val gesture = UnlockGesture.parseOrNull(gestureJson) { Ln.w("$TAG: $it") }
        if (gesture == null || gesture.steps.isEmpty()) {
            Ln.w("$TAG: no usable gesture recorded")
            return WakeUnlockResult.GESTURE_EMPTY
        }

        val pm = ServiceManager.getPowerManager()
        val wm = ServiceManager.getWindowManager()

        wakeAndRequireKeyguard(pm, wm)?.let { return it }

        // 与录制一致，在 keyguard 还在时采样
        val screen = ScreenGeometry.current()
        if (screen.rotation != gesture.rotation) {
            Ln.w("$TAG: rotation ${screen.rotation} != recorded ${gesture.rotation}")
            return WakeUnlockResult.GESTURE_SCREEN_MISMATCH
        }
        if (screen.width != gesture.screenWidth || screen.height != gesture.screenHeight) {
            Ln.w(
                "$TAG: screen ${screen.width}x${screen.height} != recorded" +
                    " ${gesture.screenWidth}x${gesture.screenHeight}, scaling"
            )
        }

        // 录制起点就是亮屏后的锁屏首屏，这里不能再 dismissKeyguard 打乱状态
        Thread.sleep(GESTURE_SETTLE_MS)
        val actions = UnlockGestureReplay.timeline(gesture, screen.width, screen.height)
        Ln.i("$TAG: replaying ${gesture.steps.size} steps / ${actions.size} actions")
        UnlockGestureReplay.execute(actions)

        return if (pollUntil(KEYGUARD_GONE_TIMEOUT_MS) { wm.isKeyguardLocked() == false }) {
            Ln.i("$TAG: unlocked (gesture accepted)")
            WakeUnlockResult.OK
        } else {
            // 与 PIN 一致：不重试，避免连错触发系统锁定
            Ln.w("$TAG: still locked after gesture replay — 轨迹失效，或 keyguard 忽略注入事件")
            WakeUnlockResult.CREDENTIAL_REJECTED
        }
    }

    /** 只亮屏，不碰 keyguard；录制手势时用，和回放走同一条唤醒路径 */
    fun wakeScreen(): Boolean = ensureScreenOn(ServiceManager.getPowerManager())

    private fun ensureScreenOn(pm: PowerManager): Boolean =
        ScreenPowerAttempts.run(
            actions = ScreenPowerAttempts.wakeActions,
            alreadyDone = { pm.isScreenOn(0) },
            perform = { action ->
                when (action) {
                    WakeAction.BINDER -> {
                        if (!pm.wakeUp()) {
                            Ln.w("$TAG: wakeUp() invoke failed, polling then falling back to keys")
                        }
                    }
                    WakeAction.KEY_WAKEUP -> {
                        Ln.w("$TAG: injecting KEYCODE_WAKEUP")
                        injectKey(KeyEvent.KEYCODE_WAKEUP)
                    }
                    WakeAction.KEY_POWER -> if (!pm.isScreenOn(0)) {
                        Ln.w("$TAG: injecting KEYCODE_POWER")
                        injectKey(KeyEvent.KEYCODE_POWER)
                    }
                }
            },
            pollAfter = { action ->
                val timeout = if (action == WakeAction.BINDER) {
                    SCREEN_ON_TIMEOUT_MS
                } else {
                    KEY_WAKE_TIMEOUT_MS
                }
                val on = pollUntil(timeout) { pm.isScreenOn(0) }
                if (!on) Ln.w("$TAG: screen still off after $action")
                on
            },
        )

    private fun ensureScreenOff(pm: PowerManager): Boolean =
        ScreenPowerAttempts.run(
            actions = ScreenPowerAttempts.sleepActions,
            alreadyDone = { !pm.isScreenOn(0) },
            perform = { action ->
                when (action) {
                    SleepAction.BINDER -> {
                        if (!pm.goToSleep()) {
                            Ln.w("$TAG: goToSleep() invoke failed, polling then falling back to keys")
                        }
                    }
                    SleepAction.KEY_SLEEP -> {
                        Ln.w("$TAG: injecting KEYCODE_SLEEP")
                        injectKey(KeyEvent.KEYCODE_SLEEP)
                    }
                    SleepAction.KEY_POWER -> if (pm.isScreenOn(0)) {
                        Ln.w("$TAG: injecting KEYCODE_POWER")
                        injectKey(KeyEvent.KEYCODE_POWER)
                    }
                }
            },
            pollAfter = { action ->
                val timeout = if (action == SleepAction.BINDER) {
                    SCREEN_OFF_TIMEOUT_MS
                } else {
                    KEY_WAKE_TIMEOUT_MS
                }
                val off = pollUntil(timeout) { !pm.isScreenOn(0) }
                if (!off) Ln.w("$TAG: screen still on after $action")
                off
            },
        )

    private fun injectKey(keyCode: Int) {
        InputControlUtils.keyDown(keyCode, 0)
        InputControlUtils.keyUp(keyCode, 0)
    }

}
