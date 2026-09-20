package com.aliothmoon.maameow.data.model.toolbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperBoxExportFormatterTest {

    private val labels = OperBoxExportLabels(
        name = "名称",
        id = "ID",
        rarity = "星级",
        elite = "精英化",
        level = "等级",
        own = "拥有",
        potential = "潜能",
        yes = "是",
        no = "否",
        mainSkillLevel = "技能等级",
        skills = "技能专精",
        equips = "模组",
    )

    private val amiya = OperBoxOperator(
        id = "char_002_amiya",
        name = "阿米娅",
        rarity = 5,
        elite = 2,
        level = 80,
        potential = 3,
        own = true,
        mainSkillLevel = 7,
        skills = listOf(
            OperBoxSkill("s1", 3),
            OperBoxSkill("s2", 3),
            OperBoxSkill("s3", 0),
        ),
        equips = listOf(
            OperBoxEquip("e_x", "X", 3),
            OperBoxEquip("e_y", "Y", 1),
            OperBoxEquip("e_d", "D", 0),
        ),
    )

    @Test
    fun localDataHasNoTrainingColumns() {
        val local =
            amiya.copy(mainSkillLevel = null, skills = emptyList(), equips = emptyList())
        val csv = OperBoxExportFormatter.toCsv(listOf(local), labels)
        assertFalse(csv.contains("技能等级"))
        assertFalse(csv.contains("技能专精"))
        assertFalse(csv.contains("模组"))
        assertEquals(7, csv.lineSequence().first().split(",").size)
    }

    @Test
    fun yituliuCsvAppendsSkillsAndEquips() {
        val csv = OperBoxExportFormatter.toCsv(listOf(amiya), labels)
        val lines = csv.lines()
        assertTrue(lines[0].endsWith("技能等级,技能专精,模组"))
        assertTrue(lines[1].endsWith("7,3/3/0,X3 Y1"))
    }

    @Test
    fun yituliuMarkdownAppendsSkillsAndEquips() {
        val md = OperBoxExportFormatter.toMarkdown(listOf(amiya), labels)
        val lines = md.lines()
        assertTrue(lines[0].endsWith("| 技能等级 | 技能专精 | 模组 |"))
        assertTrue(lines[1].endsWith("| :-- | :-- | :-- |"))
        assertTrue(lines[2].endsWith("| 7 | 3/3/0 | X3 Y1 |"))
    }

    @Test
    fun jsonCarriesTrainingOnlyWhenPresent() {
        val withTraining = OperBoxExportFormatter.toJson(listOf(amiya))
        assertTrue(withTraining.contains("\"mainSkillLevel\": 7"))
        assertTrue(withTraining.contains("\"skills\": \"3/3/0\""))
        assertTrue(withTraining.contains("\"equips\": \"X3 Y1\""))

        val local = OperBoxExportFormatter.toJson(
            listOf(amiya.copy(mainSkillLevel = null, skills = emptyList(), equips = emptyList()))
        )
        assertFalse(local.contains("mainSkillLevel"))
        assertFalse(local.contains("skills"))
        assertFalse(local.contains("equips"))
    }
}
