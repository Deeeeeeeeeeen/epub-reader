package app.reader

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import java.io.ByteArrayInputStream

private const val HOST = "epub.local"
private const val DARK_CSS = "*{background-color:#121212!important;color:#ddd!important;border-color:#444!important}a,a *{color:#8ab4f8!important}"

class ReaderActivity : Activity() {
    private lateinit var id: String
    private lateinit var lib: Library
    private lateinit var epub: Epub
    private lateinit var web: WebView
    private var index = 0
    private var pendingFrac = 0f

    override fun onCreate(b: Bundle?) {
        id = intent.getStringExtra("id")!!
        lib = Library(this)
        applyTheme(lib.dark)
        super.onCreate(b)
        epub = Epub(lib.file(id))
        title = epub.title

        web = WebView(this).apply {
            keepScreenOn = true
            setBackgroundColor(Color.parseColor(if (lib.dark) "#121212" else "#FFFFFF"))
            settings.textZoom = lib.zoom
            setOnScrollChangeListener { _, _, _, _, _ -> showProgress() }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse {
                    val path = r.url.path.orEmpty().removePrefix("/")
                    val s = epub.open(path)
                    val m = mime(path)
                    return when {
                        s == null -> WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, ByteArrayInputStream(ByteArray(0)))
                        lib.dark && (m == "application/xhtml+xml" || m == "text/html") -> {
                            val html = injectStyle(s.use { it.readBytes() }.toString(Charsets.UTF_8), DARK_CSS)
                            WebResourceResponse(m, "utf-8", ByteArrayInputStream(html.toByteArray()))
                        }
                        else -> WebResourceResponse(m, null, s)
                    }
                }

                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = r.url.host != HOST

                override fun onPageFinished(v: WebView, url: String) {
                    val p = Uri.parse(url).path.orEmpty().removePrefix("/")
                    epub.spine.indexOf(p).takeIf { it >= 0 }?.let { index = it }
                    if (pendingFrac > 0) {
                        val f = pendingFrac
                        pendingFrac = 0f
                        v.post { v.scrollTo(0, (f * range()).toInt()) }
                    }
                    showProgress()
                }
            }
        }

        fun btn(t: String, a: () -> Unit) = Button(this).apply { text = t; setOnClickListener { a() } }
        fun lp() = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        val bar = LinearLayout(this).apply {
            addView(btn("‹ Prev") { go(-1) }, lp())
            addView(btn("A−") { zoom(-10) }, lp())
            addView(btn("A+") { zoom(10) }, lp())
            addView(btn("Next ›") { go(1) }, lp())
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })

        val (i, f) = lib.position(id)
        load(i.coerceIn(0, epub.spine.lastIndex), f)
    }

    override fun onCreateOptionsMenu(m: Menu): Boolean {
        m.add(0, 1, 0, "Contents").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        m.add(0, 2, 1, if (lib.dark) "Light" else "Dark").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(i: MenuItem): Boolean {
        if (i.itemId == 1) showToc() else { lib.dark = !lib.dark; recreate() }
        return true
    }

    private fun showToc() {
        val toc = epub.toc
        if (toc.isEmpty()) { Toast.makeText(this, "No contents", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Contents")
            .setItems(toc.map { it.label }.toTypedArray()) { _, i -> load(toc[i].spine, 0f, toc[i].fragment) }
            .show()
    }

    private fun range() = (web.contentHeight * resources.displayMetrics.density - web.height).coerceAtLeast(1f)

    private fun frac() = (web.scrollY / range()).coerceIn(0f, 1f)

    private fun pct() = ((index + frac()) / epub.spine.size * 100).toInt()

    private fun showProgress() { actionBar?.subtitle = "${pct()}%" }

    private fun load(i: Int, frac: Float = 0f, fragment: String = "") {
        index = i
        pendingFrac = frac
        val u = Uri.Builder().scheme("https").authority(HOST).path("/" + epub.spine[i])
        if (fragment.isNotEmpty()) u.fragment(fragment)
        web.loadUrl(u.build().toString())
    }

    private fun go(d: Int) {
        val i = index + d
        if (i in epub.spine.indices) load(i)
    }

    private fun zoom(d: Int) {
        web.settings.textZoom = (web.settings.textZoom + d).coerceIn(60, 200)
        lib.zoom = web.settings.textZoom
    }

    override fun onPause() {
        super.onPause()
        lib.savePosition(id, index, frac(), pct())
    }

    override fun onDestroy() {
        super.onDestroy()
        web.destroy()
        epub.close()
    }
}
