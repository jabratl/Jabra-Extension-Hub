package com.jabra.hub

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity

class BrowserActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var urlBar: EditText
    private lateinit var chip: TextView
    private lateinit var panel: View
    private lateinit var panelList: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var downloader: Downloader
    private val found = LinkedHashMap<String, Media>()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)
        downloader = Downloader(applicationContext)

        web = findViewById(R.id.web)
        urlBar = findViewById(R.id.url)
        chip = findViewById(R.id.chip)
        panel = findViewById(R.id.panel)
        panelList = findViewById(R.id.panelList)
        progress = findViewById(R.id.progress)

        findViewById<View>(R.id.back).setOnClickListener { goBack() }
        findViewById<View>(R.id.close).setOnClickListener { finish() }
        findViewById<View>(R.id.panelClose).setOnClickListener { hidePanel() }
        chip.setOnClickListener { showPanel() }

        urlBar.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                go(v.text.toString())
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else false
        }

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url.toString()
                val k = MediaKind.of(u)
                if (k != null) onFound(u, k)
                return null
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                urlBar.setText(url)
                found.clear()
                updateChip()
                if (panel.visibility == View.VISIBLE) showPanel()
            }
        }
        web.setDownloadListener { u, _, _, _, _ ->
            onFound(u, "file")
            Toast.makeText(this, "Added to the found list", Toast.LENGTH_SHORT).show()
        }

        onBackPressedDispatcher.addCallback(this) {
            if (panel.visibility == View.VISIBLE) hidePanel() else goBack()
        }

        web.loadUrl(intent.getStringExtra("url") ?: "https://duckduckgo.com")
    }

    private fun goBack() {
        if (web.canGoBack()) web.goBack() else finish()
    }

    private fun go(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val target = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(" ") || !t.contains(".") -> "https://duckduckgo.com/?q=" + Uri.encode(t)
            else -> "https://$t"
        }
        web.loadUrl(target)
    }

    /** Called from a background thread by the WebView. */
    private fun onFound(url: String, kind: String) {
        runOnUiThread {
            if (found.containsKey(url)) return@runOnUiThread
            found[url] = Media(url, kind, web.url ?: "", web.title ?: "")
            updateChip()
            if (panel.visibility == View.VISIBLE) showPanel()
        }
    }

    private fun updateChip() {
        chip.text = "🎬 ${found.size} found"
        chip.visibility = if (found.isNotEmpty() && panel.visibility != View.VISIBLE) View.VISIBLE else View.GONE
    }

    private fun hidePanel() {
        panel.visibility = View.GONE
        updateChip()
    }

    private fun showPanel() {
        panel.visibility = View.VISIBLE
        chip.visibility = View.GONE
        panelList.removeAllViews()
        if (found.isEmpty()) {
            panelList.addView(TextView(this).apply {
                text = "Nothing found yet. Play a video on the page and it will show up here."
                setTextColor(getColor(R.color.br_sub))
                textSize = 14f
            })
            return
        }
        for (m in found.values.reversed()) {
            val v = layoutInflater.inflate(R.layout.item_media, panelList, false)
            val seg = Uri.parse(m.url).lastPathSegment ?: m.url
            v.findViewById<TextView>(R.id.mname).text = seg
            v.findViewById<TextView>(R.id.msub).text = "${m.kind} - ${Uri.parse(m.url).host ?: ""}"
            val status = v.findViewById<TextView>(R.id.mstatus)
            val btn = v.findViewById<TextView>(R.id.mbtn)
            btn.setOnClickListener {
                btn.isEnabled = false
                btn.alpha = 0.4f
                status.visibility = View.VISIBLE
                status.text = "Starting..."
                downloader.start(m, web.settings.userAgentString) { s, done ->
                    runOnUiThread {
                        status.text = s
                        if (done) {
                            btn.isEnabled = true
                            btn.alpha = 1f
                        }
                    }
                }
            }
            panelList.addView(v)
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
