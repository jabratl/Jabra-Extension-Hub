package com.jabra.hub

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class Media(val url: String, val kind: String, val page: String, val title: String)

object MediaKind {
    fun of(url: String): String? {
        val p = (Uri.parse(url).path ?: return null).lowercase()
        return when {
            p.endsWith(".m3u8") -> "hls"
            p.endsWith(".mp4") || p.endsWith(".webm") || p.endsWith(".mkv") || p.endsWith(".mov") -> "video"
            p.endsWith(".mp3") || p.endsWith(".m4a") || p.endsWith(".aac") ||
                p.endsWith(".ogg") || p.endsWith(".wav") -> "audio"
            p.endsWith(".vtt") || p.endsWith(".srt") -> "subtitle"
            else -> null
        }
    }

    fun ext(url: String): String {
        val p = Uri.parse(url).path ?: return "bin"
        val e = p.substringAfterLast('.', "").lowercase()
        return if (e.length in 2..5) e else "bin"
    }
}

class Downloader(private val ctx: Context) {


    private class Out(val stream: OutputStream, val finish: () -> Unit, val cancel: () -> Unit)

    fun start(m: Media, ua: String, onUpdate: (String, Boolean) -> Unit) {
        pool.execute {
            DownloadService.begin(ctx)
            try {
                val where = if (m.kind == "hls") hls(m, ua, onUpdate) else direct(m, ua, onUpdate)
                onUpdate("Saved to $where", true)
            } catch (e: Exception) {
                onUpdate("Failed: ${e.message}", true)
            } finally {
                DownloadService.end(ctx)
            }
        }
    }

    private fun direct(m: Media, ua: String, up: (String, Boolean) -> Unit): String {
        val ext = MediaKind.ext(m.url)
        val name = fileName(m, ext)
        val c = http(m.url, ua, m.page)
        val total = c.contentLengthLong
        val out = openOut(name, mimeFor(ext))
        try {
            c.inputStream.use { i ->
                out.stream.use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = 0L
                    while (true) {
                        val n = i.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - last > 500) {
                            last = now
                            up(
                                if (total > 0) "Downloading ${done * 100 / total}%"
                                else "Downloading ${done / 1024 / 1024} MB", false
                            )
                        }
                    }
                }
            }
            out.finish()
        } catch (e: Exception) {
            out.cancel()
            throw e
        }
        return "Download/JabraHub/$name"
    }

    private fun hls(m: Media, ua: String, up: (String, Boolean) -> Unit): String {
        var url = m.url
        var text = fetchText(url, ua, m.page)
        if (text.contains("#EXT-X-STREAM-INF")) {
            val lines = text.lines()
            var best: String? = null
            var bw = -1L
            for (i in lines.indices) {
                if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                    val b = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLong() ?: 0L
                    val next = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    if (next != null && b > bw) { bw = b; best = next }
                }
            }
            url = URL(URL(url), best ?: throw IOException("No stream found")).toString()
            text = fetchText(url, ua, m.page)
        }
        if (Regex("#EXT-X-KEY:.*METHOD=(?!NONE)").containsMatchIn(text)) {
            throw IOException("This stream is encrypted (not supported yet)")
        }
        val init = Regex("#EXT-X-MAP:.*URI=\"([^\"]+)\"").find(text)?.groupValues?.get(1)
        val segs = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (segs.isEmpty()) throw IOException("Empty playlist")
        val ext = if (init != null) "mp4" else "ts"
        val name = fileName(m, ext)
        val out = openOut(name, mimeFor(ext))
        try {
            out.stream.use { o ->
                if (init != null) copyUrl(URL(URL(url), init).toString(), ua, m.page, o)
                for ((idx, s) in segs.withIndex()) {
                    copyUrl(URL(URL(url), s).toString(), ua, m.page, o)
                    up("Downloading part ${idx + 1} of ${segs.size}", false)
                }
            }
            out.finish()
        } catch (e: Exception) {
            out.cancel()
            throw e
        }
        return "Download/JabraHub/$name"
    }

    private fun copyUrl(u: String, ua: String, ref: String, o: OutputStream) {
        val c = http(u, ua, ref)
        c.inputStream.use { it.copyTo(o) }
    }

    private fun fetchText(u: String, ua: String, ref: String): String =
        http(u, ua, ref).inputStream.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun http(url: String, ua: String, referer: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("User-Agent", ua)
        if (referer.isNotEmpty()) c.setRequestProperty("Referer", referer)
        CookieManager.getInstance().getCookie(url)?.let { c.setRequestProperty("Cookie", it) }
        if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
        return c
    }

    private fun fileName(m: Media, ext: String): String {
        val fromUrl = (Uri.parse(m.url).lastPathSegment ?: "").substringBeforeLast('.')
        val raw = m.title.ifBlank { fromUrl }.ifBlank { "media" }
        val base = raw.replace(Regex("[\\\\/:*?\"<>|\\s]+"), " ").trim().take(80).ifEmpty { "media" }
        return "$base.$ext"
    }

    private fun mimeFor(ext: String) = when (ext) {
        "mp4" -> "video/mp4"; "webm" -> "video/webm"; "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"; "ts" -> "video/mp2t"; "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"; "aac" -> "audio/aac"; "ogg" -> "audio/ogg"; "wav" -> "audio/wav"
        "vtt" -> "text/vtt"; "srt" -> "application/x-subrip"
        else -> "application/octet-stream"
    }

    private fun openOut(name: String, mime: String): Out {
        if (Build.VERSION.SDK_INT >= 29) {
            val r = ctx.contentResolver
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/JabraHub")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                ?: throw IOException("Cannot create file")
            val s = r.openOutputStream(uri) ?: throw IOException("Cannot write file")
            return Out(
                s,
                { r.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) },
                { r.delete(uri, null, null) }
            )
        }
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
        val f = File(dir, name)
        return Out(f.outputStream(), {}, { f.delete() })
    }

    companion object {
        private val pool = Executors.newFixedThreadPool(2)
    }
}
