package com.aliothmoon.maameow.data.model

/**
 * 任务参数组装所需的外部输入。
 *
 * 这些值不属于配置自身、由 AnalyzeTaskChainUseCase 在分析阶段解析后注入。
 * 收进单一对象而非逐个加形参：新增输入时只需改本类与真正用到它的配置类，
 * 也避免了「无参重载偷偷填默认值」这种与实际行为不符的写法。
 *
 * 只承载**分析阶段已解析完毕的纯值**或**无副作用的纯函数**。
 * 需要 IO / suspend / 可变状态的依赖必须先由 UseCase 解析成值再注入，
 * 不要把仓库或管理器本身放进来 —— 否则配置类会获得 IO 能力，
 * `data/model` 与 `domain` 的边界随之瓦解。
 *
 * 本类持有函数字段，`equals` / `hashCode` 无实际意义，不要用于相等性比较或做 Map key。
 *
 * @param clientType 客户端类型（Official / Bilibili / YoStarEN / …）。
 *   **不要给本字段加默认值** —— 填错会静默产生错误参数（旧代码硬编码 "Official" 正是此坑），
 *   由编译器强制每个调用方显式提供。
 * @param chainAllowsCreditFight 整条任务链是否允许信用作战借助战打 OF-1。
 *   仅表示「链级前提成立」，是否真的下发还要与各 MallConfig 自身的开关取与。
 * @param normalizeCoreChar 把开局干员名归一化为简中名。
 *   MaaCore 的 core_char 仅认简中名（BattleDataConfig::find_oper 只匹配 name 字段，
 *   繁中/英文名会使 get_role 返回 Unknown 导致开局干员选择失败），
 *   故下发前须把本地化名反查回简中名。对齐 WPF RoguelikeSettingsUserControlModel.cs:1073。
 *   同样不给默认值：漏传会让干员选择静默失效，与本类要消灭的「说谎的默认」同构。
 */
data class TaskParamContext(
    val clientType: String,
    val normalizeCoreChar: (String) -> String,
    val chainAllowsCreditFight: Boolean = false,
)
