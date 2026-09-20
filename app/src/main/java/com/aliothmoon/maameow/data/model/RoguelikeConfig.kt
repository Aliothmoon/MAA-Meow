package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.domain.enums.RoguelikeBlackFlowCultivationTarget
import com.aliothmoon.maameow.domain.enums.RoguelikeBoskySubNodeType
import com.aliothmoon.maameow.domain.enums.RoguelikeMode
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.enums.UiUsageConstants
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 自动肉鸽配置 - 迁移自 WPF RoguelikeSettingsUserControlModel
 * 默认值对齐 WPF RoguelikeTask.cs
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RoguelikeConfig(
    // 基础设置
    val theme: String = "Phantom",  // 主题：Phantom/Mizuki/Sami/Sarkaz/JieGarden/BlackFlow
    val difficulty: Int = Int.MAX_VALUE,  // 难度：-1=当前, MAX_VALUE=最高, 0=最低
    val mode: RoguelikeMode = RoguelikeMode.Exp,  // 策略模式
    val squad: String = "",  // 起始分队
    val roles: String = "稳扎稳打",  // 起始阵容
    val startingOpers: List<RoguelikeStartingOper> = emptyList(),  // 开局干员顺位，最多 3 位
    val useAdditionalStartingOpers: Boolean = false,  // 启用第 2、3 位开局干员

    // 开局次数 - WPF: Maximum="99999"
    val startsCount: Int = 999999,  // 开局次数限制

    // 投资相关
    val investmentEnabled: Boolean = true,  // 启用投资
    val investCount: Int = 999,  // 投资次数上限
    val stopWhenInvestmentFull: Boolean = false,  // 投资满时停止
    val investmentWithMoreScore: Boolean = false,  // 投资模式刷更多分数

    // 助战相关
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

    // 黑流树海专用
    val blackFlowCultivationTarget: RoguelikeBlackFlowCultivationTarget =
        RoguelikeBlackFlowCultivationTarget.Cat,  // 刷襁褓动物的目标品种

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
    // TODO: 尚未接线，本次迭代不做
    //  上游是纯 GUI 行为，没有对应 core 参数：战斗中（StageInfo 回调）把「停止」换成
    //  「等待 & 停止」，点了先等 RoguelikeCombatEnd 再真停，上限 10 分钟；
    //  用途是避免肉鸽战斗中途硬停——那样游戏卡在战斗里，本次探索基本就废了
    //  实现前要先搬家：上游放在全局 RuntimeSettings，这里却在每节点的 RoguelikeConfig 上，
    //  两个肉鸽节点各有一份时回调侧无从取值，应挪到 AppSettingsManager
    val delayAbortUntilCombatComplete: Boolean = false,  // 战斗结束前延迟停止

    // 旧档字段，仅用于读旧配置时并入 [startingOpers]，迁移后置空不再写盘
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("coreChar")
    val legacyCoreChar: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("useSupport")
    val legacyUseSupport: Boolean? = null,
) : TaskParamProvider {

    fun startingOperName(index: Int): String = startingOpers.getOrNull(index)?.name.orEmpty()

    fun startingOperUseSupport(index: Int): Boolean =
        startingOpers.getOrNull(index)?.useSupport == true

    /** 写第 [index] 顺位，不足处补空项，对齐 WPF EnsureStartingOper */
    fun withStartingOper(
        index: Int,
        transform: (RoguelikeStartingOper) -> RoguelikeStartingOper
    ): RoguelikeConfig {
        val opers = startingOpers.toMutableList()
        while (opers.size <= index) opers += RoguelikeStartingOper()
        opers[index] = transform(opers[index])
        return copy(startingOpers = opers)
    }

    /** 参与调度的顺位数：开关关只认第 1 位 */
    private val activeStartingOperCount: Int
        get() = if (useAdditionalStartingOpers) MAX_STARTING_OPERS else 1

    /** 从头连续填了名字的顺位数，链条在首个空名处断掉 */
    val filledStartingOperCount: Int
        get() = startingOpers.take(activeStartingOperCount).takeWhile { it.name.isNotBlank() }.size

    /** 第 [index] 顺位是否可编辑：前面的顺位都填了才轮得到它 */
    fun isStartingOperEditable(index: Int): Boolean = filledStartingOperCount >= index

    /** 第 [index] 顺位是否已填好名字，助战之类的附属开关得等它 */
    fun isStartingOperFilled(index: Int): Boolean = filledStartingOperCount > index

    /** 是否有顺位要用助战，决定「非好友助战」是否有意义 */
    val anyStartingOperUsesSupport: Boolean
        get() = (0 until activeStartingOperCount).any { startingOperUseSupport(it) }

    /** 实际下发的顺位，对齐 WPF RoguelikeSettings:1470；只过滤不改配置本体，重开开关即恢复 */
    val effectiveStartingOpers: List<RoguelikeStartingOper>
        get() = startingOpers.take(filledStartingOperCount)

    fun normalizedStartingOperNames(resourceDataManager: ResourceDataManager): List<String> =
        effectiveStartingOpers.map { resourceDataManager.normalizeCharacterName(it.name) }

    /**
     * 旧档迁移：beta.2 前的 coreChar/useSupport 并入第 1 顺位
     * 对齐 WPF RoguelikeStartingOpersConverter —— 仅在 [startingOpers] 为空时合成
     */
    override fun migrate(): RoguelikeConfig {
        if (legacyCoreChar == null && legacyUseSupport == null) return this
        val merged = startingOpers.ifEmpty {
            listOfNotNull(
                RoguelikeStartingOper(legacyCoreChar.orEmpty(), legacyUseSupport == true)
                    .takeIf { it.name.isNotBlank() || it.useSupport }
            )
        }
        return copy(startingOpers = merged, legacyCoreChar = null, legacyUseSupport = null)
    }

    override fun toTaskParams(ctx: TaskParamContext): List<MaaTaskParams> {
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
            // MaaCore 只认简中名（BattleDataConfig::find_oper 只匹配 name 字段，
            // 繁中/英文名会使 get_role 返回 Unknown 导致开局干员选择失败）
            val startOperNames = normalizedStartingOperNames(ctx.resourceDataManager)
            if (startOperNames.isNotEmpty()) {
                put("core_char_list", JsonArray(startOperNames.mapIndexed { index, name ->
                    buildJsonObject {
                        put("name", name)
                        put("use_support", startingOperUseSupport(index))
                    }
                }))
            }
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
                // WPF RoguelikeSettings:1378 黑流树海无此玩法
                put(
                    "investment_with_more_score",
                    investmentWithMoreScore && mode == RoguelikeMode.Investment &&
                            theme != UiUsageConstants.Roguelike.THEME_BLACK_FLOW
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
                        startWithEliteTwo && squadIsProfessional && theme in listOf(
                            "Mizuki",
                            "Sami"
                        )
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

                RoguelikeMode.BlackFlowBabyAnimal -> {
                    // 对齐 WPF AsstRoguelikeTask:218，主题与模式都对上才发；
                    // 其余情况 core 按 mode + investment_enabled 自行推导 strategy
                    if (theme == UiUsageConstants.Roguelike.THEME_BLACK_FLOW) {
                        put("blackflow_strategy", "baby_animal")
                        put("blackflow_cultivation_target", blackFlowCultivationTarget.value)
                    }
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
            put("use_nonfriend_support", enableNonfriendSupport)
            put("refresh_trader_with_dice", theme == "Mizuki" && refreshTraderWithDice)
            if (startWithSeed && seed.isNotBlank()) {
                put("start_with_seed", seed)
            }
        }
        return listOf(MaaTaskParams(MaaTaskType.ROGUELIKE, paramsJson.toString()))
    }

    companion object {
        /** 开局免费招募次数，上游同为 3 */
        const val MAX_STARTING_OPERS = 3

        // WPF RoguelikeSettingsUserControlModel:1117-1128 的 reward key 全集(与主题无关)
        private val ALL_COLLECTIBLE_AWARD_KEYS = listOf(
            "hot_water", "shield", "ingot", "hope", "random", "key", "dice", "ideas", "ticket"
        )
    }
}

/** 开局干员顺位项，按列表顺序对应开局第 1/2/3 次免费招募 */
@Serializable
data class RoguelikeStartingOper(
    val name: String = "",
    val useSupport: Boolean = false,
)
