package com.aliothmoon.maameow.domain.models

import androidx.annotation.StringRes
import com.aliothmoon.maameow.R

/** 任务链里不经 Core 的旁路任务，和主任务并行跑，不占任务位 */
enum class PlanSideTask(@StringRes val runningRes: Int) {
    /** 干员识别改从一图流 OpenAPI 拉取 */
    OPER_BOX_YITULIU(R.string.oper_box_yituliu_fetching),
}
