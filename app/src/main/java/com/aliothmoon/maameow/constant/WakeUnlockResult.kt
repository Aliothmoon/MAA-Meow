package com.aliothmoon.maameow.constant

object WakeUnlockResult {
    const val OK = 0
    const val WAKE_FAILED = 1
    const val CREDENTIAL_REQUIRED = 2
    const val CREDENTIAL_REJECTED = 3
    const val NO_KEYGUARD = 4
    const val UNSUPPORTED = 5
    const val LOCK_FAILED = 6

    /** 未录制手势，或录到的步骤为空 */
    const val GESTURE_EMPTY = 7

    /** 分辨率或旋转与录制时不一致 */
    const val GESTURE_SCREEN_MISMATCH = 8

    /** 找不到可读的触摸屏输入设备 */
    const val RECORD_NO_DEVICE = 9

    /** 等待用户解锁超时 */
    const val RECORD_TIMEOUT = 10

    /** 用户主动取消录制 */
    const val RECORD_CANCELLED = 11

    /** 录制期间一个触摸事件都没有，多半是指纹/人脸解锁 */
    const val RECORD_NO_TOUCH = 12
}
