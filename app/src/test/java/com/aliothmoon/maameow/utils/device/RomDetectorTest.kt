package com.aliothmoon.maameow.utils.device

import org.junit.Assert.assertEquals
import org.junit.Test

class RomDetectorTest {

    private fun detect(vararg props: Pair<String, String>): Rom {
        val map = props.toMap()
        return RomDetector.detect { map[it].orEmpty() }
    }

    /** 真机 Xiaomi 12X：HyperOS 上 MIUI 的属性还在 */
    @Test
    fun `HyperOS 先于 MIUI`() {
        assertEquals(
            Rom("hyperos", "1.0"),
            detect("ro.mi.os.version.name" to "OS1.0", "ro.miui.ui.version.name" to "V816"),
        )
        assertEquals(Rom("miui", "V14"), detect("ro.miui.ui.version.name" to "V14"))
    }

    /** 真机 vivo V2046A：os.name 报的是 Funtouch，实际是 OriginOS */
    @Test
    fun `vivo 以 display id 为准`() {
        assertEquals(
            Rom("originos", "4"),
            detect(
                "ro.vivo.os.build.display.id" to "OriginOS 4",
                "ro.vivo.os.name" to "Funtouch",
                "ro.vivo.os.version" to "14.0",
            ),
        )
        assertEquals(
            Rom("funtouch", "13.0"),
            detect("ro.vivo.os.name" to "Funtouch", "ro.vivo.os.version" to "13.0"),
        )
        assertEquals(Rom("originosocean", ""), detect("ro.vivo.os.build.display.id" to "OriginOS Ocean"))
    }

    @Test
    fun `鸿蒙先于 EMUI`() {
        assertEquals(
            Rom("harmonyos", "4.0.0"),
            detect("hw_sc.build.platform.version" to "4.0.0", "ro.build.version.emui" to "EmotionUI_13.0.0"),
        )
        assertEquals(Rom("emui", "12.0.0"), detect("ro.build.version.emui" to "EmotionUI_12.0.0"))
        assertEquals(Rom("magicos", "8.0"), detect("ro.build.version.magic" to "MagicOS_8.0"))
    }

    @Test
    fun `其余厂商`() {
        assertEquals(Rom("coloros", "V14.0.0"), detect("ro.build.version.oplusrom" to "V14.0.0"))
        assertEquals(Rom("oneui", "6.1.1"), detect("ro.build.version.oneui" to "60101"))
        assertEquals(Rom("other", ""), detect())
    }
}
