package com.aliothmoon.maameow.maa.callback

import android.content.Context
import android.content.res.Resources
import com.alibaba.fastjson2.JSONObject
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

/** 上游 v6.17.0-beta.9 起 core 的 why 是英文，日志不能再原样透出 */
class SubTaskHandlerWhyLocalizationTest {

    private val pkg = "com.aliothmoon.maameow"
    private val resources: Resources = mockk()
    private val context: Context = mockk {
        every { resources } returns this@SubTaskHandlerWhyLocalizationTest.resources
        every { packageName } returns pkg
    }
    private val sessionLogger: MaaSessionLogger = mockk(relaxed = true)

    private val handler = SubTaskHandler(
        applicationContext = context,
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

    private fun stub(resName: String, id: Int, value: String) {
        every { resources.getIdentifier(resName, "string", pkg) } returns id
        every { resources.getString(id) } returns value
    }

    @Before
    fun setUp() {
        MaaStringRes.clearCacheForTest()
        every { resources.getIdentifier(any(), "string", pkg) } returns 0
        stub("maa_has_returned", 1, "已返回")
        stub("maa_give_up_uploading_penguins", 2, "放弃上传企鹅物流")
        stub("maa_identify_the_mistakes", 3, "识别错误")
        stub("maa_recruit_refresh_limit_reached", 4, "刷新次数达到上限")
        stub("maa_penguin_upload_unknown_stage", 5, "未识别关卡")
    }

    @Test
    fun autoRecruitWhy_isLocalized() {
        handler.onSubTaskError(
            JSONObject.of("subtask", "AutoRecruitTask", "why", "recognition error")
        )

        verify { sessionLogger.append("识别错误, 已返回", LogLevel.ERROR) }
    }

    @Test
    fun recruitRefreshLimitWhy_isLocalized() {
        handler.onSubTaskError(
            JSONObject.of("subtask", "AutoRecruitTask", "why", "refresh count reached the limit")
        )

        verify { sessionLogger.append("刷新次数达到上限, 已返回", LogLevel.ERROR) }
    }

    @Test
    fun penguinUploadWhy_isLocalized() {
        handler.onSubTaskError(
            JSONObject.of("subtask", "ReportToPenguinStats", "why", "UnknownStage")
        )

        verify { sessionLogger.append("未识别关卡, 放弃上传企鹅物流", LogLevel.WARNING) }
    }

    @Test
    fun unmappedWhy_fallsBackToRawValue() {
        handler.onSubTaskError(
            JSONObject.of("subtask", "ReportToPenguinStats", "why", "SomeNewReason")
        )

        verify { sessionLogger.append("SomeNewReason, 放弃上传企鹅物流", LogLevel.WARNING) }
    }
}
