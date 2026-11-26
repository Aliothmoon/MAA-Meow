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

    @Test
    fun matchedSummaryStablyOrdersOperatorsAndCandidatesWithoutChangingOriginal() {
        val candidates = listOf(
            CopilotOperator(name = "同名", skill = 1),
            CopilotOperator(name = "同名", skill = 2),
            CopilotOperator(name = "同名", skill = 3),
            CopilotOperator(name = "另一候选", skill = 1),
        )
        val summary = summaryOf(
            CopilotOperator(name = "未拥有"), CopilotOperator(name = "已满足"),
            groups = listOf(CopilotGroup("g1", candidates), CopilotGroup("g2", candidates.reversed())),
        )
        val reasons = listOf(OperatorMismatchReason.SKILL, null, OperatorMismatchReason.MISSING, null)
        val displayed = summary.withMatch(OperatorMatch(
            mode = OperatorMatchMode.BORROW,
            missing = listOf("未拥有"),
            training = emptyList(),
            operators = listOf(OperatorMismatchReason.MISSING, null),
            groups = listOf(reasons, reasons.reversed()),
        ))

        assertEquals(listOf("已满足", "未拥有"), displayed.operators.map { it.name })
        assertEquals(listOf(null, OperatorMismatchReason.MISSING), displayed.operators.map { it.mismatchReason })
        assertEquals(listOf("g1", "g2"), displayed.groups.map { it.first })
        assertEquals(listOf(2, 1, 1, 3), displayed.groups[0].second.map { it.skill })
        assertEquals(listOf(null, null, OperatorMismatchReason.SKILL, OperatorMismatchReason.MISSING), displayed.groups[0].second.map { it.mismatchReason })
        assertEquals(listOf(1, 2, 1, 3), displayed.groups[1].second.map { it.skill })
        assertEquals(summary.totalCount, displayed.totalCount)
        // 关闭匹配重新使用原始摘要；匹配和排序未修改原始摘要或作业列表
        assertEquals(listOf(1, 2, 3, 1), summary.groups[0].second.map { it.skill })
        assertEquals(listOf(1, 2, 3, 1), candidates.map { it.skill })
        assertNull(summary.groups[0].second[0].mismatchReason)
    }

    @Test
    fun independentAndGroupOperatorsUseSameStableReasonOrder() {
        val reasons = listOf(
            OperatorMismatchReason.MISSING, OperatorMismatchReason.SKILL,
            null, OperatorMismatchReason.MODULE, OperatorMismatchReason.ELITE,
            OperatorMismatchReason.LEVEL, OperatorMismatchReason.SKILL,
            OperatorMismatchReason.MISSING, null,
        )
        val opers = reasons.indices.map { CopilotOperator(name = "op$it") }
        val original = summaryOf(*opers.toTypedArray(), groups = listOf(CopilotGroup("g", opers)))
        val displayed = original.withMatch(OperatorMatch(
            mode = OperatorMatchMode.BLOCKED,
            missing = emptyList(), training = emptyList(),
            operators = reasons, groups = listOf(reasons),
        ))
        val expected = listOf(2, 8, 3, 1, 6, 5, 4, 0, 7).map { "op$it" }
        assertEquals(expected, displayed.operators.map { it.name })
        assertEquals(expected, displayed.groups.single().second.map { it.name })
        assertEquals(opers.map { it.name }, original.operators.map { it.name })
        assertEquals(opers.map { it.name }, original.groups.single().second.map { it.name })
    }

    @Test
    fun withMatchFallsBackToOriginalOnMismatchedOperatorCount() {
        val summary = summaryOf(CopilotOperator(name = "a"), CopilotOperator(name = "b"))
        val displayed = summary.withMatch(OperatorMatch(
            mode = OperatorMatchMode.BLOCKED,
            missing = listOf("a"),
            training = emptyList(),
            operators = listOf(OperatorMismatchReason.MISSING),
            groups = emptyList(),
        ))
        assertEquals(summary, displayed)
    }

    @Test
    fun withMatchFallsBackToOriginalOnMismatchedGroupCandidateCount() {
        val summary = summaryOf(
            groups = listOf(CopilotGroup("g", listOf(CopilotOperator(name = "a"), CopilotOperator(name = "b")))),
        )
        val displayed = summary.withMatch(OperatorMatch(
            mode = OperatorMatchMode.BLOCKED,
            missing = listOf("g"),
            training = emptyList(),
            operators = emptyList(),
            groups = listOf(listOf(OperatorMismatchReason.MISSING)),
        ))
        assertEquals(summary, displayed)
    }
}
