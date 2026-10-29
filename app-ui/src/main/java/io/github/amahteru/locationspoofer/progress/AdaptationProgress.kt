package io.github.amahteru.locationspoofer.progress

import io.github.amahteru.locationspoofer.ui.screen.MarkdownBlock
import io.github.amahteru.locationspoofer.ui.screen.parseMarkdownBlocks

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
