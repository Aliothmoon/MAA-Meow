package com.aliothmoon.maameow

import org.junit.Assert.assertTrue
import org.junit.Test

/** 无界高度下 OutlinedTextField 会测出越界高度闪退，每处调用都要限高 */
class TextFieldHeightCapContractTest {

    @Test
    fun everyOutlinedTextFieldCapsHeight() {
        val sites = TestSources.resolveDir("src/main/java").walk()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val source = file.readText()
                CALL.findAll(source).map { match ->
                    val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                    "${file.name}:$line" to argumentsOf(source, match.range.last)
                }
            }
            .toList()

        assertTrue("没找到 OutlinedTextField 调用，扫描路径不对", sites.isNotEmpty())
        val missing = sites.filterNot { (_, args) -> "textFieldHeightCap()" in args }.map { it.first }
        assertTrue("缺 textFieldHeightCap()：$missing", missing.isEmpty())
    }

    /** 从左括号起按配对截出参数表 */
    private fun argumentsOf(source: String, openIndex: Int): String {
        var depth = 0
        for (i in openIndex until source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return source.substring(openIndex, i + 1)
            }
        }
        error("unbalanced call at $openIndex")
    }

    private companion object {
        val CALL = Regex("""\bOutlinedTextField\(""")
    }
}
