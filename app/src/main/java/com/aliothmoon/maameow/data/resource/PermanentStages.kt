package com.aliothmoon.maameow.data.resource

import java.time.DayOfWeek

object PermanentStages {
    val STAGES: List<StageInfo> by lazy {
        listOf(
            // ==================== 主线关卡 ====================
            createStage("1-7"),
            createStage("R8-11"),
            createStage("12-17-HARD"),

            // ==================== 资源本 ====================
            // 龙门币 CE: 周二、四、六、日
            createStage(
                code = "CE-6",
                openDays = StageOpenDays.RESOURCE_OPEN_DAYS["CE"]!!,
                tip = StageInfo.STAGE_TIPS["CE-6"] ?: ""
            ),

            // 采购凭证 AP: 周一、四、六、日
            createStage(
                code = "AP-5",
                openDays = StageOpenDays.RESOURCE_OPEN_DAYS["AP"]!!,
                tip = StageInfo.STAGE_TIPS["AP-5"] ?: ""
            ),

            // 技巧概要 CA: 周二、三、五、日
            createStage(
                code = "CA-5",
                openDays = StageOpenDays.RESOURCE_OPEN_DAYS["CA"]!!,
                tip = StageInfo.STAGE_TIPS["CA-5"] ?: ""
            ),

            // 作战记录 LS: 每天开放
            createStage(
                code = "LS-6",
                tip = StageInfo.STAGE_TIPS["LS-6"] ?: ""
            ),

            // 碳素 SK: 周一、三、五、六
            createStage(
                code = "SK-5",
                openDays = StageOpenDays.RESOURCE_OPEN_DAYS["SK"]!!,
                tip = StageInfo.STAGE_TIPS["SK-5"] ?: ""
            ),

            // ==================== 剿灭模式 ====================
            createStage("Annihilation", category = StageCategory.ANNIHILATION),

            // ==================== 芯片本 ====================
            // PR-A: 重装/医疗 - 周一、四、五、日
            createStage(
                code = "PR-A-1",
                openDays = StageOpenDays.CHIP_OPEN_DAYS["PR-A"]!!,
                tip = StageInfo.STAGE_TIPS["PR-A-1"] ?: "",
                dropGroups = listOf(listOf("3231", "3261"), listOf("3232", "3262"))
            ),
            createStage(
                code = "PR-A-2",
                openDays = StageOpenDays.CHIP_OPEN_DAYS["PR-A"]!!,
                tip = StageInfo.STAGE_TIPS["PR-A-2"] ?: ""
            ),

            // PR-B: 狙击/术师 - 周一、二、五、六
            createStage(
                code = "PR-B-1",
                openDays = StageOpenDays.CHIP_OPEN_DAYS["PR-B"]!!,
                tip = StageInfo.STAGE_TIPS["PR-B-1"] ?: "",
                dropGroups = listOf(listOf("3251", "3241"), listOf("3252", "3242"))
            ),
            createStage(
                code = "PR-B-2",
                openDays = StageOpenDays.CHIP_OPEN_DAYS["PR-B"]!!,
                tip = StageInfo.STAGE_TIPS["PR-B-2"] ?: ""
            ),

            // PR-C: 先锋/辅助 - 周三、四、六、日
            createStage(
                code = "PR-C-1",
                openDays = StageOpenDays.CHIP_OPEN_DAYS["PR-C"]!!,
                tip = StageInfo.STAGE_TIPS["PR-C-1"] ?: "",
                dropGroups = listOf(listOf("3211", "3271"), listOf("3212", "3272"))
            ),
            createStage(
                code = "PR-C-2",
                openDays = StageOpenDays.CHIP_OPEN_DAYS["PR-C"]!!,
                tip = StageInfo.STAGE_TIPS["PR-C-2"] ?: ""
            ),

            // PR-D: 近卫/特种 - 周二、三、六、日
            createStage(
                code = "PR-D-1",
                openDays = StageOpenDays.CHIP_OPEN_DAYS["PR-D"]!!,
                tip = StageInfo.STAGE_TIPS["PR-D-1"] ?: "",
                dropGroups = listOf(listOf("3221", "3281"), listOf("3222", "3282"))
            ),
            createStage(
                code = "PR-D-2",
                openDays = StageOpenDays.CHIP_OPEN_DAYS["PR-D"]!!,
                tip = StageInfo.STAGE_TIPS["PR-D-2"] ?: ""
            )
        )
    }

    private fun createStage(
        code: String,
        openDays: List<DayOfWeek> = emptyList(),
        category: StageCategory = StageCategory.fromCode(code),
        tip: String = "",
        dropGroups: List<List<String>> = emptyList()
    ): StageInfo {
        return StageInfo(
            stageId = code,
            code = code,
            openDays = openDays,
            category = category,
            tip = tip,
            dropGroups = dropGroups
        )
    }
}