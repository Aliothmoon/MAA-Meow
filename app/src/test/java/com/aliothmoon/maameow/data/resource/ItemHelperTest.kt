package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.config.MaaPathConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 「物品名显示成 ID」是历史 bug，两个来源：
 * 索引解析失败时旧实现会把表清空，以及全球服永远只读根目录那份。
 * 这里锁住合并取并集 + 失败保留旧表的行为。
 */
class ItemHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var baseDir: File
    private lateinit var enDir: File

    private fun helper(): ItemHelper {
        baseDir = tempFolder.newFolder("resource")
        enDir = tempFolder.newFolder("global-en")
        val pathConfig = mockk<MaaPathConfig> {
            every { resourceDir } returns baseDir.absolutePath
            every { globalResourceDir("YoStarEN") } returns enDir
        }
        return ItemHelper(pathConfig)
    }

    private fun writeIndex(dir: File, vararg entries: Pair<String, String>) {
        dir.mkdirs()
        val body = entries.joinToString(",") { (id, name) ->
            """"$id":{"name":"$name","sortId":${id.toIntOrNull() ?: 0}}"""
        }
        File(dir, INDEX_JSON).writeText("{$body}")
    }

    /** 需要 `classifyType` 的用例直接给整份 JSON */
    private fun writeIndexJson(dir: File, body: String) {
        dir.mkdirs()
        File(dir, INDEX_JSON).writeText(body)
    }

    @Test
    fun globalClientNameOverridesBaseNameButKeepsItemsOnlyInBase() {
        val itemHelper = helper()
        writeIndex(baseDir, "30011" to "源岩", "30061" to "破损装置")
        writeIndex(enDir, "30011" to "Orirock")

        assertTrue(itemHelper.load("YoStarEN"))

        assertEquals("Orirock", itemHelper.getItemInfo("30011")?.name)
        // 国际服那份条目更少，只在国际服里没有的 id 必须保留，否则会退回显示 ID
        assertEquals("破损装置", itemHelper.getItemInfo("30061")?.name)
    }

    @Test
    fun clientIndexAddsItemsTheBaseIndexDoesNotHave() {
        val itemHelper = helper()
        writeIndex(baseDir, "30011" to "源岩")
        writeIndex(enDir, "30011" to "Orirock", "99999" to "Brand New")

        assertTrue(itemHelper.load("YoStarEN"))

        assertEquals("Brand New", itemHelper.getItemInfo("99999")?.name)
    }

    @Test
    fun officialClientDoesNotReadTheGlobalIndex() {
        val itemHelper = helper()
        writeIndex(baseDir, "30011" to "源岩")
        writeIndex(enDir, "30011" to "Orirock")

        assertTrue(itemHelper.load("Official"))

        assertEquals("源岩", itemHelper.getItemInfo("30011")?.name)
    }

    @Test
    fun unparsableIndexKeepsThePreviousOneInsteadOfClearingIt() {
        val itemHelper = helper()
        writeIndex(baseDir, "30011" to "源岩")
        assertTrue(itemHelper.load("Official"))

        File(baseDir, INDEX_JSON).writeText("{ this is not json")

        assertFalse(itemHelper.load("Official"))
        assertTrue(itemHelper.items.value.isNotEmpty())
        assertEquals("源岩", itemHelper.getItemInfo("30011")?.name)
    }

    @Test
    fun missingIndexReportsNotLoadedAndEveryLookupMisses() {
        val itemHelper = helper()
        baseDir.mkdirs()

        assertFalse(itemHelper.load("Official"))
        assertTrue(itemHelper.items.value.isEmpty())
        assertNull(itemHelper.getItemInfo("30011"))
    }

    @Test
    fun dropItemsSkipDrawResourcesAndNonNumericIds() {
        val itemHelper = helper()
        writeIndex(
            baseDir,
            "30011" to "源岩",
            "7003" to "寻访凭证",
            "4002" to "源石",
            "not_a_number" to "怪东西",
        )

        itemHelper.load("Official")
        val ids = itemHelper.dropItems.value.map { it.id }

        assertTrue(ids.contains("30011"))
        assertFalse(ids.contains("7003"))
        assertFalse(ids.contains("4002"))
        assertFalse(ids.contains("not_a_number"))
    }

    @Test
    fun dropItemsAreSortedByRawId() {
        val itemHelper = helper()
        writeIndex(baseDir, "30093" to "研磨石", "30011" to "源岩", "30061" to "破损装置")

        itemHelper.load("Official")

        assertEquals(listOf("30011", "30061", "30093"), itemHelper.dropItems.value.map { it.id })
    }

    @Test
    fun dropItemsKeepCurrenciesAndExcludeUnfarmableMats() {
        val itemHelper = helper()
        writeIndexJson(
            baseDir,
            """
            {
              "4001":{"name":"龙门币","sortId":10201,"classifyType":"NORMAL"},
              "4006":{"name":"采购凭证","sortId":10205,"classifyType":"NORMAL"},
              "3114":{"name":"碳素组","sortId":100060,"classifyType":"NORMAL"},
              "30011":{"name":"源岩","sortId":100040,"classifyType":"MATERIAL"},
              "3213":{"name":"先锋双芯片","sortId":400020,"classifyType":"MATERIAL"},
              "30115":{"name":"聚合剂","sortId":100010,"classifyType":"MATERIAL"},
              "mod_unlock_token":{"name":"模组数据块","sortId":500001,"classifyType":"MATERIAL"}
            }
            """.trimIndent(),
        )

        assertTrue(itemHelper.load("Official"))

        assertEquals(
            listOf("30011", "3114", "4001", "4006"),
            itemHelper.dropItems.value.map { it.id },
        )
    }

    private companion object {
        const val INDEX_JSON = "item_index.json"
    }
}
