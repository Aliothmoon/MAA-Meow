package com.aliothmoon.maameow.data.resource

/**
 * 关卡分组数据
 * 用于 UI 显示分组标题
 */
data class StageGroup(
    val title: String,           // 分组标题
    val stages: List<StageItem>, // 关卡列表
    val daysLeftText: String? = null  // 剩余天数文本（活动关卡分组）
)