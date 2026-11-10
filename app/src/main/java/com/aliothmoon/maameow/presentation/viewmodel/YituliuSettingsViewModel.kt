package com.aliothmoon.maameow.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.YituliuApiService
import com.aliothmoon.maameow.data.api.message
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.repository.YituliuTokenRepository
import com.aliothmoon.maameow.domain.service.CopilotOperBoxAssist
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class YituliuSettingsViewModel(
    private val appSettingsManager: AppSettingsManager,
    taskChainState: TaskChainState,
    private val tokenRepository: YituliuTokenRepository,
    private val api: YituliuApiService,
    private val operBoxAssist: CopilotOperBoxAssist,
) : ViewModel() {

    data class ProfileToken(val profileId: String, val name: String, val token: String, val active: Boolean)

    /** [ok] 决定提示用什么颜色 */
    data class VerifyMessage(val text: UiText, val ok: Boolean)

    data class VerifyState(val verifying: Boolean = false, val message: VerifyMessage? = null)

    val reportToYituliu: StateFlow<Boolean> = appSettingsManager.reportToYituliu

    fun setReportToYituliu(enabled: Boolean) {
        viewModelScope.launch { appSettingsManager.setReportToYituliu(enabled) }
    }

    val operBoxUseYituliuApi: StateFlow<Boolean> = appSettingsManager.operBoxUseYituliuApi

    fun setOperBoxUseYituliuApi(enabled: Boolean) {
        viewModelScope.launch { appSettingsManager.setOperBoxUseYituliuApi(enabled) }
    }

    /** 与自动战斗页同一个开关 */
    val operBoxAssistState: StateFlow<CopilotOperBoxAssist.State> = operBoxAssist.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CopilotOperBoxAssist.State())

    fun setOperBoxAssistEnabled(enabled: Boolean) {
        viewModelScope.launch { operBoxAssist.setEnabled(enabled) }
    }

    val perProfile: StateFlow<Boolean> = tokenRepository.perProfile

    fun setPerProfile(enabled: Boolean) {
        // 当前配置可能被带上全局 Token，旧结果作废
        _verifyStates.value = emptyMap()
        viewModelScope.launch { tokenRepository.setPerProfile(enabled) }
    }

    val sharedToken: StateFlow<String> = tokenRepository.sharedToken

    fun setSharedToken(token: String) {
        _verifyStates.update { it - SHARED }
        viewModelScope.launch { tokenRepository.setShared(token) }
    }

    val profileTokens: StateFlow<List<ProfileToken>> = combine(
        taskChainState.profiles,
        taskChainState.profileId,
        tokenRepository.profileTokens,
    ) { profiles, activeId, tokens ->
        profiles.map { ProfileToken(it.id, it.name, tokens[it.id].orEmpty(), it.id == activeId) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setProfileToken(profileId: String, token: String) {
        _verifyStates.update { it - profileId }
        tokenRepository.setForProfile(profileId, token)
    }

    /** 键为 [SHARED] 或配置档 ID */
    private val _verifyStates = MutableStateFlow<Map<String, VerifyState>>(emptyMap())
    val verifyStates: StateFlow<Map<String, VerifyState>> = _verifyStates.asStateFlow()

    /** 只报结果，不自己打开干员识别开关 */
    fun verify(key: String) {
        val token = currentToken(key)
        if (token.isEmpty()) {
            setVerifyState(key, VerifyState(message = VerifyMessage(uiTextOf(R.string.settings_yituliu_token_empty), ok = false)))
            return
        }
        if (_verifyStates.value[key]?.verifying == true) return
        val pending = VerifyState(verifying = true)
        setVerifyState(key, pending)
        viewModelScope.launch {
            val result = api.fetchOperators(token)
            // Token 期间变了就丢弃并复位；全局 Token 异步落盘，可能拿旧值发了请求
            if (token != currentToken(key)) {
                _verifyStates.update { if (it[key] === pending) it - key else it }
                return@launch
            }
            val message = when (result) {
                is YituliuApiService.Result.Success -> VerifyMessage(
                    text = if (result.operators.isEmpty()) {
                        uiTextOf(R.string.settings_yituliu_token_valid_no_data)
                    } else {
                        uiTextOf(R.string.settings_yituliu_token_valid, result.operators.size)
                    },
                    ok = true,
                )

                is YituliuApiService.Result.Failure -> VerifyMessage(result.message(), ok = false)
            }
            setVerifyState(key, VerifyState(message = message))
        }
    }

    private fun currentToken(key: String): String = if (key == SHARED) {
        tokenRepository.sharedToken.value.trim()
    } else {
        tokenRepository.profileTokens.value[key].orEmpty()
    }

    private fun setVerifyState(key: String, state: VerifyState) {
        _verifyStates.update { it + (key to state) }
    }

    companion object {
        const val SHARED = "shared"
    }
}
