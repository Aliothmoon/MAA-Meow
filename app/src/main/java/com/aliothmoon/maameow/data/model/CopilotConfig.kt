package com.aliothmoon.maameow.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CopilotConfig(
    val formation: Boolean = true,           // 自动编队
    val addTrust: Boolean = false,           // 追加信赖
    val ignoreRequirements: Boolean = false,  // 忽略练度要求
    val useSanityPotion: Boolean = false,     // 使用理智药
    val supportUnitUsage: Int = 0,           // 助战用法: 0不用, 1需要时, 3随机
    val useSupportUnit: Boolean = false,     // 是否使用助战
    val loopTimes: Int = 1,                  // 循环次数
    val loop: Boolean = false,               // 是否循环
    val useFormation: Boolean = false,       // 是否指定编队
    val formationIndex: Int = 1,             // 编队索引 1-4
    val addUserAdditional: Boolean = false,  // 是否追加自定义干员
    val userAdditional: String = "",         // 自定义干员 JSON
)
