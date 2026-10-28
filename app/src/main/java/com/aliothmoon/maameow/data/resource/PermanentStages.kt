package com.aliothmoon.maameow.data.resource

import androidx.annotation.StringRes
import com.aliothmoon.maameow.R
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
                tipRes = R.string.panel_fight_stage_tip_ce
            ),

            // 采购凭证 AP: 周一、四、六、日
            createStage(
                code = "AP-5",
                openDays = StageOpenDays.RESOURCE_OPEN_DAYS["AP"]!!,
                tipRes = R.string.panel_fight_stage_tip_ap
            ),

            // 技巧概要 CA: 周二、三、五、日
            createStage(
                code = "CA-5",
                openDays = StageOpenDays.RESOURCE_OPEN_DAYS["CA"]!!,
                tipRes = R.string.panel_fight_stage_tip_ca,
                dropGroups = listOf(listOf("3301", "3302", "3303"))
            ),

            // 作战记录 LS: 每天开放
            createStage(
                code = "LS-6",
                tipRes = R.string.panel_fight_stage_tip_ls
            ),

            // 碳素 SK: 周一、三、五、六
            createStage(
                code = "SK-5",
                openDays = StageOpenDays.RESOURCE_OPEN_DAYS["SK"]!!,
                tipRes = R.string.panel_fight_stage_tip_sk
            ),

            // ==================== 剿灭模式 ====================
            createStage("Annihilation", category = StageCategory.ANNIHILATION),

            // ==================== 芯片本 ====================
            // PR-X-2 不单独出提示，同上游，免得与 PR-X-1 重复
            // dropGroups：芯片 / 芯片组 / 双芯片，对齐上游 #16193
            // PR-A: 重装/医疗 - 周一、四、五、日
            createStage(
                code = "PR-A-1",
                openDays = StageOpenDays.CHIP_OPEN_DAYS["PR-A"]!!,
                tipRes = R.string.panel_fight_stage_tip_pr_a,
                dropGroups = listOf(listOf("3261", "3231"), listOf("3262", "3232"), listOf("3263", "3233"))
            ),
            createStage(
                code = "PR-A-2",
                openDays = StageOpenDays.CHIP_OPEN_DAYS["PR-A"]!!
            ),

            // PR-B: 狙击/术师 - 周一、二、五、六
            createStage(
                code = "PR-B-1",
                openDays = StageOpenDays.CHIP_OPEN_DAYS["PR-B"]!!,
                tipRes = R.string.panel_fight_stage_tip_pr_b,
                dropGroups = listOf(listOf("3251", "3241"), listOf("3252", "3242"), listOf("3253", "3243"))
            ),
            createStage(
                code = "PR-B-2",
                openDays = StageOpenDays.CHIP_OPEN_DAYS["PR-B"]!!
            ),

            // PR-C: 先锋/辅助 - 周三、四、六、日
            createStage(
                code = "PR-C-1",
                openDays = StageOpenDays.CHIP_OPEN_DAYS["PR-C"]!!,
                tipRes = R.string.panel_fight_stage_tip_pr_c,
                dropGroups = listOf(listOf("3211", "3271"), listOf("3212", "3272"), listOf("3213", "3273"))
            ),
            createStage(
                code = "PR-C-2",
                openDays = StageOpenDays.CHIP_OPEN_DAYS["PR-C"]!!
            ),

            // PR-D: 近卫/特种 - 周二、三、六、日
            createStage(
                code = "PR-D-1",
                openDays = StageOpenDays.CHIP_OPEN_DAYS["PR-D"]!!,
                tipRes = R.string.panel_fight_stage_tip_pr_d,
                dropGroups = listOf(listOf("3221", "3281"), listOf("3222", "3282"), listOf("3223", "3283"))
            ),
            createStage(
                code = "PR-D-2",
                openDays = StageOpenDays.CHIP_OPEN_DAYS["PR-D"]!!
            )
        )
    }

    private fun createStage(
        code: String,
        openDays: List<DayOfWeek> = emptyList(),
        category: StageCategory = StageCategory.fromCode(code),
        @StringRes tipRes: Int? = null,
        dropGroups: List<List<String>> = emptyList()
    ): StageInfo {
        return StageInfo(
            stageId = code,
            code = code,
            openDays = openDays,
            category = category,
            tipRes = tipRes,
            dropGroups = dropGroups
        )
    }
}