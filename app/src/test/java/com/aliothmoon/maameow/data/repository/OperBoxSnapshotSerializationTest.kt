package com.aliothmoon.maameow.data.repository

import com.aliothmoon.maameow.data.model.toolbox.OperBoxEquip
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.data.model.toolbox.OperBoxSkill
import com.aliothmoon.maameow.utils.JsonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperBoxSnapshotSerializationTest {

    private val json = JsonUtils.common

    private val fromCore = OperBoxOperator(
        id = "char_002_amiya",
        name = "阿米娅",
        rarity = 5,
        elite = 2,
        level = 80,
        potential = 3,
        own = true,
    )

    @Test
    fun coreRecognitionShardCarriesNoTrainingFields() {
        val encoded = json.encodeToString(
            OperBoxSnapshot.serializer(),
            OperBoxSnapshot(owned = listOf(fromCore), syncTimeMillis = 1L),
        )
        // Core 识别不产出专精模组，分片格式要和加这两个字段之前一致
        assertFalse(encoded.contains("skills"))
        assertFalse(encoded.contains("equips"))
    }

    @Test
    fun yituliuShardCarriesTrainingFields() {
        val fromYituliu = fromCore.copy(
            skills = listOf(OperBoxSkill("skchr_amiya_2", 3)),
            equips = listOf(OperBoxEquip("uniequip_002_amiya", "Y", 3)),
        )
        val encoded = json.encodeToString(
            OperBoxSnapshot.serializer(),
            OperBoxSnapshot(owned = listOf(fromYituliu), syncTimeMillis = 1L),
        )
        assertTrue(encoded.contains("skchr_amiya_2"))

        val decoded = json.decodeFromString(OperBoxSnapshot.serializer(), encoded)
        assertEquals(fromYituliu, decoded.owned.single())
    }

    @Test
    fun oldShardWithoutTrainingFieldsStillDecodes() {
        val legacy = """
            {"owned":[{"id":"char_002_amiya","name":"阿米娅","rarity":5,"elite":2,"level":80,
            "potential":3,"own":true}],"notOwned":[],"syncTimeMillis":1}
        """.trimIndent()
        val decoded = json.decodeFromString(OperBoxSnapshot.serializer(), legacy)
        assertEquals(fromCore, decoded.owned.single())
    }
}
