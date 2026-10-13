package app.reader

import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

class Epub(file: File) : AutoCloseable {
    private val zip = ZipFile(file)
    val title: String
    val spine: List<String>

    init {
        try {
            val opfPath = xml("META-INF/container.xml").elements("rootfile").firstOrNull()
                ?.getAttribute("full-path") ?: error("No rootfile")
            val opf = xml(opfPath)
            val dir = opfPath.substringBeforeLast('/', "")
            val manifest = opf.elements("item").associate { it.getAttribute("id") to it.getAttribute("href") }
            title = opf.elements("title").firstOrNull()?.textContent?.trim()
                .takeUnless { it.isNullOrEmpty() } ?: file.nameWithoutExtension
            spine = opf.elements("itemref").mapNotNull { manifest[it.getAttribute("idref")] }
                .map { resolve(dir, URLDecoder.decode(it.replace("+", "%2B"), "UTF-8")) }
            require(spine.isNotEmpty()) { "Empty spine" }
        } catch (e: Exception) {
            zip.close()
            throw e
        }
    }

    fun open(path: String): InputStream? = zip.getEntry(path)?.let { zip.getInputStream(it) }

    override fun close() = zip.close()

    private fun xml(path: String): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(open(path) ?: error("Missing $path"))

    private fun Document.elements(name: String): List<Element> =
        getElementsByTagNameNS("*", name).let { n -> (0 until n.length).map { n.item(it) as Element } }
}

fun resolve(dir: String, href: String): String {
    val out = ArrayDeque<String>()
    (if (dir.isEmpty()) href else "$dir/$href").split('/').forEach {
        when (it) {
            "", "." -> {}
            ".." -> out.removeLastOrNull()
            else -> out.addLast(it)
        }
    }
    return out.joinToString("/")
}

fun mime(path: String) = when (path.substringAfterLast('.').lowercase()) {
    "xhtml", "xht" -> "application/xhtml+xml"
    "html", "htm" -> "text/html"
    "css" -> "text/css"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "svg" -> "image/svg+xml"
    "webp" -> "image/webp"
    "ttf" -> "font/ttf"
    "otf" -> "font/otf"
    "woff" -> "font/woff"
    "woff2" -> "font/woff2"
    else -> "application/octet-stream"
}
