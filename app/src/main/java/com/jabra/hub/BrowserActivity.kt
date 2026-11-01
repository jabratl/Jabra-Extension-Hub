package com.jabra.hub

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
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
import androidx.core.widget.NestedScrollView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
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
        /** Site of the page being loaded (for per-site ad blocking and desktop mode). */
        @Volatile var pageHost: String? = null
        var desktopOn: Boolean = false
        /** Small snapshot for the tab grid (0.9.15); taken when the tab is left or the grid opens. */
        var thumb: Bitmap? = null
    }

    private val tabs = ArrayList<Tab>()
    private var curTab: Tab? = null
    private val maxTabs = 8
    private var webHost: ViewGroup? = null
    private var webIdx = 0
    private var webId = View.NO_ID
    private var webLp: ViewGroup.LayoutParams? = null
    // Quiet Pill (0.9.14): bottom address pill
    private var tabCount: TextView? = null
    private var tabsBtn: View? = null
    private lateinit var pillWrap: ViewGroup
    private lateinit var pill: View
    private lateinit var secureIcon: ImageView
    private lateinit var clearBtn: View
    private lateinit var menuBtn: View
    private lateinit var suggestBox: View
    private lateinit var suggestList: LinearLayout
    private var fullUrl = ""
    private var compact = false
    private var compactAnim: ValueAnimator? = null
    // Tab grid (0.9.15)
    private var tabsOverlay: FrameLayout? = null

    /** The WebView of the current tab. Older code keeps using "web" and "found" unchanged. */
    private val web: WebView get() = curTab!!.web
    private lateinit var urlBar: EditText
    private lateinit var chip: TextView
    private lateinit var panel: View
    private lateinit var panelList: LinearLayout
    private lateinit var panelBar: LinearLayout
    private var panelClosing = false
    private var selectRefresh: (() -> Unit)? = null
    private lateinit var progress: ProgressBar
    private lateinit var downloader: Downloader
    private val found: LinkedHashMap<String, Media> get() = curTab!!.found

    // media panel: filter, selection (Hub 0.9.11)
    private var panelFilter = "all"
    private var selectMode = false
    private val selected = HashSet<String>()
    private val MAX_BATCH = 30

    // find in page, dark pages (Hub 0.9.10)
    private var findBar: LinearLayout? = null
    private var findInput: EditText? = null
    private var findCount: TextView? = null
    private var darkPage = false

    // address-bar suggestions (Hub 0.9.9)
    private var suggestItems: List<BrowserEntry> = emptyList()
    private var suppressSuggest = false

    private var adblock = true
    private var desktop = false
    // per-site settings (Hub 0.9.12)
    @Volatile private var allowHosts: Set<String> = emptySet()
    @Volatile private var desktopSites: Map<String, Boolean> = emptyMap()
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
            if (adblockOn(tab) && AdBlock.isBlocked(u, Uri.parse(u).host)) return
            onFound(tab, u, k)
        }

        @JavascriptInterface
        fun adblock(): Boolean = adblockOn(tab)
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
        allowHosts = BrowserStore.adblockAllowed(this)
        desktopSites = BrowserStore.desktopSites(this)
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
        panelBar = findViewById(R.id.panelBar)
        progress = findViewById(R.id.progress)
        pillWrap = findViewById(R.id.pillWrap)
        pill = findViewById(R.id.pill)
        secureIcon = findViewById(R.id.secure)
        clearBtn = findViewById(R.id.clearUrl)
        menuBtn = findViewById(R.id.menu)
        suggestBox = findViewById(R.id.suggest)
        suggestList = findViewById(R.id.suggestList)

        // Back is the system back gesture; "Close browser" is in the menu.
        menuBtn.setOnClickListener { Q.pop(it); Q.tick(it); showMenu(it) }
        pill.setOnClickListener {
            urlBar.requestFocus()
            showKeyboard(urlBar)
        }
        clearBtn.setOnClickListener { urlBar.setText("") }
        findViewById<View>(R.id.panelClose).setOnClickListener { hidePanel() }
        attachSheetDrag(findViewById(R.id.panelHead))
        chip.setOnClickListener { showPanel() }

        urlBar.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                go(v.text.toString())
                blurUrl()
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
        urlBar.setOnFocusChangeListener { _, has -> onUrlFocus(has) }

        setupTab(first)
        addTabButton()

        onBackPressedDispatcher.addCallback(this) {
            when {
                tabsOverlay != null -> hideTabs()
                findBar != null -> hideFind()
                urlBar.hasFocus() || suggestBox.visibility == View.VISIBLE -> blurUrl()
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
        t.desktopOn = desktop
        applyDesktop(t.web, desktop)
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
                if (adblockOn(t) && !isUserGesture) {
                    blockedCount++
                    return false
                }
                // A real tap on a "new window" link opens in this same view.
                val temp = WebView(this@BrowserActivity)
                temp.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        val u = request.url.toString()
                        if ((u.startsWith("http://") || u.startsWith("https://")) &&
                            !(adblockOn(t) && AdBlock.isAdHost(request.url.host))
                        ) {
                            t.web.post { if (!newTab(u)) t.web.loadUrl(u) }
                        } else if (adblockOn(t)) {
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
                if (request.isForMainFrame) t.pageHost = request.url.host
                if (adblockOn(t) && !request.isForMainFrame && AdBlock.isBlocked(u, request.url.host)) {
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
                if (adblockOn(t) && !request.hasGesture() && AdBlock.isAdHost(request.url.host)) {
                    blockedCount++
                    return true
                }
                if (request.isForMainFrame) {
                    t.pageHost = request.url.host
                    syncDesktop(t)
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                t.pageHost = Uri.parse(url).host
                // Redirects and typed addresses: switch the browser identity for this site and load it again once.
                if (!NewTabPage.isNewTab(url) && syncDesktop(t)) view.post { view.reload() }
                t.found.clear()
                blockedCount = 0
                if (t === curTab) {
                    setUrlText(url)
                    setCompact(false)
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
        // Scrolling down shrinks the address pill to the host only; scrolling up brings it back.
        t.web.setOnScrollChangeListener { _, _, y, _, oldY ->
            if (t !== curTab || urlBar.hasFocus()) return@setOnScrollChangeListener
            val dy = y - oldY
            if (y <= 0 || dy < -8) setCompact(false) else if (dy > 8) setCompact(true)
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

    private fun applyDesktop(w: WebView, on: Boolean) {
        w.settings.userAgentString = if (on) desktopUa else defaultUa
        w.settings.useWideViewPort = on
        w.settings.loadWithOverviewMode = on
    }

    // ---------- per-site ad blocker and desktop mode (Hub 0.9.12) ----------

    private fun siteAllowed(host: String?): Boolean {
        val h = BrowserStore.siteKey(host)
        if (h.isEmpty()) return false
        return allowHosts.any { h == it || h.endsWith(".$it") }
    }

    /** Ad blocker state for one tab: the global switch, unless the site is on the allow-list. */
    private fun adblockOn(t: Tab?): Boolean = adblock && !siteAllowed(t?.pageHost)

    private fun desktopFor(host: String?): Boolean = desktopSites[BrowserStore.siteKey(host)] ?: desktop

    /** Sets the tab's user agent to what its site needs. Returns true when it changed (the page must be loaded again). */
    private fun syncDesktop(t: Tab): Boolean {
        val want = desktopFor(t.pageHost ?: Uri.parse(t.web.url ?: "").host)
        if (want == t.desktopOn) return false
        t.desktopOn = want
        applyDesktop(t.web, want)
        return true
    }

    private fun applyDesktopAll() {
        for (t in tabs) syncDesktop(t)
    }

    // ---------- tabs (Hub 0.9.7): only the current tab's WebView is attached; the others are paused ----------

    private fun updateTabBadge() {
        tabCount?.text = tabs.size.toString()
        tabsBtn?.contentDescription = "Tabs, ${tabs.size} open"
    }

    /** Wires the tab-count button inside the address pill. */
    private fun addTabButton() {
        tabsBtn = findViewById(R.id.tabsBtn)
        tabCount = findViewById(R.id.tabCount)
        tabsBtn?.setOnClickListener {
            Q.pop(it)
            Q.tick(it)
            showTabs()
        }
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

    private fun switchTo(t: Tab, captureOld: Boolean = true) {
        val host = webHost ?: return
        val old = curTab
        if (old === t) return
        if (findBar != null) hideFind()
        selected.clear()
        selectMode = false
        if (old != null) {
            if (captureOld) captureThumb(old)
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
        setCompact(false)
    }

    private fun closeTab(t: Tab) {
        val i = tabs.indexOf(t)
        if (i < 0) return
        if (tabs.size == 1) {
            // The last tab is never removed: it goes back to a fresh page.
            t.found.clear()
            t.thumb = null
            t.web.loadUrl(NewTabPage.URL)
            t.web.clearHistory()
            updateChip()
            Toast.makeText(this, "Last tab: opened a fresh page", Toast.LENGTH_SHORT).show()
            return
        }
        val next = tabs[if (i > 0) i - 1 else 1]
        if (t === curTab) switchTo(next, false)
        tabs.remove(t)
        t.thumb = null
        (t.web.parent as? ViewGroup)?.removeView(t.web)
        t.web.destroy()
        updateTabBadge()
    }

    // ---------- tab switcher grid (Hub 0.9.15, step D3) ----------

    /** Small snapshot of the tab's page for the grid: RGB_565, 360 px wide, about 0.3 MB per tab (max 8 tabs). */
    private fun captureThumb(t: Tab) {
        try {
            val w = t.web
            if (w.width <= 0 || w.height <= 0 || t.pending != null) return
            val bw = 360
            val bh = (bw * THUMB_RATIO).toInt()
            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565)
            val c = android.graphics.Canvas(bmp)
            c.drawColor(Q.color(this, R.color.q_surface))
            val sc = bw.toFloat() / w.width
            c.scale(sc, sc)
            w.draw(c)
            t.thumb = bmp
        } catch (_: Throwable) {
            // No thumbnail is fine: the card shows a placeholder.
        }
    }

    private fun showTabs() {
        if (tabsOverlay != null) return
        if (findBar != null) hideFind()
        if (urlBar.hasFocus()) blurUrl()
        curTab?.let { captureThumb(it) }
        fun dp(n: Int) = Q.dp(this, n)
        val root = findViewById<ViewGroup>(android.R.id.content)
        val ov = FrameLayout(this)
        ov.setBackgroundColor(Q.color(this, R.color.q_bg))
        ov.isClickable = true

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL

        // top bar: back, title, count
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(4), dp(4), dp(16), dp(4))
        val back = ImageView(this)
        back.setImageDrawable(Q.icon(this, R.drawable.q_ic_back))
        back.setPadding(dp(12), dp(12), dp(12), dp(12))
        back.contentDescription = "Close tab switcher"
        back.isClickable = true
        back.setOnClickListener { Q.tick(it); hideTabs() }
        val title = TextView(this)
        title.text = "Tabs"
        title.textSize = 20f
        title.typeface = android.graphics.Typeface.DEFAULT_BOLD
        title.setTextColor(Q.color(this, R.color.q_text))
        val count = TextView(this)
        count.textSize = 14f
        count.setTextColor(Q.color(this, R.color.q_text_sub))
        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(count)

        // grid: rows of two cards
        val sv = ScrollView(this)
        sv.clipToPadding = false
        sv.setPadding(dp(12), dp(4), dp(12), dp(8))
        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        sv.addView(grid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // big "+ New tab" button
        val newWrap = LinearLayout(this)
        newWrap.setPadding(dp(16), dp(8), dp(16), dp(16))
        val newBtn = LinearLayout(this)
        newBtn.orientation = LinearLayout.HORIZONTAL
        newBtn.gravity = Gravity.CENTER
        newBtn.minimumHeight = dp(56)
        newBtn.setBackgroundResource(R.drawable.q_btn_accent)
        newBtn.isClickable = true
        newBtn.isFocusable = true
        newBtn.contentDescription = "New tab"
        val plus = ImageView(this)
        plus.setImageDrawable(Q.icon(this, R.drawable.q_ic_add))
        plus.imageTintList = ColorStateList.valueOf(Q.color(this, R.color.q_on_accent))
        val plusTxt = TextView(this)
        plusTxt.text = "New tab"
        plusTxt.textSize = 16f
        plusTxt.typeface = android.graphics.Typeface.DEFAULT_BOLD
        plusTxt.setTextColor(Q.color(this, R.color.q_on_accent))
        plusTxt.setPadding(dp(8), 0, 0, 0)
        newBtn.addView(plus, LinearLayout.LayoutParams(dp(24), dp(24)))
        newBtn.addView(plusTxt)
        newBtn.setOnClickListener {
            Q.pop(it)
            Q.tick(it)
            if (newTab(NewTabPage.URL)) hideTabs()
        }
        newWrap.addView(newBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        col.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        col.addView(newWrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        ov.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        var curRow: View? = null
        val cols = (resources.configuration.screenWidthDp / 170).coerceIn(2, 4)
        fun fill() {
            grid.removeAllViews()
            count.text = "${tabs.size} / $maxTabs"
            newBtn.alpha = if (tabs.size >= maxTabs) 0.5f else 1f
            val list = tabs.toList()
            var i = 0
            while (i < list.size) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                for (j in 0 until cols) {
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    lp.setMargins(dp(4), dp(4), dp(4), dp(4))
                    if (i + j < list.size) {
                        val t = list[i + j]
                        if (t === curTab) curRow = row
                        row.addView(tabCard(t, {
                            if (t !== curTab) switchTo(t)
                            hideTabs()
                        }, {
                            closeTab(t)
                            fill()
                        }), lp)
                    } else {
                        row.addView(View(this), lp)
                    }
                }
                grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                i += cols
            }
        }
        fill()

        tabsOverlay = ov
        root.getChildAt(0)?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        ov.accessibilityPaneTitle = "Tabs"
        root.addView(ov, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        ov.alpha = 0f
        ov.scaleX = 0.96f
        ov.scaleY = 0.96f
        ov.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200L)
            .setInterpolator(DecelerateInterpolator()).start()
        // bring the current tab into view when there are many
        sv.post { curRow?.let { sv.scrollTo(0, it.top) } }
    }

    private fun hideTabs() {
        val ov = tabsOverlay ?: return
        if (ov.tag == "closing") return
        ov.tag = "closing"
        ov.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(160L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                val par = ov.parent as? ViewGroup
                par?.removeView(ov)
                par?.getChildAt(0)?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                if (tabsOverlay === ov) tabsOverlay = null
            }.start()
    }

    /** One card of the grid: header (title, host, close), thumbnail, swipe left/right to close. */
    private fun tabCard(t: Tab, onOpen: () -> Unit, onClose: () -> Unit): View {
        fun dp(n: Int) = Q.dp(this, n)
        val cur = t === curTab
        val u = t.web.url ?: t.pending ?: ""
        val isNew = NewTabPage.isNewTab(u)
        val host = tabHost(t)
        val name = if (isNew) "New tab"
        else t.web.title?.takeIf { it.isNotBlank() }
            ?: t.savedTitle.takeIf { it.isNotBlank() }
            ?: host.takeIf { it.isNotBlank() }
            ?: "New tab"

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(if (cur) R.drawable.q_tab_card_on else R.drawable.q_card)
        val radius = dp(16).toFloat()
        card.clipToOutline = true
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, radius)
            }
        }
        card.isClickable = true
        card.isFocusable = true
        card.contentDescription = name + (if (host.isNotBlank() && !isNew) ", $host" else "") + (if (cur) ", current tab" else "")

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.minimumHeight = dp(48)
        head.setPadding(dp(12), 0, 0, 0)
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val tt = TextView(this)
        tt.text = name
        tt.textSize = 13f
        tt.maxLines = 1
        tt.ellipsize = android.text.TextUtils.TruncateAt.END
        tt.setTextColor(Q.color(this, R.color.q_text))
        texts.addView(tt)
        if (host.isNotBlank() && !isNew) {
            val hh = TextView(this)
            hh.text = host
            hh.textSize = 11f
            hh.maxLines = 1
            hh.ellipsize = android.text.TextUtils.TruncateAt.END
            hh.setTextColor(Q.color(this, R.color.q_text_sub))
            texts.addView(hh)
        }
        val x = ImageView(this)
        x.setImageDrawable(Q.icon(this, R.drawable.q_ic_close))
        x.setPadding(dp(12), dp(12), dp(12), dp(12))
        x.contentDescription = "Close tab: $name"
        x.isClickable = true
        x.setOnClickListener {
            Q.tick(it)
            onClose()
        }
        head.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(x, LinearLayout.LayoutParams(dp(48), dp(48)))

        val frame = RatioFrame(this, THUMB_RATIO)
        frame.setBackgroundColor(Q.color(this, R.color.q_surface))
        val th = t.thumb
        if (th != null) {
            val img = ImageView(this)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            img.setImageBitmap(th)
            frame.addView(img, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else if (isNew) {
            val ic = ImageView(this)
            ic.setImageDrawable(Q.icon(this, R.drawable.q_ic_search))
            ic.imageTintList = ColorStateList.valueOf(Q.color(this, R.color.q_text_sub))
            frame.addView(ic, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
        } else {
            val letter = TextView(this)
            letter.text = (host.firstOrNull() ?: name.first()).uppercaseChar().toString()
            letter.textSize = 36f
            letter.setTextColor(Q.color(this, R.color.q_accent_text))
            frame.addView(letter, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }

        card.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.setOnClickListener {
            Q.tick(it)
            onOpen()
        }
        attachSwipe(card, onClose)
        return card
    }

    /** Horizontal drag moves the card; far enough (or a fast fling) closes the tab, otherwise it springs back. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachSwipe(card: View, onDismiss: () -> Unit) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var x0 = 0f
        var y0 = 0f
        var drag = false
        var vt: VelocityTracker? = null
        card.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = e.rawX
                    y0 = e.rawY
                    drag = false
                    vt?.recycle()
                    vt = VelocityTracker.obtain()
                    vt?.addMovement(e)
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    vt?.addMovement(e)
                    val dx = e.rawX - x0
                    val dy = e.rawY - y0
                    if (!drag && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                        drag = true
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        v.cancelLongPress()
                        v.isPressed = false
                    }
                    if (drag) {
                        v.translationX = dx
                        v.alpha = (1f - Math.abs(dx) / (v.width * 1.2f)).coerceIn(0.2f, 1f)
                    }
                    drag
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasDrag = drag
                    drag = false
                    if (wasDrag) {
                        vt?.addMovement(e)
                        vt?.computeCurrentVelocity(1000)
                        val vx = vt?.xVelocity ?: 0f
                        val dx = v.translationX
                        val fling = Math.abs(vx) > 1200f && vx * dx > 0f
                        if (e.actionMasked == MotionEvent.ACTION_UP && (Math.abs(dx) > v.width * 0.4f || fling)) {
                            val dir = if (dx > 0f) 1f else -1f
                            Q.tick(v)
                            v.animate().translationX(dir * v.width * 1.2f).alpha(0f).setDuration(160L)
                                .setInterpolator(AccelerateInterpolator())
                                .withEndAction { onDismiss() }.start()
                        } else {
                            v.animate().translationX(0f).alpha(1f).setDuration(260L)
                                .setInterpolator(OvershootInterpolator(1.5f)).start()
                        }
                    }
                    vt?.recycle()
                    vt = null
                    wasDrag
                }
                else -> false
            }
        }
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
        if (!u.isNullOrBlank() && (u.startsWith("http://") || u.startsWith("https://"))) {
            hideTabs()
            newTab(u)
        }
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

    /** Remembers the full address. The pill shows only the host until the person taps it to type. */
    private fun setUrlText(u: String?) {
        fullUrl = if (NewTabPage.isNewTab(u)) "" else (u ?: "")
        if (urlBar.hasFocus()) {
            suppressSuggest = true
            urlBar.setText(fullUrl)
            suppressSuggest = false
        } else {
            showHostText()
        }
        updateSecureIcon()
        dismissSuggest()
    }

    private fun hostLabel(u: String): String {
        if (u.isEmpty()) return ""
        val h = Uri.parse(u).host
        return if (h.isNullOrEmpty()) u else h.removePrefix("www.")
    }

    private fun showHostText() {
        suppressSuggest = true
        urlBar.setText(hostLabel(fullUrl))
        suppressSuggest = false
    }

    private fun updateSecureIcon() {
        val https = fullUrl.startsWith("https://")
        val http = fullUrl.startsWith("http://")
        secureIcon.setImageResource(
            when {
                https -> R.drawable.q_ic_lock
                http -> R.drawable.q_ic_lock_open
                else -> R.drawable.q_ic_search
            }
        )
        secureIcon.imageTintList = if (http) ColorStateList.valueOf(Q.color(this, R.color.q_danger)) else null
        secureIcon.contentDescription = when {
            https -> "Secure connection"
            http -> "Not secure"
            else -> "Search"
        }
    }

    private fun tabHost(t: Tab): String {
        val u = t.web.url ?: t.pending ?: ""
        return if (NewTabPage.isNewTab(u)) "" else (Uri.parse(u).host ?: "")
    }

    /** Typing mode: the pill shows the full address with a clear button; the tab and menu buttons step aside. */
    private fun onUrlFocus(has: Boolean) {
        android.transition.TransitionManager.beginDelayedTransition(pillWrap)
        if (has) {
            setCompact(false)
            tabsBtn?.visibility = View.GONE
            menuBtn.visibility = View.GONE
            clearBtn.visibility = View.VISIBLE
            suppressSuggest = true
            urlBar.setText(fullUrl)
            suppressSuggest = false
            urlBar.post { urlBar.selectAll() }
            Q.tick(pill)
        } else {
            dismissSuggest()
            tabsBtn?.visibility = View.VISIBLE
            menuBtn.visibility = View.VISIBLE
            clearBtn.visibility = View.GONE
            showHostText()
        }
    }

    /** Leaves typing mode: hides the keyboard and the suggestions, and puts the focus on the pill frame. */
    private fun blurUrl() {
        dismissSuggest()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlBar.windowToken, 0)
        urlBar.clearFocus()
        pillWrap.requestFocus()
    }

    /** Shrinks the pill to a small host-only capsule (on = true) or restores it. The pill row has a fixed height, so the page never resizes. */
    private fun setCompact(on: Boolean) {
        if (compact == on) return
        compact = on
        val lp = pill.layoutParams as FrameLayout.LayoutParams
        val d = resources.displayMetrics.density
        val m0 = lp.leftMargin
        val m1 = ((if (on) 76 else 12) * d).toInt()
        val h0 = lp.height
        val h1 = ((if (on) 40 else 56) * d).toInt()
        compactAnim?.cancel()
        if (!on) {
            tabsBtn?.visibility = View.VISIBLE
            menuBtn.visibility = View.VISIBLE
        }
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 240L
        a.interpolator = OvershootInterpolator(0.8f)
        a.addUpdateListener { va ->
            val f = va.animatedValue as Float
            lp.leftMargin = m0 + ((m1 - m0) * f).toInt()
            lp.rightMargin = lp.leftMargin
            lp.height = h0 + ((h1 - h0) * f).toInt()
            pill.layoutParams = lp
            val side = (if (on) 1f - f else f).coerceIn(0f, 1f)
            tabsBtn?.alpha = side
            menuBtn.alpha = side
        }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (compact && on) {
                    tabsBtn?.visibility = View.INVISIBLE
                    menuBtn.visibility = View.INVISIBLE
                }
            }
        })
        compactAnim = a
        a.start()
    }

    private fun dismissSuggest() {
        if (::suggestBox.isInitialized) suggestBox.visibility = View.GONE
    }

    /** Suggestion list: a card right above the pill (rows are built here, newest and bookmarked first as before). */
    private fun showSuggest(text: String) {
        val q = text.trim()
        if (q.isEmpty() || suppressSuggest || !urlBar.hasFocus()) {
            dismissSuggest()
            return
        }
        val items = BrowserStore.suggest(this, q)
        suggestItems = items
        val marks = BrowserStore.bookmarks(this).map { it.url }.toHashSet()
        suggestList.removeAllViews()
        val rowH = Q.dp(this, 52)
        val maxRows = (((webHost?.height ?: 0) - Q.dp(this, 16)) / rowH).coerceIn(2, 7)
        val tv = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)

        fun row(icon: Int, title: String, sub: String, target: String) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(Q.dp(this, 16), 0, Q.dp(this, 16), 0)
            r.setBackgroundResource(tv.resourceId)
            val ic = ImageView(this)
            ic.setImageResource(icon)
            ic.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            r.addView(ic, LinearLayout.LayoutParams(Q.dp(this, 24), Q.dp(this, 24)))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val t = TextView(this)
            t.text = title
            t.textSize = 15f
            t.maxLines = 1
            t.ellipsize = android.text.TextUtils.TruncateAt.END
            t.setTextColor(Q.color(this, R.color.q_text))
            col.addView(t)
            if (sub.isNotEmpty()) {
                val st = TextView(this)
                st.text = sub
                st.textSize = 12f
                st.maxLines = 1
                st.ellipsize = android.text.TextUtils.TruncateAt.END
                st.setTextColor(Q.color(this, R.color.q_text_sub))
                col.addView(st)
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = Q.dp(this, 16)
            r.addView(col, lp)
            r.setOnClickListener {
                Q.tick(it)
                blurUrl()
                go(target)
            }
            suggestList.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH))
        }

        row(R.drawable.q_ic_search, q, "", text)
        for (e in items.take(maxRows - 1)) {
            row(
                if (e.url in marks) R.drawable.q_ic_star else R.drawable.q_ic_history,
                e.title.ifBlank { e.url }.take(80),
                e.url.removePrefix("https://").removePrefix("http://").take(90),
                e.url
            )
        }
        suggestBox.visibility = View.VISIBLE
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
        fun dp(n: Int) = Q.dp(this, n)
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundResource(R.drawable.q_pill)
        bar.minimumHeight = dp(56)
        bar.setPadding(dp(20), 0, dp(4), 0)
        bar.elevation = dp(8).toFloat()

        val input = EditText(this)
        input.hint = "Find in page"
        input.setHintTextColor(Q.color(this, R.color.q_text_sub))
        input.setTextColor(Q.color(this, R.color.q_text))
        input.textSize = 15f
        input.isSingleLine = true
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.setBackgroundColor(0x00000000)

        val count = TextView(this)
        count.setTextColor(Q.color(this, R.color.q_text_sub))
        count.textSize = 13f
        count.setPadding(dp(8), 0, dp(8), 0)

        /** 48dp icon button (the arrows are the back arrow turned). */
        fun btn(icon: Int, rot: Float, desc: String, action: () -> Unit): ImageView {
            val b = ImageView(this)
            b.setImageDrawable(Q.icon(this, icon))
            b.rotation = rot
            b.scaleType = ImageView.ScaleType.CENTER
            b.contentDescription = desc
            b.isClickable = true
            b.setOnClickListener {
                Q.tick(it)
                action()
            }
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
        bar.addView(btn(R.drawable.q_ic_back, 90f, "Previous match") { web.findNext(false) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(btn(R.drawable.q_ic_back, -90f, "Next match") { web.findNext(true) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(btn(R.drawable.q_ic_close, 0f, "Close find") { hideFind() }, LinearLayout.LayoutParams(dp(48), dp(48)))

        val root = findViewById<ViewGroup>(android.R.id.content)
        val flp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        flp.setMargins(dp(12), 0, dp(12), dp(8))
        root.addView(bar, flp)
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

    private fun toggleDesktopSite(pageUrl: String) {
        val host = BrowserStore.siteKey(Uri.parse(pageUrl).host)
        if (host.isEmpty()) {
            Toast.makeText(this, "Open a page first (or use Menu > Site settings for all sites)", Toast.LENGTH_LONG).show()
            return
        }
        val now = !desktopFor(host)
        desktopSites = BrowserStore.setDesktopSite(this, host, now, desktop)
        curTab?.let { syncDesktop(it) }
        Toast.makeText(this, "Desktop site " + (if (now) "on" else "off") + " for " + host, Toast.LENGTH_SHORT).show()
        web.reload()
    }

    /** The switches for all sites. They live in Site settings; the Ad block and Desktop buttons are for the page you are on. */
    private fun toggleAdblockAll() {
        adblock = !adblock
        BrowserStore.setAdblock(this, adblock)
        Toast.makeText(this, if (adblock) "Ad blocker on for all sites" else "Ad blocker off for all sites", Toast.LENGTH_SHORT).show()
        web.reload()
    }

    private fun toggleDesktopAll() {
        desktop = !desktop
        BrowserStore.setDesktop(this, desktop)
        applyDesktopAll()
        Toast.makeText(this, if (desktop) "Desktop mode on for all sites" else "Desktop mode off for all sites", Toast.LENGTH_SHORT).show()
        web.reload()
    }

    private fun toggleSiteAds(pageUrl: String) {
        val host = BrowserStore.siteKey(Uri.parse(pageUrl).host)
        if (host.isEmpty()) {
            Toast.makeText(this, "Open a page first", Toast.LENGTH_SHORT).show()
            return
        }
        if (!adblock) {
            Toast.makeText(this, "The ad blocker is off for all sites. Turn it on in Menu > Site settings first.", Toast.LENGTH_LONG).show()
            return
        }
        val allowNow = !siteAllowed(host)
        allowHosts = BrowserStore.setAdblockAllowed(this, host, allowNow)
        Toast.makeText(this, if (allowNow) "Ads allowed on $host" else "Ad blocker on again for $host", Toast.LENGTH_SHORT).show()
        web.reload()
    }

    /** Ad blocker and desktop mode: the switches for all sites first, then the sites with their own setting (tap one to reset it). */
    private fun showSiteSettings() {
        val items = ArrayList<String>()
        val acts = ArrayList<(() -> Unit)?>()
        items.add("Ad blocker, all sites: " + (if (adblock) "on  (tap to turn off)" else "off  (tap to turn on)"))
        acts.add { toggleAdblockAll() }
        items.add("Desktop mode, all sites: " + (if (desktop) "on  (tap to turn off)" else "off  (tap to turn on)"))
        acts.add { toggleDesktopAll() }
        if (adblock) {
            items.add("Blocked $blockedCount on this page")
            acts.add(null)
        }
        if (allowHosts.isEmpty() && desktopSites.isEmpty()) {
            items.add("No site has its own setting yet. Use the Ad block and Desktop buttons while you are on a page.")
            acts.add(null)
        }
        for (h in allowHosts.sorted()) {
            items.add("Ads allowed: $h  (tap to block again)")
            acts.add {
                allowHosts = BrowserStore.setAdblockAllowed(this, h, false)
                Toast.makeText(this, "Reset", Toast.LENGTH_SHORT).show()
            }
        }
        for ((h, on) in desktopSites.toSortedMap()) {
            items.add("Desktop " + (if (on) "on" else "off") + ": $h  (tap to reset)")
            acts.add {
                desktopSites = BrowserStore.removeDesktopSite(this, h)
                applyDesktopAll()
                Toast.makeText(this, "Reset", Toast.LENGTH_SHORT).show()
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Site settings")
            .setItems(items.toTypedArray()) { _, i -> acts[i]?.invoke() }
            .setNegativeButton("Close", null)
            .show()
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
        curTab?.let { it.pageHost = Uri.parse(target).host; syncDesktop(it) }
        web.loadUrl(target)
    }

    // ---------- menu: bottom sheet (Quiet Pill, 0.9.14) ----------

    private fun showMenu(anchor: View) {
        val pageUrl = web.url?.takeUnless { NewTabPage.isNewTab(it) } ?: ""
        val marked = pageUrl.isNotEmpty() && BrowserStore.isBookmarked(this, pageUrl)
        val pageHost = Uri.parse(pageUrl).host
        val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        val sheet = BottomSheetDialog(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(Q.dp(this, 8), Q.dp(this, 20), Q.dp(this, 8), Q.dp(this, 16))

        // Row of round quick buttons.
        val quick = LinearLayout(this)
        quick.orientation = LinearLayout.HORIZONTAL
        quick.setPadding(0, 0, 0, Q.dp(this, 8))
        fun quickBtn(icon: Int, label: String, on: Boolean, toggle: Boolean, id: Int) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            col.contentDescription = label + if (toggle) (if (on) ", on" else ", off") else ""
            val img = ImageView(this)
            img.setImageResource(icon)
            img.scaleType = ImageView.ScaleType.CENTER
            img.setBackgroundResource(if (on) R.drawable.q_circle_btn_on else R.drawable.q_circle_btn)
            img.imageTintList = ColorStateList.valueOf(Q.color(this, if (on) R.color.q_on_accent else R.color.q_icon))
            img.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            col.addView(img, LinearLayout.LayoutParams(Q.dp(this, 48), Q.dp(this, 48)))
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 11f
            tv.maxLines = 1
            tv.ellipsize = android.text.TextUtils.TruncateAt.END
            tv.gravity = Gravity.CENTER
            tv.setTextColor(Q.color(this, R.color.q_text_sub))
            tv.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            tv.setPadding(0, Q.dp(this, 4), 0, Q.dp(this, 4))
            col.addView(tv, LinearLayout.LayoutParams(WRAP, WRAP))
            col.setOnClickListener {
                Q.tick(it)
                sheet.dismiss()
                menuAction(id, pageUrl)
            }
            quick.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        quickBtn(if (marked) R.drawable.q_ic_star else R.drawable.q_ic_star_outline, "Bookmark", marked, true, 1)
        quickBtn(R.drawable.q_ic_share, "Share", false, false, 9)
        quickBtn(R.drawable.q_ic_search, "Find", false, false, 23)
        quickBtn(R.drawable.q_ic_dark, "Dark", darkPage, true, 25)
        quickBtn(R.drawable.q_ic_shield, "Ad block", adblock && !siteAllowed(pageHost), true, 28)
        quickBtn(R.drawable.q_ic_desktop, "Desktop", desktopFor(pageHost), true, 7)

        // Compact list below (no submenus).
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val sel = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, sel, true)
        fun divider() {
            val v = View(this)
            v.setBackgroundColor(Q.color(this, R.color.q_outline))
            val lp = LinearLayout.LayoutParams(MATCH, Q.dp(this, 1))
            lp.setMargins(Q.dp(this, 16), Q.dp(this, 4), Q.dp(this, 16), Q.dp(this, 4))
            list.addView(v, lp)
        }
        fun item(icon: Int, label: String, sub: String?, checked: Boolean?, id: Int, danger: Boolean = false, flip: Boolean = false) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.minimumHeight = Q.dp(this, 48)
            row.setPadding(Q.dp(this, 16), Q.dp(this, 6), Q.dp(this, 16), Q.dp(this, 6))
            row.setBackgroundResource(sel.resourceId)
            val ic = ImageView(this)
            ic.setImageResource(icon)
            if (flip) ic.scaleX = -1f
            if (danger) ic.imageTintList = ColorStateList.valueOf(Q.color(this, R.color.q_danger))
            ic.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            row.addView(ic, LinearLayout.LayoutParams(Q.dp(this, 24), Q.dp(this, 24)))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val t = TextView(this)
            t.text = label
            t.textSize = 15f
            t.setTextColor(Q.color(this, if (danger) R.color.q_danger else R.color.q_text))
            col.addView(t)
            if (!sub.isNullOrBlank()) {
                val st = TextView(this)
                st.text = sub
                st.textSize = 12f
                st.setTextColor(Q.color(this, R.color.q_text_sub))
                col.addView(st)
            }
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            lp.marginStart = Q.dp(this, 16)
            row.addView(col, lp)
            if (checked == true) {
                val ck = ImageView(this)
                ck.setImageResource(R.drawable.q_ic_check)
                ck.imageTintList = ColorStateList.valueOf(Q.color(this, R.color.q_accent))
                ck.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                row.addView(ck, LinearLayout.LayoutParams(Q.dp(this, 24), Q.dp(this, 24)))
            }
            row.contentDescription = label +
                (if (!sub.isNullOrBlank()) ", $sub" else "") +
                (if (checked != null) (if (checked) ", on" else ", off") else "")
            row.setOnClickListener {
                Q.tick(it)
                sheet.dismiss()
                menuAction(id, pageUrl)
            }
            list.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        divider()
        item(R.drawable.q_ic_add, "New tab", null, null, 21)
        item(R.drawable.q_ic_bookmark, "Bookmarks", null, null, 2)
        item(R.drawable.q_ic_history, "History", null, null, 3)
        val active = DlJobs.activeCount()
        item(R.drawable.q_ic_download, "Downloads", if (active > 0) "$active running" else null, null, 12)
        item(R.drawable.q_ic_tune, "Media settings", null, null, 30)
        divider()
        item(R.drawable.q_ic_back, "Forward", null, null, 4, flip = true)
        item(R.drawable.q_ic_refresh, "Reload", null, null, 5)
        item(R.drawable.q_ic_copy, "Copy link", null, null, 10)
        item(R.drawable.q_ic_text_size, "Text size", BrowserStore.textZoom(this).toString() + "%", null, 24)
        item(R.drawable.q_ic_save_page, "Save page (PDF / archive)", null, null, 26)
        divider()
        item(R.drawable.q_ic_globe, "Search engine", BrowserStore.engine(this).name, null, 22)
        item(
            R.drawable.q_ic_tune, "Site settings",
            "All sites: ad blocker " + (if (adblock) "on" else "off") + ", desktop " + (if (desktop) "on" else "off"),
            null, 29
        )
        item(R.drawable.q_ic_history, "Save history", null, BrowserStore.saveHistory(this), 8)
        divider()
        item(R.drawable.q_ic_bookmark, "Bookmarks: export / import", null, null, 13)
        item(R.drawable.q_ic_close, "Clear browsing data", null, null, 11, danger = true)
        item(R.drawable.q_ic_close, "Close browser", null, null, 14)

        content.addView(quick, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(list, LinearLayout.LayoutParams(MATCH, WRAP))
        val sv = NestedScrollView(this)
        sv.addView(content)
        sheet.setContentView(sv)
        sheet.setOnShowListener {
            sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundResource(R.drawable.q_sheet_bg)
            sheet.behavior.skipCollapsed = true
            sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        sheet.show()
    }

    /** One menu entry by id (same ids as the old popup menu; 14 = close the browser). */
    private fun menuAction(id: Int, pageUrl: String) {
        when (id) {
            1 -> if (pageUrl.startsWith("http")) {
                val now = BrowserStore.toggleBookmark(this, pageUrl, web.title ?: "")
                Toast.makeText(this, if (now) "Bookmarked" else "Bookmark removed", Toast.LENGTH_SHORT).show()
            }
            2 -> showManager(true)
            3 -> showManager(false)
            4 -> if (web.canGoForward()) web.goForward()
            5 -> web.reload()
            6 -> toggleAdblockAll()
            7 -> toggleDesktopSite(pageUrl)
            27 -> toggleDesktopAll()
            28 -> toggleSiteAds(pageUrl)
            29 -> showSiteSettings()
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
            30 -> MediaSettings.show(this)
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
            14 -> finish()
        }
    }

    private fun showList(title: String, list: List<BrowserEntry>, clearLabel: String, onClear: () -> Unit) {
        if (list.isEmpty()) {
            Toast.makeText(this, "$title is empty", Toast.LENGTH_SHORT).show()
            return
        }
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val labels = list.map {
            val name = if (it.title.isBlank()) it.url else it.title
            name + "\n" + (Uri.parse(it.url).host ?: "") + "  ·  " + df.format(Date(it.ts)) + (if (it.folder.isNotBlank()) "  ·  " + it.folder else "")
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
            (if (e.folder.isNotBlank()) "  ·  " + e.folder else "")
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
            if (!MediaPrefs.detect(this)) return@runOnUiThread
            if (tab.found.containsKey(url) || tab.found.size >= 200) return@runOnUiThread
            tab.found[url] = Media(url, kind, tab.web.url ?: "", tab.web.title ?: "")
            if (tab === curTab) {
                updateChip()
                if (panel.visibility == View.VISIBLE) showPanel()
            }
        }
    }

    private fun updateChip() {
        chip.text = "${found.size} found"
        chip.contentDescription = "${found.size} media found on this page. Open list"
        chip.visibility = if (found.isNotEmpty() && panel.visibility != View.VISIBLE) View.VISIBLE else View.GONE
    }

    /** Slides the media sheet down, then hides it. */
    private fun hidePanel() {
        if (panel.visibility != View.VISIBLE) {
            updateChip()
            return
        }
        panelClosing = true
        panel.animate().cancel()
        panel.animate().translationY(maxOf(panel.height, Q.dp(this, 360)).toFloat()).setDuration(180L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                panelClosing = false
                panel.visibility = View.GONE
                panel.translationY = 0f
                updateChip()
            }.start()
    }

    /** Drag the sheet's handle/title area down to close it (past 96dp), otherwise it springs back. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachSheetDrag(head: View) {
        var y0 = 0f
        var dy = 0f
        head.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    panel.animate().cancel()
                    y0 = e.rawY
                    dy = 0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    dy = maxOf(0f, e.rawY - y0)
                    panel.translationY = dy
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (e.actionMasked == MotionEvent.ACTION_UP && dy > Q.dp(this, 96)) {
                        hidePanel()
                    } else {
                        panel.animate().translationY(0f).setDuration(260L)
                            .setInterpolator(OvershootInterpolator(1.5f)).start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** Media sheet (0.9.17, D5): pinned bar (filter + Select, or the select bar), then the list. */
    private fun showPanel() {
        if (panelClosing && panel.visibility == View.VISIBLE) return
        fun dp(n: Int) = Q.dp(this, n)
        val wasHidden = panel.visibility != View.VISIBLE
        panel.animate().cancel()
        panel.visibility = View.VISIBLE
        if (wasHidden) {
            panel.translationY = dp(360).toFloat()
            panel.animate().translationY(0f).setDuration(260L).setInterpolator(DecelerateInterpolator()).start()
        } else {
            panel.translationY = 0f
        }
        val pageH = (panel.parent as? View)?.height ?: 0
        if (pageH > 0) {
            val want = minOf(dp(360), (pageH * 0.6f).toInt())
            if (panel.layoutParams.height != want) {
                panel.layoutParams.height = want
                panel.requestLayout()
            }
        }
        chip.visibility = View.GONE
        panelList.removeAllViews()
        panelBar.removeAllViews()
        selected.retainAll(found.keys)

        fun note(msg: String) = TextView(this).apply {
            text = msg
            setTextColor(Q.color(this@BrowserActivity, R.color.q_text_sub))
            textSize = 14f
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }

        if (found.isEmpty()) {
            selectMode = false
            panelBar.visibility = View.GONE
            panelList.addView(note("Nothing found yet. Play a video on the page and it will show up here."))
            return
        }
        if (panelFilter != "all" && found.values.none { it.kind == panelFilter }) panelFilter = "all"
        val shown = found.values.reversed().filter { panelFilter == "all" || it.kind == panelFilter }
        val accent = Q.color(this, R.color.q_accent)
        val rows = ArrayList<Pair<Media, CheckBox>>()

        // select mode acts on the ticked items only
        fun targets(): List<Media> = if (selectMode) shown.filter { it.url in selected } else shown

        /** Rounded chip with an icon; 48dp touch height (the visible chip is 40dp). */
        fun chipBtn(label: String, icon: Int, strong: Boolean, onClick: () -> Unit): TextView {
            val b = TextView(this)
            b.text = label
            b.textSize = 14f
            b.gravity = Gravity.CENTER_VERTICAL
            b.minHeight = dp(48)
            b.setPadding(dp(12), 0, dp(16), 0)
            b.compoundDrawablePadding = dp(8)
            val col = if (strong) Q.color(this, R.color.q_accent_text) else Q.color(this, R.color.q_text)
            b.setTextColor(col)
            val ic = Q.icon(this, icon)
            ic?.setTint(col)
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(ic, null, null, null)
            val bg = Q.icon(this, if (strong) R.drawable.q_chip_accent else R.drawable.q_chip)
            if (bg != null) b.background = InsetDrawable(bg, 0, dp(4), 0, dp(4))
            b.isClickable = true
            b.setOnClickListener {
                Q.tick(it)
                onClick()
            }
            return b
        }

        fun iconBtn(icon: Int, desc: String, onClick: () -> Unit): ImageView {
            val b = ImageView(this)
            b.setImageDrawable(Q.icon(this, icon))
            b.scaleType = ImageView.ScaleType.CENTER
            b.contentDescription = desc
            b.isClickable = true
            b.setOnClickListener {
                Q.tick(it)
                onClick()
            }
            return b
        }

        if (!selectMode) {
            val filterLabel = (if (panelFilter == "all") "All" else panelFilter) + " (${shown.size})"
            val f = chipBtn(filterLabel, R.drawable.q_ic_tune, false) { chooseMediaFilter() }
            f.contentDescription = "Filter: $filterLabel"
            val sel = chipBtn("Select", R.drawable.q_ic_check, false) {
                selectMode = true
                showPanel()
            }
            panelBar.addView(f)
            panelBar.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
            panelBar.addView(sel)
        } else {
            // contextual bar: Done, count, All/None, Copy, Grabber
            val ctx = LinearLayout(this)
            ctx.orientation = LinearLayout.HORIZONTAL
            ctx.gravity = Gravity.CENTER_VERTICAL
            ctx.setBackgroundResource(R.drawable.q_chip_accent)
            val done = iconBtn(R.drawable.q_ic_close, "Done selecting") {
                selectMode = false
                selected.clear()
                showPanel()
            }
            val count = TextView(this)
            count.textSize = 14f
            count.maxLines = 1
            count.ellipsize = android.text.TextUtils.TruncateAt.END
            count.setTextColor(Q.color(this, R.color.q_text))
            val allBtn = TextView(this)
            allBtn.textSize = 14f
            allBtn.gravity = Gravity.CENTER
            allBtn.minHeight = dp(48)
            allBtn.setPadding(dp(8), 0, dp(8), 0)
            allBtn.setTextColor(Q.color(this, R.color.q_accent_text))
            allBtn.isClickable = true
            val copy = iconBtn(R.drawable.q_ic_copy, "Copy links") { copyLinks(targets()) }
            val send = iconBtn(R.drawable.q_ic_share, "Send to Media Grabber") { sendToGrabber(targets()) }
            // Narrow screens (about 360-410dp): Download is an icon, so the "N selected" count keeps room to show.
            val compact = resources.configuration.screenWidthDp < 440
            val dl: View = if (compact) {
                iconBtn(R.drawable.q_ic_download, "Download selected") { downloadSelected(targets()) }.also {
                    it.imageTintList = ColorStateList.valueOf(Q.color(this, R.color.q_accent_text))
                }
            } else {
                chipBtn("Download", R.drawable.q_ic_download, true) { downloadSelected(targets()) }.also {
                    it.setPadding(dp(8), 0, dp(12), 0)
                }
            }
            fun refresh() {
                val n = targets().size
                count.text = if (n == 0) "Select items" else "$n selected"
                allBtn.text = if (shown.isNotEmpty() && shown.all { it.url in selected }) "None" else "All"
                copy.alpha = if (n == 0) 0.4f else 1f
                send.alpha = if (n == 0) 0.4f else 1f
                dl.alpha = if (n == 0) 0.4f else 1f
            }
            allBtn.setOnClickListener {
                Q.tick(it)
                val every = shown.isNotEmpty() && shown.all { it.url in selected }
                if (every) selected.removeAll(shown.map { it.url }.toSet()) else selected.addAll(shown.map { it.url })
                for ((m, cb) in rows) cb.isChecked = m.url in selected
                refresh()
            }
            ctx.addView(done, LinearLayout.LayoutParams(dp(48), dp(48)))
            ctx.addView(count, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            ctx.addView(allBtn)
            ctx.addView(copy, LinearLayout.LayoutParams(dp(48), dp(48)))
            ctx.addView(send, LinearLayout.LayoutParams(dp(48), dp(48)))
            if (compact) ctx.addView(dl, LinearLayout.LayoutParams(dp(48), dp(48))) else ctx.addView(dl)
            panelBar.addView(ctx, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            selectRefresh = { refresh() }
            refresh()
        }
        panelBar.visibility = View.VISIBLE

        if (shown.isEmpty()) {
            panelList.addView(note("No items of this type."))
            return
        }

        for (m in shown) {
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
            v.setOnLongClickListener {
                mediaActions(m)
                true
            }

            val cb = CheckBox(this)
            cb.visibility = if (selectMode) View.VISIBLE else View.GONE
            cb.buttonTintList = ColorStateList.valueOf(accent)
            cb.isChecked = m.url in selected
            cb.contentDescription = "Select $seg"
            cb.setOnCheckedChangeListener { _, on ->
                if (on) selected.add(m.url) else selected.remove(m.url)
                selectRefresh?.invoke()
            }
            if (selectMode) v.setOnClickListener { cb.toggle() }
            rows.add(Pair(m, cb))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.addView(cb)
            row.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            panelList.addView(row)
        }
    }

    private fun chooseMediaFilter() {
        val counts = found.values.groupingBy { it.kind }.eachCount()
        val kinds = listOf("all") + counts.keys.sorted()
        val labels = kinds.map { if (it == "all") "All (${found.size})" else "$it (${counts[it]})" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Show")
            .setSingleChoiceItems(labels, kinds.indexOf(panelFilter).coerceAtLeast(0)) { d, i ->
                panelFilter = kinds[i]
                d.dismiss()
                showPanel()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copyLinks(list: List<Media>) {
        if (list.isEmpty()) {
            Toast.makeText(this, "No links to copy", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("links", list.joinToString("\n") { it.url }))
        Toast.makeText(this, "${list.size} link(s) copied", Toast.LENGTH_SHORT).show()
    }

    /** Starts a download for every ticked item (HLS: the saved quality, else best; whole stream). Progress: menu > Downloads. */
    private fun downloadSelected(list: List<Media>) {
        if (list.isEmpty()) {
            Toast.makeText(this, "Select items first", Toast.LENGTH_SHORT).show()
            return
        }
        // Videos picked from the found list and no subtitle ticked, but the page has subtitle files: offer them (same name as the video).
        val subs = found.values.filter { it.kind == "subtitle" && it.url !in list.map { x -> x.url } }
        val onlyFound = list.all { found.containsKey(it.url) }
        if (onlyFound && subs.isNotEmpty() && list.any { it.kind == "hls" || it.kind == "video" } && list.none { it.kind == "subtitle" }) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Subtitles")
                .setMessage("This page also has ${subs.size} subtitle file(s). Download them with the video? They get the video's name and the language.")
                .setPositiveButton("Yes, add") { _, _ -> startBatch(list + subs) }
                .setNegativeButton("No") { _, _ -> startBatch(list) }
                .setNeutralButton("Cancel", null)
                .show()
        } else startBatch(list)
    }

    private fun startBatch(list: List<Media>) {
        val items = list.take(MAX_BATCH)
        val extra = list.size - items.size
        fun go() {
            val ua = web.settings.userAgentString
            val opts = DlOptions(preferHeight = MediaPrefs.hlsHeight(this))
            val gap = MediaPrefs.delayMs(this).toLong()
            val h = Handler(Looper.getMainLooper())
            items.forEachIndexed { i, m ->
                if (i == 0 || gap <= 0L) downloader.start(m, ua, opts) { _, _ -> }
                else h.postDelayed({ downloader.start(m, ua, opts) { _, _ -> } }, gap * i)
            }
            selected.clear()
            selectMode = false
            showPanel()
            Toast.makeText(
                this,
                "${items.size} added to Downloads (menu > Downloads)" + if (extra > 0) ". $extra more left out (max $MAX_BATCH at once)" else "",
                Toast.LENGTH_LONG
            ).show()
        }
        if (items.size <= 5) go() else MaterialAlertDialogBuilder(this)
            .setTitle("Download ${items.size} items?")
            .setMessage("They are saved to Downloads/JabraHub. Streams: " + MediaPrefs.hlsHeight(this).let { if (it > 0) "up to ${it}p." else "best quality." })
            .setPositiveButton("Download") { _, _ -> go() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun mediaActions(m: Media) {
        val canEp = MediaNaming.patternFromMedia(m.url)?.hashed == false
        val labels = if (canEp) arrayOf("Copy link", "Send to Media Grabber", "Make episode list…") else arrayOf("Copy link", "Send to Media Grabber")
        MaterialAlertDialogBuilder(this)
            .setTitle((Uri.parse(m.url).lastPathSegment ?: m.url).take(80))
            .setItems(labels) { _, i ->
                when (i) {
                    0 -> copyLinks(listOf(m))
                    1 -> sendToGrabber(listOf(m))
                    else -> episodeList(m)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Episode list from one found link: the episode number in the link is replaced for each number typed (1-12, 1,3,5-8).
     * Download starts them all (same limits as Select > Download); Send hands the links to Media Grabber. Links are guessed, so some may fail.
     */
    private fun episodeList(m: Media) {
        val p = MediaNaming.patternFromMedia(m.url)
        if (p == null || p.hashed) {
            Toast.makeText(this, "No episode number found in this link", Toast.LENGTH_LONG).show()
            return
        }
        val et = EditText(this)
        et.setText("1-12")
        et.hint = "1-12"
        et.setSingleLine(true)
        et.setSelection(et.text.length)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(et)
        fun build(): List<Media>? {
            val nums = MediaNaming.parseNumList(et.text.toString(), 500).take(200)
            if (nums.isEmpty()) {
                Toast.makeText(this, "Type episodes, e.g. 1-12", Toast.LENGTH_SHORT).show()
                return null
            }
            val urls = MediaNaming.expandPattern(p.pattern, nums)
            return urls.indices.map { i ->
                Media(urls[i], m.kind, m.page, MediaNaming.episodeTitle(m.title, nums[i], MediaPrefs.library(this)))
            }
        }
        val here = if (p.n % 1.0 == 0.0) p.n.toLong().toString() else p.n.toString()
        MaterialAlertDialogBuilder(this)
            .setTitle("Episode list")
            .setMessage("This link is episode $here. Type the episodes to make, e.g. 1-12 or 1,3,5-8. Other links are guessed from this one, so some may not exist.")
            .setView(box)
            .setPositiveButton("Download") { _, _ -> build()?.let { downloadSelected(it) } }
            .setNeutralButton("Send to Grabber") { _, _ -> build()?.let { sendToGrabber(it) } }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Hands the links to a plugin that accepts shared links (Media Grabber), the same way the Android
     * Share sheet does (ShareActivity): Floater.save(..., "share"), then the plugin opens. Nothing downloads by itself.
     */
    private fun sendToGrabber(list: List<Media>) {
        if (list.isEmpty()) {
            Toast.makeText(this, "No links to send", Toast.LENGTH_SHORT).show()
            return
        }
        val plugins = try {
            PluginStore(this).list().filter { Floater.acceptsShare(this, it.id) }
        } catch (e: Exception) {
            AppDiagnostics.record(applicationContext, "Browser: plugin list failed", e)
            emptyList()
        }
        val title = (web.title ?: "").replace('\n', ' ').trim().take(120)
        // The page address goes along as a normal link: Media Grabber uses a page link in the list as the referer
        // (needed by sites that answer 403 to a bare media link). Use the page the media was found on.
        val pageRef = list.map { it.page }.firstOrNull { it.startsWith("http") && !NewTabPage.isNewTab(it) }
            ?: web.url?.takeIf { it.startsWith("http") && !NewTabPage.isNewTab(it) }
        val links = list.map { it.url }.toMutableList()
        if (pageRef != null && pageRef !in links) links.add(pageRef)
        val text = (if (title.isNotEmpty()) title + "\n" else "") + links.joinToString("\n")
        fun deliver(id: String, name: String, entry: String) {
            try {
                Floater.save(applicationContext, id, text, "share")
            } catch (e: Exception) {
                AppDiagnostics.record(applicationContext, "Browser: send to plugin failed: $id", e)
                Toast.makeText(this, "Could not hand over the links", Toast.LENGTH_LONG).show()
                return
            }
            startActivity(
                Intent(this, PluginActivity::class.java)
                    .putExtra("id", id)
                    .putExtra("name", name)
                    .putExtra("entry", entry)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        when (plugins.size) {
            0 -> Toast.makeText(this, "No installed plugin accepts shared links yet. Install or update Media Grabber.", Toast.LENGTH_LONG).show()
            1 -> deliver(plugins[0].id, plugins[0].name, plugins[0].entry)
            else -> MaterialAlertDialogBuilder(this)
                .setTitle("Send ${list.size} link(s) to")
                .setItems(plugins.map { it.name }.toTypedArray()) { _, i -> deliver(plugins[i].id, plugins[i].name, plugins[i].entry) }
                .setNegativeButton("Cancel", null)
                .show()
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
        // the quality used last time is ticked (Hub 0.9.38)
        val want = MediaPrefs.hlsHeight(this)
        val pre = pickVariant(probe.variants, want)?.let { v -> probe.variants.indexOfFirst { it.url == v.url } } ?: 0
        group.check(ids[pre.coerceIn(0, ids.size - 1)])
        box.addView(group)
        val remember = CheckBox(this)
        remember.text = "Use this quality for next downloads"
        remember.isChecked = true
        if (probe.variants.size > 1) box.addView(remember)

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
                    if (probe.variants.size > 1 && remember.isChecked) {
                        MediaPrefs.setHlsHeight(this, if (idx == 0) 0 else probe.variants[idx].height)
                    }
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

private const val THUMB_RATIO = 1.2f

/** Frame whose height is always [ratio] times its width (tab thumbnails). */
private class RatioFrame(ctx: Context, private val ratio: Float) : FrameLayout(ctx) {
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val width = MeasureSpec.getSize(widthSpec)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((width * ratio).toInt(), MeasureSpec.EXACTLY)
        )
    }
}
