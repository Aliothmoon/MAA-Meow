package com.aliothmoon.maameow.data.achievement

import androidx.compose.runtime.Immutable
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

@Immutable
data class AchievementDefinition(
    val id: String,
    val category: AchievementCategory,
    val group: String = "",
    val target: Int = 0,
    val hidden: Boolean = false,
    val rare: Boolean = false,
    val groupIndex: Int = Int.MAX_VALUE,
    val trigger: AchievementTrigger? = null,
    val triggers: List<AchievementTrigger> = emptyList(),
)

@Immutable
data class AchievementTrigger(
    val event: String,
    val mode: AchievementTriggerMode = AchievementTriggerMode.UNLOCK,
    val amount: Int = 1,
    val where: Map<String, String> = emptyMap(),
    val conditions: List<AchievementCondition> = emptyList(),
    val dateKey: String = "",
    val valueKey: String = "",
)

enum class AchievementTriggerMode {
    UNLOCK,
    INCREMENT,
    SET_MAX,
    SAME_DAY_COUNT,
    DAILY_STREAK,
    RESET,
}

@Immutable
data class AchievementCondition(
    val field: String,
    val op: AchievementConditionOp = AchievementConditionOp.EQ,
    val value: String,
)

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
    val extra: Map<String, String> = emptyMap(),
)

