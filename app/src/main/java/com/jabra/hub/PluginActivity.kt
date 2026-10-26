package com.jabra.hub

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import com.google.android.material.appbar.MaterialToolbar
import org.json.JSONObject

class PluginActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var progress: View
    private lateinit var errorView: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plugin)

        val id = intent.getStringExtra("id")
        if (id == null) {
            finish()
            return
        }
        val name = intent.getStringExtra("name") ?: id
        val entry = (intent.getStringExtra("entry") ?: "index.html").trimStart('/')

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = name
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener { finish() }

        val store = PluginStore(this)
        val loader = WebViewAssetLoader.Builder()
            .setDomain(HOST)
            .addPathHandler("/plugins/", WebViewAssetLoader.InternalStoragePathHandler(this, store.root))
            .build()

        web = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        errorView = findViewById(R.id.error)
        web.setBackgroundColor(Color.parseColor("#141629"))
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        web.addJavascriptInterface(Bridge(this, id), "JabraAPI")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) showError()
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) showError()
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == HOST) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (_: Exception) {
                }
                return true
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (web.canGoBack()) web.goBack() else finish()
        }

        web.loadUrl("https://$HOST/plugins/$id/$entry")
    }

    private fun showError() {
        progress.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    /** Functions a plugin can call as JabraAPI.xxx(...) */
    class Bridge(private val ctx: Context, pluginId: String) {
        private val sp = ctx.getSharedPreferences("plugin_$pluginId", Context.MODE_PRIVATE)
        private val main = Handler(Looper.getMainLooper())

        @JavascriptInterface
        fun getItem(key: String): String? = sp.getString(key, null)

        @JavascriptInterface
        fun setItem(key: String, value: String) {
            sp.edit().putString(key, value).apply()
        }

        @JavascriptInterface
        fun removeItem(key: String) {
            sp.edit().remove(key).apply()
        }

        @JavascriptInterface
        fun toast(message: String) {
            main.post { Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface
        fun info(): String = JSONObject()
            .put("app", "Jabra Extension Hub")
            .put("android", Build.VERSION.SDK_INT)
            .toString()
    }

    companion object {
        private const val HOST = "appassets.androidplatform.net"
    }
}
