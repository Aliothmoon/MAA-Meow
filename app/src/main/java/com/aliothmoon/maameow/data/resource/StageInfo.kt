package com.aliothmoon.maameow.data.resource

import android.content.Context
import androidx.annotation.StringRes
import com.aliothmoon.maameow.R
import java.time.DayOfWeek

/**
 * 关卡信息（UI 使用）
 */
data class StageInfo(
    val stageId: String,       // 内部 ID（如 "a001_01_perm"）
    val code: String,          // 显示代码（如 "GT-1"）
    val apCost: Int = 0,       // 理智消耗
    val openDays: List<DayOfWeek> = emptyList(),  // 开放日期（空表示每天开放）
    val category: StageCategory = StageCategory.OTHER,  // 关卡分类
    @StringRes val tipRes: Int? = null,  // 关卡提示信息（迁移自 WPF TipKey）
    val dropGroups: List<List<String>> = emptyList()  // 芯片本掉落组 (迁移自 WPF DropGroups)
) {
    /** UI 显示名，迁移自 WPF 以关卡代码为 key 的本地化项；没有映射的直接显示代码 */
    fun displayName(context: Context): String =
        displayNameRes(code)?.let(context::getString) ?: code

    /**
     * 检查关卡在指定日期是否开放
     */
    fun isOpenOn(dayOfWeek: DayOfWeek): Boolean {
        return openDays.isEmpty() || dayOfWeek in openDays
    }

    /**
     * 检查关卡今天是否开放（使用鹰角历，04:00 换日）
     * @param clientType 客户端类型，用于确定服务器时区
     */
    fun isOpenToday(clientType: String = "Official"): Boolean {
        return isOpenOn(ServerTimezone.getYjDayOfWeek(clientType))
    }

    companion object {
        /** 自定义剿灭的可选关卡，顺序即选项顺序 */
        val ANNIHILATION_CODES = listOf(
            "Annihilation",
            "Chernobog@Annihilation",
            "LungmenOutskirts@Annihilation",
            "LungmenDowntown@Annihilation",
        )

        /** 常驻与剿灭关卡的显示名资源，所有关卡显示名都经这里兜底 */
        @StringRes
        fun displayNameRes(code: String): Int? = STAGE_DISPLAY_NAMES[code]

        /** 关卡代码到显示名称资源的映射 */
        private val STAGE_DISPLAY_NAMES = mapOf(
            // 资源本
            "CE-6" to R.string.panel_fight_stage_name_ce_6,
            "AP-5" to R.string.panel_fight_stage_name_ap_5,
            "CA-5" to R.string.panel_fight_stage_name_ca_5,
            "LS-6" to R.string.panel_fight_stage_name_ls_6,
            "SK-5" to R.string.panel_fight_stage_name_sk_5,

            // 芯片本
            "PR-A-1" to R.string.panel_fight_stage_name_pr_a_1,
            "PR-A-2" to R.string.panel_fight_stage_name_pr_a_2,
            "PR-B-1" to R.string.panel_fight_stage_name_pr_b_1,
            "PR-B-2" to R.string.panel_fight_stage_name_pr_b_2,
            "PR-C-1" to R.string.panel_fight_stage_name_pr_c_1,
            "PR-C-2" to R.string.panel_fight_stage_name_pr_c_2,
            "PR-D-1" to R.string.panel_fight_stage_name_pr_d_1,
            "PR-D-2" to R.string.panel_fight_stage_name_pr_d_2,

            // 剿灭模式
            "Annihilation" to R.string.panel_fight_annihilation_current,
            "Chernobog@Annihilation" to R.string.panel_fight_annihilation_chernobog,
            "Lungmen@Annihilation" to R.string.panel_fight_annihilation_outskirts,
            "LungmenOutskirts@Annihilation" to R.string.panel_fight_annihilation_outskirts,
            "LungmenDowntown@Annihilation" to R.string.panel_fight_annihilation_downtown,
        )
    }
}