@Immutable
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
    // —— 分组(group 字段取值)——
    const val SANITY_SPENDER_GROUP = "SanitySpender"
    const val SANITY_SAVER_GROUP = "SanitySaver"
    const val ROGUELIKE_GAME_PASS_GROUP = "RoguelikeGamePass"
    const val ROGUELIKE_N_GROUP = "RoguelikeN"
    const val ROGUELIKE_GROUP = "Roguelike"
    const val CLUE_USE_GROUP = "ClueUse"
    const val CLUE_SEND_GROUP = "ClueSend"
    const val SCHEDULE_MASTER_GROUP = "ScheduleMaster"
    const val MIRROR_CHYAN_GROUP = "MirrorChyan"
    const val PIONEER_GROUP = "Pioneer"
    const val UPDATE_SUCCESS_GROUP = "UpdateSuccess"
    const val HR_MANAGER_GROUP = "HrManager"
    const val USE_COPILOT_GROUP = "UseCopilot"
    const val COPILOT_LIKE_GIVEN_GROUP = "CopilotLikeGiven"
    const val RECRUIT_GROUP = "Recruit"
    const val USE_DAILY_GROUP = "UseDaily"
    const val UPDATE_GROUP = "Update"
    const val SCHEDULE_LOCKED_SKIP_GROUP = "ScheduleLockedSkip"

    // —— BASIC_USAGE ——
    const val SANITY_SPENDER_1 = "SanitySpender1"
    const val SANITY_SPENDER_2 = "SanitySpender2"
    const val SANITY_SPENDER_3 = "SanitySpender3"
    const val SANITY_SAVER_1 = "SanitySaver1"
    const val SANITY_SAVER_2 = "SanitySaver2"
    const val SANITY_SAVER_3 = "SanitySaver3"
    const val ROGUELIKE_GAME_PASS_1 = "RoguelikeGamePass1"
    const val ROGUELIKE_GAME_PASS_2 = "RoguelikeGamePass2"
    const val ROGUELIKE_GAME_PASS_3 = "RoguelikeGamePass3"
    const val ROGUELIKE_N04 = "RoguelikeN04"
    const val ROGUELIKE_N08 = "RoguelikeN08"
    const val ROGUELIKE_N12 = "RoguelikeN12"
    const val ROGUELIKE_N15 = "RoguelikeN15"
    const val ROGUELIKE_RETREAT = "RoguelikeRetreat"
    const val ROGUELIKE_GOLD_MAX = "RoguelikeGoldMax"
    const val FIRST_LAUNCH = "FirstLaunch"
    const val SANITY_EXPIRE = "SanityExpire"
    const val RECRUIT_GAMBLER = "RecruitGambler"
    const val CLUE_COLLECTOR = "ClueCollector"
    const val CLUE_PHILOSOPHER = "CluePhilosopher"
    const val CLUE_OBSESSION = "ClueObsession"
    const val CLUE_SHARER = "ClueSharer"
    const val CLUE_PHILANTHROPIST = "CluePhilanthropist"
    const val TIME_MANAGEMENT_MASTER = "TimeManagementMaster"
    const val DOUBLE_SYNC = "DoubleSync"
    const val RESUME_RECORD = "ResumeRecord"
    const val QUEUE_EXPANSION = "QueueExpansion"
    const val QUEUE_SIMPLIFIER = "QueueSimplifier"
    const val MAA_MEOW_FIRST_TASK_START = "MaaMeowFirstTaskStart"
    const val MAA_MEOW_FIRST_TASK_COMPLETE = "MaaMeowFirstTaskComplete"

    // —— FEATURE_EXPLORATION ——
    const val SCHEDULE_MASTER_1 = "ScheduleMaster1"
    const val SCHEDULE_MASTER_2 = "ScheduleMaster2"
    const val MIRROR_CHYAN_FIRST_USE = "MirrorChyanFirstUse"
    const val MIRROR_CHYAN_CDK_ERROR = "MirrorChyanCdkError"
    const val PIONEER_1 = "Pioneer1"
    const val PIONEER_2 = "Pioneer2"
    const val PIONEER_3 = "Pioneer3"
    const val UPDATE_SUCCESS_1 = "UpdateSuccess1"
    const val UPDATE_SUCCESS_2 = "UpdateSuccess2"
    const val MOSQUITO_LEG = "MosquitoLeg"
    const val LOG_SUPERVISOR = "LogSupervisor"
    const val TASK_CHAIN_KING = "TaskChainKing"
    const val WAREHOUSE_MISER = "WarehouseMiser"
    const val HR_SPECIALIST = "HrSpecialist"
    const val HR_SENIOR_SPECIALIST = "HrSeniorSpecialist"
    const val LINGUIST = "Linguist"
    const val ACHIEVEMENT_OBSERVER = "AchievementObserver"
    const val PRIVATE_DORM_MANAGER = "PrivateDormManager"

    // —— AUTO_BATTLE ——
    const val USE_COPILOT_1 = "UseCopilot1"
    const val USE_COPILOT_2 = "UseCopilot2"
    const val USE_COPILOT_3 = "UseCopilot3"
    const val COPILOT_LIKE_GIVEN_1 = "CopilotLikeGiven1"
    const val COPILOT_LIKE_GIVEN_2 = "CopilotLikeGiven2"
    const val COPILOT_LIKE_GIVEN_3 = "CopilotLikeGiven3"
    const val COPILOT_ERROR = "CopilotError"
    const val MAP_OUTDATED = "MapOutdated"
    const val IRREPLACEABLE = "Irreplaceable"

    // —— HUMOR ——
    const val QUICK_CLOSER = "QuickCloser"
    const val TACTICAL_RETREAT = "TacticalRetreat"
    const val MARTIAN = "Martian"
    const val RECRUIT_NO_SIX_STAR = "RecruitNoSixStar"
    const val RECRUIT_NO_SIX_STAR_STREAK = "RecruitNoSixStarStreak"

    // —— BUG_RELATED ——
    const val CONGRATULATION_ERROR = "CongratulationError"
    const val UNEXPECTED_CRASH = "UnexpectedCrash"
    const val PROBLEM_FEEDBACK = "ProblemFeedback"
    const val CDN_TORTURE = "CdnTorture"
    const val BACKSTAGE_EXPLORER = "BackstageExplorer"
    const val LOG_DIAGNOSTICIAN = "LogDiagnostician"
    const val ALL_PERMISSIONS_GRANTED = "AllPermissionsGranted"
    const val ALL_NOTIFICATION_PROVIDERS_ENABLED = "AllNotificationProvidersEnabled"
    const val FEEDBACK_GROUP_VISITOR = "FeedbackGroupVisitor"
    const val NOTIFICATION_DENIED = "NotificationDenied"
    const val DEBUG_MODE_ENABLED = "DebugModeEnabled"
    const val SHIZUKU_LOST = "ShizukuLost"

    // —— BEHAVIOR ——
    const val MISSION_START_COUNT = "MissionStartCount"
    const val FOREGROUND_TASK_VIEWER = "ForegroundTaskViewer"
    const val BACKGROUND_TASK_VIEWER = "BackgroundTaskViewer"
    const val TASK_START_WITHOUT_WAKE_UP = "TaskStartWithoutWakeUp"
    const val WAKE_UP_WHILE_GAME_RUNNING = "WakeUpWhileGameRunning"
    const val WAKE_UP_AFTER_STOP = "WakeUpAfterStop"
    const val GAME_NOT_RUNNING_WITHOUT_WAKE_UP = "GameNotRunningWithoutWakeUp"
    const val TASK_FAILURE_STREAK = "TaskFailureStreak"
    const val SCHEDULE_BUSY_RESULT = "ScheduleBusyResult"
    const val SCHEDULE_RESULT_COLLECTOR = "ScheduleResultCollector"
    const val SCHEDULE_LOCKED_MISS = "ScheduleLockedMiss"
    const val SCHEDULE_LOCKED_SKIP_1 = "ScheduleLockedSkip1"
    const val SCHEDULE_LOCKED_SKIP_2 = "ScheduleLockedSkip2"
    const val SCHEDULE_LOCKED_SKIP_3 = "ScheduleLockedSkip3"
    const val SCHEDULE_START_AVOIDER = "ScheduleStartAvoider"
    const val LONG_TASK_TIMEOUT = "LongTaskTimeout"
    const val PROXY_ONLINE_3_HOURS = "ProxyOnline3Hours"
    const val TASK_START_CANCEL = "TaskStartCancel"
    const val USE_DAILY_1 = "UseDaily1"
    const val USE_DAILY_2 = "UseDaily2"
    const val USE_DAILY_3 = "UseDaily3"
    const val UPDATE_OBSESSION = "UpdateObsession"
    const val UPDATE_EARLY_BIRD = "UpdateEarlyBird"

    // —— EASTER_EGG ——
    const val APRIL_FOOLS = "AprilFools"
    const val MIDNIGHT_LAUNCH = "MidnightLaunch"
    const val LUNAR_NEW_YEAR = "LunarNewYear"
    const val LUCKY = "Lucky"
    const val SANITY_PLANNER = "SanityPlanner"
    const val WAREHOUSE_KEEPER = "WarehouseKeeper"
    const val PALLAS_STARTER = "PallasStarter"
    const val SLACKING_OFF = "SlackingOff"
    const val REMOTE_ROOT_CONNECTED = "RemoteRootConnected"
    const val REMOTE_SHIZUKU_CONNECTED = "RemoteShizukuConnected"
}

