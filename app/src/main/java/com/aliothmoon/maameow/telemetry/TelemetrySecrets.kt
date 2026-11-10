package com.aliothmoon.maameow.telemetry

import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.data.notification.NotificationSettings
import com.aliothmoon.maameow.domain.models.AppSettings

/** 解锁 PIN 不在内：它不进任何日志，而纯数字按值替换会把时间戳一并抹掉 */
internal object TelemetrySecrets {

    fun collect(
        app: AppSettings,
        notification: NotificationSettings,
        chains: List<List<TaskChainNode>>,
        yituliuProfileTokens: Collection<String>,
    ): List<String> = buildList {
        add(app.penguinId)
        add(app.yituliuOpenApiToken)
        addAll(yituliuProfileTokens)
        add(app.mirrorChyanCdk)

        add(notification.serverChanSendKey)
        add(notification.discordBotToken)
        add(notification.discordWebhookUrl)
        add(notification.smtpUser)
        add(notification.smtpPassword)
        add(notification.barkSendKey)
        add(notification.telegramBotToken)
        add(notification.dingTalkAccessToken)
        add(notification.dingTalkSecret)
        add(notification.kookBotToken)
        add(notification.qmsgKey)
        add(notification.qmsgV3Key)
        add(notification.gotifyToken)
        add(notification.customWebhookUrl)
        add(notification.customWebhookHeaders)

        chains.forEach { chain ->
            chain.forEach { node -> (node.config as? WakeUpConfig)?.let { add(it.accountName) } }
        }
    }.filter(String::isNotBlank)
}
