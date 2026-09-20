package com.aliothmoon.maameow.data.achievement

import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PallasPrompt { DRUNK, HANGOVER }

class PallasDrunkState(
    private val settings: AppSettingsManager,
    private val egg: PallasDebugEasterEgg = PallasDebugEasterEgg(
        clicksRequired = if (BuildConfig.DEBUG) 1 else 10,
        triggerChance = if (BuildConfig.DEBUG) 1.0 else 0.1,
    ),
) {

    private val _debugActive = MutableStateFlow(false)

    val debugActive: StateFlow<Boolean> = _debugActive.asStateFlow()

    private val _isDrunk = MutableStateFlow(false)

    val isDrunk: StateFlow<Boolean> = _isDrunk.asStateFlow()

    private val _tip = MutableStateFlow("")

    val tip: StateFlow<String> = _tip.asStateFlow()

    private val _prompt = MutableStateFlow<PallasPrompt?>(null)

    val prompt: StateFlow<PallasPrompt?> = _prompt.asStateFlow()

    suspend fun onMedalClick() {
        val result = egg.onClick()
        if (result == PallasClickResult.Ignored) return
        _debugActive.value = egg.isTriggered

        when (result) {
            PallasClickResult.EnteredDebug -> {
                _tip.value = PallasSpeak.random(1, 10)
                _prompt.value = PallasPrompt.DRUNK
            }

            PallasClickResult.ExitedDebug -> {
                val wasDrunk = sober()
                _tip.value = ""
                _prompt.value = if (wasDrunk) PallasPrompt.HANGOVER else null
            }

            else -> _tip.value = PallasSpeak.random(1, 10)
        }
    }

    suspend fun confirmPrompt() {
        if (_prompt.value == PallasPrompt.DRUNK) {
            _isDrunk.value = true
            settings.setPallasHangover(true)
        }
        _prompt.value = null
    }

    private suspend fun sober(): Boolean {
        if (!_isDrunk.value) return false
        _isDrunk.value = false
        settings.setPallasHangover(false)
        return true
    }

    suspend fun restorePendingHangover() {
        settings.awaitLoaded()
        if (_isDrunk.value || !settings.pallasHangover.value) return
        settings.setPallasHangover(false)
        _prompt.value = PallasPrompt.HANGOVER
    }
}
