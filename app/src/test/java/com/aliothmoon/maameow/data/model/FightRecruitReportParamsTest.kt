package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.domain.models.DropTarget
import com.aliothmoon.maameow.domain.models.ReportOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FightRecruitReportParamsTest {

    @Test
    fun fight_emitsReportFlagsAndOmitsBlankIds() {
        val json = fightJson(ReportOptions.DEFAULT)
        assertTrue(json["report_to_penguin"]!!.jsonPrimitive.boolean)
        assertTrue(json["report_to_yituliu"]!!.jsonPrimitive.boolean)
        assertEquals("CN", json["server"]!!.jsonPrimitive.content)
        assertNull(json["penguin_id"])
        assertNull(json["yituliu_id"])
    }

    @Test
    fun fight_writesSharedIdWhenPresent() {
        val json = fightJson(
            ReportOptions(true, true, "4242", "JP"),
        )
        assertEquals("4242", json["penguin_id"]!!.jsonPrimitive.content)
        assertEquals("4242", json["yituliu_id"]!!.jsonPrimitive.content)
        assertEquals("JP", json["server"]!!.jsonPrimitive.content)
    }

    @Test
    fun fight_canDisableReporting() {
        val json = fightJson(
            ReportOptions(false, false, "1", "CN"),
        )
        assertFalse(json["report_to_penguin"]!!.jsonPrimitive.boolean)
        assertFalse(json["report_to_yituliu"]!!.jsonPrimitive.boolean)
        assertNull(json["penguin_id"])
        assertNull(json["yituliu_id"])
    }

    @Test
    fun fight_yituliuOnly_writesYituliuId() {
        val json = fightJson(ReportOptions(false, true, "55", "CN"))
        assertNull(json["penguin_id"])
        assertEquals("55", json["yituliu_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun recruit_emitsSameReportFields() {
        val json = Json.parseToJsonElement(
            RecruitConfig().toTaskParams(
                testTaskParamContext(
                    activityManager = alwaysOpenActivityManager(),
                    report = ReportOptions(true, false, "9", "US"),
                ),
            ).single().params,
        ).jsonObject
        assertTrue(json["report_to_penguin"]!!.jsonPrimitive.boolean)
        assertFalse(json["report_to_yituliu"]!!.jsonPrimitive.boolean)
        assertEquals("9", json["penguin_id"]!!.jsonPrimitive.content)
        assertNull(json["yituliu_id"])
        assertEquals("US", json["server"]!!.jsonPrimitive.content)
    }

    @Test
    fun dropTarget_keepsReportFieldsOnRefreshJson() {
        val json = Json.parseToJsonElement(
            DropTarget(
                dropId = "30011",
                dropCount = 10,
                stage = "1-7",
                medicine = 0,
                stone = 0,
                series = 0,
                logLabel = "1",
                report = ReportOptions(true, true, "77", "CN"),
            ).toFightParamsJson(need = 3),
        ).jsonObject
        assertEquals("77", json["penguin_id"]!!.jsonPrimitive.content)
        assertTrue(json["report_to_penguin"]!!.jsonPrimitive.boolean)
    }

    private fun fightJson(report: ReportOptions) = Json.parseToJsonElement(
        FightConfig(stage1 = "1-7").toTaskParams(
            testTaskParamContext(
                activityManager = alwaysOpenActivityManager(),
                report = report,
            ),
        ).single().params,
    ).jsonObject
}
