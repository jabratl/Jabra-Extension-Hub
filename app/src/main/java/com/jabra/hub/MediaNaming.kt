package com.jabra.hub

import java.net.URL
import java.net.URLDecoder

/**
 * Names for downloaded media (Hub 0.9.36; library names are switched by MediaPrefs.library since 0.9.39). A Kotlin port of the naming code in Media Grabber's index.html
 * (sanitize, seriesName, seriesBase, seasonOf, epNumOf, libraryTitles), so the Browser and the plugin give the same names.
 * Library style (Plex / Jellyfin / Emby / Kodi): "Show - S01E05". Pure Kotlin, no Android classes.
 */
object MediaNaming {
    private val I = RegexOption.IGNORE_CASE
    private val EXPLICIT_EP = Regex("""\bS\d{1,2}\s*E\d+|\b(?:episode|ep\.?|chapter|chap\.?|ch\.?)\s*#?\s*\d|#\s*\d""", I)

    /** A safe file-name part: no path or reserved characters, one space between words, no trailing dot or space. */
    fun sanitize(name: String?, max: Int = 100): String {
        val s = (name ?: "media")
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(max)
            .replace(Regex("""[. ]+$"""), "")
        return s.ifEmpty { "media" }
    }

    /** "Show - Site" / "Show | Site" and "Show Episode 5" become "Show"; "Watch ... Online Free" words are dropped. */
    fun seriesName(title: String?): String {
        val orig = (title ?: "").replace(Regex("""\s+"""), " ").trim()
        var t = orig
            .replace(Regex("""\s+[-\u2013\u2014\u2022\u00B7\u00BB]\s+.*$"""), "")
            .replace(Regex("""\s*\|.*$"""), "")
        val cut = t.replace(
            Regex("""[\s:,\-\u2013\u2014(\[]*\b(?:episode|eps?\.?|chapter|chap\.?|ch\.?|\u00E9pisode|folge|cap[i\u00ED]tulo)\s*#?\d+(?:\.\d+)?\b.*$""", I), ""
        )
        if (cut.isNotBlank()) t = cut
        t = t.replace(Regex("""^(?:watch|read|download|stream)\s+""", I), "")
            .replace(Regex("""[\s(\[]*\b(?:english\s*)?(?:sub(?:bed|titled)?|dub(?:bed)?)(?:\s*[\/&,]\s*(?:sub(?:bed|titled)?|dub(?:bed)?))*\b[\s)\]]*$""", I), "")
            .replace(Regex("""\s*\b(?:online|free|hd|full)\b\s*$""", I), "")
            .replace(Regex("""[\s:,\-\u2013\u2014|]+$"""), "")
            .trim()
        return t.ifEmpty { orig }
    }

    /** seriesName without a trailing season or SxxExx part. */
    fun seriesBase(title: String?): String {
        val t = title ?: ""
        val n = seriesName(t.replace(Regex("""[\s._-]*\bS\d{1,2}\s*E\d+.*$""", I), ""))
            .replace(Regex("""[\s:,\-\u2013\u2014]*\b(?:season\s*\d{1,2}|\d{1,2}(?:st|nd|rd|th)\s*season|s\d{1,2})\s*$""", I), "")
            .trim()
        return n.ifEmpty { seriesName(t) }
    }

    /** Season from "S02E05", "Season 2", "2nd season" or a trailing "S2". Default 1. */
    fun seasonOf(title: String?): Int {
        val book = title ?: ""
        val t = " | $book"
        val m = Regex("""\bS(\d{1,2})\s*E\d+""", I).find(t)
            ?: Regex("""\bseason\s*(\d{1,2})\b""", I).find(t)
            ?: Regex("""\b(\d{1,2})(?:st|nd|rd|th)\s*season\b""", I).find(t)
            ?: Regex("""\bs(\d{1,2})\b""", I).find(book)
        return m?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }

