package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.data.notification.provider.NotificationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class ExternalNotificationService(
    private val settingsManager: NotificationSettingsManager,
    private val runtimeLogCenter: RuntimeLogCenter,
    providerList: List<NotificationProvider>,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val providers = providerList.associateBy(NotificationProvider::id)
    private val _feedbackMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val feedbackMessages: SharedFlow<String> = _feedbackMessages.asSharedFlow()

    fun send(title: String, content: String) {
        scope.launch {
            dispatchToProviders(title, content, isTest = false)
        }
    }

    fun sendWithLogs(title: String, content: String) {
        scope.launch {
            val body = if (settingsManager.includeLogDetails.value) {
                val logs = runtimeLogCenter.logs.value
                    .joinToString("\n") { "[${it.formattedTime}] ${it.content}" }
                if (logs.isNotEmpty()) "$logs\n$content" else content
            } else {
                content
            }
            dispatchToProviders(title, body, isTest = false)
        }
    }

    fun sendTest(title: String = "测试通知", content: String = "这是一条来自 MaaMeow 的测试通知") {
        scope.launch {
            dispatchToProviders(title, content, isTest = true)
        }
    }

    private suspend fun dispatchToProviders(title: String, content: String, isTest: Boolean) {
        val enabledIds = settingsManager.enabledProviderIds.value

        if (enabledIds.isEmpty()) {
            if (isTest) {
                _feedbackMessages.tryEmit("请先启用至少一个通知渠道")
            }
            return
        }

        val prefixedTitle = "[MAA] $title"

        for (id in enabledIds) {
            val provider = providers[id]
            val result = if (provider == null) {
                Timber.w("未知通知渠道: $id")
                false
            } else {
                runCatching { provider.send(prefixedTitle, content) }
                    .onFailure { Timber.e(it, "通知渠道 $id 发送失败") }
                    .getOrDefault(false)
            }

            if (isTest || !result) {
                _feedbackMessages.tryEmit("$id ${if (result) "发送成功" else "发送失败"}")
            }
        }
    }
}
