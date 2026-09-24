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

class MallVisitFriendsOnceADayTest {
    private val today = LocalDate.of(2077, 1, 1)

    @Test
    fun oldConfigKeepsVisitingEveryRun() {
        val config = Json.decodeFromString<MallConfig>("""{"visitFriends":true}""")
        assertFalse(config.visitFriendsOnceADay)
        assertTrue(config.copy(visitFriendsLastDate = today.toString()).isVisitFriendsAvailable(today))
    }

    @Test
    fun completionSurvivesSerializationAndResetsNextDay() {
        val config = MallConfig(visitFriendsOnceADay = true, visitFriendsLastDate = today.toString())
        val restored = Json.decodeFromString<MallConfig>(Json.encodeToString(config))
        assertFalse(restored.isVisitFriendsAvailable(today))
        assertFalse(restored.isVisitFriendsAvailable(today.minusDays(1)))
        assertTrue(restored.isVisitFriendsAvailable(today.plusDays(1)))
    }

    @Test
    fun noSuccessAllowsRetryAndParentSwitchStillApplies() {
        val config = MallConfig(visitFriendsOnceADay = true)
        assertTrue(config.isVisitFriendsAvailable(today))
        assertTrue(config.copy(visitFriendsLastDate = "invalid").isVisitFriendsAvailable(today))
        assertFalse(config.copy(visitFriends = false).isVisitFriendsAvailable(today))
        assertFalse(config.copy(visitFriends = false, visitFriendsOnceADay = false)
            .isVisitFriendsAvailable(today))
    }

    @Test
    fun completedVisitDoesNotDisableCreditFightOrShopping() {
        val serverToday = ServerTimezone.getYjDate("Official")
        val config = MallConfig(visitFriendsOnceADay = true, creditFight = true,
            visitFriendsLastDate = serverToday.toString())
        val ctx = testTaskParamContext(chainAllowsCreditFight = true)
        val params = Json.parseToJsonElement(config.toTaskParams(ctx).single().params).jsonObject
        // 恰好跨过服务器凌晨 4 点就换日了，本次结果不作数
        assumeTrue(ServerTimezone.getYjDate("Official") == serverToday)
        assertEquals("false", params.getValue("visit_friends").jsonPrimitive.content)
        assertEquals("true", params.getValue("credit_fight").jsonPrimitive.content)
        assertEquals("true", params.getValue("shopping").jsonPrimitive.content)
    }
}
