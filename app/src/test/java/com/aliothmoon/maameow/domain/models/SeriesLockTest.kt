package com.aliothmoon.maameow.domain.models

import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.alwaysOpenActivityManager
import com.aliothmoon.maameow.data.model.testTaskParamContext
import com.aliothmoon.maameow.data.resource.ServerTimezone
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SeriesLockTest {

    private val beforeLock = LocalDate.of(2026, 7, 31)
    private val lockDate = LocalDate.of(2026, 8, 1)
    private val afterLock = LocalDate.of(2026, 8, 2)

    @Test
    fun official_notLocked_beforeLockDate() {
        assertFalse(SeriesLock.isLocked("Official", beforeLock))
    }

    @Test
    fun official_locked_onLockDate() {
        assertTrue(SeriesLock.isLocked("Official", lockDate))
    }

    @Test
    fun official_locked_afterLockDate() {
        assertTrue(SeriesLock.isLocked("Official", afterLock))
    }

    @Test
    fun bilibili_locked_onLockDate() {
        assertTrue(SeriesLock.isLocked("Bilibili", lockDate))
    }

    @Test
    fun overseasAndTxwyClients_neverLocked() {
        listOf("YoStarEN", "YoStarJP", "YoStarKR", "txwy").forEach { clientType ->
            assertFalse(clientType, SeriesLock.isLocked(clientType, afterLock))
        }
    }

    @Test
    fun fightConfig_keepsSeries_whenClientTypeNotLocked() {
        val config = FightConfig(stage1 = "1-7", series = 6)

        assertEquals(6, seriesOf(config, clientType = "YoStarJP"))
        assertEquals(6, config.series)
    }

    @Test
    fun fightConfig_downgradesSeriesToNoSwitch_whenLocked() {
        mockkObject(ServerTimezone)
        try {
            every { ServerTimezone.getYjDate("Official") } returns afterLock
            val config = FightConfig(stage1 = "1-7", series = 6)

            assertEquals(-1, seriesOf(config, clientType = "Official"))
            // 锁定只影响下发，用户原选值必须保留
            assertEquals(6, config.series)
        } finally {
            unmockkObject(ServerTimezone)
        }
    }
}

private fun seriesOf(config: FightConfig, clientType: String): Int {
    val ctx = testTaskParamContext(
        clientType = clientType,
        activityManager = alwaysOpenActivityManager(),
    )
    val params = config.toTaskParams(ctx).params.single().params
    return Json.parseToJsonElement(params).jsonObject["series"]!!.jsonPrimitive.content.toInt()
}
