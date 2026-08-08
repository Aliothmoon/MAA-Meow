package com.aliothmoon.maameow.data.preferences

import com.aliothmoon.maameow.data.model.ProfileSequenceEntry
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.model.TaskSequenceConfig
import com.aliothmoon.maameow.data.notification.NotificationSettings
import com.aliothmoon.maameow.domain.models.AppSettings
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import kotlinx.serialization.Serializable

@Serializable
data class ConfigBackup(
    val version: Int = 1,
    val exportedAt: String = "",
    val appSettings: AppSettings,
    val notificationSettings: NotificationSettings,
    val taskProfiles: List<TaskProfile>,
    val activeProfileId: String,
    val scheduleStrategies: List<ScheduleStrategy>,
    /** 旧字段：单条任务链；缺省 empty。若 [sequenceConfigs] 为空则导入时迁成一套默认任务链配置 */
    val profileSequence: List<ProfileSequenceEntry> = emptyList(),
    /** 是否启用任务链；旧备份缺省 true */
    val profileSequenceEnabled: Boolean = true,
    /** 多套任务链配置；旧备份缺省 empty */
    val sequenceConfigs: List<TaskSequenceConfig> = emptyList(),
    val activeSequenceConfigId: String = "",
)
