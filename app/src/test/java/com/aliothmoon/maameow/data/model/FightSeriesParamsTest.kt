package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.domain.enums.UiUsageConstants
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 刷理智代理倍率下发契约（对齐 WPF 6.16 SeriesList / Serialize）。
 * 通过真实 [FightConfig.toTaskParams] 校验 series 原样写入，不再做 SeriesLock 钳制。
 */
class FightSeriesParamsTest {

    @Test
    fun seriesOptions_matchUpstreamSeriesList() {
        assertEquals(
            listOf(0, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, -1),
            UiUsageConstants.seriesOptions.map { it.first },
        )
    }

    @Test
    fun toTaskParams_emitsConfiguredSeries_includingHighMultipliers() {
        listOf(0, 1, 6, 10, -1).forEach { series ->
            assertEquals(
                "series=$series",
                series,
                seriesOf(FightConfig(stage1 = "1-7", series = series)),
            )
        }
    }

    @Test
    fun toTaskParams_alwaysIncludesSeriesField() {
        val params = FightConfig(stage1 = "1-7", series = 10)
            .toTaskParams(testTaskParamContext(activityManager = alwaysOpenActivityManager()))
            .single()
            .params
        assertTrue(Json.parseToJsonElement(params).jsonObject.containsKey("series"))
    }

    private fun seriesOf(config: FightConfig): Int {
        val params = config
            .toTaskParams(testTaskParamContext(activityManager = alwaysOpenActivityManager()))
            .single()
            .params
        return Json.parseToJsonElement(params).jsonObject["series"]!!.jsonPrimitive.content.toInt()
    }
}
