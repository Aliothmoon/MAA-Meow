package com.aliothmoon.maameow.data.preferences

import com.aliothmoon.maameow.constant.OFFICIAL_SHIZUKU_PACKAGE
import com.aliothmoon.maameow.data.model.InfrastConfig
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.notification.NotificationSettings
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.data.notification.reapplyWebhookPresetIfBlank
import com.aliothmoon.maameow.domain.enums.InfrastMode
import com.aliothmoon.maameow.domain.enums.UiUsageConstants
import com.aliothmoon.maameow.domain.models.AppSettings
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ConfigBackupManager(
    private val appSettingsManager: AppSettingsManager,
    private val notificationSettingsManager: NotificationSettingsManager,
    private val taskChainState: TaskChainState,
    private val scheduleStrategyRepository: ScheduleStrategyRepository,
    private val scheduleAlarmManager: ScheduleAlarmManager,
) {
    private val json = Json(JsonUtils.common) {
        prettyPrint = true
    }

    suspend fun exportTo(outputStream: OutputStream) = withContext(Dispatchers.IO) {
        // 等待异步数据加载完成，避免导出空数据
        taskChainState.isLoaded.first { it }
        scheduleStrategyRepository.isLoaded.first { it }

        val backup = ConfigBackup(
            version = CURRENT_VERSION,
            exportedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            appSettings = appSettingsManager.settings.first().sanitized(),
            notificationSettings = notificationSettingsManager.settings.first()
                .sanitizedForExport(),
            taskProfiles = taskChainState.profiles.value.map { it.sanitized() },
            activeProfileId = taskChainState.profileId.value,
            scheduleStrategies = scheduleStrategyRepository.strategies.value,
        )
        outputStream.bufferedWriter().use { writer ->
            writer.write(json.encodeToString(ConfigBackup.serializer(), backup))
        }
    }

    /**
     * 导入配置。
     * 注意：AppSettings 采用整包写入，部分设置（如 startupBackend、debugMode）的运行态副作用
     * 不会立刻触发，建议导入后重启应用以确保所有设置完全生效。
     */
    suspend fun importFrom(inputStream: InputStream) = withContext(Dispatchers.IO) {
        val content = inputStream.use { it.readTextAtMost(MAX_IMPORT_BYTES) }
        val backup = json.decodeFromString(ConfigBackup.serializer(), content)
        require(backup.version <= CURRENT_VERSION) {
            "不支持的备份版本: ${backup.version}，当前最高支持: $CURRENT_VERSION"
        }
        // 自定义背景的开关与令牌指向本机文件，导入其他设备的配置时保留本机值。
        // 遥测开关是本机用户自己的选择，别人的备份（或没这个字段的旧备份）不能替他打开
        // 游戏包名跟着本机装的版本走，也不随备份变
        val localSettings = appSettingsManager.settings.first()
        appSettingsManager.setSettings(
            backup.appSettings.normalizedForImport().copy(
                customBackgroundEnabled = localSettings.customBackgroundEnabled,
                customBackgroundToken = localSettings.customBackgroundToken,
                telemetryEnabled = localSettings.telemetryEnabled,
                gamePackages = localSettings.gamePackages,
            )
        )
        notificationSettingsManager.updateSettings(backup.notificationSettings.reapplyWebhookPresetIfBlank())
        taskChainState.importProfiles(backup.taskProfiles, backup.activeProfileId)

        // 先取消旧闹钟，再导入并重新注册
        val oldStrategies = scheduleStrategyRepository.strategies.value
        oldStrategies.forEach { scheduleAlarmManager.cancel(it.id) }
        scheduleStrategyRepository.importStrategies(backup.scheduleStrategies)
        scheduleAlarmManager.rescheduleAll(backup.scheduleStrategies)
    }

    companion object {
        const val CURRENT_VERSION = 1

        /** 备份正常只有几十 KB，超限多半是选错了文件，整份读进内存会 OOM */
        const val MAX_IMPORT_BYTES = 8 * 1024 * 1024

        /**
         * 导出时剥离设备本地字段：CDK、解锁 PIN 与一图流 Token 属敏感信息；
         * 自定义背景与实况图标对应本机 filesDir 下的图片文件，在其他设备上不存在；
         * 自定义游戏包名取决于本机装的游戏版本
         */
        private fun AppSettings.sanitized() = copy(
            mirrorChyanCdk = "",
            wakeCredential = "",
            yituliuOpenApiToken = "",
            operBoxUseYituliuApi = "false",
            operBoxAssistEnabled = "false",
            customBackgroundEnabled = "false",
            customBackgroundToken = "",
            gamePackages = "",
        ).withoutCustomTrackerIcon()

        /**
         * 导入时对已废弃或非法的旧值做归一化，避免后续读取时违反非空约束。
         */
        private fun AppSettings.normalizedForImport() = copy(
            shizukuLaunchPackage = shizukuLaunchPackage.ifBlank { OFFICIAL_SHIZUKU_PACKAGE },
        ).withoutCustomTrackerIcon()

        /** 自定义图标文件只在本机，路径清空，CUSTOM 回落默认 */
        private fun AppSettings.withoutCustomTrackerIcon() = copy(
            liveUpdateCustomTrackerPath = "",
            liveUpdateTrackerIcon = liveUpdateTrackerIcon.takeUnless {
                it.equals(AppSettingsManager.LiveUpdateTrackerIcon.CUSTOM.name, ignoreCase = true)
            } ?: AppSettingsManager.LiveUpdateTrackerIcon.DEFAULT.name,
        )

        /**
         * 导出时将使用自定义文件的基建配置回退为常规模式，
         * 因为自定义文件路径在其他设备上不存在。
         */
        private fun TaskProfile.sanitized() = copy(
            chain = chain.map { node ->
                val cfg = node.config
                if (cfg is InfrastConfig
                    && cfg.mode == InfrastMode.Custom
                    && cfg.defaultInfrast == UiUsageConstants.USER_DEFINED_INFRAST
                ) {
                    node.copy(config = InfrastConfig())
                } else {
                    node
                }
            }
        )
    }
}

class BackupTooLargeException(val limitBytes: Int) : IOException("backup exceeds $limitBytes bytes")

internal fun InputStream.readTextAtMost(limitBytes: Int): String {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        if (out.size() + n > limitBytes) throw BackupTooLargeException(limitBytes)
        out.write(buffer, 0, n)
    }
    return out.toString(Charsets.UTF_8.name())
}

// 导出脱敏：凭证清空，识别类字段（userId/chatId 等）保留
internal fun NotificationSettings.sanitizedForExport() = copy(
    serverChanSendKey = "",
    discordBotToken = "",
    discordWebhookUrl = "",
    smtpPassword = "",
    barkSendKey = "",
    telegramBotToken = "",
    dingTalkAccessToken = "",
    dingTalkSecret = "",
    kookBotToken = "",
    qmsgKey = "",
    qmsgV3Key = "",
    gotifyToken = "",
    customWebhookUrl = "",
    customWebhookHeaders = "",
)
