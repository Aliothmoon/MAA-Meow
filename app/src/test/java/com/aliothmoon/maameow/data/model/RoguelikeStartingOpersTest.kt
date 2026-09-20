package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.data.resource.ResourceDataManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 肉鸽开局干员顺位：下发的 core_char_list 与旧配置迁移
 * 对齐 WPF RoguelikeSettingsUserControlModel:1470 与 RoguelikeStartingOpersConverter
 */
class RoguelikeStartingOpersTest {

    // relaxed mock 会给未 stub 的调用返回假值，默认按「查不到，原样返回」处理
    private val resourceDataManager = mockk<ResourceDataManager>(relaxed = true) {
        every { normalizeCharacterName(any()) } answers { firstArg() }
    }

    private fun coreCharList(config: RoguelikeConfig) =
        Json.parseToJsonElement(
            config.toTaskParams(testTaskParamContext(resourceDataManager = resourceDataManager))
                .single().params
        ).jsonObject["core_char_list"]

    private fun names(config: RoguelikeConfig) =
        coreCharList(config)!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }

    @Test
    fun noStartingOper_omitsTheKeyEntirely() {
        assertNull(coreCharList(RoguelikeConfig()))
    }

    @Test
    fun toggleOff_sendsOnlyTheFirstPosition() {
        val config = RoguelikeConfig(
            startingOpers = listOf(
                RoguelikeStartingOper("山", useSupport = true),
                RoguelikeStartingOper("银灰"),
            ),
            useAdditionalStartingOpers = false,
        )
        assertEquals(listOf("山"), names(config))
        assertTrue(
            coreCharList(config)!!.jsonArray[0]
                .jsonObject["use_support"]!!.jsonPrimitive.content.toBoolean()
        )
    }

    @Test
    fun toggleOn_sendsEveryFilledPosition() {
        val config = RoguelikeConfig(
            startingOpers = listOf(
                RoguelikeStartingOper("山"),
                RoguelikeStartingOper("银灰"),
                RoguelikeStartingOper("史尔特尔"),
            ),
            useAdditionalStartingOpers = true,
        )
        assertEquals(listOf("山", "银灰", "史尔特尔"), names(config))
    }

    @Test
    fun chainStopsAtTheFirstBlankName() {
        val config = RoguelikeConfig(
            startingOpers = listOf(
                RoguelikeStartingOper("山"),
                RoguelikeStartingOper(""),
                RoguelikeStartingOper("史尔特尔"),
            ),
            useAdditionalStartingOpers = true,
        )
        assertEquals(listOf("山"), names(config))
    }

    @Test
    fun everyPositionIsNormalizedToTheSimplifiedChineseName() {
        every { resourceDataManager.normalizeCharacterName("維什戴爾") } returns "维什戴尔"
        every { resourceDataManager.normalizeCharacterName("阿米娅-WARRIOR") } returns "阿米娅"

        val config = RoguelikeConfig(
            startingOpers = listOf(
                RoguelikeStartingOper("維什戴爾"),
                RoguelikeStartingOper("阿米娅-WARRIOR"),
            ),
            useAdditionalStartingOpers = true,
        )
        assertEquals(listOf("维什戴尔", "阿米娅"), names(config))
    }

    @Test
    fun legacySingleOperIsMigratedIntoTheFirstPosition() {
        val migrated = RoguelikeConfig(legacyCoreChar = "山", legacyUseSupport = true)
            .migrate()

        assertEquals(listOf(RoguelikeStartingOper("山", useSupport = true)), migrated.startingOpers)
        assertNull(migrated.legacyCoreChar)
        assertNull(migrated.legacyUseSupport)
    }

    @Test
    fun migrationNeverOverwritesAnExistingList() {
        val migrated = RoguelikeConfig(
            startingOpers = listOf(RoguelikeStartingOper("银灰")),
            legacyCoreChar = "山",
        ).migrate()

        assertEquals(listOf(RoguelikeStartingOper("银灰")), migrated.startingOpers)
        assertNull(migrated.legacyCoreChar)
    }

    @Test
    fun legacyEmptyValuesProduceNoStartingOper() {
        val migrated = RoguelikeConfig(legacyCoreChar = "", legacyUseSupport = false)
            .migrate()

        assertTrue(migrated.startingOpers.isEmpty())
    }
}
