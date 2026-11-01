package com.jabra.hub

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * One Settings screen (H1). Replaces the old app menu, the Settings dialog and the
 * Activity tab. Dialog texts are the same as before; only the place they open from changed.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var store: PluginStore
    private lateinit var bubbleSwitch: SwitchCompat
    private var bubbleListener: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PluginStore(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Q.color(this, R.color.q_bg))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(Q.color(this, R.color.q_surface))
        bar.setPadding(dp(4), dp(4), dp(16), dp(4))
        val back = ImageButton(this)
        back.setImageDrawable(Q.icon(this, R.drawable.q_ic_back))
        back.setBackgroundResource(selectableBorderless())
        back.contentDescription = "Back"
        back.setOnClickListener { finish() }
        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        val title = TextView(this)
        title.text = "Settings"
        title.textSize = 20f
        title.setTextColor(Q.color(this, R.color.q_text))
        title.setTypeface(title.typeface, Typeface.BOLD)
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(bar)

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(8), dp(16), dp(24))

        section(body, "Quick input") { card ->
            val sw = switchRow(card, "Floating bubble", "A J button over other apps that runs your plugins") { on ->
                if (BubbleService.toggle(this, on)) {
                    Toast.makeText(this, if (on) "Bubble on. Copy text, then tap J." else "Bubble off.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Allow \"Display over other apps\" for Jabra, come back, then turn the bubble on again.", Toast.LENGTH_LONG).show()
                    setBubbleChecked(false)
                }
            }
            bubbleSwitch = sw
            row(card, "Scan copied text", "Send the text you copied to a plugin") {
                startActivity(Intent(this, IntakeActivity::class.java).setAction(ChapterIntake.ACTION_SCAN_CLIPBOARD))
            }
            bubbleButtons(card)
        }
        section(body, "Plugins") { card ->
            row(card, "Plugin updates", "Check the plugin store for new and updated plugins") {
                Updater.checkPlugins(this, store, false) { }
            }
            row(card, "Storage & data", "Plugin files, saved data and cache") { showStorage() }
        }
        section(body, "App") { card ->
            row(card, "Check for app update", "Look for a newer Jabra Hub version") { Updater.checkApp(this, false) }
            row(card, "Diagnostics & support", "Recent local events and errors; share a report") { showDiagnostics() }
            row(card, "Developer tools · API contract", "What plugins can use on this Hub") { showApiContract() }
            row(card, "About Jabra Hub", "Version and notes") { showAbout() }
        }

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        // The overlay permission screen may have been used meanwhile; show the real state.
        setBubbleChecked(BubbleService.isEnabled(this))
    }

    // ---------- small UI helpers ----------

    private fun dp(v: Int) = Q.dp(this, v)

    private fun selectableBorderless(): Int {
        val out = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, out, true)
        return out.resourceId
    }

    private fun selectable(): Int {
        val out = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        return out.resourceId
    }

    private fun section(parent: LinearLayout, name: String, fill: (LinearLayout) -> Unit) {
        val head = TextView(this)
        head.text = name
        head.textSize = 13f
        head.setTypeface(head.typeface, Typeface.BOLD)
        head.setTextColor(Q.color(this, R.color.q_accent_text))
        head.setPadding(dp(8), dp(20), dp(8), dp(8))
        parent.addView(head)
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.q_card)
        parent.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        fill(card)
    }

    private fun textBlock(title: String, subtitle: String): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.text = title
        t.textSize = 16f
        t.setTextColor(Q.color(this, R.color.q_text))
        box.addView(t)
        val s = TextView(this)
        s.text = subtitle
        s.textSize = 13f
        s.setTextColor(Q.color(this, R.color.q_text_sub))
        box.addView(s)
        return box
    }

    private fun addDividerIfNeeded(card: LinearLayout) {
        if (card.childCount == 0) return
        val line = View(this)
        line.setBackgroundColor(Q.color(this, R.color.q_outline))
        card.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
    }

    private fun row(card: LinearLayout, title: String, subtitle: String, onClick: () -> Unit) {
        addDividerIfNeeded(card)
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.minimumHeight = dp(64)
        r.setPadding(dp(16), dp(10), dp(16), dp(10))
        r.setBackgroundResource(selectable())
        r.isClickable = true
        r.isFocusable = true
        r.setOnClickListener { onClick() }
        r.addView(textBlock(title, subtitle), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun switchRow(card: LinearLayout, title: String, subtitle: String, onChange: (Boolean) -> Unit): SwitchCompat {
        addDividerIfNeeded(card)
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.minimumHeight = dp(64)
        r.setPadding(dp(16), dp(10), dp(16), dp(10))
        r.setBackgroundResource(selectable())
        r.addView(textBlock(title, subtitle), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = SwitchCompat(this)
        sw.contentDescription = title
        val listener: (Boolean) -> Unit = { on -> onChange(on) }
        bubbleListener = listener
        sw.setOnCheckedChangeListener { v, on -> Q.tick(v); listener(on) }
        r.setOnClickListener { sw.toggle() }
        r.addView(sw)
        card.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return sw
    }

    /** One switch per installed plugin that offers a bubble button (same choice as the plugin's own menu). */
    private fun bubbleButtons(card: LinearLayout) {
        val offered = try { store.list().filter { Floater.supports(this, it.id) } } catch (_: Exception) { emptyList() }
        if (offered.isEmpty()) {
            row(card, "Bubble buttons", "No installed plugin offers a bubble button yet") { }
            return
        }
        for (p in offered) {
            val label = Floater.entryFor(this, p.id)?.label ?: p.name
            addDividerIfNeeded(card)
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.minimumHeight = dp(64)
            r.setPadding(dp(16), dp(10), dp(16), dp(10))
            r.setBackgroundResource(selectable())
            r.addView(textBlock(p.name, "Bubble button: $label"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val sw = SwitchCompat(this)
            sw.contentDescription = "Show ${p.name} in the bubble"
            sw.isChecked = Floater.isEnabled(this, p.id)
            sw.setOnCheckedChangeListener { v, on ->
                Q.tick(v)
                Floater.setEnabled(this, p.id, on)
                if (on && !BubbleService.isEnabled(this)) {
                    Toast.makeText(this, "Turn on the floating bubble above to see this button.", Toast.LENGTH_LONG).show()
                }
            }
            r.setOnClickListener { sw.toggle() }
            r.addView(sw)
            card.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** Set the switch without running the on/off action. */
    private fun setBubbleChecked(on: Boolean) {
        if (!::bubbleSwitch.isInitialized) return
        bubbleSwitch.setOnCheckedChangeListener(null)
        bubbleSwitch.isChecked = on
        val listener = bubbleListener
        bubbleSwitch.setOnCheckedChangeListener { _, checked -> listener?.invoke(checked) }
    }

    // ---------- dialogs (moved from MainActivity, same texts) ----------

    private fun showAbout() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) { "unknown" }
        MaterialAlertDialogBuilder(this)
            .setTitle("Jabra Extension Hub")
            .setMessage("Version $version\n\nA modular Android plugin hub. Import ZIP plugins, search installed tools, view plugin details, and export plugins for backup or transfer.\n\nPlugin code runs in Android WebView. Extensions should feature-detect the Jabra API and use documented fallbacks when a capability is unavailable.")
            .setPositiveButton("OK", null).show()
    }

    private fun showApiContract() {
        val capabilities = JabraApiContract.capabilities.joinToString("\n• ", prefix = "• ")
        val message = "Jabra Extension API v${JabraApiContract.API_VERSION}\nHost version: ${packageManager.getPackageInfo(packageName, 0).versionName}\n\nSupported capabilities\n$capabilities\n\nCompatibility rules\n• Declare minJabraApiVersion only when needed.\n• Declare requiredCapabilities for features the plugin cannot work without.\n• Feature-detect optional APIs and handle false/null results.\n• Do not assume Chrome extension APIs, background execution, cloud sync or arbitrary URL downloads.\n• Plugin data is scoped by stable plugin ID; do not change IDs casually.\n\nThis screen describes the host contract. It does not certify a plugin as safe."
        MaterialAlertDialogBuilder(this)
            .setTitle("Developer tools · API contract")
            .setMessage(message)
            .setPositiveButton("Share contract") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Jabra Extension API v${JabraApiContract.API_VERSION}")
                        putExtra(Intent.EXTRA_TEXT, message)
                    }
                    startActivity(Intent.createChooser(intent, "Share API contract"))
                } catch (e: Exception) { AppDiagnostics.record(this, "Share API contract failed", e) }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showDiagnostics() {
        val report = AppDiagnostics.read(this)
        MaterialAlertDialogBuilder(this)
            .setTitle("Diagnostics & support")
            .setMessage("Recent local app events and errors. This report stays on this device unless you choose to share it.\n\n$report")
            .setNeutralButton("Share report") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Jabra Extension Hub diagnostics")
                    putExtra(Intent.EXTRA_TEXT, "Jabra Extension Hub diagnostics\nVersion: ${packageManager.getPackageInfo(packageName, 0).versionName}\n\n$report")
                }
                try { startActivity(Intent.createChooser(send, "Share diagnostics")) }
                catch (e: Exception) { AppDiagnostics.record(this, "Share diagnostics failed", e) }
            }
            .setNegativeButton("Close", null)
            .setPositiveButton("Clear log") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Clear diagnostics?")
                    .setMessage("This removes only the local diagnostics log. It does not affect plugins or saved data.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear") { _, _ -> AppDiagnostics.clear(this) }
                    .show()
            }
            .show()
    }

    private fun showStorage() {
        val pluginBytes = store.totalSize()
        val dataBytes = store.totalSavedDataSize()
        val cacheBytes = store.cacheSize()
        val text = "Installed plugins: ${store.list().size}\nPlugin files: ${Formatter.formatShortFileSize(this, pluginBytes)}\nSaved plugin data: ${Formatter.formatShortFileSize(this, dataBytes)}\nTemporary cache: ${Formatter.formatShortFileSize(this, cacheBytes)}\n\nSaved plugin data is kept separately from plugin ZIP files. Clearing temporary cache does not remove installed plugins or their saved data."
        MaterialAlertDialogBuilder(this)
            .setTitle("Storage & data")
            .setMessage(text)
            .setNeutralButton("Refresh") { _, _ -> showStorage() }
            .setNegativeButton("Close", null)
            .setPositiveButton("Clear cache") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Clear temporary cache?")
                    .setMessage("Only temporary cache files will be removed. Installed plugins and plugin data will be kept.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear") { _, _ ->
                        val ok = store.clearTemporaryCache()
                        Toast.makeText(this, if (ok) "Temporary cache cleared" else "Some temporary files could not be removed", Toast.LENGTH_LONG).show()
                        showStorage()
                    }.show()
            }.show()
    }
}
