package com.aliothmoon.maameow.data.resource

/**
 * 材料信息
 */
data class ItemInfo(
    val id: String,            // 材料 ID（如 "30011"）
    val name: String,          // 中文名称（如 "源岩"）
    val icon: String = "",     // 图标文件名
    val sortId: Int = 0,       // 排序 ID
    val classifyType: String = ""  // 分类类型
)
