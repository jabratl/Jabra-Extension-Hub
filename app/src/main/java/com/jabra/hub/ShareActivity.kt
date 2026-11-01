package com.jabra.hub

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * "Jabra Grab": Android Share-sheet entry. Whatever app shares a link (browser, Telegram, ...) lands here;
 * the text is handed to a plugin that declares  "share": true  in its manifest, exactly like a bubble tap
 * (Floater.save -> plugin reads it with JabraAPI.floaterGet(), or gets window.__jabraFloater() when already open).
 * Nothing is downloaded here: the plugin decides what to do with the link.
 */
class ShareActivity : Activity() {
    private var delivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = sharedText(intent)
        if (text.isBlank()) {
            Toast.makeText(this, "Nothing to grab in that share.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val plugins = try {
            PluginStore(this).list().filter { Floater.acceptsShare(this, it.id) }
        } catch (e: Exception) {
            AppDiagnostics.record(applicationContext, "Share: plugin list failed", e)
            emptyList()
        }
        when (plugins.size) {
            0 -> {
                Toast.makeText(this, "No installed plugin accepts shared links yet. Install or update Media Grabber.", Toast.LENGTH_LONG).show()
                finish()
            }
            1 -> deliver(plugins[0].id, plugins[0].name, plugins[0].entry, text)
            else -> AlertDialog.Builder(this)
                .setTitle("Send link to")
                .setItems(plugins.map { it.name }.toTypedArray()) { _, i -> deliver(plugins[i].id, plugins[i].name, plugins[i].entry, text) }
                .setOnCancelListener { finish() }
                .show()
        }
    }

    /** EXTRA_TEXT is what browsers send (often "Page title\nhttps://..."); the plugin extracts the links itself. */
    private fun sharedText(i: Intent?): String {
        if (i == null || i.action != Intent.ACTION_SEND) return ""
        val extra = i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (!extra.isNullOrBlank()) return extra
        return try {
            i.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        } catch (_: Exception) { "" }
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
