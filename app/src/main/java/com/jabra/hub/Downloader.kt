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
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class Media(val url: String, val kind: String, val page: String, val title: String)

/** One quality level of an HLS master playlist. */
data class HlsVariant(val url: String, val bandwidth: Long, val height: Int) {
    val label: String
        get() = if (height > 0) "${height}p  (${bandwidth / 1000} kbps)" else "${bandwidth / 1000} kbps"
}

/** What the quality / clip dialog needs to know about a stream. */
data class HlsProbe(val variants: List<HlsVariant>, val durationSeconds: Double)

/** Options for one download. clipTo = 0 means "until the end". */
data class DlOptions(val variantUrl: String? = null, val clipFrom: Double = 0.0, val clipTo: Double = 0.0)

class CancelledException : IOException("Cancelled")

/** A download the Downloads screen can show. */
class DlJob(val id: Int, val title: String, val kind: String) {
    @Volatile var state: String = "queued"      // queued, running, done, failed, cancelled
    @Volatile var text: String = "Waiting..."
    @Volatile var percent: Int = -1
    @Volatile var where: String = ""
    @Volatile var uri: String = ""
    @Volatile var mime: String = ""
    @Volatile var cancelled: Boolean = false
    val active: Boolean get() = state == "queued" || state == "running"
}

object DlJobs {
    private val list = CopyOnWriteArrayList<DlJob>()
    private val seq = AtomicInteger(0)

    fun create(title: String, kind: String): DlJob {
        val j = DlJob(seq.incrementAndGet(), title, kind)
        list.add(0, j)
        while (list.size > 100) {
            val last = list[list.size - 1]
            if (last.active) break
            list.remove(last)
        }
        return j
    }

