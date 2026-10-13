package com.aliothmoon.maameow.maa.callback

import android.content.Context
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.model.OperProgressConfig
import com.aliothmoon.maameow.data.model.OperProgressPlan
import com.aliothmoon.maameow.data.model.OperProgressResult
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.maa.task.TaskSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OperProgressHandlerTest {
    private data class Write(val nodeId: String, val results: List<OperProgressResult>, val remove: Boolean)
    private class Fixture(scope: CoroutineScope, language: String = "zh-cn") {
        val context = mockk<Context>()
        val appSettings = mockk<AppSettingsManager> { every { displayLanguage } returns language }
        val logger = mockk<MaaSessionLogger>()
        val manager = mockk<ResourceDataManager>()
        val state = mockk<TaskChainState>()
        val tracker = TaskChainStatusTracker()
        val writes = mutableListOf<Write>()
        val logs = mutableListOf<Pair<String, LogLevel>>()
        var config = OperProgressConfig(listOf(
            OperProgressPlan(id = "chen", name = "陈", role = "Warrior", skillMastery = listOf(0, 0, 3)),
        ))
        val handler: OperProgressHandler

        init {
            every { context.packageName } returns "com.aliothmoon.maameow"
            every { context.resources } returns mockk(relaxed = true)
            every { context.getString(any()) } answers { "label-${firstArg<Int>()}" }
            every { context.getString(any(), *anyVararg()) } answers {
                "format-${firstArg<Int>()}:" + secondArg<Array<Any>>().joinToString("|")
            }
            every { manager.getLocalizedCharacterName(any<String>(), any()) } answers { firstArg() }
            every { logger.append(any(), any()) } answers { logs += firstArg<String>() to secondArg<LogLevel>() }
            coEvery { state.recordOperProgress(any(), any(), any()) } coAnswers {
                val write = Write(firstArg(), secondArg(), thirdArg())
                writes += write
                config = config.applyResults(write.results, write.remove)
            }
            tracker.register(11, "OperProgress", TaskSlot("original-node"))
            handler = OperProgressHandler(context, logger, tracker, state, manager, appSettings, scope)
        }
    }

    private fun array(vararg values: Any): JSONArray = JSONArray().apply { addAll(values.toList()) }
    private fun detail(
        reason: String,
        status: String = "Success",
        mastery: JSONArray? = array(0, 0, 3),
        taskId: Int = 11,
    ): JSONObject = JSONObject.of(
        "taskid", taskId, "taskchain", "OperProgress", "what", "OperProgressDetail",
        "details", JSONObject.of("name", "陈", "role", "Warrior", "result", status,
            "result_detail", reason).apply { if (mastery != null) put("skill_mastery", mastery) },
    )

    private fun summary(finished: JSONArray = array()): JSONObject = JSONObject.of(
        "taskid", 11, "taskchain", "OperProgress", "what", "OperProgressSummary",
        "details", JSONObject.of("success", 1, "failed", 0, "skipped", 0, "finished", finished),
    )

    @Test
    fun finalMasteryStartDoesNotRemovePlan_evenWhenSummaryListsItAsFinished() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.handler.onExtraInfo(detail("Completed"))
        fixture.handler.onExtraInfo(summary(array(JSONObject.of("name", "陈", "role", "Warrior", "skill_mastery", array(0, 0, 3)))))
        runCurrent()
        assertTrue(fixture.writes.first().results.single().trainingStarted)
        assertEquals(listOf(0, 0, 3), fixture.config.plans.single().skillMastery)
        assertEquals(3, fixture.config.plans.single().training!!.level)
        assertTrue(fixture.logs.first().first.contains("label-${R.string.oper_progress_result_training}"))
        assertEquals(LogLevel.INFO, fixture.logs.first().second)
    }

    @Test
    fun laterRecognitionOfReachedTargetRemovesPlan() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.handler.onExtraInfo(detail("AlreadySatisfied"))
        fixture.handler.onExtraInfo(summary())
        runCurrent()
        assertTrue(fixture.config.plans.isEmpty())
        assertEquals(LogLevel.SUCCESS, fixture.logs.first().second)
    }

    @Test
    fun prerequisiteTrainingRetainsFinalGoal() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.handler.onExtraInfo(detail("PrerequisiteTraining", mastery = array(0, 0, 1)))
        runCurrent()
        assertEquals(listOf(0, 0, 3), fixture.config.plans.single().skillMastery)
        assertEquals(1, fixture.config.plans.single().training!!.level)
    }

    @Test
    fun busyAndFailureCallbacksDoNotClearTargets_andExplainTheReason() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.handler.onExtraInfo(detail("TrainingRoomBusy", "Skipped", mastery = null))
        fixture.handler.onExtraInfo(detail("ResourceInsufficient", "Failed", mastery = null))
        runCurrent()
        assertTrue(fixture.writes.isEmpty())
        assertEquals(listOf(LogLevel.WARNING, LogLevel.ERROR), fixture.logs.map { it.second })
        assertTrue(fixture.logs[0].first.contains("label-${R.string.oper_progress_result_busy}"))
        assertTrue(fixture.logs[1].first.contains("label-${R.string.oper_progress_result_resources}"))
    }

    @Test
    fun updatesCaptureOriginalNodeBeforeTrackerIsCleared_andStayOrdered() = runTest {
        val fixture = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        coEvery { fixture.state.recordOperProgress(any(), any(), any()) } coAnswers {
            if (!thirdArg<Boolean>()) gate.await()
            fixture.writes += Write(firstArg(), secondArg(), thirdArg())
        }
        fixture.handler.onExtraInfo(detail("AlreadySatisfied"))
        fixture.handler.onExtraInfo(summary())
        fixture.tracker.clear()
        runCurrent()
        assertTrue(fixture.writes.isEmpty())
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(false, true), fixture.writes.map { it.remove })
        assertEquals(listOf("original-node", "original-node"), fixture.writes.map { it.nodeId })
    }

    @Test
    fun unknownTaskIdCannotModifyAnotherPlan() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.handler.onExtraInfo(detail("AlreadySatisfied", taskId = 999))
        runCurrent()
        assertTrue(fixture.writes.isEmpty())
        assertTrue(fixture.config.plans.single().hasTargets)
    }

    @Test
    fun summaryRecoversPromotionAndSkillUpdatesWithoutClearingMastery() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.config = fixture.config.copy(plans = fixture.config.plans.map { it.copy(elite = 2, skillLevel = 7) })
        fixture.handler.onExtraInfo(summary(array(JSONObject.of(
            "name", "陈", "role", "Warrior", "elite", 2, "skill_level", 7, "skill_mastery", array(0, 0, 3),
        ))))
        runCurrent()
        assertEquals(0, fixture.config.plans.single().elite)
        assertEquals(0, fixture.config.plans.single().skillLevel)
        assertEquals(listOf(0, 0, 3), fixture.config.plans.single().skillMastery)
    }

    @Test
    fun malformedMasteryCannotBeMistakenForCompletion() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.handler.onExtraInfo(detail("AlreadySatisfied", mastery = array(0, "bad", 3)))
        runCurrent()
        assertTrue(fixture.writes.isEmpty())
        assertTrue(fixture.config.plans.single().hasTargets)
    }

    @Test
    fun subTaskHandlerRoutesOperatorProgressIntoWriteback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(backgroundScope)
        // SubTaskHandler 从 Koin 懒取 OperProgressHandler
        startKoin { modules(module { single { fixture.handler } }) }
        try {
            val subTask = SubTaskHandler(
                applicationContext = fixture.context, sessionLogger = fixture.logger,
                copilotRuntimeStateStore = mockk(relaxed = true), resourceDataManager = fixture.manager,
                toolboxResultCollector = mockk(relaxed = true), notificationCenter = mockk(relaxed = true),
                chainState = fixture.state, activityManager = mockk(relaxed = true),
                achievementRepository = mockk(relaxed = true), depotRepository = mockk(relaxed = true),
                statusTracker = fixture.tracker,
            )
            subTask.onSubTaskExtraInfo(detail("AlreadySatisfied"))
            runCurrent()
            assertEquals("original-node", fixture.writes.single().nodeId)
            assertEquals(listOf(0, 0, 3), fixture.writes.single().results.single().skillMastery)
        } finally {
            stopKoin()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun operatorNameFollowsAppDisplayLanguage() = runTest {
        // 应用语言标签是 "en"，取系统 locale 拼不出 "en-us"
        val fixture = Fixture(backgroundScope, language = "en-us")
        every { fixture.manager.getLocalizedCharacterName("陈", "en-us") } returns "Ch'en"
        fixture.handler.onExtraInfo(detail("AlreadySatisfied"))
        assertTrue(fixture.logs.single().first.contains("Ch'en"))
    }
}
