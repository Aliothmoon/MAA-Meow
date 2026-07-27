package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.maa.task.MaaTaskParams
import kotlinx.serialization.Serializable

@Serializable
sealed interface TaskParamProvider {
    /**
     * 展开为 MaaCore 任务参数。
     *
     * 返回 List 而非单个：绝大多数任务一对一，但容器型任务（如库存保持）
     * 会展开成多个 MaaCore 任务。
     */
    fun toTaskParams(ctx: TaskParamContext): List<MaaTaskParams>
}
