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

        val normalized = normalizeFullWidth(input)
        val upperInput = normalized.uppercase()

        // 1. 先检查别名表（用归一化后的输入匹配：中文键不受全角转半角影响，
        //    英文键则能吃掉全角输入法敲出来的 ＣＥ）
        stageAliases[normalized]?.let { return it }
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
     * 全角转半角。
     *
     * 中文输入法下敲「1-7」送上来的是「1－7」（U+FF0D），不归一化就会把这个
     * 全角连字符原样存进计划，MaaCore 认不出这个关卡。
     * 全角 ！～（U+FF01–U+FF5E）与半角 0x21–0x7E 一一对应，差 0xFEE0。
     */
    private fun normalizeFullWidth(input: String): String = buildString(input.length) {
        input.forEach { char ->
            append(
                when (char.code) {
                    in 0xFF01..0xFF5E -> (char.code - 0xFEE0).toChar()
                    0x3000 -> ' '
                    else -> char
                },
            )
        }
    }

    /**
     * 检查输入是否是已知别名
     */
    fun isKnownAlias(input: String): Boolean {
        return stageAliases.containsKey(input) || stageAliases.containsKey(input.uppercase())
    }
}
