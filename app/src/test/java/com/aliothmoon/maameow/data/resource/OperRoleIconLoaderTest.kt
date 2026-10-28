package com.aliothmoon.maameow.data.resource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 编号要和 core OperBoxImageAnalyzer 的职业旗标一致，对齐上游 OperAvatarHelper */
class OperRoleIconLoaderTest {

    @Test
    fun professionsMapToCoreFlagTemplates() {
        val expected = mapOf(
            "CASTER" to 1, "MEDIC" to 2, "PIONEER" to 3, "SNIPER" to 4,
            "SPECIAL" to 5, "SUPPORT" to 6, "TANK" to 7, "WARRIOR" to 8,
        )
        expected.forEach { (profession, index) ->
            assertEquals("OperBoxFlagRole$index", OperRoleIconLoader.templateId(profession))
        }
        assertEquals("OperBoxFlagRole8", OperRoleIconLoader.templateId("warrior"))
    }

    @Test
    fun unknownProfessionsHaveNoIcon() {
        listOf(null, "", "TOKEN", "TRAP").forEach { assertNull(OperRoleIconLoader.templateId(it)) }
    }
}
