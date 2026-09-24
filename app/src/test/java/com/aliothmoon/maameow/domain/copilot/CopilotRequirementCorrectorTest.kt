package com.aliothmoon.maameow.domain.copilot

import com.aliothmoon.maameow.domain.service.copilot.CopilotRequirementCorrector
import com.aliothmoon.maameow.domain.service.copilot.CopilotRequirementCorrector.Kind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对齐 MaaWpfGui CopilotViewModel 的技能/精英化校验与动作冗余字段清理
 * 关键约束：只改 requirements.elite、skill 和动作里被覆盖的字段，作业里其他字段一律原样保留
 */
class CopilotRequirementCorrectorTest {

    /** 六星 6、五星 5、四星 4、三星 3、二星 2，其余当未收录 */
    private val lookup: (String) -> CopilotRequirementCorrector.OperatorInfo? = { name ->
        when (name) {
            "银灰" -> info(6, "char_172_svrash")
            "阿米娅" -> info(5, "char_002_amiya")
            "蓝毒" -> info(5, "char_129_bluep")
            "白面鸮" -> info(4, "char_128_plosis")
            "芬" -> info(3, "char_123_fang")
            "12F" -> info(2, "char_009_12fce")
            else -> null
        }
    }

    private fun info(rarity: Int, id: String) = CopilotRequirementCorrector.OperatorInfo(rarity, id)

    private fun correct(json: String) = CopilotRequirementCorrector.correct(json, lookup)

    private fun opers(json: String) =
        Json.parseToJsonElement(json).jsonObject["opers"]!!.jsonArray.map { it.jsonObject }

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.int

    private fun JsonObject.elite(): Int? = this["requirements"]?.jsonObject?.int("elite")

    @Test
    fun `没有需要改的地方原样返回`() {
        // 一技能不推精英化要求，六星也支持
        val json = """{"stage_name":"1-7","opers":[{"name":"银灰","skill":1}]}"""
        val result = correct(json)
        assertTrue(result.corrections.isEmpty())
        assertEquals(false, result.altered)
        assertSame(json, result.json)
    }

    @Test
    fun `稀有度不够的技能被取消`() {
        // 白面鸮四星，三技能要六星
        val result = correct("""{"opers":[{"name":"白面鸮","skill":3}]}""")
        assertEquals(1, result.corrections.size)
        assertEquals(Kind.UNSUPPORTED_SKILL, result.corrections[0].kind)
        assertEquals(0, opers(result.json)[0].int("skill"))
        assertTrue(result.altered)
    }

    @Test
    fun `二星干员连一技能都不支持`() {
        val result = correct("""{"opers":[{"name":"12F","skill":1}]}""")
        assertEquals(Kind.UNSUPPORTED_SKILL, result.corrections.single().kind)
        assertEquals(0, opers(result.json)[0].int("skill"))
    }

    @Test
    fun `未收录干员按上游口径同样降技能`() {
        val result = correct("""{"opers":[{"name":"没这个人","skill":2}]}""")
        assertEquals(Kind.UNSUPPORTED_SKILL, result.corrections.single().kind)
    }

    @Test
    fun `三技能推出精二并补全`() {
        val result = correct("""{"opers":[{"name":"银灰","skill":3}]}""")
        val correction = result.corrections.single()
        assertEquals(Kind.ELITE_FILLED, correction.kind)
        assertEquals(2, correction.to)
        assertEquals(2, opers(result.json)[0].elite())
        // 补空不算改动过作业
        assertEquals(false, result.altered)
    }

    @Test
    fun `精英化偏低会被上调且标记为改动`() {
        val json = """{"opers":[{"name":"银灰","skill":3,"requirements":{"elite":1}}]}"""
        val result = correct(json)
        val correction = result.corrections.single()
        assertEquals(Kind.ELITE_RAISED, correction.kind)
        assertEquals(1, correction.from)
        assertEquals(2, correction.to)
        assertTrue(result.altered)
    }

