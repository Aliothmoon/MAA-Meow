package com.aliothmoon.maameow.data.api

import com.aliothmoon.maameow.data.model.update.UpdateError
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mirror酱 CDK 校验状态，带 cdk 的 latest 请求都会回写
 * 到期时间落盘，校验结果只在本进程有效；规则对齐 WPF UpdateCredentialModel
 */
class MirrorChyanCdkStatus(
    private val settings: AppSettingsManager,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    sealed interface State {
        data object Unknown : State
        data object Valid : State
        data object Invalid : State
        data object Expired : State

        /** reason 为空表示网络或服务异常 */
        data class Failed(val reason: UpdateError? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Unknown)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()
    private var inFlight = 0

    fun reset() {
        _state.value = State.Unknown
    }

    /** 不是当前 CDK 的请求不跟踪 */
    @Synchronized
    fun begin(cdk: String): Boolean {
        if (!isCurrent(cdk)) return false
        inFlight++
        _checking.value = true
        return true
    }

    @Synchronized
    fun end() {
        inFlight = (inFlight - 1).coerceAtLeast(0)
        _checking.value = inFlight > 0
    }

    suspend fun onResponse(cdk: String, code: Int, expiredTime: Long?) {
        if (!isCurrent(cdk)) return
        val now = nowSeconds()
        val known = settings.mirrorChyanCdkExpiresAt.value
        val reported = expiredTime?.takeIf { it > 1 }
        when (code) {
            0 -> {
                // 没返回到期时间就沿用上次的
                val expiresAt = reported ?: known.takeIf { it > 1 } ?: 0L
                save(expiresAt)
                _state.value = if (expiresAt in 1..now) State.Expired else State.Valid
            }

            7001 -> {
                save(reported?.takeIf { it <= now } ?: known.takeIf { it in 2..now } ?: 1L)
                _state.value = State.Expired
            }

            7002 -> {
                save(0L)
                _state.value = State.Invalid
            }

            7003, 7004, 7005 -> _state.value = State.Failed(UpdateError.fromCode(code, null))
            else -> onNetworkFailure(cdk)
        }
    }

    /** 已验证有效的不因一次网络失败降级 */
    fun onNetworkFailure(cdk: String) {
        if (isCurrent(cdk) && _state.value != State.Valid) {
            _state.value = State.Failed()
        }
    }

    private fun isCurrent(cdk: String) = cdk == settings.mirrorChyanCdk.value

    private suspend fun save(expiresAt: Long) {
        if (expiresAt != settings.mirrorChyanCdkExpiresAt.value) {
            settings.setMirrorChyanCdkExpiresAt(expiresAt)
        }
    }
}
