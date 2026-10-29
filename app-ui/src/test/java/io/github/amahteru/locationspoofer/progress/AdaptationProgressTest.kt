package io.github.amahteru.locationspoofer.progress

import io.github.amahteru.locationspoofer.ui.screen.MarkdownBlock
import io.github.amahteru.locationspoofer.ui.screen.parseMarkdownBlocks
import io.github.amahteru.locationspoofer.vendor.RomFamily
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptationProgressTest {
    private val doc = File("../ADAPTATION_PROGRESS.md").readText()

    @Test
    fun `tables and horizontal rules are parsed instead of shown as raw text`() {
        val blocks = parseMarkdownBlocks(doc)
        assertTrue(blocks.none { it is MarkdownBlock.Paragraph && (it.text.startsWith("|") || it.text == "---") })
        val first = blocks.filterIsInstance<MarkdownBlock.Table>().first()
        assertEquals(listOf("系统", "适配器", "状态", "已验证的系统版本", "备注"), first.header)
        assertTrue(first.rows.none { row -> row.all { it.matches(Regex("-+")) } })
    }

    @Test
    fun `finds the global status of each system family`() {
        val hyperOs = AdaptationProgress.findGlobalSystem(doc, RomFamily.HYPEROS_MIUI.progressKeywords)!!
        assertEquals(AdaptationProgress.Status.VERIFIED, hyperOs.status)
        assertEquals("HyperOS 4、HyperOS 3", hyperOs.verifiedVersions)

        val colorOs = AdaptationProgress.findGlobalSystem(doc, RomFamily.COLOROS.progressKeywords)!!
        assertEquals(AdaptationProgress.Status.PARTIAL, colorOs.status)
        assertEquals("ColorOS 16 / Android 16", colorOs.verifiedVersions)

        val oneUi = AdaptationProgress.findGlobalSystem(doc, RomFamily.ONEUI.progressKeywords)!!
        assertEquals(AdaptationProgress.Status.UNVERIFIED, oneUi.status)
        assertEquals("", oneUi.verifiedVersions)

        for (family in RomFamily.entries) {
            val entry = AdaptationProgress.findGlobalSystem(doc, family.progressKeywords)
            assertTrue("$family should have a row", entry != null)
        }
    }

    @Test
    fun `english edition matches the chinese status of every system family`() {
        val en = File("../ADAPTATION_PROGRESS_EN.md").readText()
        for (family in RomFamily.entries) {
            val zhEntry = AdaptationProgress.findGlobalSystem(doc, family.progressKeywords)
            val enEntry = AdaptationProgress.findGlobalSystem(en, family.progressKeywords)
            assertTrue("$family should have a row in the english edition", enEntry != null)
            assertEquals("$family status", zhEntry?.status, enEntry?.status)
        }
        assertEquals("HyperOS 4, HyperOS 3", AdaptationProgress.findGlobalSystem(en, RomFamily.HYPEROS_MIUI.progressKeywords)!!.verifiedVersions)
    }

    @Test
    fun `unknown structure degrades gracefully`() {
        assertNull(AdaptationProgress.findGlobalSystem("# 适配进度\n\n没有表格", listOf("HyperOS")))
    }
}
