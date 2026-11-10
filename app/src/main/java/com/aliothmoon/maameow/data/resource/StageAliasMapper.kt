package com.aliothmoon.maameow.data.resource

/**
 * 关卡代码别名映射器
 * 迁移自 WPF FightSettingsUserControlModel._stageDictionary
 *
 */
object StageAliasMapper {

    /**
     * 别名到关卡代码的映射表
     * see: FightSettingsUserControlModel.cs
     */
    private val stageAliases: Map<String, String> = mapOf(
        // 剿灭模式
        "AN" to "Annihilation",
        "剿灭" to "Annihilation",

        // 龙门币 (CE 系列)
        "CE" to "CE-6",
        "龙门币" to "CE-6",
        "钱" to "CE-6",

        // 作战记录 (LS 系列)
        "LS" to "LS-6",
        "经验" to "LS-6",
        "狗粮" to "LS-6",
        "作战记录" to "LS-6",

        // 技能书 (CA 系列)
        "CA" to "CA-5",
        "技能" to "CA-5",
        "技能书" to "CA-5",

        // 采购凭证/红票 (AP 系列)
        "AP" to "AP-5",
        "红票" to "AP-5",
        "采购凭证" to "AP-5",

        // 碳素 (SK 系列)
        "SK" to "SK-5",
        "碳" to "SK-5",
        "炭" to "SK-5",
        "碳素" to "SK-5"
    )

    /**
     * 将输入的关卡代码进行转换
     *
     * 处理逻辑（see ToUpperAndCheckStage）：
     * 1. 空字符串直接返回
     * 2. 先尝试从别名表中查找
     * 3. 如果不在别名表中，将英文字母转为大写后返回
     *
     * @param input 用户输入的关卡代码或别名
     * @param availableStages 可选的关卡列表，用于验证关卡是否存在
     * @return 转换后的关卡代码
     */
    fun mapToStageCode(input: String, availableStages: List<String>? = null): String {
        if (input.isBlank()) return input

        val upperInput = input.uppercase()

        // 1. 先检查别名表（别名表中的 key 可能是中文，直接用原始输入匹配）
        stageAliases[input]?.let { return it }
        // 也检查大写版本
        stageAliases[upperInput]?.let { return it }

        // 2. 检查是否在可用关卡列表中
        availableStages?.let { stages ->
            // 精确匹配（忽略大小写）
            stages.find { it.equals(upperInput, ignoreCase = true) }?.let { return it }
            // 包含匹配
            stages.find { it.uppercase().contains(upperInput) }?.let { return it }
        }

        // 3. 返回大写版本（保持用户输入，仅转大写）
        return upperInput
    }

    /**
     * 检查输入是否是已知别名
     */
    fun isKnownAlias(input: String): Boolean {
        return stageAliases.containsKey(input) || stageAliases.containsKey(input.uppercase())
    }

    /**
     * 获取所有已知别名列表（用于UI提示）
     */
    fun getKnownAliases(): List<Pair<String, String>> {
        return stageAliases.entries
            .distinctBy { it.value }  // 去重相同目标
            .map { it.key to it.value }
    }
}