    /** Episode number from a label ("Episode 5", "S01E05", "#5", last number) or else from the link's path. Null if none. */
    fun epNumOf(label: String?, url: String?): Double? {
        val l = label ?: ""
        val m = Regex("""\bS\d+\s*E(\d+)""", I).find(l)
            ?: Regex("""(?:episode|ep\.?|chapter|chap\.?|ch\.?|#)\s*[-_.:]?\s*(\d+(?:\.\d+)?)""", I).find(l)
        if (m != null) return m.groupValues[1].toDoubleOrNull()
        Regex("""(\d+(?:\.\d+)?)(?!.*\d)""").find(l)?.let { return it.groupValues[1].toDoubleOrNull() }
        if (url.isNullOrBlank()) return null
        return try {
            val path = URLDecoder.decode(URL(url).path, "UTF-8")
                .replace(Regex("""\.(m3u8|mpd|mp4|webm|mkv|html?|php)$""", I), "")
            val pm = Regex("""(?:episode|ep|chapter|chap|ch)[-_/]*(\d+(?:\.\d+)?)""", I).find(path)
                ?: Regex("""(\d+(?:\.\d+)?)(?!.*\d)""").find(path)
            pm?.groupValues?.get(1)?.toDoubleOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /** 5 -> "05", 105 -> "105", 5.5 -> "05.5". */
    fun epPad(n: Double): String {
        val e = Math.floor(n).toLong()
        val s = n.toString()
        val frac = if (n % 1.0 != 0.0 && s.contains('.')) s.substring(s.indexOf('.')) else ""
        return e.toString().padStart(if (e >= 100) 3 else 2, '0') + frac
    }

    private fun tag(series: String, season: Int, ep: Double) =
        series + " - S" + season.toString().padStart(2, '0') + "E" + epPad(ep)

    /**
     * One item: "Show - S01E05" when the page title says which episode it is ("Show Episode 5 - Site", "Show S01E05").
     * Null when it does not (then the caller keeps the normal name).
     */
    fun libraryTitle(title: String?): String? {
        val t = (title ?: "").trim()
        if (t.isEmpty() || !EXPLICIT_EP.containsMatchIn(t)) return null
        val ep = epNumOf(t, null) ?: return null
        return tag(sanitize(seriesBase(t), 80), seasonOf(t), ep)
    }

    /**
     * Several items from one title. items = (kind, url). Same kind: the episode number comes from each link when all are
     * different, else it counts 1, 2, 3 in order (videos and subtitles are counted separately). Entries can be null.
     */
    fun libraryTitles(items: List<Pair<String, String>>, baseTitle: String): List<String?> {
        val out = arrayOfNulls<String>(items.size)
        val series = sanitize(seriesBase(baseTitle), 80)
        val season = seasonOf(baseTitle)
        if (items.size == 1 && EXPLICIT_EP.containsMatchIn(baseTitle)) {
            epNumOf(baseTitle, null)?.let { out[0] = tag(series, season, it) }
            return out.toList()
        }
        val groups = LinkedHashMap<String, MutableList<Int>>()
        items.forEachIndexed { i, it -> groups.getOrPut(it.first) { ArrayList() }.add(i) }
        for ((_, idx) in groups) {
            val nums = idx.map { epNumOf("", items[it].second) }
            val distinct = nums.none { it == null } && nums.toSet().size == nums.size
            idx.forEachIndexed { j, i -> out[i] = tag(series, season, if (distinct) nums[j]!! else (j + 1).toDouble()) }
        }
        return out.toList()
    }

    // ---------- episode lists (Hub 0.9.37) ----------

    /** A link with its episode number replaced by {n} ({nn} = 2 digits ...). hashed = the site adds a random code, so other links cannot be guessed. */
    class EpPattern(val pattern: String, val n: Double, val hashed: Boolean)

    private val MEDIA_EXT = Regex("""\.(m3u8|mpd|mp4|webm|mkv|mov|mp3|m4a|aac|ogg|wav|vtt|srt|html?|php)$""", I)
    private val HASHED = Regex("""((?:episode|ep|chapter|chap|ch)[-_]?)(\d+(?:\.\d+)?)([-_](?=[a-z0-9]*\d)(?=[a-z0-9]*[a-z])[a-z0-9]{3,10})\/?$""", I)
    private val LAST_INT = Regex("""(\d+)(?!.*\d)""")

    /** "1-12", "1,3,5-8", "5 to 9", "10-" (up to max). Sorted, no repeats. */
    fun parseNumList(text: String?, max: Int): List<Double> {
        val out = java.util.TreeSet<Double>()
        val norm = (text ?: "").replace(Regex("""\s*(?:\u2013|\u2014|\bto\b)\s*""", I), "-").replace(Regex("""\s*-\s*"""), "-")
        for (part in norm.split(Regex("""[,;\s]+""")).filter { it.isNotEmpty() }) {
            val m = Regex("""^(\d+(?:\.\d+)?)?(?:-|\.\.)(\d+(?:\.\d+)?)?$""").find(part)
            val g1 = m?.groupValues?.get(1) ?: ""
            val g2 = m?.groupValues?.get(2) ?: ""
            if (m != null && (g1.isNotEmpty() || g2.isNotEmpty())) {
                val a = if (g1.isNotEmpty()) g1.toDouble() else 1.0
                val b = if (g2.isNotEmpty()) g2.toDouble() else max.toDouble()
                if (b - a > 2000) continue
                var n = Math.floor(a).toLong()
                while (n <= Math.floor(b).toLong()) { out.add(n.toDouble()); n++ }
            } else if (Regex("""^\d+(?:\.\d+)?$""").matches(part)) {
                out.add(part.toDouble())
            }
        }
        return out.toList()
    }

    private fun splitUrl(url: String): Triple<String, String, String>? = try {
        val u = URL(url)
        val port = if (u.port >= 0) ":" + u.port else ""
        Triple(u.protocol + "://" + u.host + port, u.path ?: "", if (u.query != null) "?" + u.query else "")
    } catch (_: Exception) {
        null
    }

    private fun patternFromUrl(url: String): EpPattern? {
        val (origin, path, search) = splitUrl(url) ?: return null
        HASHED.find(path)?.let { hm ->
            val cut = hm.range.first + hm.groupValues[1].length
            return EpPattern(origin + path.substring(0, cut) + "{n}" + hm.groupValues[3][0] + "{h}" + search, hm.groupValues[2].toDouble(), true)
        }
        LAST_INT.find(path)?.let { m ->
            val i = m.range.first
            val d = m.value
            val width = if (d.length > 1 && d[0] == '0') minOf(d.length, 4) else 1
            return EpPattern(origin + path.substring(0, i) + "{" + "n".repeat(width) + "}" + path.substring(i + d.length) + search, d.toDouble(), false)
        }
        LAST_INT.find(search)?.let { m ->
            val i = m.range.first
            return EpPattern(origin + path + search.substring(0, i) + "{n}" + search.substring(i + m.value.length), m.value.toDouble(), false)
        }
        return null
    }

    /**
     * Finds the episode number in a media or page link. The file extension is set aside first (so "ep05.m3u8" gives 5, not the 8 of m3u8).
     * Null when there is no number, or the number is only in the query (a token or time, not an episode).
     */
    fun patternFromMedia(url: String): EpPattern? {
        val parts = splitUrl(url) ?: return null
        val ext = MEDIA_EXT.find(parts.second) ?: return patternFromUrl(url)
        val p = patternFromUrl(parts.first + parts.second.substring(0, ext.range.first) + parts.third) ?: return null
        val q = p.pattern.indexOf('?')
        val head = if (q < 0) p.pattern else p.pattern.substring(0, q)
        if (!head.contains('{')) return null
        val pat = if (q < 0) p.pattern + ext.value else head + ext.value + p.pattern.substring(q)
        return EpPattern(pat, p.n, p.hashed)
    }

    private fun numStr(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()

    /** Puts each number into the pattern: {n} = as is, {nn} = at least 2 digits, {nnn}, {nnnn}. */
    fun expandPattern(pattern: String, nums: List<Double>): List<String> =
        nums.map { n -> Regex("""\{(n{1,4})\}""").replace(pattern) { numStr(n).padStart(it.groupValues[1].length, '0') } }

    /**
     * Title for episode n of a show: library style "Show - S01E05" or plain "Show 05". baseTitle may be a page title
     * ("Show Episode 5 - Site"); the show name is taken from it. Empty when there is no title (the file name then comes from the link).
     */
    fun episodeTitle(baseTitle: String?, n: Double, library: Boolean): String {
        val series = sanitize(seriesBase(baseTitle), 80)
        if ((baseTitle ?: "").isBlank()) return ""
        return if (library) tag(series, seasonOf(baseTitle), n) else series + " " + epPad(n)
    }

    // ---------- subtitles (Hub 0.9.39) ----------

    private val LANG_NAMES = mapOf(
        "english" to "en", "spanish" to "es", "french" to "fr", "german" to "de", "italian" to "it", "portuguese" to "pt",
        "arabic" to "ar", "bengali" to "bn", "hindi" to "hi", "russian" to "ru", "japanese" to "ja", "korean" to "ko",
        "chinese" to "zh", "turkish" to "tr", "indonesian" to "id", "dutch" to "nl", "polish" to "pl", "thai" to "th", "vietnamese" to "vi"
    )
    private val LANG_CODES = setOf(
        "en", "es", "fr", "de", "it", "pt", "ar", "bn", "hi", "ru", "ja", "ko", "zh", "tr", "id", "nl", "pl", "sv", "da", "fi",
        "no", "cs", "el", "he", "th", "vi", "uk", "ro", "hu", "ms", "fa", "ur", "ta", "te"
    )
    private val LANG_ISO3 = mapOf(
        "eng" to "en", "spa" to "es", "fre" to "fr", "fra" to "fr", "ger" to "de", "deu" to "de", "ita" to "it", "por" to "pt",
        "ara" to "ar", "ben" to "bn", "hin" to "hi", "rus" to "ru", "jpn" to "ja", "kor" to "ko", "chi" to "zh", "zho" to "zh", "tur" to "tr", "ind" to "id"
    )

    /**
     * ".en" for a subtitle link like ".../en.vtt", ".../movie_eng.srt" or ".../spanish.vtt", so the file is "Show - S01E05.en.vtt"
     * (Plex / Jellyfin match it to the video). Empty when no language can be told.
     */
    fun subtitleSuffix(url: String): String {
        val seg = try {
            URLDecoder.decode(URL(url).path.substringAfterLast('/'), "UTF-8")
        } catch (_: Exception) {
            return ""
        }
        val tokens = seg.substringBeforeLast('.', seg).lowercase().split(Regex("""[._\-\s]+""")).filter { it.isNotEmpty() }
        for (t in tokens.reversed()) {
            val code = LANG_NAMES[t] ?: LANG_ISO3[t] ?: if (t in LANG_CODES) t else null
            if (code != null) return ".$code"
        }
        return ""
    }
}