object AchievementEvents {
    const val APP_LAUNCH = "app_launch"
    const val ACHIEVEMENT_PAGE_OPENED = "achievement_page_opened"
    const val ERROR_LOG_OPENED = "error_log_opened"
    const val LANGUAGE_CHANGED = "language_changed"
    const val TASK_NODE_ADDED = "task_node_added"
    const val TASK_NODE_REMOVED = "task_node_removed"
    const val MISSION_STARTED = "mission_started"
    const val TASK_STOPPED = "task_stopped"
    const val TASK_CHAIN_ERROR = "task_chain_error"
    const val ALL_TASKS_COMPLETED = "all_tasks_completed"
    const val SUB_TASK_ERROR = "subtask_error"
    const val PROCESS_TASK_STARTED = "process_task_started"
    const val PROCESS_TASK_COMPLETED = "process_task_completed"
    const val SUB_TASK_EXTRA_INFO = "subtask_extra_info"
    const val RECRUIT_RESULT = "recruit_result"
    const val MEDICINE_USED = "medicine_used"
    const val COPILOT_SUCCESS = "copilot_success"
    const val COPILOT_LIKED = "copilot_liked"
    const val SCHEDULE_SAVED = "schedule_saved"
    const val UPDATE_COMPLETED = "update_completed"
    const val UPDATE_FAILED = "update_failed"
    const val TOOLBOX_RESULT = "toolbox_result"
    const val MINI_GAME_STARTED = "mini_game_started"
    const val LOG_EXPORTED = "log_exported"
    const val SCHEDULE_RESULT = "schedule_result"
    const val REMOTE_CONNECTED = "remote_connected"
    const val PERMISSION_STATE = "permission_state"
    const val NOTIFICATION_PROVIDER_STATE = "notification_provider_state"
    const val FEEDBACK_GROUP_OPENED = "feedback_group_opened"
    const val TASK_START_BLOCKED = "task_start_blocked"
    const val TASK_STOPPED_BEFORE_NEXT_START = "task_stopped_before_next_start"
    const val REMOTE_CONNECT_FAILED = "remote_connect_failed"
    const val NOTIFICATION_PERMISSION_RESULT = "notification_permission_result"
    const val DEBUG_MODE_CHANGED = "debug_mode_changed"
}
