package com.jabra.hub

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

data class Plugin(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val icon: String,
    val entry: String
)

class PluginStore(private val ctx: Context) {

    val root: File = File(ctx.filesDir, "plugins").apply { mkdirs() }

    fun list(): List<Plugin> =
        (root.listFiles() ?: emptyArray())
            .filter { it.isDirectory }
            .mapNotNull { read(it) }
            .sortedBy { it.name.lowercase() }

    private fun read(dir: File): Plugin? = try {
        val j = JSONObject(File(dir, "manifest.json").readText())
        Plugin(
            id = dir.name,
            name = j.optString("name", dir.name),
            version = j.optString("version", "1.0"),
            description = j.optString("description", ""),
            icon = j.optString("icon", "🧩").ifEmpty { "🧩" },
            entry = j.optString("entry", "index.html")
        )
    } catch (e: Exception) {
        null
    }

    fun remove(id: String) {
        File(root, id).deleteRecursively()
    }

    fun installSample() {
        val dest = File(root, "hello-hub").apply { mkdirs() }
        ctx.assets.list("sample")?.forEach { n ->
            ctx.assets.open("sample/$n").use { i ->
                File(dest, n).outputStream().use { o -> i.copyTo(o) }
            }
        }
    }

    fun importZip(uri: Uri): Plugin {
        val tmp = File(ctx.cacheDir, "import_" + System.currentTimeMillis()).apply { mkdirs() }
        try {
            var total = 0L
            val input = ctx.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open the file")
            input.use { raw ->
                ZipInputStream(raw.buffered()).use { zin ->
                    var e = zin.nextEntry
                    while (e != null) {
                        val out = File(tmp, e.name)
                        if (!out.canonicalPath.startsWith(tmp.canonicalPath + File.separator)) {
                            throw IllegalStateException("Unsafe zip file")
                        }
                        if (e.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { o -> total += zin.copyTo(o) }
                            if (total > MAX_BYTES) throw IllegalStateException("Plugin is too large")
                        }
                        zin.closeEntry()
                        e = zin.nextEntry
                    }
                }
            }
            val base = findBase(tmp)
                ?: throw IllegalStateException("manifest.json not found in the zip")
            val j = JSONObject(File(base, "manifest.json").readText())
            val id = j.optString("id", j.optString("name", "plugin"))
                .lowercase()
                .replace(Regex("[^a-z0-9_-]"), "-")
                .trim('-')
                .ifEmpty { "plugin" }
            val entry = j.optString("entry", "index.html")
            if (!File(base, entry).exists()) {
                throw IllegalStateException("Entry file '$entry' is missing")
            }
            val dest = File(root, id)
            dest.deleteRecursively()
            base.copyRecursively(dest, overwrite = true)
            return read(dest) ?: throw IllegalStateException("Invalid manifest.json")
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun findBase(dir: File): File? {
        if (File(dir, "manifest.json").exists()) return dir
        return dir.listFiles()?.firstOrNull { it.isDirectory && File(it, "manifest.json").exists() }
    }

    companion object {
        private const val MAX_BYTES = 200L * 1024 * 1024
    }
}
