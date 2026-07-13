package com.aliothmoon.maameow.data.preferences

import com.aliothmoon.maameow.constant.OFFICIAL_SHIZUKU_PACKAGE
import com.aliothmoon.maameow.data.model.InfrastConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.notification.NotificationSettings
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
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
            notificationSettings = notificationSettingsManager.settings.first().sanitized(),
            taskProfiles = taskChainState.profiles.value.map { it.sanitized() },
            activeProfileId = taskChainState.activeProfileId.value,
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
        val content = inputStream.bufferedReader().use { it.readText() }
        val backup = json.decodeFromString(ConfigBackup.serializer(), content)
        require(backup.version <= CURRENT_VERSION) {
            "不支持的备份版本: ${backup.version}，当前最高支持: $CURRENT_VERSION"
        }
        appSettingsManager.setSettings(backup.appSettings.normalizedForImport())
        notificationSettingsManager.updateSettings(backup.notificationSettings)
        taskChainState.importProfiles(backup.taskProfiles, backup.activeProfileId)

        // 先取消旧闹钟，再导入并重新注册
        val oldStrategies = scheduleStrategyRepository.strategies.value
        oldStrategies.forEach { scheduleAlarmManager.cancel(it.id) }
        scheduleStrategyRepository.importStrategies(backup.scheduleStrategies)
        scheduleAlarmManager.rescheduleAll(backup.scheduleStrategies)
    }

    companion object {
        const val CURRENT_VERSION = 1

        private fun AppSettings.sanitized() = copy(mirrorChyanCdk = "")

        /**
         * 导入时对已废弃或非法的旧值做归一化，避免后续读取时违反非空约束。
          */
        private fun AppSettings.normalizedForImport() = copy(
            shizukuLaunchPackage = shizukuLaunchPackage.ifBlank { OFFICIAL_SHIZUKU_PACKAGE }
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

        private fun NotificationSettings.sanitized() = copy(
            serverChanSendKey = "",
            discordBotToken = "",
            discordWebhookUrl = "",
            smtpPassword = "",
            barkSendKey = "",
            telegramBotToken = "",
            dingTalkAccessToken = "",
            dingTalkSecret = "",
            qmsgKey = "",
            gotifyToken = "",
            customWebhookUrl = "",
        )
    }
}
