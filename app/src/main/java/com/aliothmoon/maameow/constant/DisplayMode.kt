package com.aliothmoon.maameow.constant

object DisplayMode {
    const val PRIMARY = 1
    const val BACKGROUND = 2
}

object VirtualDisplayBackendMode {
    const val AUTO = 0
    const val LEGACY = 1
    const val VDM = 2
}

object VirtualDisplayUpgradeOutcome {
    const val NONE = 0
    const val UPGRADED = 1
    const val FALLBACK = 2
    const val UNSUPPORTED = 3
}
