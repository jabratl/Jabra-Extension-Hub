package com.jabra.hub

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/**
 * Invisible entry point for "Jabra Scan": text-selection menu, Share sheet, Open-with,
 * and the clipboard (floating bubble / plugin button / Hub menu).
 * Reads the incoming text off the UI thread, stores it, then opens Novel Assistant.
 */
class IntakeActivity : Activity() {
    private var started = false
    private var clipboardMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clipboardMode = intent?.action == ChapterIntake.ACTION_SCAN_CLIPBOARD
        // Android 10+ only lets the focused app read the clipboard, so wait for window focus.
        if (!clipboardMode) begin(null)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && clipboardMode && !started) {
            val text = try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            } catch (_: Exception) { null }
            begin(text.orEmpty())
        }
    }

    private fun begin(clip: String?) {
        if (started) return
        started = true
        val incoming = Intent(intent)
        val app = applicationContext
        Toast.makeText(this, "Jabra: reading text…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val intake = if (clip != null) ChapterIntake.fromClipboard(clip) else ChapterIntake.fromIntent(app, incoming)
                ChapterIntake.save(app, intake)
                runOnUiThread {
                    openNovelAssistant()
                    finish()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Intake failed", e)
                try { AppDiagnostics.record(app, "Chapter intake failed: ${e.message}", e) } catch (_: Throwable) { }
                runOnUiThread {
                    Toast.makeText(this, e.message ?: "Could not read that content", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }.start()
    }

    private fun openNovelAssistant() {
        val plugin = try {
            PluginStore(this).list().firstOrNull { it.id == ChapterIntake.NOVEL_PLUGIN_ID }
        } catch (_: Exception) { null }
        if (plugin == null) {
            Toast.makeText(this, "Install the Jabra Novel Assistant plugin in the Hub first. Your text is saved and will appear when you open it.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        startActivity(
            Intent(this, PluginActivity::class.java)
                .putExtra("id", plugin.id)
                .putExtra("name", plugin.name)
                .putExtra("entry", plugin.entry)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object { private const val TAG = "JabraIntake" }
}
