package com.aliothmoon.maameow.maa.callback

import android.content.Context
import android.content.res.Resources
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/** 代理指挥被锁：普通关卡 UsePrts 点满仍未勾上，剿灭命中 UnableToAgent2 */
class SubTaskHandlerPrtsLockedTest {
    private val pkg = "com.aliothmoon.maameow"
    private val resources = mockk<Resources>()
    private val context = mockk<Context> {
        every { resources } returns this@SubTaskHandlerPrtsLockedTest.resources
        every { packageName } returns pkg
    }
    private val logger = mockk<MaaSessionLogger>(relaxed = true)
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
        depotRepository = mockk(relaxed = true),
    )

    @Before
    fun setUp() {
        MaaStringRes.clearCacheForTest()
        every { resources.getIdentifier(any(), "string", pkg) } returns 0
        every { resources.getString(R.string.fight_prts_locked) } returns STAGE_LOCKED
        every { resources.getString(R.string.fight_annihilation_prts_locked) } returns ANNIHILATION_LOCKED
    }

    // 字段取自真机 asst.log
    private fun error(preTask: String) = handler.onSubTaskError(
        JSONObject.of(
            "taskchain", "Fight",
            "class", "asst::ProcessTask",
            "subtask", "ProcessTask",
            "pre_task", preTask,
            "details", JSONObject(),
        )
    )

    private fun start(task: String, preTask: String) = handler.onSubTaskStart(
        JSONObject.of(
            "taskchain", "Fight",
            "class", "asst::ProcessTask",
            "subtask", "ProcessTask",
            "pre_task", preTask,
            "details", JSONObject.of("task", task, "exec_times", 1),
        )
    )

    @Test
    fun usePrtsExhausted_reportsLocked() {
        error("Fight@UsePrts")
        error("UsePrts")
        verify(exactly = 2) { logger.append(STAGE_LOCKED, LogLevel.ERROR) }
    }

    @Test
    fun stageQueueUsePrts_leftToCore() {
        error("StageQueue@UsePrts")
        verify(exactly = 0) { logger.append(STAGE_LOCKED, any()) }
    }

    @Test
    fun otherProcessTaskError_noHint() {
        error("Fight@GoLastBattle")
        verify(exactly = 0) { logger.append(STAGE_LOCKED, any()) }
    }

    @Test
    fun annihilationUnableToAgent_reportsLocked() {
        start("UnableToAgent2", "Annihilation")
        verify(exactly = 1) { logger.append(ANNIHILATION_LOCKED, LogLevel.ERROR) }
    }

    @Test
    fun resourceStageFallback_noHint() {
        start("UnableToAgent2", "CE-6")
        verify(exactly = 0) { logger.append(ANNIHILATION_LOCKED, any()) }
    }

    private companion object {
        const val STAGE_LOCKED = "代理被锁"
        const val ANNIHILATION_LOCKED = "剿灭被锁"
    }
}
