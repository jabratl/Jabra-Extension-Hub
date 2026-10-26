package com.jabra.hub

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.util.Date

class BrowserActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var urlBar: EditText
    private lateinit var chip: TextView
    private lateinit var panel: View
    private lateinit var panelList: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var downloader: Downloader
    private val found = LinkedHashMap<String, Media>()

    private var adblock = true
    private var desktop = false
    private var blockedCount = 0
    private var defaultUa = ""

    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    private val desktopUa =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** Bridge used by the page script to report media. Only adds to the "found" list. */
    inner class Sniff {
        @JavascriptInterface
        fun found(u: String, hint: String) {
            if (!(u.startsWith("http://") || u.startsWith("https://"))) return
            var k = kindLoose(u)
            if (k == null && hint == "av") k = "video"
            if (k == null) return
            if (adblock && AdBlock.isBlocked(u, Uri.parse(u).host)) return
            onFound(u, k)
        }

        @JavascriptInterface
        fun adblock(): Boolean = this@BrowserActivity.adblock
    }

    private fun kindLoose(u: String): String? =
        MediaKind.of(u) ?: if (u.contains(".m3u8", ignoreCase = true)) "hls" else null

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)
        downloader = Downloader(applicationContext)
        adblock = BrowserStore.adblock(this)
        desktop = BrowserStore.desktop(this)

        web = findViewById(R.id.web)
        urlBar = findViewById(R.id.url)
        chip = findViewById(R.id.chip)
        panel = findViewById(R.id.panel)
        panelList = findViewById(R.id.panelList)
        progress = findViewById(R.id.progress)

        findViewById<View>(R.id.back).setOnClickListener { goBack() }
        findViewById<View>(R.id.close).setOnClickListener { finish() }
        findViewById<View>(R.id.menu).setOnClickListener { showMenu(it) }
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
        web.settings.setSupportMultipleWindows(true)
        web.settings.builtInZoomControls = true
        web.settings.displayZoomControls = false
        defaultUa = web.settings.userAgentString
        applyDesktop()
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.addJavascriptInterface(Sniff(), "JabraSniff")

        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(web, AdBlock.PAGE_SCRIPT, setOf("*"))
            }
        } catch (_: Exception) {
            // Falls back to the onPageFinished injection below.
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                // Pop-ups / pop-unders the user did not ask for are dropped.
                if (adblock && !isUserGesture) {
                    blockedCount++
                    return false
                }
                // A real tap on a "new window" link opens in this same view.
                val temp = WebView(this@BrowserActivity)
                temp.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        val u = request.url.toString()
                        if ((u.startsWith("http://") || u.startsWith("https://")) &&
                            !(adblock && AdBlock.isAdHost(request.url.host))
                        ) {
                            web.post { web.loadUrl(u) }
                        } else if (adblock) {
                            blockedCount++
                        }
                        web.post { temp.destroy() }
                        return true
                    }
                }
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = temp
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customCb = callback
                (window.decorView as FrameLayout).addView(
                    view,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                val c = WindowInsetsControllerCompat(window, window.decorView)
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url.toString()
                if (adblock && !request.isForMainFrame && AdBlock.isBlocked(u, request.url.host)) {
                    blockedCount++
                    return AdBlock.empty()
                }
                val k = kindLoose(u)
                if (k != null) onFound(u, k)
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: ""
                // intent:, market:, custom app schemes: stay in the page instead of showing an error.
                if (scheme != "http" && scheme != "https") return true
                // Automatic redirects to ad networks (no tap involved).
                if (adblock && !request.hasGesture() && AdBlock.isAdHost(request.url.host)) {
                    blockedCount++
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                urlBar.setText(url)
                found.clear()
                blockedCount = 0
                updateChip()
                if (panel.visibility == View.VISIBLE) showPanel()
            }

            override fun onPageFinished(view: WebView, url: String) {
                // Fallback for WebViews without document-start scripts (the script guards against running twice).
                view.evaluateJavascript(AdBlock.PAGE_SCRIPT, null)
                BrowserStore.addHistory(this@BrowserActivity, url, view.title ?: "")
                BrowserStore.setLastUrl(this@BrowserActivity, url)
            }
        }
        web.setDownloadListener { u, _, _, _, _ ->
            onFound(u, "file")
            Toast.makeText(this, "Added to the found list", Toast.LENGTH_SHORT).show()
        }

        onBackPressedDispatcher.addCallback(this) {
            when {
                customView != null -> exitFullscreen()
                panel.visibility == View.VISIBLE -> hidePanel()
                else -> goBack()
            }
        }

        val start = intent.getStringExtra("url")
            ?: BrowserStore.lastUrl(this)
            ?: "https://duckduckgo.com"
        web.loadUrl(start)
    }

    private fun applyDesktop() {
        web.settings.userAgentString = if (desktop) desktopUa else defaultUa
        web.settings.useWideViewPort = desktop
        web.settings.loadWithOverviewMode = desktop
    }

    private fun exitFullscreen() {
        val v = customView ?: return
        (window.decorView as FrameLayout).removeView(v)
        customView = null
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        customCb?.onCustomViewHidden()
        customCb = null
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

    // ---------- menu ----------

    private fun showMenu(anchor: View) {
        val pageUrl = web.url ?: ""
        val marked = pageUrl.isNotEmpty() && BrowserStore.isBookmarked(this, pageUrl)
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, if (marked) "★ Remove bookmark" else "☆ Add bookmark")
        m.menu.add(0, 2, 1, "Bookmarks")
        m.menu.add(0, 3, 2, "History")
        m.menu.add(0, 4, 3, "Forward")
        m.menu.add(0, 5, 4, "Reload")
        val ab = m.menu.add(0, 6, 5, "Ad blocker" + if (adblock) " (blocked $blockedCount)" else "")
        ab.isCheckable = true
        ab.isChecked = adblock
        val dk = m.menu.add(0, 7, 6, "Desktop site")
        dk.isCheckable = true
        dk.isChecked = desktop
        val sh = m.menu.add(0, 8, 7, "Save history")
        sh.isCheckable = true
        sh.isChecked = BrowserStore.saveHistory(this)
        m.menu.add(0, 9, 8, "Share link")
        m.menu.add(0, 10, 9, "Copy link")
        m.menu.add(0, 11, 10, "Clear browsing data")
        m.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> if (pageUrl.startsWith("http")) {
                    val now = BrowserStore.toggleBookmark(this, pageUrl, web.title ?: "")
                    Toast.makeText(this, if (now) "Bookmarked" else "Bookmark removed", Toast.LENGTH_SHORT).show()
                }
                2 -> showList("Bookmarks", BrowserStore.bookmarks(this), "Remove all") { BrowserStore.clearBookmarks(this) }
                3 -> showList("History", BrowserStore.history(this), "Clear all") { BrowserStore.clearHistory(this) }
                4 -> if (web.canGoForward()) web.goForward()
                5 -> web.reload()
                6 -> {
                    adblock = !adblock
                    BrowserStore.setAdblock(this, adblock)
                    Toast.makeText(this, if (adblock) "Ad blocker on" else "Ad blocker off", Toast.LENGTH_SHORT).show()
                    web.reload()
                }
                7 -> {
                    desktop = !desktop
                    BrowserStore.setDesktop(this, desktop)
                    applyDesktop()
                    web.reload()
                }
                8 -> BrowserStore.setSaveHistory(this, !BrowserStore.saveHistory(this))
                9 -> if (pageUrl.isNotEmpty()) {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, pageUrl)
                    startActivity(Intent.createChooser(i, "Share link"))
                }
                10 -> if (pageUrl.isNotEmpty()) {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("link", pageUrl))
                    Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
                }
                11 -> confirmClear()
            }
            true
        }
        m.show()
    }

    private fun showList(title: String, list: List<BrowserEntry>, clearLabel: String, onClear: () -> Unit) {
        if (list.isEmpty()) {
            Toast.makeText(this, "$title is empty", Toast.LENGTH_SHORT).show()
            return
        }
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val labels = list.map {
            val name = if (it.title.isBlank()) it.url else it.title
            name + "\n" + (Uri.parse(it.url).host ?: "") + "  ·  " + df.format(Date(it.ts))
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(labels) { _, i -> web.loadUrl(list[i].url) }
            .setNegativeButton("Close", null)
            .setNeutralButton(clearLabel) { _, _ -> onClear() }
            .show()
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear browsing data?")
            .setMessage("Deletes history, cache, cookies and site data. Bookmarks are kept. You will be signed out of sites.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                BrowserStore.clearHistory(this)
                web.clearCache(true)
                web.clearHistory()
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
                Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ---------- found media ----------

    /** Called from a background thread by the WebView. */
    private fun onFound(url: String, kind: String) {
        runOnUiThread {
            if (found.containsKey(url) || found.size >= 200) return@runOnUiThread
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
            val play = v.findViewById<TextView>(R.id.mplay)
            val playable = m.kind == "hls" || m.kind == "video" || m.kind == "audio"
            play.visibility = if (playable) View.VISIBLE else View.GONE
            play.setOnClickListener {
                startActivity(
                    Intent(this, PlayerActivity::class.java)
                        .putExtra("url", m.url)
                        .putExtra("referer", m.page)
                        .putExtra("title", m.title)
                        .putExtra("ua", web.settings.userAgentString)
                )
            }
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

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
