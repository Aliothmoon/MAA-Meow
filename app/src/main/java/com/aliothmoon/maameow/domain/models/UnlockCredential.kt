package com.aliothmoon.maameow.domain.models

/**
 * 定时解锁用的凭证
 * 「哪种解锁方式配哪种负载、算不算配好了」只在这里判一次，
 * 免得启动管线与设置页各推一遍还会走岔
 */
sealed interface UnlockCredential {

    /** 无凭证锁屏，靠 dismissKeyguard */
    data object Swipe : UnlockCredential

    data class Pin(val digits: String) : UnlockCredential

    data class Gesture(val json: String) : UnlockCredential

    /** 设备真要密码时，这份凭证能不能解开 */
    val isReady: Boolean
        get() = when (this) {
            Swipe -> false
            is Pin -> digits.isNotBlank()
            is Gesture -> json.isNotBlank()
        }

    companion object {
        const val TYPE_SWIPE = "swipe"
        const val TYPE_PIN = "pin"
        const val TYPE_GESTURE = "gesture"

        val TYPES = setOf(TYPE_SWIPE, TYPE_PIN, TYPE_GESTURE)

        /** 手势没录成就退回滑动，至少还能把屏点亮 */
        fun of(type: String, pin: String, gestureJson: String): UnlockCredential = when (type) {
            TYPE_PIN -> Pin(pin)
            TYPE_GESTURE -> if (gestureJson.isBlank()) Swipe else Gesture(gestureJson)
            else -> Swipe
        }
    }
}
