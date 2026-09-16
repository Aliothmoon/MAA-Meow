package com.aliothmoon.maameow.domain.launch

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.domain.models.PlanSideTask
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.domain.service.OperBoxYituliuSync
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanSideTaskRunnerTest {

    private val sync = mockk<OperBoxYituliuSync>()
    private val sessionLogger = mockk<MaaSessionLogger>(relaxed = true)
    private val context = mockk<Context> {
        every { getString(any()) } answers { "s" + firstArg<Int>() }
        every { getString(any(), *anyVararg()) } answers { "s" + firstArg<Int>() }
    }

    private fun TestScope.runner() =
        PlanSideTaskRunner(context, sync, sessionLogger, backgroundScope)

    @Test
    fun successIsLoggedAndReported() = runTest {
        coEvery { sync.sync() } returns OperBoxYituliuSync.Result.Success(synced = 3, skipped = 0)

        val outcome = runner().runWithoutCore(listOf(PlanSideTask.OPER_BOX_YITULIU))

        assertTrue(outcome!!.ok)
        assertEquals(uiTextOf(R.string.oper_box_yituliu_done, 3), outcome.message)
        // 纯旁路没有会话，入队的日志没人排空，必须走 appendAndWait
        coVerify { sessionLogger.appendAndWait("s" + R.string.oper_box_yituliu_fetching) }
        coVerify { sessionLogger.appendAndWait(any<String>(), LogLevel.SUCCESS) }
    }

    @Test
    fun skippedOperatorsAreReported() = runTest {
        coEvery { sync.sync() } returns OperBoxYituliuSync.Result.Success(synced = 425, skipped = 2)

        val outcome = runner().runWithoutCore(listOf(PlanSideTask.OPER_BOX_YITULIU))

        assertEquals(uiTextOf(R.string.oper_box_yituliu_done_skipped, 425, 2), outcome!!.message)
    }

    @Test
    fun failureReasonIsReported() = runTest {
        val reason = uiTextOf(R.string.oper_box_yituliu_token_empty)
        coEvery { sync.sync() } returns OperBoxYituliuSync.Result.Failed(reason)

        val outcome = runner().runWithoutCore(listOf(PlanSideTask.OPER_BOX_YITULIU))

        assertFalse(outcome!!.ok)
        assertEquals(reason, outcome.message)
        coVerify { sessionLogger.appendAndWait(any<String>(), LogLevel.ERROR) }
    }

    @Test
    fun syncCrashIsReportedAsInternalError() = runTest {
        coEvery { sync.sync() } throws IllegalStateException("boom")

        val outcome = runner().runWithoutCore(listOf(PlanSideTask.OPER_BOX_YITULIU))

        assertFalse(outcome!!.ok)
        assertEquals(uiTextOf(R.string.oper_box_yituliu_internal_error), outcome.message)
    }

    @Test
    fun noSideTaskReportsNothing() = runTest {
        assertNull(runner().runWithoutCore(emptyList()))
    }

    @Test
    fun startAndResultAreBothNotified() = runTest {
        coEvery { sync.sync() } returns OperBoxYituliuSync.Result.Success(synced = 1, skipped = 0)
        val notified = mutableListOf<UiText>()

        runner().runWithoutCore(listOf(PlanSideTask.OPER_BOX_YITULIU)) { notified += it }

        assertEquals(
            listOf(
                uiTextOf(R.string.oper_box_yituliu_fetching),
                uiTextOf(R.string.oper_box_yituliu_done, 1),
            ),
            notified,
        )
    }
}
