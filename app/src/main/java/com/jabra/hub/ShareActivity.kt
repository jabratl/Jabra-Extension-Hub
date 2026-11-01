package com.jabra.hub

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * "Jabra": the one Share-sheet entry (Quiet Pill H5a). It decides where the shared content goes:
 *  - a file (docx, epub, html, a shared .txt) or text without a link  -> read as a chapter (IntakeActivity)
 *  - a link and no plugin accepts shared links                         -> read the page as a chapter
 *  - a link and a plugin declares  "share": true                       -> asks: that plugin, or read as a chapter
 * Link hand-off is the old "Jabra Grab" flow: Floater.save, then the plugin reads it with JabraAPI.floaterGet()
 * (or gets window.__jabraFloater() when already open). Nothing is downloaded here.
 */
class ShareActivity : Activity() {
    private var delivered = false
    private val urlRe = Regex("https?://\\S+")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        if (i == null || i.action != Intent.ACTION_SEND) { finish(); return }

        val text = sharedText(i)
        val textOnly = i.type == "text/plain" && !i.hasExtra(Intent.EXTRA_STREAM)
        if (!textOnly || !urlRe.containsMatchIn(text)) { readAsChapter(); return }

        val all = try { PluginStore(this).list() } catch (e: Exception) {
            AppDiagnostics.record(applicationContext, "Share: plugin list failed", e)
            emptyList()
        }
        val grabbers = all.filter { it.id != ChapterIntake.NOVEL_PLUGIN_ID && Floater.acceptsShare(this, it.id) }
        if (grabbers.isEmpty()) { readAsChapter(); return }

        val novelName = all.firstOrNull { it.id == ChapterIntake.NOVEL_PLUGIN_ID }?.name ?: "Novel Assistant"
        val labels = grabbers.map { it.name } + "Read as chapter: $novelName"
        AlertDialog.Builder(this)
            .setTitle("Send link to")
            .setItems(labels.toTypedArray()) { _, n ->
                if (n < grabbers.size) deliver(grabbers[n].id, grabbers[n].name, grabbers[n].entry, text) else readAsChapter()
            }
            .setOnCancelListener { finish() }
            .show()
    }

    /** EXTRA_TEXT is what browsers send (often "Page title\nhttps://..."); the plugin extracts the links itself. */
    private fun sharedText(i: Intent): String {
        val extra = i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (!extra.isNullOrBlank()) return extra
        return try {
            i.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        } catch (_: Exception) { "" }
    }

    /** Passes the same share on to the reader (IntakeActivity) and keeps access to a shared file. */
    private fun readAsChapter() {
        if (delivered) return
        delivered = true
        try {
            startActivity(
                Intent(intent)
                    .setClass(this, IntakeActivity::class.java)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: Exception) {
            AppDiagnostics.record(applicationContext, "Share: could not open the reader", e)
            Toast.makeText(this, "Could not open Jabra.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun deliver(id: String, name: String, entry: String, text: String) {
        if (delivered) return
        delivered = true
        try { Floater.save(applicationContext, id, text, "share") } catch (e: Exception) {
            AppDiagnostics.record(applicationContext, "Share payload save failed: $id", e)
        }
        startActivity(
            Intent(this, PluginActivity::class.java)
                .putExtra("id", id)
                .putExtra("name", name)
                .putExtra("entry", entry)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
