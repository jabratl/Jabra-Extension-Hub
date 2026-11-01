package com.jabra.hub

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BrowserEntry(val url: String, val title: String, val ts: Long, val folder: String = "")

/** History, bookmarks and switches of the Hub browser (kept in app-private preferences). */
object BrowserStore {
    private const val PREFS = "browser_prefs"
    private const val HISTORY = "history"
    private const val BOOKMARKS = "bookmarks"
    private const val MAX_HISTORY = 500

    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- switches ----------
    fun adblock(ctx: Context) = sp(ctx).getBoolean("adblock", true)
    fun setAdblock(ctx: Context, on: Boolean) { sp(ctx).edit().putBoolean("adblock", on).apply() }
    fun desktop(ctx: Context) = sp(ctx).getBoolean("desktop", false)
    fun setDesktop(ctx: Context, on: Boolean) { sp(ctx).edit().putBoolean("desktop", on).apply() }
    fun saveHistory(ctx: Context) = sp(ctx).getBoolean("save_history", true)
    fun setSaveHistory(ctx: Context, on: Boolean) { sp(ctx).edit().putBoolean("save_history", on).apply() }
    fun lastUrl(ctx: Context): String? = sp(ctx).getString("last_url", null)
    fun setLastUrl(ctx: Context, u: String) { sp(ctx).edit().putString("last_url", u).apply() }

