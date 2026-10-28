package com.aliothmoon.maameow.schedule.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExecutionResult {
    STARTED,
    FAILED_VALIDATION,
    FAILED_START,
    FAILED_UI_LAUNCH,
    SKIPPED_BUSY,
    SKIPPED_LOCKED,
    SKIPPED_BLACKLIST,
    CANCELLED;

    val isSkipped: Boolean
        get() = this == SKIPPED_BUSY || this == SKIPPED_LOCKED || this == SKIPPED_BLACKLIST
}
