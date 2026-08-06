package com.aliothmoon.maameow.schedule.model

/**
 * 定时/外部启动相关 Intent 常量与倒计时默认值
 * Intent 映射见 [com.aliothmoon.maameow.schedule.LaunchIntentMapper]
 */
object ScheduledExecutionRequest {
    const val ACTION_SHOW_SCHEDULE_EXECUTION =
        "com.aliothmoon.maameow.action.SHOW_SCHEDULE_EXECUTION"
    const val ACTION_LAUNCH_PROFILE =
        "com.aliothmoon.maameow.action.LAUNCH_PROFILE"
    const val EXTRA_REQUEST_ID = "extra_request_id"
    const val EXTRA_STRATEGY_ID = "extra_strategy_id"
    const val EXTRA_STRATEGY_NAME = "extra_strategy_name"
    const val EXTRA_PROFILE_ID = "extra_profile_id"
    const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
    const val EXTRA_FORCE_START = "extra_force_start"
    const val EXTRA_WAKE_UNLOCK_ENABLED = "extra_wake_unlock_enabled"
    const val EXTRA_AUTO_SLEEP_AFTER_TASK = "extra_auto_sleep_after_task"
    const val COUNTDOWN_SECONDS = 30
}
