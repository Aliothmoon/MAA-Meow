package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.domain.enums.RoguelikeBoskySubNodeType
import com.aliothmoon.maameow.domain.enums.RoguelikeMode
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.data.model.TaskParamProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 自动肉鸽配置 - 迁移自 WPF RoguelikeSettingsUserControlModel
 * 默认值对齐 WPF RoguelikeTask.cs
 */
@Serializable
data class RoguelikeConfig(
    // 基础设置
    val theme: String = "JieGarden",  // 主题：Phantom/Mizuki/Sami/Sarkaz/JieGarden
    val difficulty: Int = Int.MAX_VALUE,  // 难度：-1=当前, MAX_VALUE=最高, 0=最低
    val mode: RoguelikeMode = RoguelikeMode.Exp,  // 策略模式
    val squad: String = "",  // 起始分队
    val roles: String = "稳扎稳打",  // 起始阵容
    val coreChar: String = "",  // 开局干员

    // 开局次数 - WPF: Maximum="99999"
    val startsCount: Int = 999999,  // 开局次数限制

    // 投资相关
    val investmentEnabled: Boolean = true,  // 启用投资
    val investCount: Int = 999,  // 投资次数上限
    val stopWhenInvestmentFull: Boolean = false,  // 投资满时停止
    val investmentWithMoreScore: Boolean = false,  // 投资模式刷更多分数

    // 助战相关
    val useSupport: Boolean = false,  // 使用助战
    val enableNonfriendSupport: Boolean = false,  // 允许非好友助战

    // 刷开局相关
    val collectibleModeSquad: String = "",  // 烧水使用分队
    val collectibleModeShopping: Boolean = false,  // 刷开局启用购物
    val collectibleStartAwards: Set<String> = setOf("hot_water", "hope", "ideas"),  // 开局奖励选择
    val startWithEliteTwo: Boolean = false,  // 凹精二核心干员
    val onlyStartWithEliteTwo: Boolean = false,  // 只凹精二不作战

    // 刷等级模式
    val stopAtFinalBoss: Boolean = false,  // 在BOSS前暂停
    val stopAtMaxLevel: Boolean = false,  // 满级后停止

    // 月度小队/深入调查
    val monthlySquadAutoIterate: Boolean = true,  // 月度小队自动切换
    val monthlySquadCheckComms: Boolean = true,  // 月度小队通讯
    val deepExplorationAutoIterate: Boolean = true,  // 深入调查自动切换

    // 界园专用
    val findPlaytimeTarget: RoguelikeBoskySubNodeType = RoguelikeBoskySubNodeType.Ling,  // 目标常乐节点
    val startWithSeed: Boolean = false,  // 使用指定种子开局
    val seed: String = "",  // 种子值，格式：[\da-zA-Z]+,rogue_\d,\d

    // 水月专用
    val refreshTraderWithDice: Boolean = false,  // 骰子刷新商人

    // 萨米专用
    val firstFloorFoldartal: Boolean = false,  // 凹第一层远见密文板
    val firstFloorFoldartals: String = "",  // 远见密文板名称
    val newSquad2StartingFoldartal: Boolean = false,  // 生活队凹开局密文板
    val newSquad2StartingFoldartals: String = "",  // 开局密文板列表
    val expectedCollapsalParadigms: String = "",  // 坍缩范式列表

    // 通用高级设置
    val delayAbortUntilCombatComplete: Boolean = false  // 战斗结束前延迟停止
) : TaskParamProvider {
    override fun toTaskParams(): MaaTaskParams = toTaskParams(normalizeCoreChar = { it })

    /**
     * @param normalizeCoreChar 将开局干员名归一化为简中名。
     *   MaaCore 的 core_char 仅认简中名（BattleDataConfig::find_oper 只匹配 name 字段，
     *   繁中/英文名会使 get_role 返回 Unknown 导致开局干员选择失败），
     *   故下发前须把本地化名反查回简中名。对齐 WPF RoguelikeSettingsUserControlModel.cs:1073。
     */
    fun toTaskParams(normalizeCoreChar: (String) -> String): MaaTaskParams {
        // WPF 条件变量
        val squadIsProfessional = mode == RoguelikeMode.Collectible && theme != "Phantom" &&
                squad in listOf("突击战术分队", "堡垒战术分队", "远程战术分队", "破坏战术分队")
        val squadIsFoldartal =
            mode == RoguelikeMode.Collectible && theme == "Sami" && squad == "生活至上分队"

        val paramsJson = buildJsonObject {
            //  基础设置（始终发送） 
            put("theme", theme)
            put("difficulty", difficulty)
            put("mode", mode.value)  // MaaCore 期望整数值
            if (squad.isNotBlank()) put("squad", squad)
            if (roles.isNotBlank()) put("roles", roles)
            if (coreChar.isNotBlank()) put("core_char", normalizeCoreChar(coreChar))
            put("starts_count", startsCount)

            //  投资相关 
            put("investment_enabled", investmentEnabled)
            if (investmentEnabled) {
                // WPF AsstRoguelikeTask:1078 非投资模式发 int.MaxValue
                put(
                    "investments_count",
                    if (mode == RoguelikeMode.Investment) investCount else Int.MAX_VALUE
                )
                // WPF AsstRoguelikeTask:1079 仅投资模式生效
                put(
                    "stop_when_investment_full",
                    stopWhenInvestmentFull && mode == RoguelikeMode.Investment
                )
                put(
                    "investment_with_more_score",
                    investmentWithMoreScore && mode == RoguelikeMode.Investment
                )
            }

            //  模式特殊设置 
            when (mode) {
                RoguelikeMode.Exp -> {
                    put("stop_at_final_boss", stopAtFinalBoss)
                    put("stop_at_max_level", stopAtMaxLevel)
                }

                RoguelikeMode.Investment -> {
                    // 投资模式设置已在上面处理
                }

                RoguelikeMode.Collectible -> {
                    put("collectible_mode_squad", collectibleModeSquad)
                    put("collectible_mode_shopping", collectibleModeShopping)
                    // WPF AsstRoguelikeTask:1089 / Serialize:245
                    put(
                        "start_with_elite_two",
                        startWithEliteTwo && squadIsProfessional && theme in listOf("Mizuki", "Sami")
                    )
                    // WPF AsstRoguelikeTask:1090 / Serialize:246 (仅依赖 only 开关与主题)
                    put(
                        "only_start_with_elite_two",
                        onlyStartWithEliteTwo && theme in listOf("Mizuki", "Sami")
                    )
                    // WPF Serialize:247 在 Collectible 模式始终输出该字段;
                    // WPF:1113-1115 的 roguelikeOnlyStartWithEliteTwo(无主题约束)为真时输出空对象
                    val onlyEliteTwoForRewards =
                        onlyStartWithEliteTwo && startWithEliteTwo && squadIsProfessional
                    put("collectible_mode_start_list", buildJsonObject {
                        if (!onlyEliteTwoForRewards) {
                            ALL_COLLECTIBLE_AWARD_KEYS.forEach { key ->
                                put(key, key in collectibleStartAwards)
                            }
                        }
                    })
                }

                RoguelikeMode.Squad -> {
                    put("monthly_squad_auto_iterate", monthlySquadAutoIterate)
                    put("monthly_squad_check_comms", monthlySquadCheckComms)
                }

                RoguelikeMode.Exploration -> {
                    put("deep_exploration_auto_iterate", deepExplorationAutoIterate)
                }

                RoguelikeMode.CLP_PDS -> {
                    if (expectedCollapsalParadigms.isNotBlank()) {
                        val paradigms = expectedCollapsalParadigms.split(";")
                            .filter { it.isNotEmpty() }
                        put(
                            "expected_collapsal_paradigms",
                            JsonArray(paradigms.map { JsonPrimitive(it) })
                        )
                    }
                }

                RoguelikeMode.FindPlaytime -> {
                    put("find_playTime_target", findPlaytimeTarget.value)  // MaaCore 期望整数值
                }
            }

            //  萨米专用（跨模式） 
            if (theme == "Sami") {
                // 凹第一层远见密文板 → 发送密文板名称字符串（MaaCore 检查非空字符串）
                if (mode == RoguelikeMode.Collectible && firstFloorFoldartal && firstFloorFoldartals.isNotBlank()) {
                    put("first_floor_foldartal", firstFloorFoldartals)
                }
                // 生活队凹开局密文板 - WPF 不发送 start_with_foldartal key，仅通过 start_foldartal_list 存在与否判断
                if (newSquad2StartingFoldartal && squadIsFoldartal && newSquad2StartingFoldartals.isNotBlank()) {
                    val foldartals = newSquad2StartingFoldartals.split(";")
                        .filter { it.isNotEmpty() }
                        .take(3)
                    put("start_foldartal_list", JsonArray(foldartals.map { JsonPrimitive(it) }))
                }
            }

            //  通用设置（始终发送） 
            put("use_support", useSupport)
            put("use_nonfriend_support", enableNonfriendSupport)
            put("refresh_trader_with_dice", theme == "Mizuki" && refreshTraderWithDice)
            if (startWithSeed && seed.isNotBlank()) {
                put("start_with_seed", seed)
            }
        }
        return MaaTaskParams(MaaTaskType.ROGUELIKE, paramsJson.toString())
    }

    companion object {
        // WPF RoguelikeSettingsUserControlModel:1117-1128 的 reward key 全集(与主题无关)
        private val ALL_COLLECTIBLE_AWARD_KEYS = listOf(
            "hot_water", "shield", "ingot", "hope", "random", "key", "dice", "ideas", "ticket"
        )
    }
}
