package com.aliothmoon.maameow.data.model.copilot

import kotlinx.serialization.Serializable

/**
 * 自动战斗作业列表单项
 *
 * 用于多作业批量执行模式
 */
@Serializable
data class CopilotListItem(
    val name: String,               // 关卡 code (如 "1-7")
    val filePath: String,           // JSON 文件路径
    val isRaid: Boolean = false,    // 是否突袭
    val copilotId: Int = 0,         // PRTS Plus 作业 ID
    val isChecked: Boolean = true,  // 是否勾选执行
)
