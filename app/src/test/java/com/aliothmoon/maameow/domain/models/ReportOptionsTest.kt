package com.aliothmoon.maameow.domain.models

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportOptionsTest {

    @Test
    fun serverOf_mapsClientTypes() {
        assertEquals("CN", ReportOptions.serverOf("Official"))
        assertEquals("CN", ReportOptions.serverOf("Bilibili"))
        assertEquals("US", ReportOptions.serverOf("YoStarEN"))
        assertEquals("JP", ReportOptions.serverOf("YoStarJP"))
        assertEquals("KR", ReportOptions.serverOf("YoStarKR"))
        assertEquals("ZH_TW", ReportOptions.serverOf("txwy"))
    }

    @Test
    fun of_trimsPenguinId() {
        val options = ReportOptions.of(
            clientType = "YoStarEN",
            reportToPenguin = true,
            reportToYituliu = false,
            penguinId = " 123 ",
        )
        assertEquals("US", options.server)
        assertEquals("123", options.penguinId)
        assertEquals(false, options.reportToYituliu)
    }
}
