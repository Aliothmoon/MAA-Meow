package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.resource.CharacterInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class OperProgressPlanParserTest {
    private val chen = CharacterInfo(
        id = "char_010_chen", name = "陈", nameEn = "Ch'en", nameTw = "陳", nameJp = "チェン", nameKr = "첸",
        profession = "WARRIOR", rarity = 6,
    )
    private val operators = listOf(chen)

    @Test
    fun multilingualImportsUseCanonicalNameAndInferRole() {
        listOf("陈", "Ch'en", "陳", "チェン", "첸").forEach { name ->
            val plan = OperProgressPlanParser.parse("""[{"name":"$name","skill_mastery":[0,0,3]}]""", operators).single()
            assertEquals("陈", plan.name)
            assertEquals("Warrior", plan.role)
            assertEquals(listOf(0, 0, 3), plan.skillMastery)
        }
    }

    @Test
    fun explicitRoleIsNormalized_andOrderIsPreserved() {
        val other = chen.copy(id = "char_other", name = "另一名", nameEn = null, profession = "MEDIC")
        val plans = OperProgressPlanParser.parse("""[{"name":"另一名","elite":2},{"name":"陈","role":"WARRIOR","skill_level":7}]""", operators + other)
        assertEquals(listOf("另一名", "陈"), plans.map { it.name })
        assertEquals(listOf("Medic", "Warrior"), plans.map { it.role })
        assertEquals(2, plans.map { it.id }.distinct().size)
    }

    @Test
    fun aliasesCannotCreateDuplicatePlans() {
        val error = assertThrows(OperProgressImportException::class.java) {
            OperProgressPlanParser.parse("""[{"name":"陈","elite":2},{"name":"Ch'en","skill_level":7}]""", operators)
        }
        assertEquals(R.string.oper_progress_duplicate, error.textRes)
        assertThrows(OperProgressImportException::class.java) {
            OperProgressPlanParser.parse("""[{"name":"陳","elite":2}]""", operators, listOf(OperProgressPlan.forOperator(chen)))
        }
    }

    @Test
    fun rejectsMalformedAndUnsupportedTargets() {
        listOf(
            "{\"plans\":[]}", "[]", "[true]", "[{\"name\":\"陈\"}]",
            """[{"name":"陈","elite":0}]""", """[{"name":"陈","elite":null}]""",
            """[{"name":"陈","elite":"2"}]""", """[{"name":"陈","elite":2.0}]""",
            """[{"name":"陈","skill_level":1}]""", """[{"name":"陈","level":90}]""",
            """[{"name":"陈","skill_mastery":[0,3]}]""", """[{"name":"陈","skill_mastery":[0,0,4]}]""",
            """[{"name":"陈","skill_mastery":[0,0,"3"]}]""",
            """[{"name":"陈","skill_level":6,"skill_mastery":[0,0,3]}]""",
        ).forEach { input ->
            assertThrows(input, OperProgressImportException::class.java) { OperProgressPlanParser.parse(input, operators) }
        }
    }

    @Test
    fun rejectsUnavailableMasteryAndPromotionForLowRarity() {
        val three = chen.copy(rarity = 3)
        assertThrows(OperProgressImportException::class.java) {
            OperProgressPlanParser.parse("""[{"name":"陈","elite":2}]""", listOf(three))
        }
        assertThrows(OperProgressImportException::class.java) {
            OperProgressPlanParser.parse("""[{"name":"陈","skill_mastery":[1,0,0]}]""", listOf(three))
        }
    }

    @Test
    fun rejectsUnverifiedAmiyaAndAmbiguousOperatorNames() {
        val amiya = chen.copy(id = "char_002_amiya", name = "阿米娅", profession = "CASTER")
        assertThrows(OperProgressImportException::class.java) {
            OperProgressPlanParser.parse("""[{"name":"阿米娅","elite":2}]""", listOf(amiya))
        }
        val morph = chen.copy(id = "char_other", profession = "MEDIC")
        assertThrows(OperProgressImportException::class.java) {
            OperProgressPlanParser.parse("""[{"name":"陈","elite":2}]""", operators + morph)
        }
        assertEquals("Medic", OperProgressPlanParser.parse("""[{"name":"陈","role":"Medic","elite":2}]""", operators + morph).single().role)
    }

    @Test
    fun invalidBatchLeavesExistingPlansIntact() {
        val existing = listOf(OperProgressPlan.forOperator(chen))
        val original = existing.toList()
        assertThrows(OperProgressImportException::class.java) {
            OperProgressPlanParser.parse("""[{"name":"不存在","elite":2}]""", operators, existing)
        }
        assertEquals(original, existing)
        assertTrue(existing.single().hasTargets)
    }

    @Test
    fun unrecognizedRoleAlias_fallsBackToNameMatch() {
        listOf("Guard", "近卫").forEach { role ->
            val plan = OperProgressPlanParser.parse("""[{"name":"陈","role":"$role","elite":2}]""", operators).single()
            assertEquals("Warrior", plan.role)
        }
    }

    @Test
    fun emptyClipboard_isToldApartFromBadFormat() {
        listOf("", "  \n").forEach { input ->
            val error = assertThrows(OperProgressImportException::class.java) {
                OperProgressPlanParser.parse(input, operators)
            }
            assertEquals(R.string.oper_progress_import_empty, error.textRes)
        }
        val error = assertThrows(OperProgressImportException::class.java) {
            OperProgressPlanParser.parse("not json", operators)
        }
        assertEquals(R.string.oper_progress_import_invalid, error.textRes)
    }
}
