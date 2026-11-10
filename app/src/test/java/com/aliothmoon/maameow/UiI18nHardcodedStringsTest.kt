package com.aliothmoon.maameow

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全量扫描 main 源码里的中文字面量，跳过注释与 Timber / Ln / Log 调用参数
 * 界面文案一律走字符串资源；传给 MAA 的游戏数据、映射 key、仅诊断用的文案逐条登记到 [ALLOWLIST]
 */
class UiI18nHardcodedStringsTest {

    @Test
    fun mainSources_haveNoUnregisteredChineseLiterals() {
        val root = resolve(SOURCE_ROOT)
        val found = root.walkTopDown()
            .filter { it.isFile && it.extension in SOURCE_EXTENSIONS }
            .flatMap { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                hanLiterals(file.readText()).map { Entry(path, it) }
            }
            .toSet()
        val allowed = loadAllowlist()

        val unexpected = (found - allowed).sortedWith(ENTRY_ORDER)
        assertTrue(
            "未登记的中文字面量；界面文案请改用字符串资源，确属游戏数据 / 映射 key / 诊断文案的追加到 $ALLOWLIST：\n" +
                    unexpected.joinToString("\n") { "${it.path}\t${it.literal}" },
            unexpected.isEmpty()
        )
        val stale = (allowed - found).sortedWith(ENTRY_ORDER)
        assertTrue(
            "$ALLOWLIST 中这些条目已不在源码里，请删除：\n" +
                    stale.joinToString("\n") { "${it.path}\t${it.literal}" },
            stale.isEmpty()
        )
    }

    @Test
    fun scanner_skipsCommentsAndLogCalls() {
        val src = """
            // 注释
            /* 块注释 */
            val a = "界面"
            Timber.i("日志 %s", x)
            Timber.w(
                "多行日志",
                y,
            )
            Ln.e("服务进程日志")
            val b = "外层 ${'$'}{"模板里"} 尾巴"
            val c = '中'
        """.trimIndent()
        assertEquals(
            listOf("界面", "外层 ${'$'}{\"模板里\"} 尾巴"),
            hanLiterals(src),
        )
    }

    private data class Entry(val path: String, val literal: String)

    private fun loadAllowlist(): Set<Entry> =
        resolve(ALLOWLIST).readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val tab = line.indexOf('\t')
                check(tab > 0) { "$ALLOWLIST 格式应为 <路径>\\t<字面量>: $line" }
                Entry(line.substring(0, tab), line.substring(tab + 1))
            }
            .toSet()

    private fun resolve(relativePath: String): File {
        val file = listOf(File(relativePath), File("app/$relativePath"), File("../app/$relativePath"))
            .firstOrNull { it.exists() }
        checkNotNull(file) { "找不到 $relativePath" }
        return file
    }

    companion object {
        private const val SOURCE_ROOT = "src/main/java/com/aliothmoon/maameow"
        private const val ALLOWLIST = "src/test/resources/i18n/chinese_literal_allowlist.tsv"
        private val SOURCE_EXTENSIONS = setOf("kt", "java")
        private val ENTRY_ORDER = compareBy<Entry>({ it.path }, { it.literal })

        private val HAN = Regex("\\p{IsHan}")
        private val LOG_CALL =
            Regex("(?:^|[^\\w.])(?:Timber|Ln|Log)(?:\\.tag\\([^()]*\\))?\\.[a-z]\\w*\\s*$")

        /** 返回不在注释、不在日志调用参数里的中文字面量（引号内的源码原文） */
        internal fun hanLiterals(src: String): List<String> {
            val out = mutableListOf<String>()
            val logParens = ArrayDeque<Boolean>()
            fun record(literal: String) {
                if (HAN.containsMatchIn(literal) && logParens.none { it }) out += literal
            }

            var i = 0
            val n = src.length
            while (i < n) {
                when {
                    src.startsWith("//", i) -> i = src.indexOf('\n', i).let { if (it < 0) n else it }
                    src.startsWith("/*", i) -> i = src.indexOf("*/", i + 2).let { if (it < 0) n else it + 2 }
                    src.startsWith("\"\"\"", i) -> {
                        val close = src.indexOf("\"\"\"", i + 3).let { if (it < 0) n else it }
                        var end = (close + 3).coerceAtMost(n)
                        while (end < n && src[end] == '"') end++
                        record(src.substring(i + 3, maxOf(i + 3, end - 3)))
                        i = end
                    }

                    src[i] == '"' -> {
                        val close = stringEnd(src, i + 1)
                        record(src.substring(i + 1, close))
                        i = close + 1
                    }

                    src[i] == '\'' -> i = charEnd(src, i)
                    src[i] == '(' -> {
                        logParens.addLast(LOG_CALL.containsMatchIn(src.substring(maxOf(0, i - 64), i)))
                        i++
                    }

                    src[i] == ')' -> {
                        logParens.removeLastOrNull()
                        i++
                    }

                    else -> i++
                }
            }
            return out
        }

        private fun stringEnd(src: String, start: Int): Int {
            var j = start
            while (j < src.length) {
                when {
                    src[j] == '\\' -> j += 2
                    src.startsWith("\${", j) -> j = templateEnd(src, j + 2)
                    src[j] == '"' || src[j] == '\n' -> return j
                    else -> j++
                }
            }
            return src.length
        }

        private fun templateEnd(src: String, start: Int): Int {
            var depth = 1
            var j = start
            while (j < src.length && depth > 0) {
                when (src[j]) {
                    '{' -> depth++
                    '}' -> depth--
                    '"' -> j = stringEnd(src, j + 1)
                }
                j++
            }
            return j
        }

        private fun charEnd(src: String, i: Int): Int {
            val from = if (src.getOrNull(i + 1) == '\\') i + 3 else i + 2
            val close = src.indexOf('\'', from)
            return if (close in from..i + 8) close + 1 else i + 1
        }
    }
}
