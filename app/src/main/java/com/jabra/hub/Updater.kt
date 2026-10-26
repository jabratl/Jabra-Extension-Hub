package com.jabra.hub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * In-app updates, Mihon style.
 *  - App: looks at the newest GitHub release of this repo and installs the APK.
 *  - Plugins: reads catalog.json from the "store" release and installs chosen plugin ZIPs.
 * Everything is fetched from public GitHub release downloads; no account or token needed.
 */
object Updater {
    const val OWNER = "jabratl"
    const val REPO = "Jabra-Extension-Hub"

    private const val BASE = "https://github.com/$OWNER/$REPO/releases"
    private const val APP_JSON = "$BASE/latest/download/app-update.json"
    private const val STORE_DL = "$BASE/download/store"
    private const val CATALOG = "$STORE_DL/catalog.json"
    private const val CHECK_EVERY_MS = 12L * 60 * 60 * 1000

    private class StoreEntry(
        val id: String, val name: String, val version: String,
        val url: String, val sha: String, val installed: String?
    )

    // ---------- helpers ----------

    private fun open(url: String): HttpURLConnection {
        var u = url
        for (i in 0 until 6) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "JabraHub")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("Bad redirect")
                u = URL(URL(u), loc).toString()
                c.disconnect()
                continue
            }
            if (code !in 200..299) {
                throw IOException(if (code == 404) "Nothing published yet (HTTP 404). The GitHub repo must be public and have a release." else "HTTP $code")
            }
            return c
        }
        throw IOException("Too many redirects")
    }

    private fun getJson(url: String): JSONObject =
        open(url).inputStream.use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }

    private fun download(url: String, dest: File, onProgress: (Int) -> Unit): String {
        val c = open(url)
        val total = c.contentLengthLong
        val md = MessageDigest.getInstance("SHA-256")
        dest.parentFile?.mkdirs()
        c.inputStream.use { input ->
            dest.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var last = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    md.update(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val p = (done * 100 / total).toInt()
                        if (p != last) { last = p; onProgress(p) }
                    }
                }
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun toast(act: Activity, msg: String) {
        act.runOnUiThread { Toast.makeText(act, msg, Toast.LENGTH_LONG).show() }
    }

    // ---------- automatic check (at most every 12 hours) ----------

    fun autoCheck(act: Activity, store: PluginStore, onChanged: () -> Unit) {
        val sp = act.getSharedPreferences("hub", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("update_last_check", 0L) < CHECK_EVERY_MS) return
        sp.edit().putLong("update_last_check", now).apply()
        checkApp(act, true)
        checkPlugins(act, store, true, onChanged)
    }

    // ---------- app update ----------

    fun checkApp(act: Activity, silent: Boolean) {
        Thread {
            try {
                val j = getJson(APP_JSON)
                val info = act.packageManager.getPackageInfo(act.packageName, 0)
                val current = PackageInfoCompat.getLongVersionCode(info)
                if (j.optLong("versionCode", 0L) <= current) {
                    if (!silent) toast(act, "You have the latest version (v${info.versionName})")
                    return@Thread
                }
                act.runOnUiThread { promptApp(act, j) }
            } catch (e: Exception) {
                AppDiagnostics.record(act, "App update check failed", e)
                if (!silent) toast(act, "Update check failed: ${e.message}")
            }
        }.start()
    }

    private fun promptApp(act: Activity, j: JSONObject) {
        val notes = j.optString("notes").take(900).ifBlank { "A new version is ready." }
        MaterialAlertDialogBuilder(act)
            .setTitle("Update available: v${j.optString("versionName")}")
            .setMessage("$notes\n\nYour plugins and saved data stay as they are.")
            .setPositiveButton("Download & install") { _, _ -> downloadApp(act, j) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadApp(act: Activity, j: JSONObject) {
        val dlg = MaterialAlertDialogBuilder(act)
            .setTitle("Downloading update")
            .setMessage("0%")
            .setCancelable(false)
            .show()
        Thread {
            try {
                val url = j.optString("apkUrl").ifBlank { "$BASE/latest/download/Jabra-Extension-Hub.apk" }
                val file = File(act.cacheDir, "updates/Jabra-Extension-Hub.apk")
                val sha = download(url, file) { p -> act.runOnUiThread { dlg.setMessage("$p%") } }
                val expected = j.optString("sha256").lowercase()
                if (expected.isNotEmpty() && expected != sha) {
                    file.delete()
                    throw IOException("The downloaded file did not match its checksum")
                }
                act.runOnUiThread {
                    dlg.dismiss()
                    installApk(act, file)
                }
            } catch (e: Exception) {
                AppDiagnostics.record(act, "App update download failed", e)
                act.runOnUiThread { dlg.dismiss() }
                toast(act, "Update failed: ${e.message}")
            }
        }.start()
    }

    private fun installApk(act: Activity, file: File) {
        if (!act.packageManager.canRequestPackageInstalls()) {
            MaterialAlertDialogBuilder(act)
                .setTitle("Allow installing updates")
                .setMessage("Android needs your permission once. Turn on \"Allow from this source\", come back, and choose Check for app update again.")
                .setPositiveButton("Open settings") { _, _ ->
                    act.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}"))
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val uri = FileProvider.getUriForFile(act, "${act.packageName}.fileprovider", file)
        act.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ---------- plugin store ----------

    fun checkPlugins(act: Activity, store: PluginStore, silent: Boolean, onChanged: () -> Unit) {
        Thread {
            try {
                val arr = getJson(CATALOG).optJSONArray("plugins") ?: throw IOException("Catalog has no plugin list")
                val have = store.list().associateBy { it.id }
                val items = ArrayList<StoreEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val ver = o.optString("version")
                    val file = o.optString("file")
                    val sha = o.optString("sha256").lowercase()
                    if (id.isBlank() || ver.isBlank() || file.isBlank() || file.contains('/') || sha.length != 64) continue
                    val cur = have[id]?.version
                    if (cur != null && !isNewer(ver, cur)) continue
                    items.add(StoreEntry(id, o.optString("name", id), ver, "$STORE_DL/$file", sha, cur))
                }
                if (items.isEmpty()) {
                    if (!silent) toast(act, "All plugins are up to date")
                    return@Thread
                }
                if (silent) {
                    toast(act, "${items.size} plugin update(s) or new plugin(s) available. Menu > Plugin store.")
                    return@Thread
                }
                act.runOnUiThread { showEntries(act, store, items, onChanged) }
            } catch (e: Exception) {
                AppDiagnostics.record(act, "Plugin store check failed", e)
                if (!silent) toast(act, "Plugin store check failed: ${e.message}")
            }
        }.start()
    }

    private fun showEntries(act: Activity, store: PluginStore, items: List<StoreEntry>, onChanged: () -> Unit) {
        val labels = items.map {
            if (it.installed == null) "NEW  ${it.name} v${it.version}"
            else "${it.name}  v${it.installed} > v${it.version}"
        }.toTypedArray()
        val checked = BooleanArray(items.size) { items[it].installed != null }
        MaterialAlertDialogBuilder(act)
            .setTitle("Plugin store")
            .setMultiChoiceItems(labels, checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("Install selected") { _, _ ->
                val chosen = items.filterIndexed { i, _ -> checked[i] }
                if (chosen.isNotEmpty()) installEntries(act, store, chosen, onChanged)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun installEntries(act: Activity, store: PluginStore, items: List<StoreEntry>, onChanged: () -> Unit) {
        val dlg = MaterialAlertDialogBuilder(act)
            .setTitle("Installing plugins")
            .setMessage("Starting...")
            .setCancelable(false)
            .show()
        Thread {
            var ok = 0
            val failed = ArrayList<String>()
            for ((n, e) in items.withIndex()) {
                val file = File(act.cacheDir, "updates/plugin_${e.id}.zip")
                try {
                    val sha = download(e.url, file) { p ->
                        act.runOnUiThread { dlg.setMessage("${e.name} (${n + 1}/${items.size}) $p%") }
                    }
                    if (sha != e.sha) throw IOException("checksum mismatch")
                    val p = store.importZip(Uri.fromFile(file))
                    AppDiagnostics.record(act, "Plugin store install succeeded: ${p.id} v${p.version}")
                    ok++
                } catch (ex: Exception) {
                    AppDiagnostics.record(act, "Plugin store install failed: ${e.id}", ex)
                    failed.add("${e.name}: ${ex.message}")
                } finally {
                    file.delete()
                }
            }
            act.runOnUiThread {
                dlg.dismiss()
                onChanged()
                val msg = "Installed $ok of ${items.size}." + if (failed.isEmpty()) "" else "\n\n" + failed.joinToString("\n")
                MaterialAlertDialogBuilder(act).setTitle("Plugin store").setMessage(msg).setPositiveButton("OK", null).show()
            }
        }.start()
    }
}
