package com.aliothmoon.maameow.domain.service

/** 硬件熄屏：只管关，亮屏交给系统唤醒 */
interface ScreenPowerController {

    enum class Origin { MANUAL, SCHEDULE }

    /** 面板被我们关着，此时系统仍算亮屏未锁；系统休眠、被唤醒或提权服务换了就作废 */
    val poweredOff: Origin?

    /** 指令发出即返回，提权服务不可用时为 false */
    fun powerOff(origin: Origin): Boolean
}
