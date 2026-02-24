package com.aliothmoon.maameow.domain.enums

object UiUsageConstants {
    val annihilations = listOf(
        "当期剿灭" to "Annihilation",
        "切尔诺伯格" to "Chernobog@Annihilation",
        "龙门外环" to "LungmenOutskirts@Annihilation",
        "龙门市区" to "LungmenDowntown@Annihilation"
    )

    /** 用户自定义文件的 key */
    const val USER_DEFINED_INFRAST = "user_defined"

    /**
     * 内置自定义基建配置预设
     * Pair<文件名(不含.json), 显示名称>
     * 对应 WPF: DefaultInfrastList
     */
    val defaultInfrastPresets = listOf(
        USER_DEFINED_INFRAST to "自定义（暂不支持）",
        "153_layout_3_times_a_day.json" to "153 一天 3 换",
        "153_layout_4_times_a_day.json" to "153 一天 4 换",
        "243_layout_3_times_a_day.json" to "243 一天 3 换",
        "243_layout_4_times_a_day.json" to "243 一天 4 换",
        "333_layout_for_Orundum_3_times_a_day.json" to "333 一天 3 换",
    )


    // 代理倍率选项 (SeriesList)
    val seriesOptions = listOf(
        0 to "AUTO",
        6 to "6",
        5 to "5",
        4 to "4",
        3 to "3",
        2 to "2",
        1 to "1",
        -1 to "不切换"
    )

    val dropItems = listOf(
        "30011", "30012", "30013", "30014",  // 技能类材料
        "30021", "30022", "30023", "30024",
        "30031", "30032", "30033", "30034",
        "30041", "30042", "30043", "30044",
        "30051", "30052", "30053", "30054",
        "30061", "30062", "30063", "30064"
    )

    // see UsesOfDronesList
    val dronesUsages = listOf(
        "_NotUse" to "不使用无人机",
        "Money" to "贸易站-龙门币",
        "SyntheticJade" to "贸易站-合成玉",
        "CombatRecord" to "制造站-经验书",
        "PureGold" to "制造站-赤金",
        "OriginStone" to "制造站-源石碎片",
        "Chip" to "制造站-芯片组"
    )

    /**
     * 肉鸽 UI 展示常量
     * 迁移自 RoguelikeConfig.companion / WPF RoguelikeSettingsUserControlModel
     */
    object Roguelike {
        // 主题列表
        val THEME_OPTIONS = listOf(
            "Phantom" to "傀影",
            "Mizuki" to "水月",
            "Sami" to "萨米",
            "Sarkaz" to "萨卡兹",
            "JieGarden" to "界园"
        )

        // 策略模式列表（含描述）
        val MODE_OPTIONS = listOf(
            "Exp" to "刷等级，尽可能稳定地打更多层数",
            "Investment" to "刷源石锭，投资完成后自动退出",
            "Collectible" to "刷开局，刷取热水壶或精二干员开局",
            "Squad" to "刷月度小队，尽可能稳定地打更多层数",
            "Exploration" to "刷深入调查，尽可能稳定地打更多层数",
            "CLP_PDS" to "刷坍缩范式，遇到非稀有坍缩范式后直接重开",
            "FindPlaytime" to "刷常乐节点，第一层进洞，找不到需要的节点就重开"
        )

        // 简短模式名称（用于下拉框）
        val MODE_SHORT_OPTIONS = listOf(
            "Exp" to "刷等级",
            "Investment" to "刷源石锭",
            "Collectible" to "刷开局",
            "Squad" to "月度小队",
            "Exploration" to "深入调查",
            "CLP_PDS" to "刷坍缩",
            "FindPlaytime" to "刷常乐"
        )

        // 目标常乐节点
        val PLAYTIME_TARGET_OPTIONS = listOf(
            "Ling" to "令 - 掷地有声",
            "Shu" to "黍 - 种因得果",
            "Nian" to "年 - 三缺一"
        )

        // 职业阵容列表（按主题动态变化）
        // WPF: UpdateRoguelikeRolesList (lines 145-166)
        fun getRolesOptionsForTheme(theme: String): List<Pair<String, String>> {
            val list = mutableListOf(
                "先手必胜" to "先手必胜（先锋、狙击、特种）",
                "稳扎稳打" to "稳扎稳打（重装、术师、狙击）",
                "取长补短" to "取长补短（近卫、辅助、医疗）",
            )
            if (theme == "JieGarden") {
                list.add("灵活部署" to "灵活部署（先锋、辅助、特种）")
                list.add("坚不可摧" to "坚不可摧（重装、术师、医疗）")
            }
            list.add("随心所欲" to "随心所欲（三张随机）")
            return list
        }

        // 获取主题最大难度
        fun getMaxDifficultyForTheme(theme: String): Int = when (theme) {
            "Phantom" -> 15
            "Mizuki" -> 18
            "Sami" -> 15
            "Sarkaz" -> 18
            "JieGarden" -> 18
            else -> 20
        }

        // 难度选项 - WPF: DifficultyList (lines 80-92)
        // 顺序: 不切换(-1) → MAX → maxDiff...0
        fun getDifficultyOptions(theme: String): List<Pair<Int, String>> {
            val maxDiff = getMaxDifficultyForTheme(theme)
            return buildList {
                add(-1 to "不切换 (-1)")
                add(Int.MAX_VALUE to "MAX ($maxDiff)")
                for (i in maxDiff downTo 1) {
                    add(i to "$i")
                }
                add(0 to "MIN (0)")
            }
        }

