package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.model.copilot.CopilotGroup
import com.aliothmoon.maameow.data.model.copilot.CopilotOperator
import com.aliothmoon.maameow.data.model.copilot.CopilotOperatorRequirements
import com.aliothmoon.maameow.data.model.copilot.CopilotTaskData
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 干员摘要只给结构化字段，文案留给 UI 按语言渲染 */
class CopilotOperatorSummaryTest {

    private val manager = CopilotManager(apiService = mockk(), repository = mockk())

    private fun summaryOf(vararg opers: CopilotOperator, groups: List<CopilotGroup> = emptyList()) =
        manager.getOperatorSummary(CopilotTaskData(opers = opers.toList(), groups = groups))

    @Test
    fun fullRequirements_mapAsIs() {
        val item = summaryOf(
            CopilotOperator(
                name = "凛御银灰",
                skill = 3,
                requirements = CopilotOperatorRequirements(elite = 2, level = 60, skillLevel = 10, module = 1)
            )
        ).operators.single()

        assertEquals(OperatorPromotion(2, 60), item.promotion)
        assertEquals(3, item.skill)
        assertEquals(10, item.skillLevel)
        assertEquals(1, item.module)
    }

    @Test
    fun noSkill_hidesSkillLevel() {
        val item = summaryOf(
            CopilotOperator(
                name = "Lancet-2",
                skill = 0,
                requirements = CopilotOperatorRequirements(elite = 0, level = 30, skillLevel = 1)
            )
        ).operators.single()

        assertEquals(OperatorPromotion(0, 30), item.promotion)
        assertEquals(0, item.skill)
        assertNull(item.skillLevel)
    }

    @Test
    fun missingOrOutOfRangeRequirements_areDropped() {
        val (bare, zeroed, outOfRange) = summaryOf(
            CopilotOperator(name = "无要求", skill = 1),
            CopilotOperator(name = "全零", skill = 2, requirements = CopilotOperatorRequirements()),
            CopilotOperator(
                name = "越界",
                skill = 1,
                requirements = CopilotOperatorRequirements(elite = 1, level = 1, skillLevel = 11, module = 6)
            ),
        ).operators

        assertNull(bare.promotion)
        assertNull(bare.skillLevel)
        assertNull(bare.module)
        assertNull("精零 0 级视作无要求", zeroed.promotion)
        assertNull("module 默认 -1 不显示", zeroed.module)
        assertNull(outOfRange.skillLevel)
        assertNull(outOfRange.module)
    }

    @Test
    fun noModuleRequirement_isKeptAsZero() {
        val item = summaryOf(
            CopilotOperator(name = "伊内丝", requirements = CopilotOperatorRequirements(module = 0))
        ).operators.single()

        assertEquals(0, item.module)
    }

    @Test
    fun groupMembers_hidePromotion_andCountAsOne() {
        val summary = summaryOf(
            CopilotOperator(name = "遥"),
            groups = listOf(
                CopilotGroup(
                    name = "奶",
                    opers = listOf(
                        CopilotOperator(
                            name = "凯尔希",
                            skill = 2,
                            requirements = CopilotOperatorRequirements(elite = 2, level = 90, skillLevel = 9)
                        ),
                        CopilotOperator(name = "闪灵", skill = 2),
                    )
                )
            ),
        )

        assertEquals(2, summary.totalCount)
        val (groupName, members) = summary.groups.single()
        assertEquals("奶", groupName)
        assertNull("对齐 WPF，组内不显示精英化", members[0].promotion)
        assertEquals(9, members[0].skillLevel)
        assertEquals(2, members.size)
    }
}
