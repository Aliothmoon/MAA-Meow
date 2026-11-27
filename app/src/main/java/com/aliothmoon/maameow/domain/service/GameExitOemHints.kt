package com.aliothmoon.maameow.domain.service

import android.os.Build
import androidx.annotation.StringRes
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.utils.device.RomDetector
import java.util.Locale

internal object GameExitOemHints {

    @StringRes
    fun hintResForCurrentDevice(): Int? =
        hintResFor(RomDetector.current.name, Build.MANUFACTURER.orEmpty())

    @StringRes
    fun hintResFor(romName: String, manufacturer: String): Int? {
        val rom = romName.trim().lowercase(Locale.ROOT)
        when {
            rom == "hyperos" || rom == "miui" -> return R.string.game_exit_oem_hint_miui
            rom.startsWith("originos") || rom.startsWith("funtouch") -> return R.string.game_exit_oem_hint_vivo
            rom == "coloros" -> return R.string.game_exit_oem_hint_oppo
            rom == "harmonyos" || rom == "emui" || rom == "magicos" -> return R.string.game_exit_oem_hint_huawei
            rom == "oneui" -> return R.string.game_exit_oem_hint_samsung
        }
        return when (manufacturer.trim().lowercase(Locale.ROOT)) {
            "xiaomi", "redmi", "poco", "blackshark" -> R.string.game_exit_oem_hint_miui
            "vivo", "iqoo" -> R.string.game_exit_oem_hint_vivo
            "oppo", "realme", "oneplus" -> R.string.game_exit_oem_hint_oppo
            "huawei", "honor" -> R.string.game_exit_oem_hint_huawei
            "samsung" -> R.string.game_exit_oem_hint_samsung
            "lenovo" -> R.string.game_exit_oem_hint_lenovo
            "meizu" -> R.string.game_exit_oem_hint_meizu
            else -> null
        }
    }
}
