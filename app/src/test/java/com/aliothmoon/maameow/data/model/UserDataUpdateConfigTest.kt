package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.repository.DepotSnapshot
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.data.repository.OperBoxSnapshot
import com.aliothmoon.maameow.domain.models.PlanSideTask
import com.aliothmoon.maameow.domain.models.UserDataUpdateTriggerInterval
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataUpdateConfigTest {

    private fun ctx(
        operSync: Long = 0L,
        depotSync: Long = 0L,
        operBoxUseYituliuApi: Boolean = false,
    ): TaskParamContext {
        val operRepo = mockk<OperBoxRepository> {
            every { snapshot } returns MutableStateFlow(OperBoxSnapshot(syncTimeMillis = operSync))
        }
        val depotRepo = mockk<DepotRepository> {
            every { snapshot } returns MutableStateFlow(DepotSnapshot(syncTimeMillis = depotSync))
        }
        return testTaskParamContext(
            node = testTaskChainNode(name = "同步数据"),
            operBoxRepository = operRepo,
            depotRepository = depotRepo,
            operBoxUseYituliuApi = operBoxUseYituliuApi,
        )
    }

    @Test
    fun bothSwitchesOff_producesNothing() {
        val result = UserDataUpdateConfig(updateOperBox = false, updateDepot = false)
            .toTaskParams(ctx())
        assertTrue(result.isEmpty())
    }

    @Test
    fun everyTime_bothOn_ordersOperBoxThenDepot() {
        val result = UserDataUpdateConfig().toTaskParams(ctx())
        assertEquals(
            listOf(MaaTaskType.OPER_BOX, MaaTaskType.DEPOT),
            result.map { it.type },
        )
        assertEquals(
            listOf(
                uiTextOf(R.string.runlog_task_with_detail, "同步数据", uiTextOf(R.string.maa_oper_box)),
                uiTextOf(R.string.runlog_task_with_detail, "同步数据", uiTextOf(R.string.maa_depot)),
            ),
            result.map { it.logName },
        )
    }

    @Test
    fun onlyDepot_producesDepotOnly() {
        val result = UserDataUpdateConfig(updateOperBox = false, updateDepot = true)
            .toTaskParams(ctx())
        assertEquals(listOf(MaaTaskType.DEPOT), result.map { it.type })
    }

    @Test
    fun daily_recentSync_skips() {
        val now = System.currentTimeMillis()
        val result = UserDataUpdateConfig(
            triggerInterval = UserDataUpdateTriggerInterval.DAILY,
        ).toTaskParams(ctx(operSync = now, depotSync = now))
        assertTrue(result.isEmpty())
    }

    @Test
    fun operBoxViaYituliu_registersSideTaskInsteadOfCoreTask() {
        val context = ctx(operBoxUseYituliuApi = true)
        val result = UserDataUpdateConfig().toTaskParams(context)
        assertEquals(listOf(MaaTaskType.DEPOT), result.map { it.type })
        assertEquals(listOf(PlanSideTask.OPER_BOX_YITULIU), context.sideTasks)
    }

    @Test
    fun operBoxViaYituliu_notDue_registersNothing() {
        val context = ctx(operSync = System.currentTimeMillis(), operBoxUseYituliuApi = true)
        UserDataUpdateConfig(updateDepot = false, triggerInterval = UserDataUpdateTriggerInterval.DAILY)
            .toTaskParams(context)
        assertTrue(context.sideTasks.isEmpty())
    }
}
