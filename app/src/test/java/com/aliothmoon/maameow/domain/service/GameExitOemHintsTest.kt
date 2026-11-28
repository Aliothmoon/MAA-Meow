package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameExitOemHintsTest {

    @Test
    fun romNameTakesPriorityOverManufacturer() {
        assertEquals(R.string.game_exit_oem_hint_miui, GameExitOemHints.hintResFor("hyperos", "unknown"))
        assertEquals(R.string.game_exit_oem_hint_miui, GameExitOemHints.hintResFor("miui", "unknown"))
        assertEquals(R.string.game_exit_oem_hint_vivo, GameExitOemHints.hintResFor("originos", "unknown"))
        assertEquals(R.string.game_exit_oem_hint_vivo, GameExitOemHints.hintResFor("funtouch", "unknown"))
        assertEquals(R.string.game_exit_oem_hint_oppo, GameExitOemHints.hintResFor("coloros", "unknown"))
        assertEquals(R.string.game_exit_oem_hint_huawei, GameExitOemHints.hintResFor("harmonyos", "unknown"))
        assertEquals(R.string.game_exit_oem_hint_huawei, GameExitOemHints.hintResFor("magicos", "unknown"))
        assertEquals(R.string.game_exit_oem_hint_huawei, GameExitOemHints.hintResFor("emui", "unknown"))
        assertEquals(R.string.game_exit_oem_hint_samsung, GameExitOemHints.hintResFor("oneui", "unknown"))
    }

    @Test
    fun manufacturerFallbackCoversKnownBrands() {
        assertEquals(R.string.game_exit_oem_hint_miui, GameExitOemHints.hintResFor("other", "Xiaomi"))
        assertEquals(R.string.game_exit_oem_hint_miui, GameExitOemHints.hintResFor("other", "Redmi"))
        assertEquals(R.string.game_exit_oem_hint_miui, GameExitOemHints.hintResFor("other", "POCO"))
        assertEquals(R.string.game_exit_oem_hint_vivo, GameExitOemHints.hintResFor("other", "vivo"))
        assertEquals(R.string.game_exit_oem_hint_vivo, GameExitOemHints.hintResFor("other", "iQOO"))
        assertEquals(R.string.game_exit_oem_hint_oppo, GameExitOemHints.hintResFor("other", "OPPO"))
        assertEquals(R.string.game_exit_oem_hint_oppo, GameExitOemHints.hintResFor("other", "OnePlus"))
        assertEquals(R.string.game_exit_oem_hint_oppo, GameExitOemHints.hintResFor("other", "realme"))
        assertEquals(R.string.game_exit_oem_hint_huawei, GameExitOemHints.hintResFor("other", "HUAWEI"))
        assertEquals(R.string.game_exit_oem_hint_huawei, GameExitOemHints.hintResFor("other", "HONOR"))
        assertEquals(R.string.game_exit_oem_hint_samsung, GameExitOemHints.hintResFor("other", "samsung"))
        assertEquals(R.string.game_exit_oem_hint_lenovo, GameExitOemHints.hintResFor("other", "Lenovo"))
        assertEquals(R.string.game_exit_oem_hint_meizu, GameExitOemHints.hintResFor("other", "Meizu"))
    }

    @Test
    fun unknownRomAndManufacturerGetNoHint() {
        assertNull(GameExitOemHints.hintResFor("other", "Google"))
        assertNull(GameExitOemHints.hintResFor("other", "motorola"))
        assertNull(GameExitOemHints.hintResFor("", ""))
    }
}
