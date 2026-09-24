package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.data.resource.ServerTimezone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate

class MallCreditFightOnceADayTest {
    private val today = LocalDate.of(2077, 1, 1)

    @Test
    fun oldConfigDefaultsToOnceADayAndCanRun() {
        val config = Json.decodeFromString<MallConfig>("""{"creditFight":true}""")
        assertTrue(config.creditFightOnceADay)
        assertTrue(config.isCreditFightAvailable(today))
    }

    @Test
    fun successBlocksSameDayAndAllowsNextDay() {
        val config = MallConfig(creditFight = true, creditFightLastDate = today.toString())
        assertFalse(config.isCreditFightAvailable(today))
        assertTrue(config.isCreditFightAvailable(today.plusDays(1)))
        assertFalse(config.isCreditFightAvailable(today.minusDays(1)))
        val restored = Json.decodeFromString<MallConfig>(Json.encodeToString(config))
        assertFalse(restored.isCreditFightAvailable(today))
    }

    @Test
    fun disabledLimitAllowsRepeatButDoesNotEnableCreditFight() {
        val config = MallConfig(creditFight = true, creditFightOnceADay = false,
            creditFightLastDate = today.toString())
        assertTrue(config.isCreditFightAvailable(today))
        assertFalse(config.copy(creditFight = false).isCreditFightAvailable(today))
        assertTrue(config.copy(creditFightOnceADay = true, creditFightLastDate = "invalid")
            .isCreditFightAvailable(today))
    }

    @Test
    fun completedTodayDisablesOnlyCreditFightInCoreParams() {
        val serverToday = ServerTimezone.getYjDate("Official")
        val config = MallConfig(creditFight = true, creditFightLastDate = serverToday.toString())
        val ctx = testTaskParamContext(chainAllowsCreditFight = true)
        val params = Json.parseToJsonElement(config.toTaskParams(ctx).single().params).jsonObject
        // 恰好跨过服务器凌晨 4 点就换日了，本次结果不作数
        assumeTrue(ServerTimezone.getYjDate("Official") == serverToday)
        assertEquals("false", params.getValue("credit_fight").jsonPrimitive.content)
        assertEquals("true", params.getValue("visit_friends").jsonPrimitive.content)
        assertEquals("true", params.getValue("shopping").jsonPrimitive.content)
        assertFalse("formation_index" in params)
    }
}
