package com.aliothmoon.maameow.utils.device

/**
 * 小米「USB 调试（安全设置）」：关着时 shell 身份的触控输入会被系统拦截
 * 运行时读属性即生效；App 进程就能读，不依赖提权服务
 */
internal object XiaomiAdbInput {

    fun isRestricted(): Boolean =
        RomDetector.current.isXiaomi && SystemProps.get("persist.security.adbinput") != "1"
}
