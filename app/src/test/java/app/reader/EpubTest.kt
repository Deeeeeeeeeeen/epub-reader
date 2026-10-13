package app.reader

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EpubTest {
    private fun sample(): File {
        val f = File.createTempFile("test", ".epub")
        ZipOutputStream(f.outputStream()).use { z ->
            fun put(n: String, s: String) { z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
            put("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""")
            put("OEBPS/content.opf", """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Test Book</dc:title></metadata><manifest><item id="a" href="a.xhtml"/><item id="b" href="text/b%20c.xhtml"/></manifest><spine><itemref idref="b"/><itemref idref="a"/></spine></package>""")
            put("OEBPS/a.xhtml", "<html/>")
            put("OEBPS/text/b c.xhtml", "<html/>")
        }
        return f
    }

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
}
