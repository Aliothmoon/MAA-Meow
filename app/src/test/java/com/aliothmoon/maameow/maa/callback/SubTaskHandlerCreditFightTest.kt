package com.aliothmoon.maameow.maa.callback

import android.content.Context
import android.content.res.Resources
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ServerTimezone
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class SubTaskHandlerCreditFightTest {
    private val resources = mockk<Resources>(relaxed = true)
    private val context = mockk<Context> {
        every { resources } returns this@SubTaskHandlerCreditFightTest.resources
        every { packageName } returns "com.aliothmoon.maameow"
    }
    private val chainState = mockk<TaskChainState>(relaxed = true) {
        every { clientType } returns "Official"
        every { lastUsedClientType } returns null
    }
    private val officialDate = LocalDate.of(2077, 1, 2)
    private val enDate = LocalDate.of(2077, 1, 1)
    private val tracker = mockk<TaskChainStatusTracker> {
        every { getNodeId(325) } returns "mall-node-114514"
        every { getNodeId(799) } returns null
    }
    private val handler = SubTaskHandler(
        applicationContext = context,
        sessionLogger = mockk(relaxed = true),
        copilotRuntimeStateStore = mockk(relaxed = true),
        resourceDataManager = mockk(relaxed = true),
        toolboxResultCollector = mockk(relaxed = true),
        notificationCenter = mockk(relaxed = true),
        chainState = chainState,
        activityManager = mockk(relaxed = true),
        achievementRepository = mockk(relaxed = true),
        depotRepository = mockk(relaxed = true),
        statusTracker = tracker,
    )

    @Before
    fun setUp() {
        MaaStringRes.clearCacheForTest()
        // 固定各服务器日期，断言不受真实时钟跨日影响
        mockkObject(ServerTimezone)
        every { ServerTimezone.getYjDate("Official") } returns officialDate
        every { ServerTimezone.getYjDate("YoStarEN") } returns enDate
    }

    @After
    fun tearDown() = unmockkObject(ServerTimezone)

    private fun completed(task: String, taskId: Int = 325, chain: String = "Mall") =
        JSONObject.of("subtask", "ProcessTask", "taskchain", chain, "taskid", taskId,
            "details", JSONObject.of("task", task))

    @Test
    fun successfulCreditFightRecordsItsNodeAndServerDate() {
        handler.onSubTaskCompleted(completed("StageDrops-Stars-3"))
        coVerify(timeout = 3000, exactly = 1) {
            chainState.recordCreditFightCompleted("mall-node-114514", officialDate.toString())
        }
    }

    @Test
    fun visitCompletionCallbacksRecordServerDate() {
        handler.onSubTaskCompleted(completed("VisitLimited"))
        handler.onSubTaskCompleted(completed("VisitNextBlack"))
        coVerify(timeout = 3000, exactly = 2) {
            chainState.recordVisitFriendsCompleted("mall-node-114514", officialDate.toString())
        }
        coVerify(exactly = 0) { chainState.recordCreditFightCompleted(any(), any()) }
    }

    @Test
    fun completionDateFollowsRunningSessionNotCurrentProfile() {
        // 美服会话运行中切到了国服 Profile：会话是 YoStarEN，当前 Profile 是 Official
        every { chainState.lastUsedClientType } returns "YoStarEN"
        handler.onSubTaskCompleted(completed("StageDrops-Stars-3"))
        handler.onSubTaskCompleted(completed("VisitLimited"))
        coVerify(timeout = 3000, exactly = 1) {
            chainState.recordCreditFightCompleted("mall-node-114514", enDate.toString())
            chainState.recordVisitFriendsCompleted("mall-node-114514", enDate.toString())
        }
    }

    @Test
    fun unrelatedVisitCallbacksDoNotRecord() {
        handler.onSubTaskCompleted(completed("VisitLimited", chain = "Fight"))
        handler.onSubTaskCompleted(completed("VisitNextBlack", taskId = 799))
        coVerify(exactly = 0) { chainState.recordVisitFriendsCompleted(any(), any()) }
    }

    @Test
    fun otherTasksAndUnregisteredNodesDoNotRecordCompletion() {
        handler.onSubTaskCompleted(completed("VisitLimited"))
        handler.onSubTaskCompleted(completed("StageDrops-Stars-3", chain = "Fight"))
        handler.onSubTaskCompleted(completed("StageDrops-Stars-3", taskId = 799))
        coVerify(exactly = 0) { chainState.recordCreditFightCompleted(any(), any()) }
    }
}
