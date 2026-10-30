package com.aliothmoon.maameow.maa.callback

import android.content.Context
import android.content.res.Resources
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class SubTaskHandlerInfrastRecycleProductTest {
    private val pkg = "com.aliothmoon.maameow"
    private val resources = mockk<Resources>()
    private val context = mockk<Context> {
        every { resources } returns this@SubTaskHandlerInfrastRecycleProductTest.resources
        every { packageName } returns pkg
    }
    private val logger = mockk<MaaSessionLogger>(relaxed = true)
    private val depotRepository = mockk<DepotRepository>(relaxed = true)
    private val handler = SubTaskHandler(
        applicationContext = context,
        statusTracker = mockk(relaxed = true),
        sessionLogger = logger,
        copilotRuntimeStateStore = mockk(relaxed = true),
        resourceDataManager = mockk(relaxed = true),
        toolboxResultCollector = mockk(relaxed = true),
        notificationCenter = mockk(relaxed = true),
        chainState = mockk(relaxed = true),
        activityManager = mockk(relaxed = true),
        achievementRepository = mockk(relaxed = true),
        depotRepository = depotRepository,
    )

    @Before
    fun setUp() {
        MaaStringRes.clearCacheForTest()
        every { resources.getIdentifier(any(), "string", pkg) } returns 0
        every { resources.getIdentifier("maa_infrast_recycle_product", "string", pkg) } returns 1
        every { resources.getString(1) } returns "回收站产出"
    }

    @After
    fun tearDown() = MaaStringRes.clearCacheForTest()

    private fun drop(id: String, name: String, quantity: Int) =
        JSONObject.of("itemId", id, "itemName", name, "quantity", quantity)

    @Test
    fun drops_listedInOrder_withoutTouchingDepot() {
        handler.onSubTaskExtraInfo(
            JSONObject.of(
                "taskchain", "Infrast",
                "class", "asst::ProcessTask",
                "what", "InfrastRecycleProduct",
                "details", JSONObject.of(
                    "drops", JSONArray.of(drop("30012", "固源岩", 3), drop("3003", "赤金", 12)),
                ),
            )
        )

        verify { logger.append("回收站产出\n固源岩 : 3\n赤金 : 12", LogLevel.TRACE) }
        verify(exactly = 0) { depotRepository.merge(any()) }
    }

    @Test
    fun missingFields_fallBackLikeWpf() {
        handler.onSubTaskExtraInfo(
            JSONObject.of(
                "taskchain", "Infrast",
                "class", "asst::ProcessTask",
                "what", "InfrastRecycleProduct",
                "details", JSONObject.of("drops", JSONArray.of(JSONObject.of("itemId", "30012"))),
            )
        )
        handler.onSubTaskExtraInfo(
            JSONObject.of(
                "taskchain", "Infrast",
                "class", "asst::ProcessTask",
                "what", "InfrastRecycleProduct",
            )
        )

        verify { logger.append("回收站产出\n : 0", LogLevel.TRACE) }
        verify { logger.append("回收站产出", LogLevel.TRACE) }
    }
}
