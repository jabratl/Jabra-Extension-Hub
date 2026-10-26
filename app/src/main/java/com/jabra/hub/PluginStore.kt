package com.jabra.hub

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

    fun totalSize(): Long =
        (root.listFiles() ?: emptyArray()).filter { it.isDirectory }
            .sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

    fun savedDataRoot(): File = File(ctx.filesDir, "plugin_data").apply { mkdirs() }

    fun savedDataSize(id: String): Long =
        File(savedDataRoot(), id).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun totalSavedDataSize(): Long =
        (savedDataRoot().listFiles() ?: emptyArray()).filter { it.isDirectory }
            .sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

    /** Export one installed plugin as a portable ZIP archive. */
    fun exportPlugin(id: String, uri: Uri) {
        val source = File(root, id)
        require(source.isDirectory && File(source, "manifest.json").isFile) { "Plugin files are missing" }
        val output = ctx.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Cannot create the destination file")
        output.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                val basePath = source.canonicalPath + File.separator
                source.walkTopDown().filter { it.isFile }.forEach { file ->
                    val path = file.canonicalPath
                    require(path.startsWith(basePath)) { "Unsafe plugin path" }
                    val relative = file.relativeTo(source).invariantSeparatorsPath
                    zip.putNextEntry(java.util.zip.ZipEntry("$id/$relative"))
                    file.inputStream().buffered().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    fun cacheSize(): Long =
        (ctx.cacheDir.listFiles() ?: emptyArray()).filter { it.isFile }
            .sumOf { it.length() } + (ctx.cacheDir.listFiles() ?: emptyArray())
            .filter { it.isDirectory }.sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

    /** Clears temporary cache only; installed plugins and plugin preferences are untouched. */
    fun clearTemporaryCache(): Boolean {
        val ok = (ctx.cacheDir.listFiles() ?: emptyArray()).all { it.deleteRecursively() }
        ctx.cacheDir.mkdirs()
        return ok
    }

    /** Removes plugin code but deliberately preserves its saved data for a later reinstall. */
    fun remove(id: String) {
        File(root, id).deleteRecursively()
    }

    /** Destructive opt-in: erase the plugin's private persistent data and preferences too. */
    fun erasePluginData(id: String) {
        File(File(ctx.filesDir, "plugin_data"), id).deleteRecursively()
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
            val previous = File(ctx.filesDir, "previous_$id")
            try {
                staging.deleteRecursively()
                previous.deleteRecursively()
                base.copyRecursively(staging, overwrite = true)
                // Keep the previous installation until the replacement is safely staged.
                if (dest.exists() && !dest.renameTo(previous)) {
                    throw IllegalStateException("Could not prepare the existing plugin for update")
                }
                val installed = staging.renameTo(dest) || try {
                    staging.copyRecursively(dest, overwrite = true)
                    true
                } catch (_: Exception) { false }
                if (!installed) {
                    dest.deleteRecursively()
                    if (previous.exists()) previous.renameTo(dest)
                    throw IllegalStateException("Could not install plugin; previous version was restored when possible")
                }
                previous.deleteRecursively()
            } finally {
                staging.deleteRecursively()
                if (!dest.exists() && previous.exists()) previous.renameTo(dest)
                previous.deleteRecursively()
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
