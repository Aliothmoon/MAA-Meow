package com.aliothmoon.maameow.utils.device

internal data class Rom(val name: String, val version: String) {
    val isXiaomi: Boolean get() = name == "hyperos" || name == "miui"
}

/**
 * 厂商 ROM 的类型与版本，两个进程共用
 *
 * 提权失败、定时不触发、后台被杀多半跟 ROM 有关，Android 版本号分不出是哪家
 */
internal object RomDetector {

    private const val OTHER = "other"

    /** 系统属性开机后不变，查一次就够 */
    val current: Rom by lazy { detect() }

    /** 顺序有讲究：HyperOS 仍带着 MIUI 的属性，鸿蒙仍带着 EMUI 的属性 */
    fun detect(prop: (String) -> String = SystemProps::get): Rom {
        prop("ro.mi.os.version.name").ifNotBlank { return Rom("hyperos", it.removePrefix("OS")) }
        prop("ro.miui.ui.version.name").ifNotBlank { return Rom("miui", it) }
        // vivo 的 os.name 在 OriginOS 上仍报 Funtouch，以 display id 为准
        prop("ro.vivo.os.build.display.id").ifNotBlank { return splitNameAndVersion(it) }
        prop("ro.vivo.os.name").ifNotBlank { return Rom(it.lowercase(), prop("ro.vivo.os.version")) }
        prop("ro.build.version.oplusrom").ifNotBlank { return Rom("coloros", it) }
        prop("ro.build.version.opporom").ifNotBlank { return Rom("coloros", it) }
        prop("hw_sc.build.platform.version").ifNotBlank { return Rom("harmonyos", it) }
        prop("ro.build.version.magic").ifNotBlank { return Rom("magicos", it.substringAfter('_')) }
        prop("ro.build.version.emui").ifNotBlank { return Rom("emui", it.substringAfter('_')) }
        prop("ro.build.version.oneui").ifNotBlank { return Rom("oneui", oneUiVersion(it)) }
        return Rom(OTHER, "")
    }

    /** `OriginOS 4` → originos / 4 */
    private fun splitNameAndVersion(display: String): Rom {
        val text = display.trim()
        val version = text.substringAfterLast(' ', "")
        return if (version.firstOrNull()?.isDigit() == true) {
            Rom(text.substringBeforeLast(' ').replace(" ", "").lowercase(), version)
        } else {
            Rom(text.replace(" ", "").lowercase(), "")
        }
    }

    /** 三星把版本编成一个整数，`60101` 即 6.1.1 */
    private fun oneUiVersion(raw: String): String {
        val code = raw.toIntOrNull() ?: return raw
        return "${code / 10000}.${code / 100 % 100}.${code % 100}"
    }

    private inline fun String.ifNotBlank(block: (String) -> Unit) {
        if (isNotBlank()) block(trim())
    }
}
