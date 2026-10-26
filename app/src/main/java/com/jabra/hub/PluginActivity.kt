package com.jabra.hub

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebChromeClient
import android.webkit.JsResult
import android.webkit.JsPromptResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.appbar.MaterialToolbar
import org.json.JSONObject

class PluginActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var progress: View
    private lateinit var errorView: TextView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        filePathCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        filePathCallback = null
    }

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
        // Persist standard localStorage calls in app-private files, scoped to this plugin.
        // The script runs before plugin JavaScript, so ordinary web plugins need no changes.
        // This enhancement is optional. Some Android System WebView versions report the
        // feature as supported but may still reject script registration at runtime. Never let
        // that optional storage shim crash the entire plugin Activity.
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(
                    web,
                    persistentLocalStorageScript(),
                    setOf("https://$HOST/*")
                )
            } else {
                Log.w(TAG, "DOCUMENT_START_SCRIPT unavailable; plugin native storage API remains available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not install optional localStorage persistence shim", e)
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@PluginActivity.filePathCallback?.onReceiveValue(null)
                this@PluginActivity.filePathCallback = filePathCallback
                // OpenDocument accepts MIME types, not filename extensions such as ".json".
                // Normalize WebView acceptTypes and fall back safely when a site only provides extensions.
                val rawAccepts = fileChooserParams?.acceptTypes.orEmpty()
                    .flatMap { it.split(",") }
                    .map { it.trim().lowercase() }
                    .filter { it.contains("/") && !it.startsWith(".") }
                    .distinct()
                val accepts = rawAccepts.ifEmpty { listOf("*/*") }.toTypedArray()
                return try {
                    filePicker.launch(accepts)
                    true
                } catch (_: Exception) {
                    this@PluginActivity.filePathCallback?.onReceiveValue(null)
                    this@PluginActivity.filePathCallback = null
                    false
                }
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                MaterialAlertDialogBuilder(this@PluginActivity)
                    .setMessage(message.orEmpty())
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                MaterialAlertDialogBuilder(this@PluginActivity)
                    .setMessage(message.orEmpty())
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setNegativeButton("Cancel") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                val input = EditText(this@PluginActivity).apply { setText(defaultValue.orEmpty()) }
                MaterialAlertDialogBuilder(this@PluginActivity)
                    .setMessage(message.orEmpty())
                    .setView(input)
                    .setPositiveButton("OK") { _, _ -> result?.confirm(input.text.toString()) }
                    .setNegativeButton("Cancel") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }
        }
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

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                Log.e(TAG, "WebView renderer exited; didCrash=${detail.didCrash()}")
                progress.visibility = View.GONE
                errorView.visibility = View.VISIBLE
                errorView.text = "This plugin's WebView stopped unexpectedly. Go back to the Hub and reopen it. If it repeats, update Android System WebView and share the crash log."
                return true
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
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    /** Native, app-private persistence APIs available to every plugin as JabraAPI.xxx(...). */
    class Bridge(private val ctx: Context, private val pluginId: String) {
        private val sp = ctx.getSharedPreferences("plugin_$pluginId", Context.MODE_PRIVATE)
        private val main = Handler(Looper.getMainLooper())
        private val dataRoot: File by lazy {
            File(File(ctx.filesDir, "plugin_data"), pluginId).apply { mkdirs() }
        }

        private fun encodedKey(key: String): String =
            "d_" + android.util.Base64.encodeToString(key.toByteArray(Charsets.UTF_8), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)

        private fun dataFile(key: String): File {
            require(key.length <= 512) { "Storage key is too long" }
            return File(dataRoot, encodedKey(key))
        }

        @JavascriptInterface
        fun storageGet(key: String): String? = try {
            val f = dataFile(key)
            if (f.isFile) f.readText(Charsets.UTF_8) else null
        } catch (_: Exception) { null }

        @JavascriptInterface
        fun storageSet(key: String, value: String): Boolean = try {
            require(value.length <= 40_000_000) { "Storage value is too large" }
            dataRoot.mkdirs()
            val dest = dataFile(key)
            val tmp = File(dataRoot, dest.name + ".tmp")
            FileOutputStream(tmp).use { out ->
                out.write(value.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            val backup = File(dataRoot, dest.name + ".bak")
            backup.delete()
            if (dest.exists() && !dest.renameTo(backup)) throw IllegalStateException("Cannot prepare existing saved data")
            val installed = tmp.renameTo(dest) || try { tmp.copyTo(dest, overwrite = true); tmp.delete(); true } catch (_: Exception) { false }
            if (!installed) {
                dest.delete()
                if (backup.exists()) backup.renameTo(dest)
                throw IllegalStateException("Could not commit saved data")
            }
            backup.delete()
            true
        } catch (_: Exception) { false }

        @JavascriptInterface
        fun storageRemove(key: String): Boolean = try { !dataFile(key).exists() || dataFile(key).delete() } catch (_: Exception) { false }

        @JavascriptInterface
        fun storageKeys(prefix: String): String = try {
            val keys = (dataRoot.listFiles() ?: emptyArray()).asSequence()
                .filter { it.isFile && it.name.startsWith("d_") && !it.name.endsWith(".tmp") }
                .mapNotNull { f -> try { String(android.util.Base64.decode(f.name.removePrefix("d_"), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING), Charsets.UTF_8) } catch (_: Exception) { null } }
                .filter { it.startsWith(prefix) }.sorted().toList()
            org.json.JSONArray(keys).toString()
        } catch (_: Exception) { "[]" }

        @JavascriptInterface
        fun storageRemovePrefix(prefix: String): Boolean = try {
            val keys = org.json.JSONArray(storageKeys(prefix))
            var ok = true
            for (i in 0 until keys.length()) {
                val k = keys.optString(i)
                if (!storageRemove(k)) ok = false
            }
            ok
        } catch (_: Exception) { false }

        @JavascriptInterface
        fun getItem(key: String): String? = sp.getString(key, null)

        @JavascriptInterface
        fun setItem(key: String, value: String) { sp.edit().putString(key, value).commit() }

        @JavascriptInterface
        fun removeItem(key: String) { sp.edit().remove(key).commit() }

        @JavascriptInterface
        fun toast(message: String) {
            main.post { Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface
        fun info(): String = JSONObject()
            .put("app", "Jabra Extension Hub")
            .put("android", Build.VERSION.SDK_INT)
            .put("pluginId", pluginId)
            .put("durableStorage", true)
            .toString()
    }

    private fun persistentLocalStorageScript(): String = """
        (function(){
          try {
            if (!window.JabraAPI || !JabraAPI.storageGet || window.__jabraPersistentStorageInstalled) return;
            window.__jabraPersistentStorageInstalled = true;
            var proto = Storage.prototype;
            var original = {
              get: proto.getItem, set: proto.setItem, remove: proto.removeItem,
              key: proto.key, clear: proto.clear,
              length: Object.getOwnPropertyDescriptor(proto, 'length')
            };
            function isLocal(s){ return s === window.localStorage; }
            function keys(){
              var out=[];
              try { out=JSON.parse(JabraAPI.storageKeys('ls:')||'[]').map(function(k){return k.slice(3)}); } catch(e) {}
              try { for(var i=0;i<(original.length?original.length.get.call(localStorage):0);i++){var k=original.key.call(localStorage,i);if(k!==null&&out.indexOf(k)<0)out.push(k);} } catch(e){}
              return out;
            }
            proto.getItem=function(k){
              if(!isLocal(this)) return original.get.call(this,k);
              k=String(k); var v=JabraAPI.storageGet('ls:'+k);
              if(v!==null && v!==undefined) return v;
              try { v=original.get.call(localStorage,k); if(v!==null) JabraAPI.storageSet('ls:'+k,v); } catch(e){}
              return v===undefined?null:v;
            };
            proto.setItem=function(k,v){
              if(!isLocal(this)) return original.set.call(this,k,v);
              if(!JabraAPI.storageSet('ls:'+String(k),String(v))) throw new Error('Jabra Hub could not save local data. Export a backup and free storage space.');
            };
            proto.removeItem=function(k){
              if(!isLocal(this)) return original.remove.call(this,k);
              JabraAPI.storageRemove('ls:'+String(k));
            };
            proto.clear=function(){
              if(!isLocal(this)) return original.clear.call(this);
              JabraAPI.storageRemovePrefix('ls:');
            };
            proto.key=function(i){
              if(!isLocal(this)) return original.key.call(this,i);
              var a=keys(); return Number(i)>=0&&Number(i)<a.length?a[Number(i)]:null;
            };
            if(original.length && original.length.configurable){
              Object.defineProperty(proto,'length',{configurable:true,enumerable:true,get:function(){return isLocal(this)?keys().length:original.length.get.call(this)}});
            }
          } catch(e) { /* Keep the browser's native storage if the shim is unavailable. */ }
        })();
    """.trimIndent()

    companion object {
        private const val HOST = "appassets.androidplatform.net"
        private const val TAG = "JabraPluginActivity"
    }
}
