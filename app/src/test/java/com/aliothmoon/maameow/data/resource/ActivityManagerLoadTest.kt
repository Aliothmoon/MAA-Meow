package com.aliothmoon.maameow.data.resource

import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/** 同一份 StageActivityV2.json 按客户端取各自那段，切客户端不残留上一个的活动 */
class ActivityManagerLoadTest {

    private val manager = ActivityManager(
        context = mockk(relaxed = true),
        chainState = mockk(relaxed = true),
        itemHelper = mockk(relaxed = true),
    )

    private fun open(stage: String) = manager.isStageOpen(stage, DayOfWeek.MONDAY)

    @Test
    fun switchingClientProjectsItsOwnSection() {
        manager.load("YoStarJP", JSON)
        assertFalse(open("YW-6"))

        manager.load("Official", JSON)
        assertTrue(open("YW-6"))
    }

    @Test
    fun missingSectionDropsPreviousClientStages() {
        manager.load("Official", JSON)
        manager.load("YoStarKR", JSON)

        assertFalse(open("YW-6"))
        assertTrue(manager.activityStages.value.isEmpty())
    }

    @Test
    fun noCacheYet_permanentStagesStillOpen() {
        manager.load("Official", null)

        assertTrue(open("1-7"))
        assertFalse(open("YW-6"))
    }

    private companion object {
        val JSON = """
            {
              "Official": {
                "sideStoryStage": {
                  "YW": {
                    "Activity": {
                      "Tip": "", "StageName": "测试活动", "TimeZone": 8,
                      "UtcStartTime": "2000/01/01 00:00:00", "UtcExpireTime": "2099/01/01 00:00:00"
                    },
                    "Stages": [{ "Display": "YW-6", "Value": "YW-6", "Drop": "30063" }]
                  }
                }
              },
              "YoStarJP": { "sideStoryStage": {} }
            }
        """.trimIndent()
    }
}
