package com.aliothmoon.maameow.maa.callback

import android.content.Context
import android.content.res.Resources
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.repository.DepotSnapshot
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/** 仓库识别落库后，运行日志只报与上次识别相比的变化数 */
class SubTaskHandlerDepotDeltaTest {
    private val resources = mockk<Resources>()
    private val context = mockk<Context> {
        every { resources } returns this@SubTaskHandlerDepotDeltaTest.resources
        every { packageName } returns "com.aliothmoon.maameow"
    }
    private val logger = mockk<MaaSessionLogger>(relaxed = true)
    private val collector = mockk<ToolboxResultCollector>()
    private val handler = SubTaskHandler(
        applicationContext = context,
        statusTracker = mockk(relaxed = true),
        sessionLogger = logger,
        copilotRuntimeStateStore = mockk(relaxed = true),
        resourceDataManager = mockk(relaxed = true),
        toolboxResultCollector = collector,
        notificationCenter = mockk(relaxed = true),
        chainState = mockk(relaxed = true),
        activityManager = mockk(relaxed = true),
        achievementRepository = mockk(relaxed = true),
        depotRepository = mockk(relaxed = true),
    )

    @Before
    fun setUp() {
        every { resources.getString(R.string.runlog_depot_delta_summary, *anyVararg()) } answers {
            "仓库有 ${secondArg<Array<Any>>()[1]} 种物品变化"
        }
        every { resources.getString(R.string.runlog_depot_delta_none, *anyVararg()) } returns "仓库无变化"
    }

    private fun depotDone() = handler.onSubTaskExtraInfo(
        JSONObject.of("taskchain", "Depot", "details", JSONObject.of("done", true))
    )

    private fun stored(snapshot: DepotSnapshot?) {
        every { collector.onDepotResult(any()) } returns snapshot
    }

    private fun scans(prev: Map<String, Int>, cur: Map<String, Int>) = stored(
        DepotSnapshot(
            items = cur,
            syncTimeMillis = 2_000L,
            scanItems = cur,
            prevScanItems = prev,
            prevSyncTimeMillis = 1_000L,
        )
    )

    @Test
    fun changes_logsCountOnly() {
        scans(prev = mapOf("30012" to 100, "2002" to 50), cur = mapOf("30012" to 80, "2002" to 550, "3003" to 1))

        depotDone()

        verify { logger.append("仓库有 3 种物品变化", LogLevel.TRACE) }
    }

    @Test
    fun noChanges_logsSingleLine() {
        scans(prev = mapOf("30012" to 100), cur = mapOf("30012" to 100))

        depotDone()

        verify { logger.append("仓库无变化", LogLevel.TRACE) }
    }

    @Test
    fun noBaseline_logsNothing() {
        stored(DepotSnapshot(items = mapOf("30012" to 1), syncTimeMillis = 2_000L, scanItems = mapOf("30012" to 1)))

        depotDone()

        verify(exactly = 0) { logger.append(any<String>(), any(), any()) }
    }

    @Test
    fun notStored_logsNothing() {
        stored(null)

        depotDone()

        verify(exactly = 0) { logger.append(any<String>(), any(), any()) }
    }
}
