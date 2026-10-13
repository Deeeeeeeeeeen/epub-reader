package app.reader

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import java.io.ByteArrayInputStream

private const val HOST = "epub.local"

class ReaderActivity : Activity() {
    private lateinit var id: String
    private lateinit var lib: Library
    private lateinit var epub: Epub
    private lateinit var web: WebView
    private var index = 0
    private var pendingFrac = 0f

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        id = intent.getStringExtra("id")!!
        lib = Library(this)
        epub = Epub(lib.file(id))
        title = epub.title

        web = WebView(this).apply {
            settings.textZoom = lib.zoom
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse {
                    val path = r.url.path.orEmpty().removePrefix("/")
                    val s = epub.open(path)
                    return if (s != null) WebResourceResponse(mime(path), null, s)
                    else WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, ByteArrayInputStream(ByteArray(0)))
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

    private fun range() = (web.contentHeight * resources.displayMetrics.density - web.height).coerceAtLeast(1f)

    private fun load(i: Int, frac: Float = 0f) {
        index = i
        pendingFrac = frac
        web.loadUrl(Uri.Builder().scheme("https").authority(HOST).path("/" + epub.spine[i]).build().toString())
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
        lib.savePosition(id, index, (web.scrollY / range()).coerceIn(0f, 1f))
    }

    override fun onDestroy() {
        super.onDestroy()
        web.destroy()
        epub.close()
    }
}
