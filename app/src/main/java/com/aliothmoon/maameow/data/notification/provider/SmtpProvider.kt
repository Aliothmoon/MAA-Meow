package com.aliothmoon.maameow.data.notification.provider

import androidx.core.text.htmlEncode
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Date
import java.util.Properties

class SmtpProvider(
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "SMTP"

    override suspend fun send(title: String, content: String): NotificationSendResult {
        val settings = settingsManager.settings.first()
        val server = settings.smtpServer.takeIf { it.isNotBlank() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_smtp_server_empty)
            )
        val port = settings.smtpPort.toIntOrNull()
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_smtp_port_invalid)
            )
        val from = settings.smtpFrom.takeIf { it.isNotBlank() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_smtp_from_empty)
            )
        val to = settings.smtpTo.takeIf { it.isNotBlank() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_smtp_to_empty)
            )
        val useSsl = settings.smtpUseSsl.toBooleanStrictOrNull() ?: false
        val requireAuthentication =
            settings.smtpRequireAuthentication.toBooleanStrictOrNull() ?: false
        val user = settings.smtpUser
        val password = settings.smtpPassword

        if (requireAuthentication && (user.isBlank() || password.isBlank())) {
            return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_smtp_auth_empty)
            )
        }

        val properties = Properties().apply {
            put("mail.transport.protocol", "smtp")
            put("mail.smtp.host", server)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", requireAuthentication.toString())
            put("mail.smtp.ssl.enable", useSsl.toString())
            put("mail.smtp.connectiontimeout", "30000")
            put("mail.smtp.timeout", "60000")
            put("mail.smtp.writetimeout", "60000")
        }

        val authenticator = if (requireAuthentication) {
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(user, password)
                }
            }
        } else {
            null
        }

        val sanitizedTitle = title.replace("\r", "").replace("\n", "")
        val htmlBody = buildHtmlBody(sanitizedTitle, content)

        return runCatching {
            val session = Session.getInstance(properties, authenticator)
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(from))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                subject = sanitizedTitle
                sentDate = Date()
                setContent(htmlBody, "text/html; charset=UTF-8")
            }
            Transport.send(message)
            NotificationSendResult.Success
        }.getOrElse {
            Timber.e(it, "SMTP send failed")
            NotificationSendResult.Transient(uiTextOf(R.string.notification_err_network))
        }
    }

    private fun buildHtmlBody(title: String, content: String): String {
        val safeTitle = title.htmlEncode()
        val safeContent = content.htmlEncode()
            .replace("\r", "")
            .replace("\n", "<br/>")

        return """
            <html lang="zh">
            <body style="font-family: sans-serif; color: #222222; line-height: 1.6;">
                <h1 style="text-align: center;">MaaMeow</h1>
                <hr />
                <h2>$safeTitle</h2>
                <p>$safeContent</p>
            </body>
            </html>
        """.trimIndent()
    }
}
