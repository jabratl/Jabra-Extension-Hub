package com.jabra.hub

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BrowserEntry(val url: String, val title: String, val ts: Long)

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
                if (u.isNotEmpty()) out.add(BrowserEntry(u, o.optString("t"), o.optLong("ts")))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun save(ctx: Context, key: String, list: List<BrowserEntry>) {
        val arr = JSONArray()
        for (e in list) arr.put(JSONObject().put("u", e.url).put("t", e.title).put("ts", e.ts))
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
}