    // ---------- lists ----------
    private fun load(ctx: Context, key: String): MutableList<BrowserEntry> {
        val out = ArrayList<BrowserEntry>()
        try {
            val arr = JSONArray(sp(ctx).getString(key, "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val u = o.optString("u")
                if (u.isNotEmpty()) out.add(BrowserEntry(u, o.optString("t"), o.optLong("ts"), o.optString("f")))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun save(ctx: Context, key: String, list: List<BrowserEntry>) {
        val arr = JSONArray()
        for (e in list) {
            val o = JSONObject().put("u", e.url).put("t", e.title).put("ts", e.ts)
            if (e.folder.isNotBlank()) o.put("f", e.folder)
            arr.put(o)
        }
        sp(ctx).edit().putString(key, arr.toString()).apply()
    }

    fun history(ctx: Context): List<BrowserEntry> = load(ctx, HISTORY)

    fun addHistory(ctx: Context, url: String, title: String) {
        if (!saveHistory(ctx)) return
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return
        val list = load(ctx, HISTORY)
        // Same page again (reload / title update): keep one entry at the top.
        list.removeAll { it.url == url }
        list.add(0, BrowserEntry(url, title.take(160), System.currentTimeMillis()))
        while (list.size > MAX_HISTORY) list.removeAt(list.size - 1)
        save(ctx, HISTORY, list)
    }

    fun clearHistory(ctx: Context) { sp(ctx).edit().remove(HISTORY).apply() }

    fun bookmarks(ctx: Context): List<BrowserEntry> = load(ctx, BOOKMARKS)

    fun isBookmarked(ctx: Context, url: String) = load(ctx, BOOKMARKS).any { it.url == url }

    /** Adds or removes. Returns true when the page is bookmarked afterwards. */
    fun toggleBookmark(ctx: Context, url: String, title: String): Boolean {
        val list = load(ctx, BOOKMARKS)
        val had = list.removeAll { it.url == url }
        if (!had) list.add(0, BrowserEntry(url, title.take(160), System.currentTimeMillis()))
        save(ctx, BOOKMARKS, list)
        return !had
    }

    fun clearBookmarks(ctx: Context) { sp(ctx).edit().remove(BOOKMARKS).apply() }

    // ---------- bookmark export / import (Hub 0.9.6) ----------
    // JSON = our own backup format; HTML = the "Netscape bookmark file" every browser (Chrome, Firefox, Edge, Samsung) imports and exports.
    private const val MAX_BOOKMARKS = 3000

    data class ImportResult(val added: Int, val skipped: Int, val found: Int)

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun exportJson(ctx: Context): String {
        val arr = JSONArray()
        for (e in bookmarks(ctx)) {
            val o = JSONObject().put("u", e.url).put("t", e.title).put("ts", e.ts)
            if (e.folder.isNotBlank()) o.put("f", e.folder)
            arr.put(o)
        }
        return JSONObject().put("format", "jabra-bookmarks").put("version", 1).put("bookmarks", arr).toString(2)
    }

    fun exportHtml(ctx: Context): String {
        fun link(e: BrowserEntry) =
            "<DT><A HREF=\"${esc(e.url)}\" ADD_DATE=\"${e.ts / 1000}\">${esc(e.title.ifBlank { e.url })}</A>\n"
        val sb = StringBuilder()
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<!-- This is an automatically generated file. -->\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n")
        val groups = bookmarks(ctx).groupBy { it.folder }
        for (e in groups[""].orEmpty()) sb.append("    ").append(link(e))
        for ((folder, items) in groups) {
            if (folder.isBlank()) continue
            sb.append("    <DT><H3>").append(esc(folder)).append("</H3>\n    <DL><p>\n")
            for (e in items) sb.append("        ").append(link(e))
            sb.append("    </DL><p>\n")
        }
        sb.append("</DL><p>\n")
        return sb.toString()
    }

    private fun okUrl(u: String) =
        u.length <= 2000 && (u.startsWith("http://", true) || u.startsWith("https://", true))

    /** ADD_DATE in files is seconds (Chrome, Firefox), sometimes microseconds or milliseconds. */
    private fun toMillis(n: Long): Long = when {
        n <= 0L -> System.currentTimeMillis()
        n >= 1_000_000_000_000_000L -> n / 1000
        n >= 100_000_000_000L -> n
        else -> n * 1000
    }

    private fun plain(s: String): String {
        var r = s.replace(Regex("<[^>]*>"), "")
        r = Regex("&#(x?)([0-9a-fA-F]+);").replace(r) { m ->
            try { String(Character.toChars(m.groupValues[2].toInt(if (m.groupValues[1].isEmpty()) 10 else 16))) } catch (_: Exception) { "" }
        }
        return r.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&").trim()
    }

    /** Reads our JSON, a browser's bookmark HTML, or any text with links (one per line). Never throws. */
    fun parseBookmarks(text: String): List<BrowserEntry> {
        val t = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        return try {
            when {
                t.startsWith("{") || t.startsWith("[") -> parseJson(t)
                t.contains("<a ", ignoreCase = true) -> parseHtml(t)
                else -> parseLines(t)
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseJson(t: String): List<BrowserEntry> {
        val arr = if (t.startsWith("[")) JSONArray(t) else (JSONObject(t).optJSONArray("bookmarks") ?: return emptyList())
        val out = ArrayList<BrowserEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val u = o.optString("u").ifEmpty { o.optString("url") }
            if (!okUrl(u)) continue
            out.add(BrowserEntry(u, o.optString("t").ifEmpty { o.optString("title") }, toMillis(o.optLong("ts", 0L)), o.optString("f").ifEmpty { o.optString("folder") }))
        }
        return out
    }

    private fun parseHtml(t: String): List<BrowserEntry> {
        val tag = Regex("<h3[^>]*>(.*?)</h3>|<a\\s([^>]*)>(.*?)</a>|<dl\\b[^>]*>|</dl>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val hrefRe = Regex("href\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
        val dateRe = Regex("add_date\\s*=\\s*[\"'](\\d+)[\"']", RegexOption.IGNORE_CASE)
        val out = ArrayList<BrowserEntry>()
        val stack = ArrayList<String>()
        var pending: String? = null
        for (m in tag.findAll(t)) {
            when {
                m.groups[1] != null -> pending = plain(m.groupValues[1])
                m.groups[2] != null -> {
                    val attrs = m.groupValues[2]
                    val u = plain(hrefRe.find(attrs)?.groupValues?.get(1) ?: "")
                    if (!okUrl(u)) continue
                    val ts = toMillis(dateRe.find(attrs)?.groupValues?.get(1)?.toLongOrNull() ?: 0L)
                    out.add(BrowserEntry(u, plain(m.groupValues[3]), ts, stack.filter { it.isNotBlank() }.joinToString(" / ")))
                }
                m.value.startsWith("</", true) -> { if (stack.isNotEmpty()) stack.removeAt(stack.size - 1); pending = null }
                else -> { stack.add(pending ?: ""); pending = null }
            }
        }
        return out
    }

    private fun parseLines(t: String): List<BrowserEntry> =
        Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE).findAll(t)
            .map { it.value.trimEnd(',', '.', ';', ')', ']') }
            .filter { okUrl(it) }
            .map { BrowserEntry(it, "", System.currentTimeMillis()) }
            .toList()

    /** Adds the entries (skipping links already saved) or, with replace, swaps the whole list. */
    fun importBookmarks(ctx: Context, items: List<BrowserEntry>, replace: Boolean): ImportResult {
        val list: MutableList<BrowserEntry> = if (replace) ArrayList() else load(ctx, BOOKMARKS)
        val seen = HashSet<String>()
        for (e in list) seen.add(e.url)
        var added = 0
        var skipped = 0
        for (e in items) {
            if (!seen.add(e.url) || list.size >= MAX_BOOKMARKS) { skipped++; continue }
            list.add(BrowserEntry(e.url, e.title.take(160), e.ts, e.folder.take(80)))
            added++
        }
        save(ctx, BOOKMARKS, list)
        return ImportResult(added, skipped, items.size)
    }

    // ---------- saved tabs (Hub 0.9.8): addresses of the open tabs, restored at the next start ----------
    private const val TABS = "tabs"
    private const val MAX_TABS = 8

    fun saveTabs(ctx: Context, items: List<Pair<String, String>>, cur: Int) {
        val arr = JSONArray()
        for ((u, t) in items.take(MAX_TABS)) arr.put(JSONObject().put("u", u).put("t", t.take(120)))
        sp(ctx).edit().putString(TABS, arr.toString()).putInt("tabs_cur", cur).apply()
    }

    fun savedTabs(ctx: Context): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        try {
            val arr = JSONArray(sp(ctx).getString(TABS, "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val u = o.optString("u")
                if (okUrl(u) && out.size < MAX_TABS) out.add(Pair(u, o.optString("t")))
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun savedTabCur(ctx: Context): Int = sp(ctx).getInt("tabs_cur", 0)

    fun clearTabs(ctx: Context) { sp(ctx).edit().remove(TABS).remove("tabs_cur").apply() }

    // ---------- manager helpers (Hub 0.9.7): rename, move, delete one, history export ----------

    fun bookmarkFolders(ctx: Context): List<String> =
        load(ctx, BOOKMARKS).map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()

    /** Changes the title and folder of one bookmark (found by its link). */
    fun updateBookmark(ctx: Context, url: String, title: String, folder: String) {
        val list = load(ctx, BOOKMARKS)
        val i = list.indexOfFirst { it.url == url }
        if (i < 0) return
        val old = list[i]
        list[i] = BrowserEntry(old.url, title.trim().take(160), old.ts, folder.trim().replace('\n', ' ').take(80))
        save(ctx, BOOKMARKS, list)
    }

    fun removeBookmark(ctx: Context, url: String) {
        val list = load(ctx, BOOKMARKS)
        if (list.removeAll { it.url == url }) save(ctx, BOOKMARKS, list)
    }

    fun removeHistory(ctx: Context, url: String) {
        val list = load(ctx, HISTORY)
        if (list.removeAll { it.url == url }) save(ctx, HISTORY, list)
    }

    /** Plain text, newest first: date and title on one line, the link on the next. */
    fun exportHistoryText(ctx: Context): String {
        val df = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        val sb = StringBuilder()
        for (e in history(ctx)) {
            sb.append(df.format(java.util.Date(e.ts))).append("  ").append(e.title.ifBlank { e.url }).append('\n')
            sb.append(e.url).append("\n\n")
        }
        return sb.toString()
    }
}
