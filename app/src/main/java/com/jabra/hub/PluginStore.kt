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

    fun sizeOf(id: String): Long =
        File(root, id).walkTopDown().filter { it.isFile }.map { it.length() }.sum()

    fun remove(id: String) {
        File(root, id).deleteRecursively()
        // plugin-er saved data-o muche felo (age uninstall-er por data theke jeto)
        ctx.deleteSharedPreferences("plugin_$id")
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
            var entries = 0
            val input = ctx.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open the file")
            input.use { raw ->
                ZipInputStream(raw.buffered()).use { zin ->
                    var e = zin.nextEntry
                    while (e != null) {
                        if (++entries > MAX_ENTRIES) throw IllegalStateException("Too many files in the zip")
                        val out = File(tmp, e.name)
                        if (!out.canonicalPath.startsWith(tmp.canonicalPath + File.separator)) {
                            throw IllegalStateException("Unsafe zip file")
                        }
                        if (e.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { o ->
                                val buf = ByteArray(16 * 1024)
                                while (true) {
                                    val n = zin.read(buf)
                                    if (n < 0) break
                                    total += n
                                    if (total > MAX_BYTES) throw IllegalStateException("Plugin is too large")
                                    o.write(buf, 0, n)
                                }
                            }
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
            val entryFile = File(base, entry)
            if (!entryFile.isFile) {
                throw IllegalStateException("Entry file '$entry' is missing")
            }
            if (!entryFile.canonicalPath.startsWith(base.canonicalPath + File.separator)) {
                throw IllegalStateException("Unsafe entry path")
            }
            val dest = File(root, id)
            val staging = File(ctx.filesDir, "staging_$id")
            try {
                staging.deleteRecursively()
                base.copyRecursively(staging, overwrite = true)
                // copy sofol hole tarpor-i purono version soriye notun-ta boshao
                dest.deleteRecursively()
                if (!staging.renameTo(dest)) {
                    staging.copyRecursively(dest, overwrite = true)
                }
            } finally {
                staging.deleteRecursively()
            }
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
        private const val MAX_ENTRIES = 5000
    }
}
