package com.aliothmoon.maameow.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 自定义基建排班配置 JSON 模型
 *
 * 对应 WPF CustomInfrastConfig.cs
 * JSON 结构来自 MAA Core 基建排班协议:
 * https://maa.plus/docs/zh-cn/protocol/base-scheduling-schema.html
 *
 * 仅解析 UI 展示所需的字段（title/description/plans 的 name/period）,
 * rooms/operators 等执行细节由 MAA Core 直接解析
 */
@Serializable
data class CustomInfrastConfig(
    val title: String? = null,
    val description: String? = null,
    val plans: List<Plan> = emptyList()
) {
    /**
     * 基建计划
     *
     * @property name 计划名称，如 "A+B 16H"
     * @property description 计划描述
     * @property descriptionPost 计划执行完成后的描述
     * @property period 生效时间段列表，每项为 ["HH:mm", "HH:mm"]（起止时间）
     *                  为空表示该计划不参与时间轮换
     */
    @Serializable
    data class Plan(
        val name: String? = null,
        val description: String? = null,
        @SerialName("description_post")
        val descriptionPost: String? = null,
        val period: List<List<String>> = emptyList()
    )
}
