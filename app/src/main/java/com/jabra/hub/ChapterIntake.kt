package com.jabra.hub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/**
 * Turns anything the user sends to Jabra (selected text, shared text, a shared web link,
 * a .txt/.docx/.epub/.html file) into plain chapter text and stores it in app-private
 * storage until the Novel Assistant plugin reads it through JabraAPI.intakeGet().
 *
 * Honest limits: Android does not let an app silently read other apps' screens. The user
 * must select text, press Share, or open a file with Jabra. PDF is not supported.
 */
object ChapterIntake {
    const val NOVEL_PLUGIN_ID = "jabra-novel-assistant"
    const val ACTION_SCAN_CLIPBOARD = "com.jabra.hub.SCAN_CLIPBOARD"
    private const val MAX_CHARS = 3_000_000
    private const val MAX_FILE_BYTES = 40_000_000

    class Intake(
        val title: String,
        val text: String,
        val source: String,      // selection | share | web | file
        val sourceName: String,  // file name or host, for display
        val mode: String,        // chapter | lookup
        val parts: List<Pair<String, String>> = emptyList(),
        val truncated: Boolean = false
    )

    // ---------- storage ----------
    private fun pendingFile(ctx: Context) = File(File(ctx.filesDir, "intake").apply { mkdirs() }, "pending.json")

    fun save(ctx: Context, i: Intake) {
        val o = JSONObject()
            .put("title", i.title)
            .put("text", if (i.parts.size > 1) "" else i.text)
            .put("source", i.source)
            .put("sourceName", i.sourceName)
            .put("mode", i.mode)
            .put("truncated", i.truncated)
            .put("ts", System.currentTimeMillis())
        if (i.parts.size > 1) {
            val arr = JSONArray()
            i.parts.forEach { arr.put(JSONObject().put("title", it.first).put("text", it.second)) }
            o.put("parts", arr)
        }
        val f = pendingFile(ctx)
        val tmp = File(f.parentFile, "pending.tmp")
        tmp.writeText(o.toString(), Charsets.UTF_8)
        f.delete()
        if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
    }

    fun loadRaw(ctx: Context): String? = try {
        val f = pendingFile(ctx)
        if (f.isFile) f.readText(Charsets.UTF_8) else null
    } catch (_: Exception) { null }

    fun clear(ctx: Context): Boolean = try { val f = pendingFile(ctx); !f.exists() || f.delete() } catch (_: Exception) { false }

    // ---------- entry point ----------
    fun fromIntent(ctx: Context, i: Intent): Intake {
        return when (i.action) {
            Intent.ACTION_PROCESS_TEXT -> {
                val t = i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty().trim()
                require(t.isNotEmpty()) { "No text was selected" }
                val short = t.length <= 60 && !t.contains('\n')
                build(firstLine(t), t, "selection", "Selected text", if (short) "lookup" else "chapter")
            }
            Intent.ACTION_SEND -> {
                val stream: Uri? = streamUri(i)
                if (stream != null) fromUri(ctx, stream, i.type)
                else fromSharedText(i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty(), i.getStringExtra(Intent.EXTRA_SUBJECT))
            }
            Intent.ACTION_VIEW -> {
                val d = i.data ?: throw IllegalArgumentException("No file was provided")
                fromUri(ctx, d, i.type)
            }
            else -> throw IllegalArgumentException("Unsupported action")
        }
    }