    fun all(): List<DlJob> = list.toList()
    fun activeCount(): Int = list.count { it.active }
    fun clearFinished() { list.removeAll { !it.active } }
}

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

    private class Out(val stream: OutputStream, val finish: () -> Unit, val cancel: () -> Unit, val uri: String)
    private class Seg(val url: String, val dur: Double, val key: String?, val iv: ByteArray?, val seq: Long)
    private class Parsed(val segs: List<Seg>, val initUri: String?)

    /**
     * Starts a download in the background and returns its job (shown on the Downloads screen).
     * onUpdate gets (message, finished).
     */
    fun start(m: Media, ua: String, opts: DlOptions = DlOptions(), onUpdate: (String, Boolean) -> Unit): DlJob {
        val shown = m.title.ifBlank { (Uri.parse(m.url).lastPathSegment ?: m.url) }
        val job = DlJobs.create(shown.take(100), m.kind)
        pool.execute {
            if (job.cancelled) {
                job.state = "cancelled"
                job.text = "Cancelled"
                onUpdate("Cancelled", true)
            } else {
                job.state = "running"
                job.text = "Starting..."
                DownloadService.begin(ctx)
                try {
                    val up: (String, Boolean) -> Unit = { s, d ->
                        job.text = s
                        onUpdate(s, d)
                    }
                    val where = if (m.kind == "hls") hls(m, ua, opts, job, up) else direct(m, ua, job, up)
                    job.state = "done"
                    job.percent = 100
                    job.where = where
                    job.text = "Saved to $where"
                    onUpdate("Saved to $where", true)
                } catch (e: CancelledException) {
                    job.state = "cancelled"
                    job.text = "Cancelled"
                    onUpdate("Cancelled", true)
                } catch (e: Exception) {
                    job.state = "failed"
                    job.text = "Failed: ${e.message}"
                    onUpdate(job.text, true)
                } finally {
                    DownloadService.end(ctx)
                }
            }
        }
        return job
    }

    /** Reads an HLS link: its quality levels and its length. Call on a background thread. */
    fun probe(m: Media, ua: String): HlsProbe {
        var url = m.url
        var text = fetchText(url, ua, m.page)
        var vs: List<HlsVariant> = emptyList()
        if (text.contains("#EXT-X-STREAM-INF")) {
            vs = variants(url, text)
            if (vs.isEmpty()) throw IOException("No stream found")
            url = vs[0].url
            text = fetchText(url, ua, m.page)
        }
        val p = parse(url, text)
        return HlsProbe(vs, p.segs.sumOf { it.dur })
    }

    // ---------- direct files ----------

    private fun direct(m: Media, ua: String, job: DlJob, up: (String, Boolean) -> Unit): String {
        val ext = MediaKind.ext(m.url)
        val name = fileName(m, ext, "")
        val c = http(m.url, ua, m.page)
        val total = c.contentLengthLong
        val out = openOut(name, mimeFor(ext), job)
        try {
            c.inputStream.use { i ->
                out.stream.use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = 0L
                    val t0 = System.currentTimeMillis()
                    while (true) {
                        if (job.cancelled) throw CancelledException()
                        val n = i.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - last > 500) {
                            last = now
                            val speed = done * 1000 / maxOf(1L, now - t0)
                            if (total > 0) job.percent = (done * 100 / total).toInt()
                            up(
                                (if (total > 0) "Downloading ${done * 100 / total}%" else "Downloading ${done / 1024 / 1024} MB") +
                                    " · " + fmt(speed) + "/s",
                                false
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

    // ---------- HLS ----------

    private fun hls(m: Media, ua: String, opts: DlOptions, job: DlJob, up: (String, Boolean) -> Unit): String {
        var url = m.url
        var text = fetchText(url, ua, m.page)
        if (text.contains("#EXT-X-STREAM-INF")) {
            val vs = variants(url, text)
            if (vs.isEmpty()) throw IOException("No stream found")
            url = opts.variantUrl ?: vs[0].url
            text = fetchText(url, ua, m.page)
        }
        val p = parse(url, text)
        if (p.segs.isEmpty()) throw IOException("Empty playlist")

        var segs = p.segs
        var suffix = ""
        if (opts.clipFrom > 0.0 || opts.clipTo > 0.0) {
            val from = opts.clipFrom
            val to = if (opts.clipTo > 0.0) opts.clipTo else Double.MAX_VALUE
            var t = 0.0
            val sel = ArrayList<Seg>()
            for (s in segs) {
                val e = t + s.dur
                if (e > from && t < to) sel.add(s)
                t = e
            }
            if (sel.isEmpty()) throw IOException("The clip range is outside the video")
            segs = sel
            suffix = " clip " + from.toInt() + "s-" + (if (opts.clipTo > 0.0) opts.clipTo.toInt().toString() + "s" else "end")
        }

        val ext = if (p.initUri != null) "mp4" else "ts"
        val name = fileName(m, ext, suffix)
        val out = openOut(name, mimeFor(ext), job)
        val keys = ConcurrentHashMap<String, ByteArray>()
        val exec = Executors.newFixedThreadPool(SEG_THREADS)
        try {
            out.stream.use { o ->
                val initUri = p.initUri
                if (initUri != null) o.write(fetchBytes(initUri, ua, m.page))
                val window = ArrayDeque<Future<ByteArray>>()
                var next = 0
                var done = 0
                var bytes = 0L
                val t0 = System.currentTimeMillis()
                val list = segs
                while (next < list.size && window.size < WINDOW) {
                    val s = list[next++]
                    window.addLast(exec.submit(Callable { fetchSeg(s, ua, m.page, keys, job) }))
                }
                while (window.isNotEmpty()) {
                    if (job.cancelled) throw CancelledException()
                    val data = try {
                        window.removeFirst().get()
                    } catch (e: ExecutionException) {
                        val c = e.cause
                        if (c is Exception) throw c else throw IOException(e.message)
                    }
                    o.write(data)
                    bytes += data.size
                    done++
                    if (next < list.size) {
                        val s = list[next++]
                        window.addLast(exec.submit(Callable { fetchSeg(s, ua, m.page, keys, job) }))
                    }
                    job.percent = done * 100 / list.size
                    val secs = maxOf(1L, (System.currentTimeMillis() - t0) / 1000)
                    up("Downloading part $done of ${list.size} · ${fmt(bytes / secs)}/s", false)
                }
            }
            out.finish()
        } catch (e: Exception) {
            out.cancel()
            throw e
        } finally {
            exec.shutdownNow()
        }
        return "Download/JabraHub/$name"
    }

    private fun fetchSeg(s: Seg, ua: String, ref: String, keys: ConcurrentHashMap<String, ByteArray>, job: DlJob): ByteArray {
        var last: Exception? = null
        for (attempt in 1..3) {
            if (job.cancelled) throw CancelledException()
            try {
                var data = http(s.url, ua, ref).inputStream.use { it.readBytes() }
                val keyUri = s.key
                if (keyUri != null) {
                    val k = keys.getOrPut(keyUri) { fetchBytes(keyUri, ua, ref) }
                    val iv = s.iv ?: ByteArray(16).also {
                        var v = s.seq
                        for (i in 15 downTo 8) {
                            it[i] = (v and 0xff).toByte()
                            v = v shr 8
                        }
                    }
                    val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    c.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), IvParameterSpec(iv))
                    data = c.doFinal(data)
                }
                return data
            } catch (e: CancelledException) {
                throw e
            } catch (e: Exception) {
                last = e
                try { Thread.sleep(400L * attempt) } catch (_: InterruptedException) { throw CancelledException() }
            }
        }
        throw IOException(last?.message ?: "A part failed to download")
    }

    // ---------- playlist parsing ----------

    private fun resolve(base: String, ref: String): String = URL(URL(base), ref).toString()

    private fun attr(line: String, name: String): String? {
        val m = Regex("[,:]$name=(\"([^\"]*)\"|([^,]*))").find(line) ?: return null
        return m.groups[2]?.value ?: m.groups[3]?.value
    }

    private fun hexToBytes(h: String): ByteArray {
        var s = h.trim()
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2)
        s = s.padStart(32, '0')
        val out = ByteArray(16)
        for (i in 0 until 16) out[i] = s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        return out
    }

    private fun variants(url: String, text: String): List<HlsVariant> {
        val lines = text.lines().map { it.trim() }
        val out = ArrayList<HlsVariant>()
        for (i in lines.indices) {
            if (!lines[i].startsWith("#EXT-X-STREAM-INF")) continue
            val bw = attr(lines[i], "BANDWIDTH")?.toLongOrNull() ?: 0L
            val h = attr(lines[i], "RESOLUTION")?.substringAfter('x', "")?.toIntOrNull() ?: 0
            val next = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: continue
            out.add(HlsVariant(resolve(url, next), bw, h))
        }
        return out.sortedByDescending { it.bandwidth }
    }

    private fun parse(url: String, text: String): Parsed {
        var seq = Regex("#EXT-X-MEDIA-SEQUENCE:(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        var initUri: String? = null
        var keyUri: String? = null
        var iv: ByteArray? = null
        var dur = 0.0
        val out = ArrayList<Seg>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXT-X-KEY")) {
                val method = attr(line, "METHOD") ?: "NONE"
                if (method == "NONE") {
                    keyUri = null
                    iv = null
                } else if (method == "AES-128") {
                    val u = attr(line, "URI") ?: throw IOException("Encryption key link is missing")
                    keyUri = resolve(url, u)
                    iv = attr(line, "IV")?.let { hexToBytes(it) }
                } else {
                    throw IOException("This stream uses $method encryption (not supported)")
                }
            } else if (line.startsWith("#EXT-X-MAP")) {
                val u = attr(line, "URI")
                if (u != null) initUri = resolve(url, u)
            } else if (line.startsWith("#EXT-X-BYTERANGE")) {
                throw IOException("Byte-range streams are not supported yet")
            } else if (line.startsWith("#EXTINF:")) {
                dur = line.removePrefix("#EXTINF:").substringBefore(',').trim().toDoubleOrNull() ?: 0.0
            } else if (!line.startsWith("#")) {
                out.add(Seg(resolve(url, line), dur, keyUri, iv, seq))
                seq++
                dur = 0.0
            }
        }
        return Parsed(out, initUri)
    }

    // ---------- network ----------

    private fun fetchText(u: String, ua: String, ref: String): String =
        http(u, ua, ref).inputStream.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun fetchBytes(u: String, ua: String, ref: String): ByteArray =
        http(u, ua, ref).inputStream.use { it.readBytes() }

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

    // ---------- files ----------

    private fun fmt(bytes: Long): String =
        if (bytes >= 1_048_576) String.format("%.1f MB", bytes / 1048576.0) else "${bytes / 1024} KB"

    private fun fileName(m: Media, ext: String, suffix: String): String {
        val fromUrl = (Uri.parse(m.url).lastPathSegment ?: "").substringBeforeLast('.')
        val raw = m.title.ifBlank { fromUrl }.ifBlank { "media" }
        val base = raw.replace(Regex("[\\\\/:*?\"<>|\\s]+"), " ").trim().take(80).ifEmpty { "media" }
        return "$base$suffix.$ext"
    }

    private fun mimeFor(ext: String) = when (ext) {
        "mp4" -> "video/mp4"; "webm" -> "video/webm"; "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"; "ts" -> "video/mp2t"; "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"; "aac" -> "audio/aac"; "ogg" -> "audio/ogg"; "wav" -> "audio/wav"
        "vtt" -> "text/vtt"; "srt" -> "application/x-subrip"
        else -> "application/octet-stream"
    }

    private fun openOut(name: String, mime: String, job: DlJob): Out {
        job.mime = mime
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
            job.uri = uri.toString()
            return Out(
                s,
                { r.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) },
                { r.delete(uri, null, null) },
                uri.toString()
            )
        }
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
        val f = File(dir, name)
        job.uri = ""
        return Out(f.outputStream(), {}, { f.delete() }, "")
    }

    companion object {
        private val pool = Executors.newFixedThreadPool(3)
        private const val SEG_THREADS = 4
        private const val WINDOW = 8
    }
}
