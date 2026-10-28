package com.aliothmoon.maameow.maa.callback

import android.content.Context
import android.content.res.Resources
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.domain.models.StaleSkippedPlan
import com.aliothmoon.maameow.domain.service.FightDropsRefresher
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/** 仓库识别完成时复查库存保持预检跳过的计划，其他任务链不触发 */
class TaskChainHandlerDepotReviewTest {

    private val pkg = "com.aliothmoon.maameow"
    private val resources: Resources = mockk()
    private val context: Context = mockk {
        every { resources } returns this@TaskChainHandlerDepotReviewTest.resources
        every { packageName } returns pkg
        every { getString(R.string.runlog_depot_plan_stale_skipped, *anyVararg()) } answers {
            secondArg<Array<Any>>().joinToString("|")
        }
    }
    private val sessionLogger: MaaSessionLogger = mockk(relaxed = true)
    private val dropsRefresher: FightDropsRefresher = mockk(relaxed = true) {
        every { reviewSkippedPlans() } returns listOf(StaleSkippedPlan("材料补货", 2, "源岩", 30, 40))
    }
    private val handler = TaskChainHandler(
        applicationContext = context,
        sessionLogger = sessionLogger,
        statusTracker = TaskChainStatusTracker(),
        notificationCenter = mockk(relaxed = true),
        subTaskHandler = mockk(relaxed = true),
        taskChainState = mockk(relaxed = true),
        achievementRepository = mockk(relaxed = true),
        achievementReporter = mockk(relaxed = true),
        dropsRefresher = dropsRefresher,
    )

    @Before
    fun setUp() {
        MaaStringRes.clearCacheForTest()
        every { resources.getIdentifier(any(), "string", pkg) } returns 0
    }

    @After
    fun tearDown() = MaaStringRes.clearCacheForTest()

    @Test
    fun depotCompletion_logsStalePlansAsWarnings() {
        handler.onTaskChainCompleted(JSONObject.of("taskid", 5, "taskchain", "Depot"))

        verify { sessionLogger.append("材料补货|2|源岩|30|40", LogLevel.WARNING) }
    }

    @Test
    fun otherChains_doNotReview() {
        handler.onTaskChainCompleted(JSONObject.of("taskid", 6, "taskchain", "Fight"))

        verify(exactly = 0) { dropsRefresher.reviewSkippedPlans() }
    }
}
