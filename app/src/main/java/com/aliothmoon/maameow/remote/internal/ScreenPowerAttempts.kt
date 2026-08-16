package com.aliothmoon.maameow.remote.internal

/** 亮屏/息屏：binder 失败后再注系统键，避免把客户端 AppOps 异常当成 ROM 不支持 */
internal object ScreenPowerAttempts {

    enum class WakeAction { BINDER, KEY_WAKEUP, KEY_POWER }

    enum class SleepAction { BINDER, KEY_SLEEP, KEY_POWER }

    val wakeActions = listOf(WakeAction.BINDER, WakeAction.KEY_WAKEUP, WakeAction.KEY_POWER)

    val sleepActions = listOf(SleepAction.BINDER, SleepAction.KEY_SLEEP, SleepAction.KEY_POWER)

    inline fun <A> run(
        actions: List<A>,
        alreadyDone: () -> Boolean,
        perform: (A) -> Unit,
        pollAfter: (A) -> Boolean,
    ): Boolean {
        if (alreadyDone()) return true
        for (action in actions) {
            perform(action)
            if (pollAfter(action)) return true
        }
        return false
    }
}