    @Test
    fun `精英化已达标不动`() {
        val json = """{"opers":[{"name":"银灰","skill":3,"requirements":{"elite":2}}]}"""
        assertTrue(correct(json).corrections.isEmpty())
    }

    @Test
    fun `专精等级推精英化`() {
        // skill_level 5~7 → 精 1，8~10 → 精 2
        val one = correct("""{"opers":[{"name":"银灰","skill":1,"requirements":{"skill_level":5}}]}""")
        assertEquals(1, opers(one.json)[0].elite())

        val two = correct("""{"opers":[{"name":"银灰","skill":1,"requirements":{"skill_level":8}}]}""")
        assertEquals(2, opers(two.json)[0].elite())

        val none = correct("""{"opers":[{"name":"银灰","skill":1,"requirements":{"skill_level":4}}]}""")
        assertTrue(none.corrections.isEmpty())
    }

    @Test
    fun `带模组直接要精二`() {
        val result = correct("""{"opers":[{"name":"银灰","skill":1,"requirements":{"module":1}}]}""")
        assertEquals(2, opers(result.json)[0].elite())
    }

    @Test
    fun `技能被取消后不再按技能号推精英化`() {
        // 芬三星，二技能不支持 → skill 归零，skillElite = -1，不应补出精 1
        val result = correct("""{"opers":[{"name":"芬","skill":2}]}""")
        assertEquals(listOf(Kind.UNSUPPORTED_SKILL), result.corrections.map { it.kind })
        assertNull(opers(result.json)[0].elite())
    }

    @Test
    fun `备用干员组同样校正`() {
        val json = """{"groups":[{"name":"近卫","opers":[{"name":"银迈","skill":3}]}]}"""
        val result = correct(json)
        assertTrue(result.corrections.isNotEmpty())
        val group = Json.parseToJsonElement(result.json).jsonObject["groups"]!!
            .jsonArray[0].jsonObject
        assertEquals("近卫", group["name"]!!.jsonPrimitive.content)
        assertEquals(0, group["opers"]!!.jsonArray[0].jsonObject.int("skill"))
    }

