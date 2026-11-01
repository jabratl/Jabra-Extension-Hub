package com.jabra.hub

import android.content.Context

/** Saved choices for media downloads: stream quality (0.9.38), detect, library names, start delay (0.9.39). Edited in MediaSettings. */
object MediaPrefs {
    private const val FILE = "media_prefs"
    private const val HLS_HEIGHT = "hls_height"

    /** Quality the user picked last for streams: a height like 720, or 0 = best. */
    fun hlsHeight(ctx: Context): Int = try {
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(HLS_HEIGHT, 0)
    } catch (_: Exception) {
        0
    }

    fun setHlsHeight(ctx: Context, height: Int) {
        try {
            ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putInt(HLS_HEIGHT, height.coerceAtLeast(0)).apply()
        } catch (_: Exception) {
        }
    }

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Find media on the pages in the Browser (found list). Default on. */
    fun detect(ctx: Context): Boolean = try { sp(ctx).getBoolean("detect", true) } catch (_: Exception) { true }
    fun setDetect(ctx: Context, on: Boolean) { try { sp(ctx).edit().putBoolean("detect", on).apply() } catch (_: Exception) { } }

    /** Library names (Plex / Jellyfin: "Show - S01E05") when the title says which episode. Default off = names as before. */
    fun library(ctx: Context): Boolean = try { sp(ctx).getBoolean("library", false) } catch (_: Exception) { false }
    fun setLibrary(ctx: Context, on: Boolean) { try { sp(ctx).edit().putBoolean("library", on).apply() } catch (_: Exception) { } }

    /** Wait between starting the downloads of one batch, in ms (same choices as Media Grabber: 180, 1000, 3000). */
    fun delayMs(ctx: Context): Int = try { sp(ctx).getInt("delay_ms", 180) } catch (_: Exception) { 180 }
    fun setDelayMs(ctx: Context, ms: Int) { try { sp(ctx).edit().putInt("delay_ms", ms.coerceIn(0, 10000)).apply() } catch (_: Exception) { } }
}
