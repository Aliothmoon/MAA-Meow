package com.aliothmoon.maameow.maa.callback

import android.content.Context
import android.content.res.Resources
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.MaaNotificationCenter
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/** 上游 v6.17.2 材料合成插件的 SubTaskExtraInfo 消息组，对齐 WPF ProcMaterialSynthesisMsg */
class SubTaskHandlerMaterialSynthesisTest {

    private val pkg = "com.aliothmoon.maameow"
    private val resources: Resources = mockk()
    private val context: Context = mockk {
        every { resources } returns this@SubTaskHandlerMaterialSynthesisTest.resources
        every { packageName } returns pkg
    }
    private val sessionLogger: MaaSessionLogger = mockk(relaxed = true)

    private val handler = SubTaskHandler(
        applicationContext = context,
        statusTracker = mockk(relaxed = true),
        sessionLogger = sessionLogger,
        copilotRuntimeStateStore = mockk(relaxed = true),
        resourceDataManager = mockk<ResourceDataManager>(relaxed = true),
        toolboxResultCollector = mockk(relaxed = true),
        notificationCenter = mockk<MaaNotificationCenter>(relaxed = true),
        chainState = mockk<TaskChainState>(relaxed = true),
        activityManager = mockk<ActivityManager>(relaxed = true),
        achievementRepository = mockk<AchievementRepository>(relaxed = true),
        depotRepository = mockk<DepotRepository>(relaxed = true),
    )

    @Before
    fun setUp() {
        every { resources.getString(R.string.material_synthesis_log_start) } returns "开始自动合成材料"
        every { resources.getString(R.string.material_synthesis_log_done) } returns "材料合成完成"
        every { resources.getString(R.string.material_synthesis_log_material, *anyVararg()) } answers {
            val a = secondArg<Array<Any>>()
            "当前材料：${a[0]}，还需合成 ${a[1]} 个（第 ${a[2]} 层）"
        }
        every {
            resources.getString(R.string.material_synthesis_log_ingredient_unavailable, *anyVararg())
        } answers {
            val a = secondArg<Array<Any>>()
            "${a[0]} 的第 ${a[1]} 种下级材料无法合成"
        }
        every { resources.getString(R.string.material_synthesis_log_failed, *anyVararg()) } answers {
            "材料合成停止：${secondArg<Array<Any>>()[0]}"
        }
        every {
            resources.getString(R.string.material_synthesis_reason_operator_unavailable)
        } returns "没有可用的满心情加工站干员"
        every { resources.getString(R.string.material_synthesis_reason_unknown) } returns "未知错误"
    }

    private fun extra(what: String, details: JSONObject? = null) = handler.onSubTaskExtraInfo(
        JSONObject.of("taskchain", "Custom", "what", what, "details", details)
    )

    @Test
    fun start_logsInfo() {
        extra("MaterialSynthesisStart")
        verify { sessionLogger.append("开始自动合成材料", LogLevel.INFO) }
    }

    @Test
    fun material_depthIsOneBased() {
        extra("MaterialSynthesisMaterial", JSONObject.of("material", "聚酸酯", "count", 3, "depth", 0))
        verify { sessionLogger.append("当前材料：聚酸酯，还需合成 3 个（第 1 层）", LogLevel.INFO) }
    }

    @Test
    fun ingredientUnavailable_logsWarning() {
        extra("MaterialSynthesisIngredientUnavailable", JSONObject.of("material", "聚酸酯", "ingredient", 2))
        verify { sessionLogger.append("聚酸酯 的第 2 种下级材料无法合成", LogLevel.WARNING) }
    }

    @Test
    fun completed_logsSuccess() {
        extra("MaterialSynthesisCompleted")
        verify { sessionLogger.append("材料合成完成", LogLevel.SUCCESS) }
    }

    @Test
    fun failed_mapsKnownReason() {
        extra("MaterialSynthesisFailed", JSONObject.of("result", "operator_unavailable"))
        verify { sessionLogger.append("材料合成停止：没有可用的满心情加工站干员", LogLevel.ERROR) }
    }

    @Test
    fun failed_unknownReasonFallsBack() {
        extra("MaterialSynthesisFailed", JSONObject.of("result", "something_new"))
        verify { sessionLogger.append("材料合成停止：未知错误", LogLevel.ERROR) }
    }
}
