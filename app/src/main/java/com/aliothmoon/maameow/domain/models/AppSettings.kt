package com.aliothmoon.maameow.domain.models

import com.aliothmoon.preferences.PrefKey
import com.aliothmoon.preferences.PrefSchema

@PrefSchema
data class AppSettings(
    @PrefKey(default = "ACCESSIBILITY") val overlayMode: String = "ACCESSIBILITY",

    @PrefKey(default = "FOREGROUND") val runMode: String = "FOREGROUND",

    @PrefKey(default = "GITHUB") val updateSource: String = "GITHUB",

    @PrefKey(default = "") val mirrorChyanCdk: String = "",

    @PrefKey(default = "false") val debugMode: String = "false"
)