    @Suppress("DEPRECATION")
    private fun streamUri(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

    private fun build(title: String, text0: String, source: String, name: String, mode: String, parts: List<Pair<String, String>> = emptyList()): Intake {
        var truncated = false
        var text = text0
        if (text.length > MAX_CHARS) { text = text.substring(0, MAX_CHARS); truncated = true }
        return Intake(title.ifBlank { "Untitled" }.take(160), text, source, name, mode, parts, truncated)
    }

    // ---------- shared text / web link ----------
    private val urlRe = Regex("https?://\\S+")

    /** Text copied by the user (any app). A lone link is fetched; a short single line is a glossary lookup. */
    fun fromClipboard(raw: String): Intake {
        val t = raw.trim()
        require(t.isNotEmpty()) { "The clipboard is empty. Copy some text first." }
        val isLink = urlRe.find(t) != null
        if (!isLink && t.length <= 60 && !t.contains('\n')) return build(t, t, "clipboard", "Clipboard", "lookup")
        return fromSharedText(t, null, "clipboard")
    }

    private fun fromSharedText(raw: String, subject: String?, label: String = "share"): Intake {
        val t = raw.trim()
        require(t.isNotEmpty()) { "Nothing to read in the shared content" }
        val m = urlRe.find(t)
        if (m != null) {
            val rest = (t.substring(0, m.range.first) + " " + t.substring(m.range.last + 1)).trim()
            if (rest.length <= 300) {
                val (title, text) = fetchPage(m.value.trimEnd(')', ',', '.', '"'))
                val host = try { URL(m.value).host } catch (_: Exception) { "web" }
                return build(title.ifBlank { rest.ifBlank { host } }, text, "web", host, "chapter")
            }
        }
        return build(subject?.takeIf { it.isNotBlank() } ?: firstLine(t), t, label, if (label == "clipboard") "Clipboard" else "Shared text", "chapter")
    }

    private fun firstLine(t: String): String = t.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(100) ?: "Shared text"

    private fun fetchPage(url0: String): Pair<String, String> {
        val url = if (url0.startsWith("http://")) "https://" + url0.removePrefix("http://") else url0
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12_000
        c.readTimeout = 20_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        c.setRequestProperty("Accept-Language", "en,zh;q=0.8,*;q=0.5")
        try {
            val code = c.responseCode
            require(code in 200..299) { "The site answered HTTP $code. Open the page, select the chapter text and choose Jabra Scan instead." }
            val bytes = c.inputStream.use { readLimited(it, 6_000_000) }
            val html = String(bytes, pickCharset(c.contentType, bytes))
            val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.get(1)?.let { decodeEntities(it).replace(Regex("\\s+"), " ").trim() }.orEmpty()
            val text = htmlToText(html, true)
            require(text.length >= 40) { "No readable text was found on that page (it may need JavaScript or a login). Select the text and choose Jabra Scan instead." }
            return Pair(title, text)
        } finally {
            c.disconnect()
        }
    }

    private fun pickCharset(contentType: String?, bytes: ByteArray): Charset {
        val fromHeader = contentType?.let { Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        val head = String(bytes, 0, minOf(bytes.size, 3000), Charsets.ISO_8859_1)
        val fromMeta = Regex("<meta[^>]+charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
        for (n in listOf(fromHeader, fromMeta)) {
            if (!n.isNullOrBlank()) try { return Charset.forName(n) } catch (_: Exception) { }
        }
        return Charsets.UTF_8
    }

    // ---------- files ----------
    private fun displayName(ctx: Context, uri: Uri): String {
        var name: String? = null
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
        } catch (_: Exception) { }
        return name ?: uri.lastPathSegment ?: "file"
    }

    private fun fromUri(ctx: Context, uri: Uri, mime0: String?): Intake {
        val name = displayName(ctx, uri)
        val lower = name.lowercase()
        val mime = (mime0 ?: ctx.contentResolver.getType(uri) ?: "").lowercase()
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { readLimited(it, MAX_FILE_BYTES) }
            ?: throw IllegalStateException("Could not open the file")
        val base = name.substringBeforeLast('.', name)
        return when {
            lower.endsWith(".docx") || mime.contains("wordprocessingml") ->
                build(base, docxText(bytes), "file", name, "chapter")
            lower.endsWith(".epub") || mime.contains("epub") -> {
                val parts = epubParts(bytes)
                require(parts.isNotEmpty()) { "No readable chapters were found in this EPUB" }
                val joined = parts.joinToString("\n\n") { it.first + "\n" + it.second }
                val over = joined.length > MAX_CHARS
                Intake(base.take(160), "", "file", name, "chapter", capParts(parts), over)
            }
            lower.endsWith(".pdf") || mime.contains("pdf") ->
                throw IllegalArgumentException("PDF text cannot be read yet. Open the PDF, select the text and choose Jabra Scan, or share it as text.")
            lower.endsWith(".html") || lower.endsWith(".htm") || mime.contains("html") ->
                build(base, htmlToText(String(bytes, pickCharset(null, bytes)), true), "file", name, "chapter")
            else -> build(base, decodeText(bytes), "file", name, "chapter")
        }
    }

    private fun capParts(parts: List<Pair<String, String>>): List<Pair<String, String>> {
        var total = 0
        val out = ArrayList<Pair<String, String>>()
        for (p in parts) {
            if (total + p.second.length > MAX_CHARS) break
            total += p.second.length
            out.add(p)
        }
        return out
    }

    /** UTF-8 first; many Chinese .txt novels are GB18030, so fall back to that. */
    private fun decodeText(bytes: ByteArray): String {
        var b = bytes
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) b = b.copyOfRange(3, b.size)
        try {
            val dec = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            return dec.decode(ByteBuffer.wrap(b)).toString()
        } catch (_: Exception) { }
        return try { String(b, Charset.forName("GB18030")) } catch (_: Exception) { String(b, Charsets.UTF_8) }
    }

    private fun readLimited(s: InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = s.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw IllegalArgumentException("This file is too large to read here")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    // ---------- docx ----------
    private fun docxText(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == "word/document.xml") {
                    val xml = String(readLimited(z, 30_000_000), Charsets.UTF_8)
                    val opts = setOf(RegexOption.DOT_MATCHES_ALL)
                    val s = xml
                        .replace(Regex("<w:instrText[^>]*>.*?</w:instrText>", opts), "")
                        .replace(Regex("<w:delText[^>]*>.*?</w:delText>", opts), "")
                        .replace("</w:p>", "\n")
                        .replace(Regex("<w:br[^>]*/>"), "\n")
                        .replace(Regex("<w:tab[^>]*/>"), "\t")
                        .replace(Regex("<[^>]+>"), "")
                    val t = tidy(decodeEntities(s))
                    require(t.isNotBlank()) { "This .docx has no readable text" }
                    return t
                }
                e = z.nextEntry
            }
        }
        throw IllegalArgumentException("This file is not a readable .docx")
    }

    // ---------- epub ----------
    private fun attr(tag: String, name: String): String? =
        Regex("\\b" + name + "\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)

    private fun joinPath(base: String, href0: String): String {
        val href = href0.substringBefore('#')
        val full = if (base.isEmpty()) href else "$base/$href"
        val parts = ArrayList<String>()
        for (seg in full.split('/')) {
            if (seg.isEmpty() || seg == ".") continue
            if (seg == "..") {
                if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
            } else {
                parts.add(seg)
            }
        }
        return parts.joinToString("/")
    }

    private fun epubParts(bytes: ByteArray): List<Pair<String, String>> {
        val files = HashMap<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val l = e.name.lowercase()
                if (!e.isDirectory && (l.endsWith(".xhtml") || l.endsWith(".html") || l.endsWith(".htm") || l.endsWith(".opf") || l.endsWith("container.xml"))) {
                    val b = readLimited(z, 12_000_000)
                    total += b.size
                    if (total > 80_000_000) throw IllegalArgumentException("This EPUB is too large to read here")
                    files[e.name] = b
                }
                e = z.nextEntry
            }
        }
        val container = files["META-INF/container.xml"]?.toString(Charsets.UTF_8)
        val opfPath = container?.let { attr(Regex("<rootfile\\b[^>]*>", RegexOption.IGNORE_CASE).find(it)?.value ?: "", "full-path") }
        var order: List<String> = emptyList()
        val opfBytes = opfPath?.let { files[it] }
        if (opfPath != null && opfBytes != null) {
            val opf = String(opfBytes, Charsets.UTF_8)
            val base = opfPath.substringBeforeLast('/', "")
            val manifest = HashMap<String, String>()
            Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(opf).forEach { m ->
                val id = attr(m.value, "id")
                val href = attr(m.value, "href")
                if (id != null && href != null) manifest[id] = href
            }
            order = Regex("<itemref\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(opf)
                .mapNotNull { m -> attr(m.value, "idref")?.let { manifest[it] } }
                .map { joinPath(base, Uri.decode(it)) }
                .toList()
        }
        if (order.isEmpty()) {
            order = files.keys.filter { val l = it.lowercase(); l.endsWith(".xhtml") || l.endsWith(".html") || l.endsWith(".htm") }.sorted()
        }
        val out = ArrayList<Pair<String, String>>()
        val opts = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        for (p in order) {
            val b = files[p] ?: continue
            val html = String(b, Charsets.UTF_8)
            val text = htmlToText(html, false)
            if (text.length < 120) continue
            val heading = Regex("<h[1-3][^>]*>(.*?)</h[1-3]>", opts).find(html)?.groupValues?.get(1)
                ?: Regex("<title[^>]*>(.*?)</title>", opts).find(html)?.groupValues?.get(1)
            val title = heading?.let { decodeEntities(it.replace(Regex("<[^>]+>"), "")).replace(Regex("\\s+"), " ").trim() }
                .orEmpty().ifBlank { "Part ${out.size + 1}" }.take(120)
            out.add(Pair(title, text))
        }
        return out
    }

    // ---------- html / text helpers ----------
    private fun htmlToText(html0: String, paragraphsOnly: Boolean): String {
        val opts = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val html = html0.replace(Regex("<(script|style|noscript|svg|head|nav|footer|form)\\b[^>]*>.*?</\\1>", opts), " ")
        if (paragraphsOnly) {
            val ps = Regex("<p\\b[^>]*>(.*?)</p>", opts).findAll(html)
                .map { tidy(decodeEntities(it.groupValues[1].replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n").replace(Regex("<[^>]+>"), ""))) }
                .filter { it.isNotBlank() }.toList()
            if (ps.size >= 5 && ps.sumOf { it.length } > 800) return ps.joinToString("\n\n")
        }
        val s = html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|h[1-6]|li|tr|blockquote|section|article)>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
        return tidy(decodeEntities(s))
    }

    private fun tidy(s: String): String =
        s.replace("\r", "")
            .lines().joinToString("\n") { it.replace(Regex("[ \\t\\u00a0\\u3000]+"), " ").trim() }
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    private fun cp(i: Int): String? = try { String(Character.toChars(i)) } catch (_: Exception) { null }

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "ldquo" to "\u201c", "rdquo" to "\u201d", "lsquo" to "\u2018", "rsquo" to "\u2019",
        "hellip" to "\u2026", "mdash" to "\u2014", "ndash" to "\u2013"
    )

    private fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        return Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").replace(s) { m ->
            val g = m.groupValues[1]
            when {
                g.startsWith("#x") || g.startsWith("#X") -> g.substring(2).toIntOrNull(16)?.let { cp(it) } ?: m.value
                g.startsWith("#") -> g.substring(1).toIntOrNull()?.let { cp(it) } ?: m.value
                else -> named[g] ?: m.value
            }
        }
    }
}
