package com.aliothmoon.maameow.data.model.copilot

import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

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
    val source: String = "web",     // web / local / resource
    /** 作业标题，旧数据没有 */
    val title: String = "",
    /** 列表 key；普通/突袭两项 filePath 与 name 相同，不能拿它们当 key */
    val id: String = UUID.randomUUID().toString(),
) {
    /** 作业站原始 id；校正过的条目 copilotId 归零，只能按落盘文件名 {id}.json 认 */
    val prtsId: Int?
        get() = if (source == "web") File(filePath).nameWithoutExtension.toIntOrNull() else null
}
