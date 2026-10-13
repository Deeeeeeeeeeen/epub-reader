package app.reader

import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

data class TocEntry(val label: String, val spine: Int, val fragment: String)

class Epub(file: File) : AutoCloseable {
    private val zip = ZipFile(file)
    private val tocPath: String?
    private val tocIsNav: Boolean
    val title: String
    val spine: List<String>
    val toc: List<TocEntry> by lazy { runCatching { readToc() }.getOrDefault(emptyList()) }

    init {
        try {
            val opfPath = xml("META-INF/container.xml").elements("rootfile").firstOrNull()
                ?.getAttribute("full-path") ?: error("No rootfile")
            val opf = xml(opfPath)
            val dir = opfPath.substringBeforeLast('/', "")
            val items = opf.elements("item")
            val manifest = items.associate { it.getAttribute("id") to it.getAttribute("href") }
            title = opf.elements("title").firstOrNull()?.textContent?.trim()
                .takeUnless { it.isNullOrEmpty() } ?: file.nameWithoutExtension
            spine = opf.elements("itemref").mapNotNull { manifest[it.getAttribute("idref")] }
                .map { resolve(dir, decode(it)) }
            require(spine.isNotEmpty()) { "Empty spine" }
            val nav = items.firstOrNull { "nav" in it.getAttribute("properties").split(' ') }
            val ncx = items.firstOrNull { it.getAttribute("media-type") == "application/x-dtbncx+xml" }
            tocIsNav = nav != null
            tocPath = (nav ?: ncx)?.let { resolve(dir, decode(it.getAttribute("href"))) }
        } catch (e: Exception) {
            zip.close()
            throw e
        }
    }

    fun open(path: String): InputStream? = zip.getEntry(path)?.let { zip.getInputStream(it) }

    override fun close() = zip.close()

    private fun readToc(): List<TocEntry> {
        val path = tocPath ?: return emptyList()
        val dir = path.substringBeforeLast('/', "")
        val doc = xml(path)
        val raw: List<Pair<String, String>> = if (tocIsNav) {
            val navs = doc.elements("nav")
            val nav = navs.firstOrNull { it.getAttributeNS("http://www.idpf.org/2007/ops", "type") == "toc" }
                ?: navs.firstOrNull() ?: return emptyList()
            nav.elements("a").map { clean(it.textContent) to it.getAttribute("href") }
        } else {
            doc.elements("navPoint").map { p ->
                clean(p.elements("text").first().textContent) to p.elements("content").first().getAttribute("src")
            }
        }
        return raw.mapNotNull { (label, href) ->
            val i = spine.indexOf(resolve(dir, decode(href.substringBefore('#'))))
            if (i < 0 || label.isEmpty()) null else TocEntry(label, i, href.substringAfter('#', ""))
        }
    }

    private fun xml(path: String): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(open(path) ?: error("Missing $path"))

    private fun Document.elements(name: String) = getElementsByTagNameNS("*", name).toElements()
    private fun Element.elements(name: String) = getElementsByTagNameNS("*", name).toElements()
    private fun NodeList.toElements() = (0 until length).map { item(it) as Element }
}

private fun decode(s: String) = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

private fun clean(s: String) = s.trim().replace(Regex("\\s+"), " ")

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

fun injectStyle(html: String, css: String): String {
    val tag = "<style>$css</style>"
    val i = html.indexOf("</head>", ignoreCase = true)
    return if (i >= 0) html.substring(0, i) + tag + html.substring(i) else tag + html
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
