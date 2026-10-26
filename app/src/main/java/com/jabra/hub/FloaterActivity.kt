package com.jabra.hub

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible helper behind a bubble button. If the plugin wants the clipboard it waits for
 * window focus (Android 10+ only lets the focused app read it), stores the payload, then
 * opens the plugin.
 */
class FloaterActivity : Activity() {
    private var started = false
    private var pluginId = ""
    private var source = "none"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pluginId = intent?.getStringExtra(EXTRA_ID).orEmpty()
        source = intent?.getStringExtra(EXTRA_SOURCE) ?: "none"
        if (pluginId.isEmpty()) { finish(); return }
        if (source != "clipboard") deliver("")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && source == "clipboard" && !started && pluginId.isNotEmpty()) {
            val text = try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            } catch (_: Exception) { null }
            deliver(text.orEmpty())
        }
    }

    private fun deliver(text: String) {
        if (started) return
        started = true
        val entry = Floater.entryFor(this, pluginId)
        if (entry == null) {
            Toast.makeText(this, "That plugin is no longer installed.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (source == "clipboard" && text.isBlank() && !entry.allowEmpty) {
            Toast.makeText(this, "Clipboard is empty. Copy some text first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        try { Floater.save(applicationContext, pluginId, text, source) } catch (e: Exception) {
            AppDiagnostics.record(applicationContext, "Floater payload save failed: $pluginId", e)
        }
        val plugin = try { PluginStore(this).list().firstOrNull { it.id == pluginId } } catch (_: Exception) { null }
        if (plugin == null) {
            Toast.makeText(this, "Could not open the plugin.", Toast.LENGTH_LONG).show()
        } else {
            startActivity(
                Intent(this, PluginActivity::class.java)
                    .putExtra("id", plugin.id)
                    .putExtra("name", plugin.name)
                    .putExtra("entry", plugin.entry)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }

    companion object {
        const val EXTRA_ID = "plugin_id"
        const val EXTRA_SOURCE = "source"
    }
}
