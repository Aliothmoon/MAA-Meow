package com.aliothmoon.maameow.announcement

/** 公告分节；标题含 (NEW!!!) 的节视为新增，标记从标题剔除 */
data class AnnouncementSection(
    val title: String,
    val isNew: Boolean,
    val content: String,
)

/**
 * 公告 markdown 分节解析：按 `## ` 二级标题切分（对齐 WPF ParseMiniGameEntries 思路，
 * 分隔符适配本项目公告写作约定）；首个标题前的前言只出现在全量视图
 */
object AnnouncementSectionParser {

    private const val NEW_MARK = "(NEW!!!)"
    private const val HEADER = "## "

    /** 全量视图：NEW 标记替换为 *（与 WPF 一致） */
    fun fullContent(markdown: String): String =
        markdown.replace(NEW_MARK, "*", ignoreCase = true).trim()

    fun parse(markdown: String): List<AnnouncementSection> {
        val sections = mutableListOf<AnnouncementSection>()
        var title: String? = null
        var isNew = false
        val body = StringBuilder()

        fun flush() {
            val t = title ?: return
            sections += AnnouncementSection(
                title = t,
                isNew = isNew,
                content = body.toString().trim(' ', '\n', '-'),
            )
        }

        markdown.lineSequence().forEach { line ->
            if (line.startsWith(HEADER)) {
                flush()
                var raw = line.removePrefix(HEADER).trim()
                isNew = raw.contains(NEW_MARK, ignoreCase = true)
                if (isNew) {
                    raw = raw.replace(NEW_MARK, "", ignoreCase = true).trim()
                }
                title = raw
                body.clear()
                body.append(HEADER).append(raw).append('\n')
            } else if (title != null) {
                body.append(line).append('\n')
            }
        }
        flush()
        return sections
    }
}
