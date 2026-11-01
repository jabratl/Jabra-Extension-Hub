package com.jabra.hub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import java.io.File

/** One button a plugin shows in the floating Jabra bubble. */
data class FloaterEntry(
    val pluginId: String,
    val pluginName: String,
    val letter: String,      // first letter of the label, drawn in a round tile (no emoji)
    val label: String,
    val color: Int,
    val source: String,      // "none" or "clipboard"
    val allowEmpty: Boolean,
    val legacy: Boolean      // the original Novel Assistant clipboard scan
)

/**
 * General-purpose floating bubble support. Any plugin can declare in manifest.json:
 *   "floater": { "label": "Grab", "color": "#E85D9A", "source": "clipboard" }
 * "label" (max 14 characters) names the button and its first letter fills the tile. "color" is optional
 * (default: the app accent). "icon" is no longer used; an emoji there is ignored.
 * The user decides which plugins appear in the bubble. Tapping a plugin's button opens it
 * and hands over a payload it reads with JabraAPI.floaterGet().
 */
object Floater {
    private const val PREFS = "hub_prefs"
    private const val KEY = "floater_enabled"
    private const val MAX_CHARS = 3_000_000

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun manifest(ctx: Context, id: String): JSONObject? = try {
        val f = File(File(File(ctx.filesDir, "plugins"), safe(id)), "manifest.json")
        if (f.isFile && f.length() < 200_000) JSONObject(f.readText(Charsets.UTF_8)) else null
    } catch (_: Exception) { null }

    private fun letterOf(s: String): String =
        s.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "J"

    fun entryFor(ctx: Context, id: String): FloaterEntry? {
        val m = manifest(ctx, id) ?: return null
        val name = m.optString("name", id)
        val accent = ContextCompat.getColor(ctx, R.color.q_accent)
        val fl = m.optJSONObject("floater")
        if (fl == null) {
            // Novel Assistant predates this API, so it keeps its built-in clipboard scan button.
            return if (id == ChapterIntake.NOVEL_PLUGIN_ID)
                FloaterEntry(id, name, "S", "Scan", accent, "clipboard", false, true)
            else null
        }
        val source = if (fl.optString("source", "none") == "clipboard") "clipboard" else "none"
        val color = try { Color.parseColor(fl.optString("color", "")) } catch (_: Exception) { accent }
        val label = fl.optString("label", name).take(14).ifBlank { name.take(14) }
        return FloaterEntry(
            id, name, letterOf(label), label,
            color, source, fl.optBoolean("allowEmpty", false), false
        )
    }

    fun supports(ctx: Context, id: String) = entryFor(ctx, id) != null

    /** First http(s) link in a shared or copied text (page title lines are skipped). */
    fun firstLink(text: String): String? =
        Regex("https?://[^\\s<>\"']+").find(text)?.value?.trimEnd('.', ',', ')', ';')

    /** Open the first link of [text] in the Hub Browser; its found list scans the page (when "Find media" is on). */
    fun openInBrowser(ctx: Context, text: String): Boolean {
        val u = firstLink(text) ?: return false
        return try {
            ctx.startActivity(Intent(ctx, BrowserActivity::class.java).putExtra("url", u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) { false }
    }

    /**
     * Plugins that declare  "share": true  in manifest.json can receive a link from the Android Share sheet
     * ("Jabra" in the Share sheet, see ShareActivity). Independent of the bubble: no "floater" block is needed.
     */
    fun acceptsShare(ctx: Context, id: String): Boolean =
        manifest(ctx, id)?.optBoolean("share", false) == true

    fun enabledIds(ctx: Context): Set<String> {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Before the user changes anything, the bubble keeps its old job: Novel Assistant scan.
        return if (sp.contains(KEY)) (sp.getStringSet(KEY, emptySet()) ?: emptySet()).toSet()
        else setOf(ChapterIntake.NOVEL_PLUGIN_ID)
    }

    fun isEnabled(ctx: Context, id: String) = enabledIds(ctx).contains(id)

    fun setEnabled(ctx: Context, id: String, on: Boolean) {
        val s = enabledIds(ctx).toMutableSet()
        if (on) s.add(id) else s.remove(id)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, s).apply()
    }

    fun entries(ctx: Context): List<FloaterEntry> =
        enabledIds(ctx).sorted().mapNotNull { entryFor(ctx, it) }

    /** Run a bubble button. */
    fun trigger(ctx: Context, e: FloaterEntry) {
        val i = if (e.legacy) {
            Intent(ctx, IntakeActivity::class.java).setAction(ChapterIntake.ACTION_SCAN_CLIPBOARD)
        } else {
            Intent(ctx, FloaterActivity::class.java)
                .putExtra(FloaterActivity.EXTRA_ID, e.pluginId)
                .putExtra(FloaterActivity.EXTRA_SOURCE, e.source)
        }
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Ask the user to add a plugin's button to the bubble (never silent). */
    fun askEnable(act: Activity, id: String) {
        val e = entryFor(act, id) ?: return
        MaterialAlertDialogBuilder(act)
            .setTitle("Show in floating bubble?")
            .setMessage("${e.pluginName} gets its own button in the Jabra bubble that floats over other apps. You can hide it again from the plugin's menu.")
            .setPositiveButton("Show") { _, _ ->
                setEnabled(act, id, true)
                if (!BubbleService.isEnabled(act) && !BubbleService.toggle(act, true)) {
                    Toast.makeText(
                        act,
                        "Allow \"Display over other apps\" for Jabra, come back, then turn the bubble on from the main menu.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- payload handed to the plugin ----------

    private fun file(ctx: Context, id: String) =
        File(File(ctx.filesDir, "floater").apply { mkdirs() }, "${safe(id)}.json")

    fun save(ctx: Context, id: String, text: String, source: String) {
        val o = JSONObject()
            .put("text", text.take(MAX_CHARS))
            .put("source", source)
            .put("ts", System.currentTimeMillis())
        file(ctx, id).writeText(o.toString(), Charsets.UTF_8)
    }

    fun load(ctx: Context, id: String): String? = try {
        val f = file(ctx, id)
        if (f.isFile) f.readText(Charsets.UTF_8) else null
    } catch (_: Exception) { null }

    fun clear(ctx: Context, id: String): Boolean = try {
        val f = file(ctx, id)
        !f.exists() || f.delete()
    } catch (_: Exception) { false }
}
