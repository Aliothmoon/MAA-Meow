package com.aliothmoon.maameow.data.notification.provider

interface NotificationProvider {
    val id: String
    suspend fun send(title: String, content: String): NotificationSendResult
}
