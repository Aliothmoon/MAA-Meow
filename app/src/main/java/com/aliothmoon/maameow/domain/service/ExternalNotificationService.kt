package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.data.notification.provider.NotificationProvider
import com.aliothmoon.maameow.data.notification.provider.NotificationSendResult
import com.aliothmoon.maameow.domain.models.NotificationImage
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextJoin
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class ExternalNotificationService(
    private val settingsManager: NotificationSettingsManager,
    private val sessionLogger: MaaSessionLogger,
    providerList: List<NotificationProvider>,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 发送单独一个 scope，退出前只等它
    private val sendJob = SupervisorJob()
    private val sendScope = CoroutineScope(sendJob + Dispatchers.IO)
    private val providers = providerList.associateBy(NotificationProvider::id)
    private val _feedbackMessages = MutableSharedFlow<UiText>(extraBufferCapacity = 16)
    val feedbackMessages: SharedFlow<UiText> = _feedbackMessages.asSharedFlow()

    /** 等在途的发送，等的过程中排上队的也等；超时不再等 */
    suspend fun awaitPendingSends(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                val pending = sendJob.children.toList()
                if (pending.isEmpty()) break
                pending.joinAll()
            }
        } ?: Timber.w("pending notification sends still running after %dms", timeoutMs)
    }

    fun send(title: String, content: String) {
        sendScope.launch {
            dispatchToProviders(title, content, isTest = false)
        }
    }

    /** 已启用的渠道里有能发图的，截图才有去处 */
    val imageChannelEnabled: StateFlow<Boolean> = settingsManager.enabledProviderIds
        .map(::hasImageChannel)
        .stateIn(scope, SharingStarted.Eagerly, hasImageChannel(settingsManager.enabledProviderIds.value))

    private fun hasImageChannel(ids: List<String>): Boolean =
        ids.any { providers[it]?.supportsImage == true }

    fun sendWithLogs(title: String, content: String, image: NotificationImage? = null) {
        sendScope.launch {
            val body = if (settingsManager.includeLogDetails.value) {
                val logs = sessionLogger.logs.value
                    .joinToString("\n") { "[${it.formattedTime}] ${it.content}" }
                if (logs.isNotEmpty()) "$logs\n$content" else content
            } else {
                content
            }
            dispatchToProviders(title, body, isTest = false, image = image)
        }
    }

    fun sendTest(title: String, content: String) {
        sendScope.launch {
            dispatchToProviders(title, content, isTest = true)
        }
    }

    private suspend fun dispatchToProviders(
        title: String,
        content: String,
        isTest: Boolean,
        image: NotificationImage? = null,
    ) {
        val enabledIds = settingsManager.enabledProviderIds.value

        if (enabledIds.isEmpty()) {
            if (isTest) {
                _feedbackMessages.tryEmit(uiTextOf(R.string.notification_feedback_no_channel))
            }
            return
        }

        val prefixedTitle = "[MAA] $title"

        for (id in enabledIds) {
            val provider = providers[id]
            val result = if (provider == null) {
                Timber.w("未知通知渠道: $id")
                null
            } else {
                runCatching {
                    if (image != null) {
                        provider.send(prefixedTitle, content, image)
                    } else {
                        provider.send(prefixedTitle, content)
                    }
                }
                    .getOrElse {
                        Timber.e(it, "通知渠道 $id 发送异常")
                        NotificationSendResult.Transient(uiTextOf(R.string.notification_err_network))
                    }
            } ?: continue

            when (result) {
                is NotificationSendResult.Success -> {
                    if (isTest) {
                        _feedbackMessages.tryEmit(
                            uiTextOf(
                                R.string.notification_feedback_send_success,
                                id
                            )
                        )
                    }
                }

                is NotificationSendResult.Failed -> {
                    Timber.w("通知渠道 %s 发送失败: Failed", id)
                    emitFailure(id, result.message)
                }

                is NotificationSendResult.Transient -> {
                    Timber.w("通知渠道 %s 发送失败: Transient", id)
                    if (isTest) emitFailure(id, result.message)
                }
            }
        }
    }

    private fun emitFailure(id: String, message: UiText) {
        _feedbackMessages.tryEmit(
            uiTextJoin(
                uiTextOf(R.string.notification_feedback_send_failed, id),
                message,
                separator = UiText.Dynamic("："),
            )
        )
    }
}
