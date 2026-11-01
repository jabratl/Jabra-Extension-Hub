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
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
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
import android.webkit.ValueCallback
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BrowserActivity : AppCompatActivity() {

    /** One browser tab: its WebView and the media found on its page. (Hub 0.9.7) */
    internal class Tab(val web: WebView) {
        val found = LinkedHashMap<String, Media>()
        /** Address restored from the last session; loaded the first time the tab is shown. */
        var pending: String? = null
        var savedTitle: String = ""
    }

    private val tabs = ArrayList<Tab>()
    private var curTab: Tab? = null
    private val maxTabs = 8
    private var webHost: ViewGroup? = null
    private var webIdx = 0
    private var webId = View.NO_ID
    private var webLp: ViewGroup.LayoutParams? = null
    private var tabBtn: TextView? = null

    /** The WebView of the current tab. Older code keeps using "web" and "found" unchanged. */
    private val web: WebView get() = curTab!!.web
    private lateinit var urlBar: EditText
    private lateinit var chip: TextView
    private lateinit var panel: View
    private lateinit var panelList: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var downloader: Downloader
    private val found: LinkedHashMap<String, Media> get() = curTab!!.found

    // find in page, dark pages (Hub 0.9.10)
    private var findBar: LinearLayout? = null
    private var findInput: EditText? = null
    private var findCount: TextView? = null
    private var darkPage = false

    // address-bar suggestions (Hub 0.9.9)
    private var suggestPopup: ListPopupWindow? = null
    private var suggestItems: List<BrowserEntry> = emptyList()
    private var suppressSuggest = false

    private var adblock = true
    private var desktop = false
    private var blockedCount = 0
    private var defaultUa = ""

    private var fileCb: ValueCallback<Array<Uri>>? = null
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        fileCb?.onReceiveValue(if (uris.isNullOrEmpty()) null else uris.toTypedArray())
        fileCb = null
    }

    // ---------- bookmark export / import (Hub 0.9.6): Android file picker, no storage permission needed ----------
    private var bmOp = ""
    private val bmFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        val op = bmOp
        bmOp = ""
        if (res.resultCode == RESULT_OK && uri != null && op.isNotEmpty()) runBookmarkFile(op, uri)
    }

    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    private val desktopUa =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** Bridge used by the page script to report media. Only adds to the "found" list. */
    internal inner class Sniff(private val tab: Tab) {
        @JavascriptInterface
        fun found(u: String, hint: String) {
            if (!(u.startsWith("http://") || u.startsWith("https://"))) return
            var k = kindLoose(u)
            if (k == null && hint == "av") k = "video"
            if (k == null) return
            if (adblock && AdBlock.isBlocked(u, Uri.parse(u).host)) return
            onFound(tab, u, k)
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
        darkPage = BrowserStore.darkPage(this)

        val first = Tab(findViewById(R.id.web))
        tabs.add(first)
        curTab = first
        val host = first.web.parent as ViewGroup
        webHost = host
        webIdx = host.indexOfChild(first.web)
        webId = first.web.id
        webLp = first.web.layoutParams
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

        urlBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                showSuggest(s?.toString().orEmpty())
            }
        })
        urlBar.setOnFocusChangeListener { _, has ->
            if (has) urlBar.post { urlBar.selectAll() } else dismissSuggest()
        }

        setupTab(first)
        addTabButton()

        onBackPressedDispatcher.addCallback(this) {
            when {
                findBar != null -> hideFind()
                suggestPopup?.isShowing == true -> dismissSuggest()
                customView != null -> exitFullscreen()
                panel.visibility == View.VISIBLE -> hidePanel()
                else -> goBack()
            }
        }

        val ext = intent.getStringExtra("url")
        val saved = BrowserStore.savedTabs(this)
        if (saved.isEmpty()) {
            web.loadUrl(ext ?: BrowserStore.lastUrl(this) ?: NewTabPage.URL)
        } else {
            first.pending = saved[0].first
            first.savedTitle = saved[0].second
            for (i in 1 until saved.size) {
                if (newTab(saved[i].first, show = false, lazyLoad = true)) tabs.last().savedTitle = saved[i].second
            }
            val idx = BrowserStore.savedTabCur(this).coerceIn(0, tabs.size - 1)
            if (idx == 0) loadPending(first) else switchTo(tabs[idx])
            if (!ext.isNullOrBlank()) newTab(ext)
        }
    }

    /** Everything a tab needs: WebView settings, scripts, and its own clients. */
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupTab(t: Tab) {
        t.web.settings.javaScriptEnabled = true
        t.web.settings.domStorageEnabled = true
        t.web.settings.setSupportMultipleWindows(true)
        t.web.settings.builtInZoomControls = true
        t.web.settings.displayZoomControls = false
        if (defaultUa.isEmpty()) defaultUa = t.web.settings.userAgentString
        applyDesktop(t.web)
        t.web.settings.textZoom = BrowserStore.textZoom(this)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(t.web, true)
        t.web.addJavascriptInterface(Sniff(t), "JabraSniff")

        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(t.web, AdBlock.PAGE_SCRIPT, setOf("*"))
            }
        } catch (_: Exception) {
            // Falls back to the onPageFinished injection below.
        }

        t.web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (t === curTab) {
                    progress.progress = newProgress
                    progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                }
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
                            t.web.post { if (!newTab(u)) t.web.loadUrl(u) }
                        } else if (adblock) {
                            blockedCount++
                        }
                        t.web.post { temp.destroy() }
                        return true
                    }
                }
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = temp
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = filePathCallback
                return try {
                    picker.launch(arrayOf("*/*"))
                    true
                } catch (_: Exception) {
                    fileCb?.onReceiveValue(null)
                    fileCb = null
                    false
                }
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

        t.web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (NewTabPage.isNewTab(request.url.toString())) return NewTabPage.response(this@BrowserActivity, request.url)
                val u = request.url.toString()
                if (adblock && !request.isForMainFrame && AdBlock.isBlocked(u, request.url.host)) {
                    blockedCount++
                    return AdBlock.empty()
                }
                val k = kindLoose(u)
                if (k != null) onFound(t, u, k)
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
                t.found.clear()
                blockedCount = 0
                if (t === curTab) {
                    setUrlText(url)
                    updateChip()
                    if (panel.visibility == View.VISIBLE) showPanel()
                }
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                applyDark(view, url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                // Fallback for WebViews without document-start scripts (the script guards against running twice).
                view.evaluateJavascript(AdBlock.PAGE_SCRIPT, null)
                applyDark(view, url)
                if (!NewTabPage.isNewTab(url)) {
                    BrowserStore.addHistory(this@BrowserActivity, url, view.title ?: "")
                    if (t === curTab) BrowserStore.setLastUrl(this@BrowserActivity, url)
                }
            }
        }
        t.web.setDownloadListener { u, _, _, _, _ ->
            onFound(t, u, "file")
            Toast.makeText(this, "Added to the found list", Toast.LENGTH_SHORT).show()
        }
        // Long-press on a link (also a linked image): open in new tab / copy.
        t.web.setOnLongClickListener { v ->
            val type = (v as WebView).hitTestResult.type
            if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                val h = android.os.Handler(android.os.Looper.getMainLooper()) { m ->
                    val u = m.data.getString("url")
                    if (!u.isNullOrBlank() && (u.startsWith("http://") || u.startsWith("https://"))) showLinkMenu(u)
                    true
                }
                v.requestFocusNodeHref(h.obtainMessage())
                true
            } else false
        }
    }

    private fun applyDesktop(w: WebView) {
        w.settings.userAgentString = if (desktop) desktopUa else defaultUa
        w.settings.useWideViewPort = desktop
        w.settings.loadWithOverviewMode = desktop
    }

    private fun applyDesktopAll() {
        for (t in tabs) applyDesktop(t.web)
    }

    // ---------- tabs (Hub 0.9.7): only the current tab's WebView is attached; the others are paused ----------

    private fun updateTabBadge() {
        tabBtn?.text = "▢${tabs.size}"
    }

    /** Adds a small tab-count button next to the menu button (only if the top bar is a horizontal LinearLayout; the menu always has "Tabs"). */
    private fun addTabButton() {
        val menuBtn = findViewById<View>(R.id.menu)
        val par = menuBtn.parent
        if (par !is LinearLayout || par.orientation != LinearLayout.HORIZONTAL) return
        val dp = resources.displayMetrics.density
        val ref = (findViewById<View>(R.id.close) as? TextView) ?: (menuBtn as? TextView)
        val b = TextView(this)
        b.gravity = android.view.Gravity.CENTER
        b.textSize = 14f
        b.minWidth = (36 * dp).toInt()
        b.setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
        b.setTextColor(ref?.currentTextColor ?: 0xFFAAAAAA.toInt())
        b.setOnClickListener { showTabs() }
        par.addView(
            b,
            par.indexOfChild(menuBtn),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        tabBtn = b
        updateTabBadge()
    }

    /** Opens a tab (shown at once unless show = false). Returns false when the tab limit is reached. */
    private fun newTab(url: String?, show: Boolean = true, lazyLoad: Boolean = false): Boolean {
        if (webHost == null) return false
        if (tabs.size >= maxTabs) {
            Toast.makeText(this, "Tab limit ($maxTabs) reached. Close a tab first.", Toast.LENGTH_SHORT).show()
            return false
        }
        val w = WebView(this)
        w.id = webId
        val t = Tab(w)
        tabs.add(t)
        setupTab(t)
        if (show) switchTo(t) else updateTabBadge()
        if (!url.isNullOrBlank()) {
            if (lazyLoad) t.pending = url else w.loadUrl(url)
        }
        return true
    }

    private fun loadPending(t: Tab) {
        val u = t.pending ?: return
        t.pending = null
        t.web.loadUrl(u)
    }

    private fun switchTo(t: Tab) {
        val host = webHost ?: return
        val old = curTab
        if (old === t) return
        if (findBar != null) hideFind()
        if (old != null) {
            old.web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(m){try{m.pause()}catch(e){}})", null)
            old.web.onPause()
            host.removeView(old.web)
        }
        curTab = t
        if (t.web.parent == null) host.addView(t.web, webIdx, webLp)
        t.web.onResume()
        loadPending(t)
        setUrlText(t.web.url ?: t.pending ?: "")
        progress.visibility = View.GONE
        updateChip()
        if (panel.visibility == View.VISIBLE) showPanel()
        updateTabBadge()
    }

    private fun closeTab(t: Tab) {
        val i = tabs.indexOf(t)
        if (i < 0) return
        if (tabs.size == 1) {
            // The last tab is never removed: it goes back to a fresh page.
            t.found.clear()
            t.web.loadUrl(NewTabPage.URL)
            t.web.clearHistory()
            updateChip()
            Toast.makeText(this, "Last tab: opened a fresh page", Toast.LENGTH_SHORT).show()
            return
        }
        val next = tabs[if (i > 0) i - 1 else 1]
        if (t === curTab) switchTo(next)
        tabs.remove(t)
        (t.web.parent as? ViewGroup)?.removeView(t.web)
        t.web.destroy()
        updateTabBadge()
    }

    private fun showTabs() {
        val dp = resources.displayMetrics.density
        val pad = (14 * dp).toInt()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(this)
        sv.addView(box)
        var dlg: androidx.appcompat.app.AlertDialog? = null
        fun fill() {
            box.removeAllViews()
            for (t in tabs.toList()) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = android.view.Gravity.CENTER_VERTICAL
                val title = t.web.title?.takeIf { it.isNotBlank() } ?: t.savedTitle.takeIf { it.isNotBlank() } ?: t.web.url ?: t.pending ?: "New tab"
                val label = TextView(this)
                label.text = (if (t === curTab) "● " else "") + title + "\n" + tabHost(t)
                label.textSize = 15f
                label.setPadding(pad, pad, pad, pad)
                label.setOnClickListener {
                    dlg?.dismiss()
                    switchTo(t)
                }
                val x = TextView(this)
                x.text = "✕"
                x.textSize = 18f
                x.setPadding(pad, pad, pad, pad)
                x.setOnClickListener {
                    closeTab(t)
                    fill()
                }
                row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(x)
                box.addView(row)
            }
        }
        fill()
        dlg = MaterialAlertDialogBuilder(this)
            .setTitle("Tabs (${tabs.size})")
            .setView(sv)
            .setNegativeButton("Close", null)
            .setPositiveButton("New tab") { _, _ -> newTab(NewTabPage.URL) }
            .show()
    }

    private fun showLinkMenu(u: String) {
        val items = arrayOf("Open in new tab", "Open in this tab", "Copy link")
        MaterialAlertDialogBuilder(this)
            .setTitle(u.take(80))
            .setItems(items) { _, i ->
                when (i) {
                    0 -> {
                        if (!newTab(u)) web.loadUrl(u)
                    }
                    1 -> web.loadUrl(u)
                    2 -> {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("link", u))
                        Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
                    }
                    else -> Unit
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** A link sent to the browser while it is already open (singleTop) opens as a new tab. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val u = intent.getStringExtra("url")
        if (!u.isNullOrBlank() && (u.startsWith("http://") || u.startsWith("https://"))) newTab(u)
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

    // ---------- address-bar suggestions and search engine (Hub 0.9.9) ----------

    /** Sets the address text without opening the suggestion list. The new-tab page shows an empty bar. */
    private fun setUrlText(u: String?) {
        suppressSuggest = true
        urlBar.setText(if (NewTabPage.isNewTab(u)) "" else (u ?: ""))
        suppressSuggest = false
        dismissSuggest()
    }

    private fun tabHost(t: Tab): String {
        val u = t.web.url ?: t.pending ?: ""
        return if (NewTabPage.isNewTab(u)) "" else (Uri.parse(u).host ?: "")
    }

    private fun dismissSuggest() {
        suggestPopup?.dismiss()
        suggestPopup = null
    }

    private fun showSuggest(text: String) {
        val q = text.trim()
        if (q.isEmpty() || suppressSuggest || !urlBar.hasFocus()) {
            dismissSuggest()
            return
        }
        val items = BrowserStore.suggest(this, q)
        suggestItems = items
        val marks = BrowserStore.bookmarks(this).map { it.url }.toHashSet()
        val rows = ArrayList<String>()
        rows.add("\uD83D\uDD0D  " + q)
        for (e in items) {
            rows.add((if (e.url in marks) "★ " else "\uD83D\uDD58 ") + e.title.ifBlank { e.url }.take(80) + "\n" + e.url.removePrefix("https://").removePrefix("http://").take(90))
        }
        val anchor = (urlBar.parent as? View) ?: urlBar
        val p = suggestPopup ?: ListPopupWindow(this).also {
            it.anchorView = anchor
            it.inputMethodMode = ListPopupWindow.INPUT_METHOD_NEEDED
            it.isModal = false
            it.setOnItemClickListener { _, _, pos, _ ->
                val target = if (pos == 0) urlBar.text.toString() else suggestItems.getOrNull(pos - 1)?.url ?: urlBar.text.toString()
                dismissSuggest()
                go(target)
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(urlBar.windowToken, 0)
                urlBar.clearFocus()
            }
            suggestPopup = it
        }
        p.width = anchor.width.coerceAtLeast(1)
        p.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, rows))
        try {
            p.show()
        } catch (_: Exception) {
            suggestPopup = null
        }
    }

    private fun chooseEngine() {
        val names = BrowserStore.engines.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Search engine")
            .setSingleChoiceItems(names, BrowserStore.engineIndex(this)) { d, i ->
                BrowserStore.setEngineIndex(this, i)
                d.dismiss()
                Toast.makeText(this, "Search with " + names[i], Toast.LENGTH_SHORT).show()
                for (t in tabs) if (NewTabPage.isNewTab(t.web.url)) t.web.reload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- page tools (Hub 0.9.10): find in page, text size, dark pages, save page ----------

    private fun showFind() {
        findInput?.let { it.requestFocus(); showKeyboard(it); return }
        val dp = resources.displayMetrics.density
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(0xF2202338.toInt())
        bar.setPadding((10 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
        bar.elevation = 8 * dp

        val input = EditText(this)
        input.hint = "Find in page"
        input.setHintTextColor(0xFF9A9DBB.toInt())
        input.setTextColor(0xFFFFFFFF.toInt())
        input.isSingleLine = true
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.setBackgroundColor(0x00000000)

        val count = TextView(this)
        count.setTextColor(0xFF9A9DBB.toInt())
        count.textSize = 13f
        count.setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)

        fun btn(label: String, action: () -> Unit): TextView {
            val b = TextView(this)
            b.text = label
            b.textSize = 18f
            b.setTextColor(0xFFFFFFFF.toInt())
            b.gravity = Gravity.CENTER
            b.minWidth = (40 * dp).toInt()
            b.setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
            b.setOnClickListener { action() }
            return b
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, c: Int) {
                val q = s?.toString().orEmpty()
                if (q.isEmpty()) {
                    web.clearMatches()
                    count.text = ""
                } else {
                    web.findAllAsync(q)
                }
            }
        })
        input.setOnEditorActionListener { _, _, _ ->
            web.findNext(true)
            true
        }
        web.setFindListener { active, total, _ ->
            count.text = if (input.text.isNullOrEmpty()) "" else if (total == 0) "0" else "${active + 1}/$total"
        }

        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(count)
        bar.addView(btn("▲") { web.findNext(false) })
        bar.addView(btn("▼") { web.findNext(true) })
        bar.addView(btn("✕") { hideFind() })

        val root = findViewById<ViewGroup>(android.R.id.content)
        root.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        findBar = bar
        findInput = input
        findCount = count
        input.requestFocus()
        showKeyboard(input)
    }

    private fun showKeyboard(v: View) {
        v.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideFind() {
        val bar = findBar ?: return
        try {
            web.clearMatches()
            web.setFindListener(null)
        } catch (_: Exception) {
        }
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        findInput?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
        (bar.parent as? ViewGroup)?.removeView(bar)
        findBar = null
        findInput = null
        findCount = null
    }

    private fun chooseTextSize() {
        val sizes = intArrayOf(80, 90, 100, 110, 125, 150, 175, 200)
        val labels = sizes.map { if (it == 100) "100% (normal)" else "$it%" }.toTypedArray()
        val cur = sizes.indexOf(BrowserStore.textZoom(this)).coerceAtLeast(sizes.indexOf(100))
        MaterialAlertDialogBuilder(this)
            .setTitle("Text size")
            .setSingleChoiceItems(labels, cur) { d, i ->
                BrowserStore.setTextZoom(this, sizes[i])
                for (t in tabs) t.web.settings.textZoom = sizes[i]
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private val darkOnJs = """
        (function(){try{
          var s=document.getElementById('jabra-dark');
          if(!s){s=document.createElement('style');s.id='jabra-dark';(document.head||document.documentElement).appendChild(s);}
          s.textContent='html{filter:invert(1) hue-rotate(180deg)!important;background:#fff!important}img,picture,video,canvas,iframe,[style*="background-image"]{filter:invert(1) hue-rotate(180deg)!important}';
        }catch(e){}})();
    """.trimIndent()

    private val darkOffJs = "(function(){try{var s=document.getElementById('jabra-dark');if(s)s.remove();}catch(e){}})();"

    /** Dark pages: inverts the page colours (images and video are turned back). Skipped on the new-tab page. */
    private fun applyDark(w: WebView, url: String?) {
        if (NewTabPage.isNewTab(url)) return
        w.evaluateJavascript(if (darkPage) darkOnJs else darkOffJs, null)
    }

    private fun savePage() {
        if (NewTabPage.isNewTab(web.url) || web.url.isNullOrEmpty()) {
            Toast.makeText(this, "Open a page first", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Save page")
            .setItems(arrayOf("Save as PDF (print dialog)", "Save as web archive (.mht)")) { _, i ->
                if (i == 0) savePdf() else saveArchive()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pageFileName(): String =
        (web.title ?: "page").replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().take(60).ifBlank { "page" }

    /** Android's print dialog: choose "Save as PDF" there. */
    private fun savePdf() {
        try {
            val pm = getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
            val name = pageFileName()
            pm.print(name, web.createPrintDocumentAdapter(name), android.print.PrintAttributes.Builder().build())
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start printing: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveArchive() {
        val dir = java.io.File(cacheDir, "shared_exports").apply { mkdirs() }
        val file = java.io.File(dir, pageFileName() + ".mht")
        web.saveWebArchive(file.absolutePath, false) { path ->
            if (path == null) {
                Toast.makeText(this, "Could not save the page", Toast.LENGTH_LONG).show()
            } else {
                finishArchive(java.io.File(path))
            }
        }
    }

    /** Android 10+: copy into Downloads/Jabra Extension Hub. Older Android: open the share sheet. */
    private fun finishArchive(f: java.io.File) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, f.name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "multipart/related")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/Jabra Extension Hub")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Android could not create the file")
                try {
                    contentResolver.openOutputStream(uri, "w")?.use { out -> f.inputStream().use { it.copyTo(out) } }
                        ?: throw IllegalStateException("Could not write the file")
                    values.clear()
                    values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                } catch (e: Exception) {
                    contentResolver.delete(uri, null, null)
                    throw e
                }
                f.delete()
                Toast.makeText(this, "Saved to Downloads/Jabra Extension Hub/${f.name}", Toast.LENGTH_LONG).show()
            } else {
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
                val i = Intent(Intent.ACTION_SEND).setType("multipart/related")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(i, "Save ${f.name}"))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save the page: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun goBack() {
        val cur = curTab
        if (web.canGoBack()) web.goBack()
        else if (tabs.size > 1 && cur != null) closeTab(cur)
        else finish()
    }

    private fun go(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val target = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(" ") || !t.contains(".") -> BrowserStore.searchUrl(this, t)
            else -> "https://$t"
        }
        web.loadUrl(target)
    }

    // ---------- menu ----------

    private fun showMenu(anchor: View) {
        val pageUrl = web.url?.takeUnless { NewTabPage.isNewTab(it) } ?: ""
        val marked = pageUrl.isNotEmpty() && BrowserStore.isBookmarked(this, pageUrl)
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 20, 0, "Tabs (${tabs.size})")
        m.menu.add(0, 21, 0, "New tab")
        m.menu.add(0, 1, 0, if (marked) "★ Remove bookmark" else "☆ Add bookmark")
        m.menu.add(0, 2, 1, "Bookmarks")
        m.menu.add(0, 13, 1, "Bookmarks: export / import")
        m.menu.add(0, 3, 2, "History")
        m.menu.add(0, 22, 2, "Search engine: " + BrowserStore.engine(this).name)
        val pt = m.menu.addSubMenu(0, 30, 3, "Page tools")
        pt.add(0, 23, 0, "Find in page")
        pt.add(0, 24, 1, "Text size: " + BrowserStore.textZoom(this) + "%")
        val dp = pt.add(0, 25, 2, "Dark pages")
        dp.isCheckable = true
        dp.isChecked = darkPage
        pt.add(0, 26, 3, "Save page (PDF / archive)")
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
        val active = DlJobs.activeCount()
        m.menu.add(0, 12, 11, if (active > 0) "Downloads ($active running)" else "Downloads")
        m.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> if (pageUrl.startsWith("http")) {
                    val now = BrowserStore.toggleBookmark(this, pageUrl, web.title ?: "")
                    Toast.makeText(this, if (now) "Bookmarked" else "Bookmark removed", Toast.LENGTH_SHORT).show()
                }
                2 -> showManager(true)
                3 -> showManager(false)
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
                    applyDesktopAll()
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
                12 -> startActivity(Intent(this, DownloadsActivity::class.java))
                20 -> showTabs()
                21 -> newTab(NewTabPage.URL)
                22 -> chooseEngine()
                23 -> showFind()
                24 -> chooseTextSize()
                25 -> {
                    darkPage = !darkPage
                    BrowserStore.setDarkPage(this, darkPage)
                    for (t in tabs) applyDark(t.web, t.web.url)
                    Toast.makeText(this, if (darkPage) "Dark pages on" else "Dark pages off", Toast.LENGTH_SHORT).show()
                }
                26 -> savePage()
                13 -> showBookmarkTools()
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
            name + "\n" + (Uri.parse(it.url).host ?: "") + "  ·  " + df.format(Date(it.ts)) + (if (it.folder.isNotBlank()) "  ·  📁 " + it.folder else "")
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(labels) { _, i -> web.loadUrl(list[i].url) }
            .setNegativeButton("Close", null)
            .setNeutralButton(clearLabel) { _, _ -> onClear() }
            .show()
    }

    // ---------- bookmark / history manager (Hub 0.9.7): search, long-press actions ----------

    private fun entryLabel(e: BrowserEntry, df: DateFormat): String {
        val name = if (e.title.isBlank()) e.url else e.title
        return name + "\n" + (Uri.parse(e.url).host ?: "") + "  ·  " + df.format(Date(e.ts)) +
            (if (e.folder.isNotBlank()) "  ·  📁 " + e.folder else "")
    }

    private fun showManager(isBookmarks: Boolean) {
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        var all: List<BrowserEntry> = emptyList()
        var shown: List<BrowserEntry> = emptyList()
        var dlg: androidx.appcompat.app.AlertDialog? = null
        val search = EditText(this)
        search.hint = "Search title, link or folder"
        search.setSingleLine(true)
        val listView = android.widget.ListView(this)
        val adapter = android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        listView.adapter = adapter
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(search)
        box.addView(listView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (300 * dp).toInt()))

        fun refresh() {
            all = if (isBookmarks) BrowserStore.bookmarks(this) else BrowserStore.history(this)
            val q = search.text.toString().trim().lowercase(Locale.getDefault())
            shown = if (q.isEmpty()) all else all.filter {
                (it.title + " " + it.url + " " + it.folder).lowercase(Locale.getDefault()).contains(q)
            }
            adapter.clear()
            if (shown.isEmpty()) adapter.add(if (all.isEmpty()) "Nothing saved yet" else "No match")
            else adapter.addAll(shown.map { entryLabel(it, df) })
            adapter.notifyDataSetChanged()
        }

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { refresh() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        listView.setOnItemClickListener { _, _, i, _ ->
            if (i in shown.indices) {
                dlg?.dismiss()
                web.loadUrl(shown[i].url)
            }
        }
        listView.setOnItemLongClickListener { _, _, i, _ ->
            if (i in shown.indices) entryActions(shown[i], isBookmarks, { dlg?.dismiss() }, { refresh() })
            true
        }

        dlg = MaterialAlertDialogBuilder(this)
            .setTitle(if (isBookmarks) "Bookmarks" else "History")
            .setView(box)
            .setNegativeButton("Close", null)
            .setNeutralButton("Clear") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(if (isBookmarks) "Remove all bookmarks?" else "Clear all history?")
                    .setMessage(if (isBookmarks) "Export a backup first if you are not sure." else "This cannot be undone.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Yes") { _, _ ->
                        if (isBookmarks) BrowserStore.clearBookmarks(this) else BrowserStore.clearHistory(this)
                    }
                    .show()
            }
            .setPositiveButton(if (isBookmarks) "Tools" else "Export") { _, _ ->
                if (isBookmarks) showBookmarkTools() else historyExport()
            }
            .show()
        refresh()
    }

    private fun entryActions(e: BrowserEntry, isBookmarks: Boolean, onOpen: () -> Unit, onChanged: () -> Unit) {
        val items = ArrayList<String>()
        items.add("Open")
        if (isBookmarks) {
            items.add("Rename")
            items.add("Move to folder")
            items.add("Delete")
        } else {
            items.add("Add bookmark")
            items.add("Delete from history")
        }
        items.add("Copy link")
        MaterialAlertDialogBuilder(this)
            .setTitle(if (e.title.isBlank()) e.url else e.title)
            .setItems(items.toTypedArray()) { _, i ->
                when (items[i]) {
                    "Open" -> {
                        onOpen()
                        web.loadUrl(e.url)
                    }
                    "Rename" -> promptText("Rename bookmark", e.title.ifBlank { e.url }, "Name") { n ->
                        BrowserStore.updateBookmark(this, e.url, n.ifBlank { e.url }, e.folder)
                        onChanged()
                    }
                    "Move to folder" -> moveToFolder(e, onChanged)
                    "Delete" -> {
                        BrowserStore.removeBookmark(this, e.url)
                        onChanged()
                    }
                    "Add bookmark" -> {
                        if (!BrowserStore.isBookmarked(this, e.url)) BrowserStore.toggleBookmark(this, e.url, e.title)
                        Toast.makeText(this, "Bookmarked", Toast.LENGTH_SHORT).show()
                    }
                    "Delete from history" -> {
                        BrowserStore.removeHistory(this, e.url)
                        onChanged()
                    }
                    "Copy link" -> {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("link", e.url))
                        Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
                    }
                    else -> Unit
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun moveToFolder(e: BrowserEntry, onChanged: () -> Unit) {
        val opts = ArrayList<String>()
        opts.add("No folder")
        opts.addAll(BrowserStore.bookmarkFolders(this))
        opts.add("New folder…")
        MaterialAlertDialogBuilder(this)
            .setTitle("Move to folder")
            .setItems(opts.toTypedArray()) { _, i ->
                fun put(f: String) {
                    BrowserStore.updateBookmark(this, e.url, e.title, f)
                    onChanged()
                }
                when {
                    i == 0 -> put("")
                    i == opts.size - 1 -> promptText("New folder", "", "Folder name (A / B makes a subfolder)") { n ->
                        if (n.isNotBlank()) put(n)
                    }
                    else -> put(opts[i])
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptText(title: String, initial: String, hintText: String, ok: (String) -> Unit) {
        val et = EditText(this)
        et.setText(initial)
        et.hint = hintText
        et.setSingleLine(true)
        et.setSelection(et.text.length)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(et)
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ -> ok(et.text.toString().trim()) }
            .show()
    }

    private fun historyExport() {
        if (BrowserStore.history(this).isEmpty()) {
            Toast.makeText(this, "History is empty", Toast.LENGTH_SHORT).show()
            return
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        launchBm(
            "export_history",
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TITLE, "jabra-history-$stamp.txt")
        )
    }

    // ---------- bookmark tools ----------

    private fun showBookmarkTools() {
        val n = BrowserStore.bookmarks(this).size
        val items = arrayOf(
            "Export as JSON (backup)",
            "Export as HTML (Chrome / Firefox / Edge)",
            "Import: add to my bookmarks",
            "Import: replace all my bookmarks"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Bookmarks ($n)")
            .setItems(items) { _, i ->
                when (i) {
                    0 -> bmExport("export_json")
                    1 -> bmExport("export_html")
                    2 -> bmImport("import_merge")
                    3 -> MaterialAlertDialogBuilder(this)
                        .setTitle("Replace all bookmarks?")
                        .setMessage("Your current $n bookmarks are removed and replaced by the ones in the file. Export a backup first if you are not sure.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Choose file") { _, _ -> bmImport("import_replace") }
                        .show()
                    else -> Unit
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun bmExport(op: String) {
        if (BrowserStore.bookmarks(this).isEmpty()) {
            Toast.makeText(this, "No bookmarks to export", Toast.LENGTH_SHORT).show()
            return
        }
        val json = op == "export_json"
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        launchBm(
            op,
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(if (json) "application/json" else "text/html")
                .putExtra(Intent.EXTRA_TITLE, "jabra-bookmarks-$stamp." + if (json) "json" else "html")
        )
    }

    private fun bmImport(op: String) {
        launchBm(op, Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"))
    }

    private fun launchBm(op: String, i: Intent) {
        bmOp = op
        try {
            bmFile.launch(i)
        } catch (_: Exception) {
            bmOp = ""
            Toast.makeText(this, "No file picker available on this device", Toast.LENGTH_LONG).show()
        }
    }

    private fun runBookmarkFile(op: String, uri: Uri) {
        val app = applicationContext
        Thread {
            val msg = try {
                if (op.startsWith("export")) {
                    val text = when (op) {
                        "export_json" -> BrowserStore.exportJson(app)
                        "export_history" -> BrowserStore.exportHistoryText(app)
                        else -> BrowserStore.exportHtml(app)
                    }
                    val out = contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("cannot write the file")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    if (op == "export_history") "Exported ${BrowserStore.history(app).size} history entries" else "Exported ${BrowserStore.bookmarks(app).size} bookmarks"
                } else {
                    val ins = contentResolver.openInputStream(uri) ?: throw java.io.IOException("cannot read the file")
                    val bytes = ins.use { readLimited(it, 5_000_000) }
                    val items = BrowserStore.parseBookmarks(String(bytes, Charsets.UTF_8))
                    if (items.isEmpty()) {
                        "No bookmarks found in that file"
                    } else {
                        val r = BrowserStore.importBookmarks(app, items, op == "import_replace")
                        "Imported ${r.added} new" + if (r.skipped > 0) ", skipped ${r.skipped} (already saved or over the limit)" else ""
                    }
                }
            } catch (e: Exception) {
                AppDiagnostics.record(app, "Bookmarks $op failed", e)
                "Failed: ${e.message ?: "unknown error"}"
            }
            runOnUiThread { Toast.makeText(app, msg, Toast.LENGTH_LONG).show() }
        }.start()
    }

    private fun readLimited(ins: java.io.InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16384)
        var total = 0
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw java.io.IOException("file is too large (limit 5 MB)")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear browsing data?")
            .setMessage("Deletes history, cache, cookies and site data. Bookmarks are kept. You will be signed out of sites.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                BrowserStore.clearHistory(this)
                BrowserStore.clearTabs(this)
                web.clearCache(true)
                for (t in tabs) t.web.clearHistory()
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
                Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ---------- found media ----------

    /** Called from a background thread by the WebView. */
    private fun onFound(tab: Tab, url: String, kind: String) {
        runOnUiThread {
            if (tab.found.containsKey(url) || tab.found.size >= 200) return@runOnUiThread
            tab.found[url] = Media(url, kind, tab.web.url ?: "", tab.web.title ?: "")
            if (tab === curTab) {
                updateChip()
                if (panel.visibility == View.VISIBLE) showPanel()
            }
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
                if (m.kind == "hls") askHls(m, btn, status) else startDl(m, DlOptions(), btn, status)
            }
            panelList.addView(v)
        }
    }

    private fun startDl(m: Media, opts: DlOptions, btn: TextView, status: TextView) {
        btn.isEnabled = false
        btn.alpha = 0.4f
        status.visibility = View.VISIBLE
        status.text = "Starting..."
        downloader.start(m, web.settings.userAgentString, opts) { s, done ->
            runOnUiThread {
                status.text = s
                if (done) {
                    btn.isEnabled = true
                    btn.alpha = 1f
                }
            }
        }
        Toast.makeText(this, "Added to Downloads (menu > Downloads)", Toast.LENGTH_SHORT).show()
    }

    /** HLS: read the stream, then let the user pick quality and an optional clip. */
    private fun askHls(m: Media, btn: TextView, status: TextView) {
        btn.isEnabled = false
        btn.alpha = 0.4f
        status.visibility = View.VISIBLE
        status.text = "Reading stream..."
        val ua = web.settings.userAgentString
        Thread {
            try {
                val probe = downloader.probe(m, ua)
                runOnUiThread {
                    btn.isEnabled = true
                    btn.alpha = 1f
                    status.text = ""
                    status.visibility = View.GONE
                    showHlsDialog(m, probe, btn, status)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btn.isEnabled = true
                    btn.alpha = 1f
                    status.text = "Failed: " + (e.message ?: "cannot read stream")
                }
            }
        }.start()
    }

    private fun parseTime(t: String): Double {
        val s = t.trim()
        if (s.isEmpty()) return 0.0
        var total = 0.0
        for (part in s.split(":")) total = total * 60 + (part.trim().toDoubleOrNull() ?: return -1.0)
        return total
    }

    private fun showHlsDialog(m: Media, probe: HlsProbe, btn: TextView, status: TextView) {
        val d = resources.displayMetrics.density
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0)

        val group = RadioGroup(this)
        val ids = ArrayList<Int>()
        val urls = ArrayList<String?>()
        fun addOpt(label: String, url: String?) {
            val rb = RadioButton(this)
            rb.id = View.generateViewId()
            rb.text = label
            group.addView(rb)
            ids.add(rb.id)
            urls.add(url)
        }
        if (probe.variants.isEmpty()) {
            addOpt("Only one quality available", null)
        } else {
            addOpt("Best  (" + probe.variants[0].label + ")", probe.variants[0].url)
            for (i in 1 until probe.variants.size) addOpt(probe.variants[i].label, probe.variants[i].url)
        }
        group.check(ids[0])
        box.addView(group)

        val dur = probe.durationSeconds.toInt()
        val hint = TextView(this)
        hint.text = "Only a part (optional). Length: " + (if (dur >= 3600) "${dur / 3600}:" else "") +
            String.format("%02d:%02d", (dur % 3600) / 60, dur % 60) + "  ·  type 90, 1:30 or 1:02:30"
        hint.textSize = 12f
        hint.setPadding(0, (12 * d).toInt(), 0, 0)
        box.addView(hint)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val from = EditText(this)
        from.hint = "From"
        from.inputType = android.text.InputType.TYPE_CLASS_TEXT
        val to = EditText(this)
        to.hint = "To (empty = end)"
        to.inputType = android.text.InputType.TYPE_CLASS_TEXT
        row.addView(from, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(to, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(row)

        val sv = ScrollView(this)
        sv.addView(box)
        MaterialAlertDialogBuilder(this)
            .setTitle("Download stream")
            .setView(sv)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Download") { _, _ ->
                val a = parseTime(from.text.toString())
                val b = parseTime(to.text.toString())
                if (a < 0 || b < 0 || (b > 0 && b <= a)) {
                    Toast.makeText(this, "Check the clip times", Toast.LENGTH_LONG).show()
                } else {
                    val idx = ids.indexOf(group.checkedRadioButtonId).coerceAtLeast(0)
                    startDl(m, DlOptions(urls[idx], a, b), btn, status)
                }
            }
            .show()
    }

    /** Remembers the open tabs (addresses only) so they come back after the app was closed. Off when "Save history" is off. */
    private fun saveTabs() {
        if (!BrowserStore.saveHistory(this)) {
            BrowserStore.clearTabs(this)
            return
        }
        val items = ArrayList<Pair<String, String>>()
        var cur = 0
        for (t in tabs) {
            val u = t.web.url ?: t.pending ?: continue
            if (!(u.startsWith("http://") || u.startsWith("https://"))) continue
            if (t === curTab) cur = items.size
            items.add(Pair(u, t.web.title?.takeIf { it.isNotBlank() } ?: t.savedTitle))
        }
        if (items.isNotEmpty()) BrowserStore.saveTabs(this, items, cur)
    }

    override fun onPause() {
        dismissSuggest()
        if (findBar != null) hideFind()
        for (t in tabs) t.web.onPause()
        saveTabs()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        curTab?.web?.onResume()
    }

    override fun onDestroy() {
        dismissSuggest()
        for (t in tabs) t.web.destroy()
        super.onDestroy()
    }
}
