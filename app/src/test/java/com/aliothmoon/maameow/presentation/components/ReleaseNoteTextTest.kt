package com.aliothmoon.maameow.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseNoteTextTest {

    @Test
    fun splitsGitCliffHeader() {
        val note = "\n## [0.25.3](https://github.com/Aliothmoon/MAA-Meow/releases/tag/v0.25.3) - 2026-11-04\r\n\r\n" +
            "### ✨ 新增 | Features\n- 一条"

        val (header, body) = splitReleaseHeader(note)

        assertEquals(
            ReleaseNoteHeader("0.25.3", "https://github.com/Aliothmoon/MAA-Meow/releases/tag/v0.25.3", "2026-11-04"),
            header,
        )
        assertEquals("### ✨ 新增 | Features\n- 一条", body)
    }

    @Test
    fun keepsPrereleaseVersion() {
        val (header, _) = splitReleaseHeader("## [0.26.0-beta.1](https://x/v0.26.0-beta.1) - 2026-11-09\n- a")
        assertEquals("0.26.0-beta.1", header?.version)
    }

    @Test
    fun otherMarkdownUntouched() {
        val note = "## 普通标题\n- a"
        val (header, body) = splitReleaseHeader(note)
        assertNull(header)
        assertEquals(note, body)
    }

    @Test
    fun headerOnly() {
        val (header, body) = splitReleaseHeader("## [1.0.0](https://x) - 2026-01-01")
        assertEquals("1.0.0", header?.version)
        assertEquals("", body)
    }
}
