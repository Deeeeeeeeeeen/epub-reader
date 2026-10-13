package app.reader

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubTest {
    private val container = "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>"""

    private fun zip(vararg entries: Pair<String, String>): File = File.createTempFile("test", ".epub").also { f ->
        ZipOutputStream(f.outputStream()).use { z ->
            (listOf(container) + entries).forEach { (n, s) -> z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
        }
    }

    private fun opf(manifest: String, spine: String) = "OEBPS/content.opf" to
        """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Test Book</dc:title></metadata><manifest>$manifest</manifest><spine>$spine</spine></package>"""

    private fun sample() = zip(
        opf("""<item id="a" href="a.xhtml"/><item id="b" href="text/b%20c.xhtml"/>""", """<itemref idref="b"/><itemref idref="a"/>"""),
        "OEBPS/a.xhtml" to "<html/>", "OEBPS/text/b c.xhtml" to "<html/>")

    @Test fun titleAndSpineOrder() = Epub(sample()).use {
        assertEquals("Test Book", it.title)
        assertEquals(listOf("OEBPS/text/b c.xhtml", "OEBPS/a.xhtml"), it.spine)
    }

    @Test fun opensEntries() = Epub(sample()).use {
        assertNotNull(it.open("OEBPS/a.xhtml"))
        assertNull(it.open("nope"))
    }

    @Test fun resolvesRelativePaths() {
        assertEquals("OEBPS/img/x.png", resolve("OEBPS/text", "../img/x.png"))
        assertEquals("a.xhtml", resolve("", "./a.xhtml"))
    }

    @Test fun noTocIsEmpty() = Epub(sample()).use { assertTrue(it.toc.isEmpty()) }

    @Test fun epub3NavToc() = Epub(zip(
        opf("""<item id="a" href="a.xhtml"/><item id="b" href="b.xhtml"/><item id="n" href="nav.xhtml" properties="nav"/>""",
            """<itemref idref="a"/><itemref idref="b"/>"""),
        "OEBPS/nav.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="landmarks"><ol><li><a href="a.xhtml">Cover</a></li></ol></nav><nav epub:type="toc"><ol><li><a href="a.xhtml">One</a></li><li><a href="b.xhtml#s2">Two</a><ol><li><a href="b.xhtml#s3"> Three
 </a></li></ol></li></ol></nav></body></html>""")).use {
        assertEquals(listOf(TocEntry("One", 0, ""), TocEntry("Two", 1, "s2"), TocEntry("Three", 1, "s3")), it.toc)
    }

    @Test fun epub2NcxToc() = Epub(zip(
        opf("""<item id="a" href="a.xhtml"/><item id="b" href="text/b.xhtml"/><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""",
            """<itemref idref="a"/><itemref idref="b"/>"""),
        "OEBPS/toc.ncx" to """<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap><navPoint id="1"><navLabel><text>One</text></navLabel><content src="a.xhtml"/><navPoint id="2"><navLabel><text>Two</text></navLabel><content src="text/b.xhtml#x"/></navPoint></navPoint></navMap></ncx>""")).use {
        assertEquals(listOf(TocEntry("One", 0, ""), TocEntry("Two", 1, "x")), it.toc)
    }

    @Test fun injectsStyleBeforeHeadEnd() {
        assertEquals("<html><head><style>c</style></HEAD><body/></html>", injectStyle("<html><head></HEAD><body/></html>", "c"))
    }

    @Test fun injectsStyleWithoutHead() {
        assertEquals("<style>c</style><p>x</p>", injectStyle("<p>x</p>", "c"))
    }
}
