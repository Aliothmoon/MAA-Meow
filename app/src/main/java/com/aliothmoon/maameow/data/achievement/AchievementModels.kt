package com.aliothmoon.maameow.data.achievement

import kotlinx.serialization.Serializable

enum class AchievementCategory {
    BASIC_USAGE,
    FEATURE_EXPLORATION,
    AUTO_BATTLE,
    HUMOR,
    BUG_RELATED,
    BEHAVIOR,
    EASTER_EGG,
}

@Serializable
data class LocalizedText(
    val zh: String = "",
    val en: String = "",
) {
    fun resolve(languageTag: String): String = if (languageTag.startsWith("zh")) {
        zh.ifBlank { en }
    } else {
        en.ifBlank { zh }
    }
}

@Serializable
data class AchievementDefinition(
    val id: String,
    val title: LocalizedText,
    val description: LocalizedText,
    val condition: LocalizedText,
    val category: AchievementCategory,
    val group: String = "",
    val target: Int = 0,
    val hidden: Boolean = false,
    val rare: Boolean = false,
    val groupIndex: Int = Int.MAX_VALUE,
    val releasePhase: Int = 1,
    val trigger: AchievementTrigger? = null,
    val triggers: List<AchievementTrigger> = emptyList(),
)

@Serializable
data class AchievementTrigger(
    val event: String,
    val mode: AchievementTriggerMode = AchievementTriggerMode.UNLOCK,
    val amount: Int = 1,
    val where: Map<String, String> = emptyMap(),
    val conditions: List<AchievementCondition> = emptyList(),
    val dateKey: String = "",
)

@Serializable
enum class AchievementTriggerMode {
    UNLOCK,
    INCREMENT,
    SET_MAX,
    SAME_DAY_COUNT,
    DAILY_STREAK,
    RESET,
}

@Serializable
data class AchievementCondition(
    val field: String,
    val op: AchievementConditionOp = AchievementConditionOp.EQ,
    val value: String,
)

@Serializable
enum class AchievementConditionOp {
    EQ,
    NE,
    GT,
    GTE,
    LT,
    LTE,
    BETWEEN,
    CONTAINS,
    MONTH_DAY,
    MONTH_DAY_BETWEEN,
}

@Serializable
data class AchievementRecord(
    val id: String,
    val unlocked: Boolean = false,
    val unlockedAtMillis: Long? = null,
    val progress: Int = 0,
    val customData: Map<String, String> = emptyMap(),
)

data class AchievementState(
    val definition: AchievementDefinition,
    val unlocked: Boolean,
    val unlockedAtMillis: Long?,
    val progress: Int,
    val isNewUnlock: Boolean = false,
) {
    val progressive: Boolean = definition.target > 0
    val visible: Boolean = unlocked || !definition.hidden
}

object AchievementIds {
    const val SanitySpenderGroup = "SanitySpender"
    const val SanitySaverGroup = "SanitySaver"
    const val RoguelikeGamePassGroup = "RoguelikeGamePass"
    const val ClueUseGroup = "ClueUse"
    const val ClueSendGroup = "ClueSend"
    const val HrManager = "HrManager"
    const val RecruitGroup = "Recruit"
    const val FirstLaunch = "FirstLaunch"
    const val MissionStartCount = "MissionStartCount"
    const val UseDailyGroup = "UseDaily"
    const val UseDaily1 = "UseDaily1"
    const val UseDaily2 = "UseDaily2"
    const val UseCopilotGroup = "UseCopilot"
    const val RoguelikeRetreat = "RoguelikeRetreat"
    const val SanityExpire = "SanityExpire"
    const val RecruitGambler = "RecruitGambler"
    const val RecruitNoSixStar = "RecruitNoSixStar"
    const val RecruitNoSixStarStreak = "RecruitNoSixStarStreak"
    const val ClueObsession = "ClueObsession"
    const val MosquitoLeg = "MosquitoLeg"
    const val CongratulationError = "CongratulationError"
    const val UnexpectedCrash = "UnexpectedCrash"
    const val CopilotError = "CopilotError"
    const val MapOutdated = "MapOutdated"
    const val Irreplaceable = "Irreplaceable"
    const val CopilotLikeGroup = "CopilotLikeGiven"
    const val TacticalRetreat = "TacticalRetreat"
    const val LongTaskTimeout = "LongTaskTimeout"
    const val ProxyOnline3Hours = "ProxyOnline3Hours"
    const val TaskStartCancel = "TaskStartCancel"
    const val AchievementObserver = "AchievementObserver"
    const val QueueExpansion = "QueueExpansion"
    const val QueueSimplifier = "QueueSimplifier"
    const val Linguist = "Linguist"
    const val MidnightLaunch = "MidnightLaunch"
    const val AprilFools = "AprilFools"
    const val Lucky = "Lucky"
}

object AchievementEvents {
    const val AppLaunch = "app_launch"
    const val AchievementPageOpened = "achievement_page_opened"
    const val ErrorLogOpened = "error_log_opened"
    const val LanguageChanged = "language_changed"
    const val TaskNodeAdded = "task_node_added"
    const val TaskNodeRemoved = "task_node_removed"
    const val MissionStarted = "mission_started"
    const val TaskStopped = "task_stopped"
    const val TaskChainError = "task_chain_error"
    const val AllTasksCompleted = "all_tasks_completed"
    const val SubTaskError = "subtask_error"
    const val ProcessTaskStarted = "process_task_started"
    const val ProcessTaskCompleted = "process_task_completed"
    const val SubTaskExtraInfo = "subtask_extra_info"
    const val RecruitResult = "recruit_result"
    const val MedicineUsed = "medicine_used"
    const val CopilotSuccess = "copilot_success"
    const val CopilotLiked = "copilot_liked"
    const val ScheduleSaved = "schedule_saved"
    const val UpdateCompleted = "update_completed"
    const val UpdateFailed = "update_failed"
    const val ToolboxResult = "toolbox_result"
    const val MiniGameStarted = "mini_game_started"
    const val LogExported = "log_exported"
}
