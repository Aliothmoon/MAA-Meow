package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.utils.i18n.UiText
import kotlinx.serialization.Serializable

@Serializable
sealed interface TaskParamProvider {
    /** 展开为 MaaCore 参数列表；诊断经 [TaskParamContext.appendLog]。 */
    fun toTaskParams(ctx: TaskParamContext): List<MaaTaskParams>

    /** 旧档字段归位，读配置时调用；默认无需迁移 */
    fun migrate(): TaskParamProvider = this
}

fun interface PreflightLogSink {
    fun append(text: UiText, level: LogLevel)
}

class CollectingPreflightLogSink : PreflightLogSink {
    private val _entries = mutableListOf<Pair<UiText, LogLevel>>()
    val entries: List<Pair<UiText, LogLevel>> get() = _entries.toList()

    override fun append(text: UiText, level: LogLevel) {
        _entries += text to level
    }
}
