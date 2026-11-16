package com.aliothmoon.maameow.data.resource

import androidx.annotation.StringRes

/**
 * 统一的关卡项（活动关卡和常驻关卡的统一表示）
 */
data class StageItem(
    val code: String,            // 关卡代码（如 "ME-8", "CE-6"）
    val displayName: String,     // 显示名称（活动关卡为服务端文案）
    @StringRes val displayNameRes: Int? = null,  // 显示名称的字符串资源（常驻关卡）
    val isActivityStage: Boolean = false,  // 是否为活动关卡
    val isOpenToday: Boolean = true,       // 今天是否开放
    val drop: String? = null,    // 掉落物品 ID（活动关卡）
    val dropGroups: List<List<String>> = emptyList()  // 芯片本掉落组 (迁移自 WPF)
)