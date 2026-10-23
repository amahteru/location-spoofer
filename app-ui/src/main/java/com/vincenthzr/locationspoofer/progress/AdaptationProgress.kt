package com.vincenthzr.locationspoofer.progress

import com.vincenthzr.locationspoofer.ui.screen.MarkdownBlock
import com.vincenthzr.locationspoofer.ui.screen.parseMarkdownBlocks

/**
 * 从 ADAPTATION_PROGRESS.md（或英文版 ADAPTATION_PROGRESS_EN.md）里查出"本机"相关的条目。文档是给人看的 Markdown，
 * 这里按它的固定结构取数："## 全局方案 / Global scheme" 下 "### 按系统 / By system" 的表格（第一列是系统名）。
 * 文档结构变了查不到时返回 null，界面按"未验证"显示，完整进度页照常显示全文。
 */
object AdaptationProgress {

    enum class Status { VERIFIED, PARTIAL, BROKEN, UNVERIFIED }

    data class SystemEntry(
        val system: String,
        val status: Status,
        val verifiedVersions: String,
        val note: String
    )

    fun parseStatus(cell: String): Status = when {
        cell.contains("✅") -> Status.VERIFIED
        cell.contains("⚠") -> Status.PARTIAL
        cell.contains("❌") -> Status.BROKEN
        else -> Status.UNVERIFIED
    }

    /** 全局方案"按系统"表里第一列包含任一关键字的行 */
    fun findGlobalSystem(markdown: String, keywords: List<String>): SystemEntry? {
        val table = tableUnder(markdown, section = listOf("全局方案", "Global scheme"), subsection = listOf("按系统", "By system"))
            ?: return null
        val header = table.header
        fun column(vararg names: String) = header.indexOfFirst { h -> names.any { h.contains(it, ignoreCase = true) } }
        val status = column("状态", "Status")
        val versions = column("版本", "Version")
        val note = column("备注", "Notes")
        val row = table.rows.firstOrNull { row ->
            val name = row.getOrElse(0) { "" }
            keywords.any { name.contains(it, ignoreCase = true) }
        } ?: return null
        fun cell(i: Int) = row.getOrNull(i)?.takeIf { it != "—" && it != "-" }.orEmpty()
        return SystemEntry(row[0], parseStatus(cell(status)), cell(versions), cell(note))
    }

    /** 某个二级标题（及可选的三级标题）下的第一张表；标题按前缀匹配，传入各语言的写法 */
    private fun tableUnder(markdown: String, section: List<String>, subsection: List<String>?): MarkdownBlock.Table? {
        var inSection = false
        var inSubsection = subsection == null
        for (block in parseMarkdownBlocks(markdown)) {
            when {
                block is MarkdownBlock.Header && block.level <= 2 -> {
                    inSection = block.level == 2 && section.any { block.text.startsWith(it) }
                    inSubsection = subsection == null
                }
                block is MarkdownBlock.Header && block.level == 3 && inSection && subsection != null ->
                    inSubsection = subsection.any { block.text.startsWith(it) }
                block is MarkdownBlock.Table && inSection && inSubsection -> return block
            }
        }
        return null
    }
}
