package com.jabra.hub

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class Plugin(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val icon: String,
    val entry: String,
    val category: String = "Other"
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
            entry = j.optString("entry", "index.html"),
            category = normalizeCategory(j.optString("category", "Other"))
        )
    } catch (e: Exception) {
        null
    }

    private fun normalizeCategory(raw: String): String {
        val allowed = listOf("Character", "Item", "Skill", "Place", "Organization", "Title", "Other", "Utility", "Writing", "Translation", "Developer")
        return allowed.firstOrNull { it.equals(raw.trim(), ignoreCase = true) } ?: "Other"
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
        val tmp = File(ctx.cacheDir, "import_${System.nanoTime()}").apply { mkdirs() }
        try {
            var total = 0L
            var entries = 0
            val seenPaths = HashSet<String>()
            val input = ctx.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open the selected file")
            input.use { raw ->
                ZipInputStream(raw.buffered()).use { zin ->
                    var e = zin.nextEntry
                    while (e != null) {
                        if (++entries > MAX_ENTRIES) throw IllegalStateException("Plugin contains too many files (limit: $MAX_ENTRIES)")
                        val name = e.name
                        if (name.isBlank() || name.startsWith("/") || name.startsWith("\\") || name.contains('\\') ||
                            name.split('/').any { it == ".." || it == "." }) {
                            throw IllegalStateException("Unsafe path in plugin ZIP")
                        }
                        val normalized = name.trimEnd('/')
                        if (!seenPaths.add(normalized)) throw IllegalStateException("Duplicate path in plugin ZIP: $normalized")
                        val out = File(tmp, name)
                        val rootPath = tmp.canonicalFile.toPath()
                        if (!out.canonicalFile.toPath().startsWith(rootPath)) throw IllegalStateException("Unsafe ZIP entry path")
                        if (e.isDirectory) {
                            if (!out.mkdirs() && !out.isDirectory) throw IllegalStateException("Could not create plugin folder")
                        } else {
                            out.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IllegalStateException("Could not create plugin folder") }
                            out.outputStream().buffered().use { o ->
                                val buf = ByteArray(16 * 1024)
                                while (true) {
                                    val n = zin.read(buf)
                                    if (n < 0) break
                                    total += n
                                    if (total > MAX_BYTES) throw IllegalStateException("Plugin exceeds the 200 MB uncompressed limit")
                                    o.write(buf, 0, n)
                                }
                            }
                        }
                        zin.closeEntry()
                        e = zin.nextEntry
                    }
                }
            }
            if (entries == 0) throw IllegalStateException("The selected ZIP is empty")
            val base = findBase(tmp) ?: throw IllegalStateException("manifest.json not found, or multiple manifests found in the ZIP")
            val manifestFile = File(base, "manifest.json")
            if (manifestFile.length() > MAX_MANIFEST_BYTES) throw IllegalStateException("manifest.json is too large")
            val j = try { JSONObject(manifestFile.readText(Charsets.UTF_8)) }
                catch (_: Exception) { throw IllegalStateException("manifest.json is not valid JSON") }
            val rawId = j.optString("id", "").trim().ifEmpty { j.optString("name", "plugin").trim() }
            val id = rawId.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_-]"), "-").trim('-', '_')
            if (id.isEmpty()) throw IllegalStateException("Plugin manifest needs a valid id or name")
            if (id.length > MAX_ID_LENGTH) throw IllegalStateException("Plugin ID is too long (maximum $MAX_ID_LENGTH characters)")
            val name = j.optString("name", "").trim()
            if (name.isEmpty()) throw IllegalStateException("Plugin manifest is missing its name")
            if (name.length > MAX_NAME_LENGTH) throw IllegalStateException("Plugin name is too long")
            val version = j.optString("version", "1.0").trim()
            if (version.isEmpty() || version.length > MAX_VERSION_LENGTH) throw IllegalStateException("Plugin version is invalid")
            val entry = j.optString("entry", "index.html").trim()
            if (entry.isEmpty() || entry.startsWith("/") || entry.contains('\\') || entry.split('/').any { it == ".." || it == "." }) {
                throw IllegalStateException("Plugin entry path is invalid")
            }
            val entryFile = File(base, entry)
            if (!entryFile.canonicalFile.toPath().startsWith(base.canonicalFile.toPath()) || !entryFile.isFile) {
                throw IllegalStateException("Entry file '$entry' is missing or unsafe")
            }
            val dest = File(root, id)
            val staging = File(ctx.filesDir, "staging_$id")
            val previous = File(ctx.filesDir, "previous_$id")
            val rollback = File(ctx.filesDir, "rollback_$id")
            val recoverySlot = File(ctx.filesDir, "rollback_recovery_$id")
            val oldRollback = File(ctx.filesDir, "rollback_old_$id")
            staging.deleteRecursively()
            if (previous.exists() && dest.exists()) {
                val recoveryTarget = if (!rollback.exists()) rollback else recoverySlot
                if (recoveryTarget.exists()) recoveryTarget.deleteRecursively()
                if (!previous.renameTo(recoveryTarget)) {
                    throw IllegalStateException("A previous recovery copy exists and could not be safely moved; no plugin files were changed")
                }
            }
            try {
                base.copyRecursively(staging, overwrite = true, onError = { _, _ -> throw IllegalStateException("Could not stage plugin files") })
                if (dest.exists() && !dest.renameTo(previous)) throw IllegalStateException("Could not safely prepare the existing plugin for update")
                val installed = staging.renameTo(dest) || try {
                    staging.copyRecursively(dest, overwrite = true, onError = { _, _ -> throw IllegalStateException("Could not copy plugin files") })
                    true
                } catch (_: Exception) { false }
                if (!installed || read(dest) == null) {
                    dest.deleteRecursively()
                    if (previous.exists() && !previous.renameTo(dest)) {
                        throw IllegalStateException("Update failed. The previous version is preserved in app recovery storage; do not clear app data.")
                    }
                    throw IllegalStateException("Plugin installation failed; previous version restored")
                }
                // Keep exactly one prior installed version for a user-controlled rollback.
                // If backup rotation fails, leave the previous directory as recovery data.
                if (previous.exists()) {
                    oldRollback.deleteRecursively()
                    val oldMoved = !rollback.exists() || rollback.renameTo(oldRollback)
                    if (oldMoved && previous.renameTo(rollback)) {
                        oldRollback.deleteRecursively()
                    } else {
                        if (!rollback.exists() && oldRollback.exists()) oldRollback.renameTo(rollback)
                        AppDiagnostics.record(ctx, "Update installed for $id, but rollback backup rotation needs attention")
                    }
                }
            } catch (e: Exception) {
                if (!dest.exists() && previous.exists() && !previous.renameTo(dest)) {
                    throw IllegalStateException("${e.message}. Previous version remains in app recovery storage; do not clear app data.")
                }
                throw e
            } finally {
                staging.deleteRecursively()
                // Do not delete `previous` here if it is the only remaining recovery copy.
                if (!dest.exists() && previous.exists()) previous.renameTo(dest)
            }
            return read(dest) ?: throw IllegalStateException("Installed plugin manifest could not be read")
        } finally {
            tmp.deleteRecursively()
        }
    }

    fun hasRollback(id: String): Boolean {
        val rollback = File(ctx.filesDir, "rollback_$id")
        val recovery = File(ctx.filesDir, "previous_$id")
        val recoverySlot = File(ctx.filesDir, "rollback_recovery_$id")
        return (rollback.isDirectory && File(rollback, "manifest.json").isFile) ||
            (recovery.isDirectory && File(recovery, "manifest.json").isFile) ||
            (recoverySlot.isDirectory && File(recoverySlot, "manifest.json").isFile)
    }

    fun rollbackVersion(id: String): String? {
        val rollback = File(ctx.filesDir, "rollback_$id")
        val recovery = File(ctx.filesDir, "previous_$id")
        val recoverySlot = File(ctx.filesDir, "rollback_recovery_$id")
        val source = when {
            rollback.isDirectory && File(rollback, "manifest.json").isFile -> rollback
            recovery.isDirectory && File(recovery, "manifest.json").isFile -> recovery
            else -> recoverySlot
        }
        return read(source)?.version
    }

    /** Swap the installed plugin with its last saved version without touching plugin-owned data. */
    fun rollbackPlugin(id: String): Plugin {
        val current = File(root, id)
        val rollback = File(ctx.filesDir, "rollback_$id")
        val recovery = File(ctx.filesDir, "previous_$id")
        val recoverySlot = File(ctx.filesDir, "rollback_recovery_$id")
        val source = when {
            rollback.isDirectory && File(rollback, "manifest.json").isFile -> rollback
            recovery.isDirectory && File(recovery, "manifest.json").isFile -> recovery
            else -> recoverySlot
        }
        require(current.isDirectory && File(current, "manifest.json").isFile) { "Installed plugin files are missing" }
        require(source.isDirectory && File(source, "manifest.json").isFile) { "No previous plugin version is available" }
        val swap = File(ctx.filesDir, "rollback_swap_$id")
        swap.deleteRecursively()
        if (!current.renameTo(swap)) throw IllegalStateException("Could not prepare the current version for rollback")
        if (!source.renameTo(current)) {
            if (!swap.renameTo(current)) throw IllegalStateException("Rollback failed; current version remains in app recovery storage. Do not clear app data.")
            throw IllegalStateException("Could not restore the previous plugin version")
        }
        if (!swap.renameTo(rollback)) {
            // Keep the displaced version in a recoverable path; never delete it here.
            if (!recoverySlot.exists()) swap.renameTo(recoverySlot)
            AppDiagnostics.record(ctx, "Rollback succeeded for $id, but the displaced version remains in recovery storage")
        }
        return read(current) ?: throw IllegalStateException("Rolled-back plugin manifest could not be read")
    }

    private fun findBase(dir: File): File? {
        val candidates = ArrayList<File>()
        fun scan(folder: File, depth: Int) {
            if (depth > 3 || candidates.size > 1) return
            if (File(folder, "manifest.json").isFile) { candidates.add(folder); return }
            folder.listFiles()?.filter { it.isDirectory }?.forEach { scan(it, depth + 1) }
        }
        scan(dir, 0)
        return candidates.singleOrNull()
    }

    companion object {
        private const val MAX_BYTES = 200L * 1024 * 1024
        private const val MAX_ENTRIES = 5000
        private const val MAX_MANIFEST_BYTES = 256L * 1024
        private const val MAX_ID_LENGTH = 80
        private const val MAX_NAME_LENGTH = 120
        private const val MAX_VERSION_LENGTH = 64
    }
}
