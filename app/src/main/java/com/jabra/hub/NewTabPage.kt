package com.jabra.hub

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * The browser's "new tab" page (Hub 0.9.9): a search box, the bookmarks and the latest history.
 * It is a normal https address that the browser answers itself (no network), so back, forward and
 * reload work like on any page. Nothing here is saved to history, bookmarks or "last page".
 */
object NewTabPage {
    const val HOST = "jabra.newtab"
    const val URL = "https://$HOST/"

    fun isNewTab(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return try { Uri.parse(url).host == HOST } catch (_: Exception) { false }
    }

    /** Answer for a request to the new-tab address: the page for "/", an empty 404 for anything else (favicon...). */
    fun response(ctx: Context, uri: Uri): WebResourceResponse {
        val path = uri.path.orEmpty()
        if (path.isEmpty() || path == "/") {
            return WebResourceResponse(
                "text/html", "UTF-8", 200, "OK",
                mapOf("Cache-Control" to "no-store"),
                ByteArrayInputStream(html(ctx).toByteArray(Charsets.UTF_8))
            )
        }
        return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun hostOf(u: String): String = try { Uri.parse(u).host?.removePrefix("www.") ?: u } catch (_: Exception) { u }

    private fun label(e: BrowserEntry): String = e.title.ifBlank { hostOf(e.url) }

    fun html(ctx: Context): String {
        val engine = BrowserStore.engine(ctx)
        val marks = BrowserStore.bookmarks(ctx).take(24)
        val recent = if (BrowserStore.saveHistory(ctx)) BrowserStore.history(ctx).filter { !isNewTab(it.url) }.take(6) else emptyList()

        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        sb.append("<title>New tab</title><style>")
        sb.append(
            """
            :root{--bg:#141629;--fg:#e9eaf6;--sub:#9a9dbb;--card:#1f2240;--line:#2c3058;--acc:#7c8cff}
            @media (prefers-color-scheme:light){:root{--bg:#f4f5fb;--fg:#1b1d33;--sub:#62668a;--card:#ffffff;--line:#dcdeee;--acc:#4254e0}}
            *{box-sizing:border-box}
            body{margin:0;padding:22px 16px 40px;background:var(--bg);color:var(--fg);font:16px/1.35 system-ui,sans-serif}
            .wrap{max-width:560px;margin:0 auto}
            form{display:flex;gap:8px;margin:6px 0 22px}
            input{flex:1;min-width:0;padding:13px 14px;border-radius:12px;border:1px solid var(--line);background:var(--card);color:var(--fg);font-size:16px;outline:none}
            input:focus{border-color:var(--acc)}
            button{padding:0 16px;border:0;border-radius:12px;background:var(--acc);color:#fff;font-size:15px;font-weight:600}
            .eng{margin:-14px 2px 20px;font-size:12px;color:var(--sub)}
            h2{font-size:13px;letter-spacing:.04em;text-transform:uppercase;color:var(--sub);margin:22px 2px 10px}
            .grid{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}
            .grid a{display:flex;flex-direction:column;align-items:center;gap:6px;text-decoration:none;color:var(--fg);padding:10px 4px;border-radius:12px;background:var(--card);border:1px solid var(--line);min-width:0}
            .tile{width:40px;height:40px;border-radius:10px;background:var(--acc);color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:18px}
            .grid span.t{font-size:12px;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .list a{display:block;padding:10px 12px;margin-bottom:8px;border-radius:12px;background:var(--card);border:1px solid var(--line);text-decoration:none;color:var(--fg);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .list small{display:block;color:var(--sub);font-size:12px;overflow:hidden;text-overflow:ellipsis}
            .empty{color:var(--sub);font-size:14px;padding:4px 2px}
            @media (max-width:340px){.grid{grid-template-columns:repeat(3,1fr)}}
            """.trimIndent()
        )
        sb.append("</style></head><body><div class=\"wrap\">")
        sb.append("<form id=\"f\"><input id=\"q\" type=\"text\" inputmode=\"search\" enterkeyhint=\"search\" autocomplete=\"off\" autocapitalize=\"off\" placeholder=\"Search or type a link\"><button type=\"submit\">Go</button></form>")
        sb.append("<div class=\"eng\">Search with ").append(esc(engine.name)).append(" (change it in the 3-dot menu)</div>")

        sb.append("<h2>Bookmarks</h2>")
        if (marks.isEmpty()) {
            sb.append("<div class=\"empty\">No bookmarks yet. Use the 3-dot menu &rarr; Add bookmark on any page.</div>")
        } else {
            sb.append("<div class=\"grid\">")
            for (e in marks) {
                val name = label(e)
                val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
                sb.append("<a href=\"").append(esc(e.url)).append("\"><div class=\"tile\">").append(esc(letter))
                    .append("</div><span class=\"t\">").append(esc(name)).append("</span></a>")
            }
            sb.append("</div>")
        }

        if (recent.isNotEmpty()) {
            sb.append("<h2>Recent</h2><div class=\"list\">")
            for (e in recent) {
                sb.append("<a href=\"").append(esc(e.url)).append("\">").append(esc(label(e)))
                    .append("<small>").append(esc(hostOf(e.url))).append("</small></a>")
            }
            sb.append("</div>")
        }

        sb.append("</div><script>")
        sb.append("var T=").append(JSONObject.quote(engine.url)).append(";")
        sb.append(
            """
            document.getElementById('f').addEventListener('submit',function(ev){
              ev.preventDefault();
              var q=document.getElementById('q').value.trim();
              if(!q)return;
              var u;
              if(/^https?:\/\//i.test(q)) u=q;
              else if(q.indexOf(' ')<0&&q.indexOf('.')>0) u='https://'+q;
              else u=T.replace('%s',encodeURIComponent(q));
              location.href=u;
            });
            """.trimIndent()
        )
        sb.append("</script></body></html>")
        return sb.toString()
    }
}
