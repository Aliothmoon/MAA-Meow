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
        USER_DEFINED_INFRAST to "自定义文件",
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
    val droneUsageValues = listOf(
        "_NotUse",
        "Money",
        "SyntheticJade",
        "CombatRecord",
        "PureGold",
        "OriginStone",
        "Chip"
    )

    /**
     * 肉鸽 UI 展示常量
     * 迁移自 RoguelikeConfig.companion / WPF RoguelikeSettingsUserControlModel
     */
    object Roguelike {
        val THEMES = listOf("Phantom", "Mizuki", "Sami", "Sarkaz", "JieGarden")

        const val DEFAULT_SQUAD = "指挥分队"
        const val DEFAULT_ROLE = "稳扎稳打"
        const val ROLE_FIRST_MOVE_ADVANTAGE = "先手必胜"
        const val ROLE_OVERCOMING_WEAKNESSES = "取长补短"
        const val ROLE_FLEXIBLE_DEPLOYMENT = "灵活部署"
        const val ROLE_UNBREAKABLE = "坚不可摧"
        const val ROLE_AS_YOUR_HEART_DESIRES = "随心所欲"

        val PLAYTIME_TARGETS = listOf("Ling", "Shu", "Nian")

        // 职业阵容列表（按主题动态变化）
        // WPF: UpdateRoguelikeRolesList (lines 145-166)
        fun getRoleKeysForTheme(theme: String): List<String> {
            val list = mutableListOf(
                ROLE_FIRST_MOVE_ADVANTAGE,
                DEFAULT_ROLE,
                ROLE_OVERCOMING_WEAKNESSES,
            )
            if (theme == "JieGarden") {
                list.add(ROLE_FLEXIBLE_DEPLOYMENT)
                list.add(ROLE_UNBREAKABLE)
            }
            list.add(ROLE_AS_YOUR_HEART_DESIRES)
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
        fun getDifficultyValues(theme: String): List<Int> {
            val maxDiff = getMaxDifficultyForTheme(theme)
            return buildList {
                add(-1)
                add(Int.MAX_VALUE)
                for (i in maxDiff downTo 1) {
                    add(i)
                }
                add(0)
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
        fun getModeKeysForTheme(theme: String): List<String> = when (theme) {
            "JieGarden" -> listOf("Exp", "Investment", "Collectible", "FindPlaytime")
            "Sami" -> listOf("Exp", "Investment", "Collectible", "Squad", "Exploration", "CLP_PDS")
            else -> listOf("Exp", "Investment", "Collectible", "Squad", "Exploration")
        }

        // 验证模式是否对当前主题有效
        fun isModeValidForTheme(mode: RoguelikeMode, theme: String): Boolean {
            return getModeKeysForTheme(theme).any { it == mode.name }
        }

        // 开局奖励选项 - WPF: UpdateRoguelikeStartWithAllDict (line 556-588)
        fun getCollectibleAwardKeys(theme: String): List<String> {
            return buildList {
                addAll(listOf("hot_water", "shield", "ingot", "hope", "random"))
                when (theme) {
                    "Mizuki" -> {
                        add("key")
                        add("dice")
                    }

                    "Sarkaz" -> {
                        add("ideas")
                    }

                    "JieGarden" -> {
                        // 界园移除希望，添加票券
                        remove("hope")
                        add("ticket")
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
