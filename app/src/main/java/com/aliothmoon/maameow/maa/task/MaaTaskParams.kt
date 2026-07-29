package com.aliothmoon.maameow.maa.task

/** AsstAppendTask 票根；[slot] 由 Analyze 注入，链外路径为 null。 */
data class MaaTaskParams(
    val type: MaaTaskType,
    val params: String,
    val slot: TaskSlot? = null,
)
