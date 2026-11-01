package com.jabra.hub

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * The browser's "new tab" page (Hub 0.9.9, restyled 0.9.16 as Quiet Pill D4): one big search pill, bookmark tiles with favicons, a few recent pages.
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

    /** Favicon address of a site (its own /favicon.ico); the page falls back to a letter when it does not load. */
    private fun faviconOf(u: String): String? = try {
        val x = Uri.parse(u)
        if (x.scheme == "http" || x.scheme == "https") "${x.scheme}://${x.authority}/favicon.ico" else null
    } catch (_: Exception) { null }

    private fun letterOf(name: String): String =
        name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"

    /** Tile icon: the letter, with the favicon laid over it (removed again if it fails or is tiny). */
    private fun iconHtml(e: BrowserEntry, name: String, cls: String): String {
        val sb = StringBuilder()
        sb.append("<div class=\"").append(cls).append("\"><b>").append(esc(letterOf(name))).append("</b>")
        val fav = faviconOf(e.url)
        if (fav != null) {
            sb.append("<img alt=\"\" loading=\"lazy\" src=\"").append(esc(fav))
                .append("\" onerror=\"this.remove()\" onload=\"if(this.naturalWidth<16)this.remove()\">")
        }
        sb.append("</div>")
        return sb.toString()
    }

    fun html(ctx: Context): String {
        val engine = BrowserStore.engine(ctx)
        val marks = BrowserStore.bookmarks(ctx).take(24)
        val recent = if (BrowserStore.saveHistory(ctx)) BrowserStore.history(ctx).filter { !isNewTab(it.url) }.take(4) else emptyList()
        val shown = 8

        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        sb.append("<meta name=\"color-scheme\" content=\"dark light\">")
        sb.append("<title>New tab</title><style>")
        sb.append(
            """
            :root{--bg:#141629;--card:#1B1E38;--hi:#222648;--line:#30356B;--fg:#F4F4FF;--sub:#A3A8D6;--acc:#FF8A5B;--onacc:#1A1020}
            @media (prefers-color-scheme:light){:root{--bg:#F6F6FB;--card:#FFFFFF;--hi:#ECEDF7;--line:#DDDFF0;--fg:#1B1D33;--sub:#62668A;--acc:#D9562A;--onacc:#FFFFFF}}
            *{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
            body{margin:0;padding:10vh 20px 40px;background:var(--bg);color:var(--fg);font:16px/1.35 system-ui,sans-serif}
            .wrap{max-width:520px;margin:0 auto}
            form{position:relative;margin:0 0 32px}
            form svg{position:absolute;left:18px;top:16px;width:24px;height:24px;fill:var(--sub);pointer-events:none}
            input{width:100%;height:56px;padding:0 20px 0 52px;border-radius:28px;border:1px solid var(--line);background:var(--card);color:var(--fg);font-size:16px;outline:none}
            input::placeholder{color:var(--sub)}
            input:focus{border-color:var(--acc)}
            h2{font-size:13px;font-weight:600;color:var(--sub);margin:28px 4px 12px}
            .grid{display:grid;grid-template-columns:repeat(4,1fr);gap:16px 8px}
            .grid a{display:flex;flex-direction:column;align-items:center;gap:8px;text-decoration:none;color:var(--fg);min-width:0}
            .ic{position:relative;width:56px;height:56px;border-radius:16px;background:var(--hi);display:flex;align-items:center;justify-content:center;overflow:hidden}
            .ic b{font-size:22px;font-weight:600;color:var(--acc)}
            .ic img{position:absolute;left:12px;top:12px;width:32px;height:32px;border-radius:8px;background:#fff;padding:3px;object-fit:contain}
            .grid span{font-size:12px;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .grid a.x{display:none}
            .all .grid a.x{display:flex}
            .more{display:block;margin:16px auto 0;padding:10px 20px;border:0;border-radius:20px;background:var(--hi);color:var(--fg);font-size:14px}
            .all .more{display:none}
            .list a{display:flex;align-items:center;gap:12px;padding:8px 4px;text-decoration:none;color:var(--fg);min-width:0}
            .sm{position:relative;width:36px;height:36px;flex:none;border-radius:12px;background:var(--hi);display:flex;align-items:center;justify-content:center;overflow:hidden}
            .sm b{font-size:15px;font-weight:600;color:var(--acc)}
            .sm img{position:absolute;left:6px;top:6px;width:24px;height:24px;border-radius:6px;background:#fff;padding:2px;object-fit:contain}
            .list div.t{min-width:0}
            .list span{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .list small{display:block;color:var(--sub);font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .empty{color:var(--sub);font-size:14px;padding:0 4px}
            @media (max-width:340px){.grid{grid-template-columns:repeat(3,1fr)}}
            """.trimIndent()
        )
        sb.append("</style></head><body><div class=\"wrap\">")
        sb.append("<form id=\"f\"><svg viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z\"/></svg>")
        sb.append("<input id=\"q\" type=\"text\" inputmode=\"search\" enterkeyhint=\"go\" autocomplete=\"off\" autocapitalize=\"off\" aria-label=\"Search or type a link\" placeholder=\"Search with ")
            .append(esc(engine.name)).append("\"></form>")

        if (marks.isEmpty()) {
            sb.append("<div class=\"empty\">No bookmarks yet. Menu &rarr; Bookmark on any page.</div>")
        } else {
            sb.append("<div id=\"bm\"><div class=\"grid\">")
            for ((i, e) in marks.withIndex()) {
                val name = label(e)
                sb.append("<a").append(if (i >= shown) " class=\"x\"" else "").append(" href=\"").append(esc(e.url)).append("\">")
                    .append(iconHtml(e, name, "ic"))
                    .append("<span>").append(esc(name)).append("</span></a>")
            }
            sb.append("</div>")
            if (marks.size > shown) sb.append("<button type=\"button\" class=\"more\" id=\"more\">Show all</button>")
            sb.append("</div>")
        }

        if (recent.isNotEmpty()) {
            sb.append("<h2>Recent</h2><div class=\"list\">")
            for (e in recent) {
                sb.append("<a href=\"").append(esc(e.url)).append("\">")
                    .append(iconHtml(e, label(e), "sm"))
                    .append("<div class=\"t\"><span>").append(esc(label(e))).append("</span><small>")
                    .append(esc(hostOf(e.url))).append("</small></div></a>")
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
            var m=document.getElementById('more');
            if(m)m.addEventListener('click',function(){document.getElementById('bm').className='all';});
            """.trimIndent()
        )
        sb.append("</script></body></html>")
        return sb.toString()
    }
}
