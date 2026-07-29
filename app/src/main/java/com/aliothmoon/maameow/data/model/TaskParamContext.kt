package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.FightDropsRefresher
import com.aliothmoon.maameow.utils.i18n.UiText

/**
 * 展开环境：只读世界状态 + 本趟 [appendLog] / [FightDropsRefresher.stage]。
 * 非值对象；配置类不得反向抓依赖。
 */
class TaskParamContext(
    val node: TaskChainNode,
    val clientType: String,
    val chainAllowsCreditFight: Boolean,
    val activityManager: ActivityManager,
    val depotRepository: DepotRepository,
    val operBoxRepository: OperBoxRepository,
    val itemHelper: ItemHelper,
    val resourceDataManager: ResourceDataManager,
    val dropsRefresher: FightDropsRefresher,
    val logSink: PreflightLogSink,
) {
    fun appendLog(text: UiText, level: LogLevel = LogLevel.INFO) {
        logSink.append(text, level)
    }
}