    @Test
    fun `未建模的字段必须原样保留`() {
        val json = """
            {"stage_name":"1-7","minimum_required":"v4.0.0","custom_field":{"a":[1,2,3]},
             "doc":{"title":"标题","details":"说明"},
             "opers":[{"name":"银灰","skill":3,"skill_usage":1,"custom_oper":"keep",
                       "requirements":{"elite":1,"level":60,"potentiality":2}}],
             "actions":[{"type":"部署","name":"银灰","location":[5,5]}]}
        """.trimIndent()
        val result = correct(json)
        val root = Json.parseToJsonElement(result.json).jsonObject
        assertEquals("v4.0.0", root["minimum_required"]!!.jsonPrimitive.content)
        assertEquals(3, root["custom_field"]!!.jsonObject["a"]!!.jsonArray.size)
        assertEquals("标题", root["doc"]!!.jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals(1, root["actions"]!!.jsonArray.size)

        val oper = root["opers"]!!.jsonArray[0].jsonObject
        assertEquals("keep", oper["custom_oper"]!!.jsonPrimitive.content)
        assertEquals(1, oper.int("skill_usage"))
        // requirements 里的其他字段不能被改写掉
        val requirements = oper["requirements"]!!.jsonObject
        assertEquals(60, requirements.int("level"))
        assertEquals(2, requirements.int("potentiality"))
        assertEquals(2, requirements.int("elite"))
    }

    @Test
    fun `坏 JSON 原样返回不抛异常`() {
        val broken = "{not json"
        val result = correct(broken)
        assertSame(broken, result.json)
        assertTrue(result.corrections.isEmpty())
    }

    @Test
    fun `阿米娅五星三技能按 id 放行`() {
        val result = correct("""{"opers":[{"name":"阿米娅","skill":3}]}""")
        // 技能保留，三技能照常推精二
        assertEquals(listOf(Kind.ELITE_FILLED), result.corrections.map { it.kind })
        assertEquals(3, opers(result.json)[0].int("skill"))
        assertEquals(2, opers(result.json)[0].elite())
        assertEquals(false, result.altered)
    }

    private fun actions(json: String) =
        Json.parseToJsonElement(json).jsonObject["actions"]!!.jsonArray.map { it.jsonObject }

    @Test
    fun `同填干员与坐标时去掉干员和职业`() {
        val json = """
            {"actions":[
              {"type":"Skill","name":"银灰","role":"Warrior","location":[3,4],"kills":5},
              {"type":"Retreat","name":"银灰","location":[3,4]},
              {"type":"BulletTime","name":"银灰","location":[3,4]},
              {"type":"SkillUsage","name":"银灰","location":[3,4],"skill_usage":2}
            ]}
        """.trimIndent()
        val result = correct(json)
        assertEquals(4, result.corrections.size)
        assertTrue(result.corrections.all { it.kind == Kind.LOCATION_OVER_OPER })
        assertEquals("Skill[3,4]", result.corrections[0].target)
        // 改过作业，不再回传原作业 id
        assertTrue(result.altered)

        actions(result.json).forEach { action ->
            assertNull(action["name"])
            assertNull(action["role"])
            assertEquals(listOf(3, 4), action["location"]!!.jsonArray.map { it.jsonPrimitive.int })
        }
        // 其他字段原样保留
        assertEquals(5, actions(result.json)[0].int("kills"))
        assertEquals(2, actions(result.json)[3].int("skill_usage"))
    }

    @Test
    fun `点击同填区域与坐标时去掉坐标`() {
        val json = """{"actions":[{"type":"Click","rect":[100,200,50,60],"location":[3,4]}]}"""
        val result = correct(json)
        val correction = result.corrections.single()
        assertEquals(Kind.RECT_OVER_LOCATION, correction.kind)
        assertEquals("Click[100,200,50,60]", correction.target)
        assertTrue(result.altered)

        val action = actions(result.json).single()
        assertNull(action["location"])
        assertEquals(4, action["rect"]!!.jsonArray.size)
    }

    @Test
    fun `动作类型只认上游写法且其余动作不动`() {
        // 上游按字符串精确匹配：别名、部署、单填都不校正；显式 null 当没写
        val json = """
            {"actions":[
              {"type":"技能","name":"银灰","location":[3,4]},
              {"type":"skill","name":"银灰","location":[3,4]},
              {"type":"Deploy","name":"银灰","location":[3,4],"direction":"Left"},
              {"type":"Skill","name":"银灰"},
              {"type":"Skill","location":[3,4]},
              {"type":"Retreat","name":null,"location":[3,4]},
              {"type":"Click","rect":[1,2,3,4]},
              {"type":"Click","rect":null,"location":[3,4]}
            ]}
        """.trimIndent()
        val result = correct(json)
        assertTrue(result.corrections.isEmpty())
        assertSame(json, result.json)
    }

    @Test
    fun `提示先干员后动作`() {
        // 键序把 actions 放前面，提示顺序也不能跟着变
        val json = """
            {"actions":[{"type":"Skill","name":"白面鸮","location":[1,1]}],
             "opers":[{"name":"白面鸮","skill":3}]}
        """.trimIndent()
        val result = correct(json)
        assertEquals(
            listOf(Kind.UNSUPPORTED_SKILL, Kind.LOCATION_OVER_OPER),
            result.corrections.map { it.kind }
        )
    }

    @Test
    fun `其他五星三技能仍被取消`() {
        val result = correct("""{"opers":[{"name":"蓝毒","skill":3}]}""")
        assertEquals(listOf(Kind.UNSUPPORTED_SKILL), result.corrections.map { it.kind })
        assertEquals(0, opers(result.json)[0].int("skill"))
    }
}