        // 专业分队列表 - WPF: RoguelikeSquadIsProfessional (line 445)
        private val PROFESSIONAL_SQUADS =
            listOf("突击战术分队", "堡垒战术分队", "远程战术分队", "破坏战术分队")

        // 判断分队是否为专业分队
        fun isSquadProfessional(
            squad: String,
            mode: RoguelikeMode,
            theme: String
        ): Boolean {
            return mode == RoguelikeMode.Collectible && theme != "Phantom" && squad in PROFESSIONAL_SQUADS
        }

        // 判断分队是否为密文板分队 - WPF: RoguelikeSquadIsFoldartal (line 448)
        fun isSquadFoldartal(
            squad: String,
            mode: RoguelikeMode,
            theme: String
        ): Boolean {
            return mode == RoguelikeMode.Collectible && theme == "Sami" && squad == "生活至上分队"
        }

        // 多任务共享提示 - WPF: MultiTasksShareTip
        const val MULTI_TASKS_SHARE_TIP = "以下选项为多任务共用"

        // 策略模式列表（按主题动态变化）
        fun getModeOptionsForTheme(theme: String): List<Pair<String, String>> = when (theme) {
            "JieGarden" -> listOf(
                "Exp" to "刷等级",
                "Investment" to "刷源石锭",
                "Collectible" to "刷开局",
                "FindPlaytime" to "刷常乐"
            )

            "Sami" -> listOf(
                "Exp" to "刷等级",
                "Investment" to "刷源石锭",
                "Collectible" to "刷开局",
                "Squad" to "月度小队",
                "Exploration" to "深入调查",
                "CLP_PDS" to "刷坍缩"
            )

            else -> listOf(
                "Exp" to "刷等级",
                "Investment" to "刷源石锭",
                "Collectible" to "刷开局",
                "Squad" to "月度小队",
                "Exploration" to "深入调查"
            )
        }

        // 验证模式是否对当前主题有效
        fun isModeValidForTheme(mode: RoguelikeMode, theme: String): Boolean {
            return getModeOptionsForTheme(theme).any { it.first == mode.name }
        }

        // 开局奖励选项 - WPF: UpdateRoguelikeStartWithAllDict (line 556-588)
        // key 是发送给 MaaCore 的参数名, value 是显示名称
        private val BASE_COLLECTIBLE_AWARDS = listOf(
            "hot_water" to "热水壶",
            "shield" to "盾",
            "ingot" to "锭",
            "hope" to "希望",
            "random" to "随机奖励",
        )

        fun getCollectibleAwardOptions(theme: String): List<Pair<String, String>> {
            return buildList {
                addAll(BASE_COLLECTIBLE_AWARDS)
                when (theme) {
                    "Mizuki" -> {
                        add("key" to "钥匙")
                        add("dice" to "骰子")
                    }

                    "Sarkaz" -> {
                        add("ideas" to "构想")
                    }

                    "JieGarden" -> {
                        // 界园移除希望，添加票券
                        removeAll { it.first == "hope" }
                        add("ticket" to "票券")
                    }
                }
            }
        }

        // 通用分队（所有主题共享）
        // WPF: _commonSquads (lines 233-242)
        private val COMMON_SQUADS = listOf(
            "指挥分队", "后勤分队",
            "突击战术分队", "堡垒战术分队",
            "远程战术分队", "破坏战术分队",
            "高规格分队"
        )

        // 各主题专属分队
        // WPF: _squadDictionary (lines 168-230)
        private val THEME_SQUADS = mapOf(
            "Phantom" to listOf("集群分队", "矛头分队", "研究分队"),
            "Mizuki" to listOf(
                "集群分队",
                "矛头分队",
                "心胜于物分队",
                "物尽其用分队",
                "以人为本分队",
                "研究分队"
            ),
            "Sami" to listOf(
                "集群分队",
                "矛头分队",
                "永恒狩猎分队",
                "生活至上分队",
                "科学主义分队",
                "特训分队"
            ),
            "Sarkaz" to listOf(
                "集群分队",
                "矛头分队",
                "魂灵护送分队",
                "博闻广记分队",
                "蓝图测绘分队",
                "因地制宜分队",
                "异想天开分队",
                "点刺成锭分队",
                "拟态学者分队",
                "专业人士分队"
            ),
            "JieGarden" to listOf(
                "特勤分队",
                "高台突破分队",
                "地面突破分队",
                "游客分队",
                "司岁台分队",
                "天师府分队",
                "花团锦簇分队",
                "棋行险着分队",
                "岁影回音分队",
                "代理人分队",
                "知学分队",
                "商贾分队"
            )
        )

        // 萨卡兹投资模式专属分队
        // WPF: UpdateRoguelikeSquadList (lines 244-275)
        private val SARKAZ_INVESTMENT_SQUADS = listOf(
            "集群分队", "矛头分队", "博闻广记分队", "蓝图测绘分队", "点刺成锭分队", "拟态学者分队"
        )

        // 分队列表（按主题）= 专属分队 + 通用分队
        // WPF: UpdateRoguelikeSquadList (lines 244-275)
        fun getSquadOptionsForTheme(
            theme: String,
            mode: RoguelikeMode = RoguelikeMode.Exp
        ): List<String> {
            if (theme == "Sarkaz" && mode == RoguelikeMode.Investment) {
                return SARKAZ_INVESTMENT_SQUADS + COMMON_SQUADS
            }
            val themeSquads = THEME_SQUADS[theme] ?: emptyList()
            return themeSquads + COMMON_SQUADS
        }
    }
}