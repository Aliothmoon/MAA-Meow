package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.model.copilot.CopilotGroup
import com.aliothmoon.maameow.data.model.copilot.CopilotOperator
import com.aliothmoon.maameow.data.model.copilot.CopilotOperatorRequirements
import com.aliothmoon.maameow.data.model.toolbox.OperBoxEquip
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.data.model.toolbox.OperBoxSkill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotOperatorMatcherTest {

    private fun owned(
        name: String,
        rarity: Int = 6,
        elite: Int = 2,
        level: Int = 90,
        mainSkillLevel: Int? = null,
        mastery: List<Int> = emptyList(),
        equips: List<String> = emptyList(),
    ) = OperBoxOperator(
        id = name, name = name, rarity = rarity, elite = elite, level = level, potential = 1, own = true,
        mainSkillLevel = mainSkillLevel,
        skills = mastery.mapIndexed { i, m -> OperBoxSkill("sk$i", m) },
        equips = equips.map { OperBoxEquip("eq_$it", it, 1) },
    )

    private fun oper(
        name: String,
        skill: Int = 1,
        elite: Int = 0,
        level: Int = 0,
        skillLevel: Int = 0,
        module: Int = -1,
    ) = CopilotOperator(
        name = name,
        skill = skill,
        requirements = CopilotOperatorRequirements(elite = elite, level = level, skillLevel = skillLevel, module = module),
    )

    private fun match(
        opers: List<CopilotOperator>,
        box: List<OperBoxOperator>,
        groups: List<CopilotGroup> = emptyList(),
    ): OperatorMatch {
        val byName = box.associateBy { it.name }
        return CopilotOperatorMatcher.match(opers, groups) { byName[it.name] }
    }

    private val trained = (1..12).map { owned("op$it", mainSkillLevel = 7, mastery = listOf(3, 3, 3)) }

    @Test
    fun allOwnedIsReady() {
        val result = match(trained.take(3).map { oper(it.name, elite = 2, level = 60) }, trained)
        assertEquals(OperatorMatchMode.READY, result.mode)
    }

    @Test
    fun oneMissingNeedsBorrow() {
        val result = match(listOf(oper("op1"), oper("ghost")), trained)
        assertEquals(OperatorMatchMode.BORROW, result.mode)
        assertEquals(listOf("ghost"), result.missing)
    }

    @Test
    fun twoMissingIsBlocked() {
        assertEquals(OperatorMatchMode.BLOCKED, match(listOf(oper("a"), oper("b")), trained).mode)
    }

    @Test
    fun missingPlusUnderTrainedNeedsTraining() {
        val box = listOf(owned("weak", elite = 0, level = 30))
        val result = match(listOf(oper("weak", elite = 2, level = 1), oper("ghost")), box)
        assertEquals(OperatorMatchMode.TRAIN, result.mode)
        assertEquals(listOf("weak"), result.training)
    }

    @Test
    fun singleUnderTrainedCanBeBorrowed() {
        val box = listOf(owned("weak", elite = 1, level = 1), owned("weak2", elite = 1, level = 1))
        assertEquals(OperatorMatchMode.BORROW, match(listOf(oper("weak", elite = 2, level = 1)), box).mode)
        assertEquals(
            OperatorMatchMode.TRAIN,
            match(listOf(oper("weak", elite = 2, level = 1), oper("weak2", elite = 2, level = 1)), box).mode,
        )
    }

    @Test
    fun fullSquadNeedsSupportSlotAndOverflowIsBlocked() {
        val box = (1..14).map { owned("op$it") }
        assertEquals(OperatorMatchMode.BORROW, match((1..13).map { oper("op$it") }, box).mode)
        assertEquals(OperatorMatchMode.BLOCKED, match((1..14).map { oper("op$it") }, box).mode)
    }

    @Test
    fun levelOnlyRequirementInfersPromotion() {
        // 六星 60 级只能是精英一
        val need = oper("x", level = 60)
        assertFalse(CopilotOperatorMatcher.meets(need, owned("x", elite = 0, level = 50)))
        assertFalse(CopilotOperatorMatcher.meets(need, owned("x", elite = 1, level = 59)))
        assertTrue(CopilotOperatorMatcher.meets(need, owned("x", elite = 1, level = 60)))
        assertTrue(CopilotOperatorMatcher.meets(need, owned("x", elite = 2, level = 1)))
    }

    @Test
    fun masteryChecksTheChosenSkill() {
        val need = oper("x", skill = 3, skillLevel = 10)
        assertTrue(CopilotOperatorMatcher.meets(need, owned("x", mainSkillLevel = 7, mastery = listOf(0, 0, 3))))
        assertFalse(CopilotOperatorMatcher.meets(need, owned("x", mainSkillLevel = 7, mastery = listOf(3, 3, 2))))
        assertFalse(CopilotOperatorMatcher.meets(need, owned("x", mainSkillLevel = 7, mastery = listOf(3, 3))))
    }

    @Test
    fun mainSkillLevelChecked() {
        val need = oper("x", skillLevel = 7)
        assertFalse(CopilotOperatorMatcher.meets(need, owned("x", mainSkillLevel = 6, mastery = listOf(0))))
        assertTrue(CopilotOperatorMatcher.meets(need, owned("x", mainSkillLevel = 7, mastery = listOf(0))))
    }

    @Test
    fun moduleNeedsMatchingBranch() {
        val need = oper("x", module = 2)
        assertFalse(CopilotOperatorMatcher.meets(need, owned("x", mastery = listOf(3), equips = listOf("X"))))
        assertTrue(CopilotOperatorMatcher.meets(need, owned("x", mastery = listOf(3), equips = listOf("Y"))))
        // 0 为不切模组
        assertTrue(CopilotOperatorMatcher.meets(oper("x", module = 0), owned("x", mastery = listOf(3))))
    }

    @Test
    fun recognizedDataSkipsSkillAndModule() {
        val recognized = owned("x", elite = 2, level = 60)
        assertTrue(CopilotOperatorMatcher.meets(oper("x", skill = 3, skillLevel = 10, module = 1), recognized))
        assertFalse(CopilotOperatorMatcher.meets(oper("x", elite = 2, level = 70), recognized))
    }

    @Test
    fun groupNeedsOneQualifiedCandidate() {
        val box = listOf(owned("a", elite = 1, level = 1), owned("b"))
        fun group(vararg names: String) = CopilotGroup("g", names.map { oper(it, elite = 2, level = 1) })

        assertEquals(OperatorMatchMode.READY, match(emptyList(), box, listOf(group("a", "b"))).mode)
        assertEquals(listOf("g"), match(emptyList(), box, listOf(group("a"))).training)
        assertEquals(listOf("g"), match(emptyList(), box, listOf(group("ghost"))).missing)
    }

    @Test
    fun recognizedDataFallsBackToOtherMorphForm() {
        val caster = owned("char_002_amiya", rarity = 5)
        val guard = CopilotOperator(name = "阿米娅", role = "Warrior")
        val resolve: (CopilotOperator) -> String? = { "char_1001_amiya2" }

        val partial = CopilotOperatorMatching.Matcher(mapOf(caster.id to caster), resolve)
        assertTrue(partial.partial)
        assertEquals(OperatorMatchMode.READY, partial.match(listOf(guard), emptyList()).mode)

        // 一图流给全部形态，不回退
        val synced = caster.copy(mainSkillLevel = 7)
        val full = CopilotOperatorMatching.Matcher(mapOf(synced.id to synced), resolve)
        assertEquals(listOf("阿米娅"), full.match(listOf(guard), emptyList()).missing)
    }

    @Test
    fun parseModesFallsBackToDefault() {
        assertEquals(
            setOf(OperatorMatchMode.READY, OperatorMatchMode.BLOCKED),
            CopilotOperatorMatching.parseModes("READY, BLOCKED,bogus"),
        )
        assertEquals(CopilotOperatorMatching.DEFAULT_MODES, CopilotOperatorMatching.parseModes(""))
    }
}
