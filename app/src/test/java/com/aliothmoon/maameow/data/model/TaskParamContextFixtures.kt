package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import io.mockk.every
import io.mockk.mockk

/**
 * 测试用 [TaskParamContext] 工厂。
 *
 * 依赖默认用 relaxed mock；需要特定行为时用 [copy] 或具名参数覆盖。
 * 不给 [TaskParamContext] 本身加默认值 —— 那会在生产路径里滋生「说谎的默认」。
 */
fun testTaskParamContext(
    clientType: String = "Official",
    chainAllowsCreditFight: Boolean = false,
    activityManager: ActivityManager = mockk(relaxed = true),
    depotRepository: DepotRepository = mockk(relaxed = true),
    operBoxRepository: OperBoxRepository = mockk(relaxed = true),
    itemHelper: ItemHelper = mockk(relaxed = true),
    resourceDataManager: ResourceDataManager = mockk(relaxed = true),
): TaskParamContext = TaskParamContext(
    clientType = clientType,
    chainAllowsCreditFight = chainAllowsCreditFight,
    activityManager = activityManager,
    depotRepository = depotRepository,
    operBoxRepository = operBoxRepository,
    itemHelper = itemHelper,
    resourceDataManager = resourceDataManager,
)

/** 始终视为开放的 ActivityManager，适合只关心 JSON 组装的 FightConfig 单测。 */
fun alwaysOpenActivityManager(): ActivityManager = mockk {
    every { isStageOpen(any(), any()) } returns true
    every { isStageOpen(any()) } returns true
    every { getYjDayOfWeek() } returns java.time.DayOfWeek.MONDAY
    every { getMergedStageList(any()) } returns emptyList()
    every { getActivityAwareExpireDays() } returns 0
    every { isActivityOpen() } returns false
    every { isResourceCollectionOpen() } returns false
}
