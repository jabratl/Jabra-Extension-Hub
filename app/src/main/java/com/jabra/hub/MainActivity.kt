package com.jabra.hub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.text.format.Formatter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var store: PluginStore
    private lateinit var list: LinearLayout
    private lateinit var empty: View
    private lateinit var count: TextView
    private lateinit var emptyTitle: TextView
    private lateinit var emptyMsg: TextView
    private var query = ""
    private var showFavoritesOnly = false
    private var categoryFilter = "All categories"
    private var sortMode = "name_asc"

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importZip(uri)
    }
    private var exportPluginId: String? = null
    private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val id = exportPluginId
        exportPluginId = null
        if (uri != null && id != null) exportPlugin(id, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = PluginStore(this)
        val managerPrefs = getSharedPreferences("hub_manager", MODE_PRIVATE)
        showFavoritesOnly = managerPrefs.getBoolean("favorites_only", false)
        categoryFilter = managerPrefs.getString("category_filter", "All categories") ?: "All categories"
        sortMode = managerPrefs.getString("sort_mode", "name_asc") ?: "name_asc"
        val sp = getSharedPreferences("hub", MODE_PRIVATE)
        if (!sp.getBoolean("seeded", false)) {
            try { store.installSample() } catch (_: Exception) {}
            sp.edit().putBoolean("seeded", true).apply()
        }

        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        count = findViewById(R.id.count)
        emptyTitle = findViewById(R.id.empty_title)
        emptyMsg = findViewById(R.id.empty_msg)
        findViewById<EditText>(R.id.search).addTextChangedListener {
            query = it?.toString().orEmpty().trim()
            refresh()
        }
        findViewById<ExtendedFloatingActionButton>(R.id.fab).setOnClickListener {
            picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        }
        findViewById<TextView>(R.id.app_menu).setOnClickListener { anchor -> showAppMenu(anchor) }
        findViewById<TextView>(R.id.filter_sort).setOnClickListener { anchor -> showFilterSortMenu(anchor) }
        findViewById<View>(R.id.nav_dashboard).setOnClickListener {
            query = ""; findViewById<EditText>(R.id.search).setText(""); showFavoritesOnly = false; categoryFilter = "All categories"; refresh()
        }
        findViewById<View>(R.id.nav_installed).setOnClickListener {
            findViewById<EditText>(R.id.search).requestFocus(); refresh()
        }
        findViewById<View>(R.id.nav_import).setOnClickListener { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
        findViewById<View>(R.id.nav_activity).setOnClickListener { showDiagnostics() }
        findViewById<View>(R.id.nav_settings).setOnClickListener { showSettings() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        Updater.autoCheck(this, store) { refresh() }
    }


    private fun showAppMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "Storage & data")
        menu.menu.add(0, 2, 1, "About Jabra Hub")
        menu.menu.add(0, 3, 2, "Diagnostics & support")
        menu.menu.add(0, 4, 3, "Developer tools · API contract")
        menu.menu.add(0, 5, 4, "Scan copied text (clipboard)")
        menu.menu.add(0, 6, 5, if (BubbleService.isEnabled(this)) "Floating bubble: turn OFF" else "Floating bubble: turn ON")
        menu.menu.add(0, 7, 6, "Check for app update")
        menu.menu.add(0, 8, 7, "Plugin store · updates")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showStorage()
                2 -> {
                    val version = try {
                        packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
                    } catch (_: Exception) { "unknown" }
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Jabra Extension Hub")
                        .setMessage("Version $version\n\nA modular Android plugin hub. Import ZIP plugins, search installed tools, view plugin details, and export plugins for backup or transfer.\n\nPlugin code runs in Android WebView. Extensions should feature-detect the Jabra API and use documented fallbacks when a capability is unavailable.")
                        .setPositiveButton("OK", null).show()
                }
                3 -> showDiagnostics()
                4 -> showApiContract()
                7 -> Updater.checkApp(this, false)
                8 -> Updater.checkPlugins(this, store, false) { refresh() }
                5 -> startActivity(Intent(this, IntakeActivity::class.java).setAction(ChapterIntake.ACTION_SCAN_CLIPBOARD))
                6 -> {
                    val on = !BubbleService.isEnabled(this)
                    if (BubbleService.toggle(this, on)) {
                        Toast.makeText(this, if (on) "Bubble on. Copy text, then tap J." else "Bubble off.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Allow \"Display over other apps\" for Jabra, come back, then turn the bubble on again.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            true
        }
        menu.show()
    }


    private fun showSettings() {
        val themes = arrayOf("System default", "Dark", "Light")
        val prefs = getSharedPreferences("hub_settings", MODE_PRIVATE)
        val current = prefs.getInt("theme_choice", 0).coerceIn(0, 2)
        MaterialAlertDialogBuilder(this)
            .setTitle("Settings")
            .setSingleChoiceItems(themes, current) { dialog, which ->
                prefs.edit().putInt("theme_choice", which).apply()
                val msg = when (which) {
                    1 -> "Dark theme preference saved. Full palette support depends on the installed theme resources."
                    2 -> "Light theme preference saved. Full palette support depends on the installed theme resources."
                    else -> "System theme preference saved."
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
            .setNeutralButton("Storage & data") { _, _ -> showStorage() }
            .setNegativeButton("Close", null)
            .setPositiveButton("Diagnostics") { _, _ -> showDiagnostics() }
            .show()
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

    private fun exportPlugin(id: String, uri: Uri) {
        Thread {
            try {
                store.exportPlugin(id, uri)
                AppDiagnostics.record(this, "Plugin export succeeded: $id")
                runOnUiThread { Toast.makeText(this, "Plugin exported successfully", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                AppDiagnostics.record(this, "Plugin export failed: $id", e)
                runOnUiThread { Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun safeFileName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('_')
        .ifEmpty { "plugin" }

    private fun favoriteIds(): Set<String> =
        getSharedPreferences("hub_favorites", MODE_PRIVATE).getStringSet("ids", emptySet<String>())?.toSet() ?: emptySet()

    private fun setFavorite(id: String, favorite: Boolean) {
        val prefs = getSharedPreferences("hub_favorites", MODE_PRIVATE)
        val ids = favoriteIds().toMutableSet()
        if (favorite) ids.add(id) else ids.remove(id)
        prefs.edit().putStringSet("ids", ids).apply()
        refresh()
    }

    private fun showFilterSortMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, if (showFavoritesOnly) "Show all plugins" else "Show favourites only")
        menu.menu.add(0, 2, 1, "Sort: Name A–Z")
        menu.menu.add(0, 3, 2, "Sort: Name Z–A")
        menu.menu.add(0, 4, 3, "Sort: Favourites first")
        menu.menu.add(0, 5, 4, "Category: All categories")
        store.list().map { it.category }.distinct().sorted().forEachIndexed { index, category ->
            menu.menu.add(1, 100 + index, 5 + index, "Category: $category")
        }
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showFavoritesOnly = !showFavoritesOnly
                2 -> sortMode = "name_asc"
                3 -> sortMode = "name_desc"
                4 -> sortMode = "favorites"
                5 -> categoryFilter = "All categories"
                else -> if (item.groupId == 1) categoryFilter = item.title.toString().removePrefix("Category: ")
            }
            getSharedPreferences("hub_manager", MODE_PRIVATE).edit()
                .putBoolean("favorites_only", showFavoritesOnly)
                .putString("category_filter", categoryFilter)
                .putString("sort_mode", sortMode)
                .apply()
            refresh()
            true
        }
        menu.show()
    }

    private fun refresh() {
        val all = store.list()
        val favorites = favoriteIds()
        var plugins = all.filter { p ->
            (query.isEmpty() || p.name.contains(query, ignoreCase = true) || p.description.contains(query, ignoreCase = true) || p.id.contains(query, ignoreCase = true)) &&
                (!showFavoritesOnly || favorites.contains(p.id)) &&
                (categoryFilter == "All categories" || p.category.equals(categoryFilter, ignoreCase = true))
        }
        plugins = when (sortMode) {
            "name_desc" -> plugins.sortedByDescending { it.name.lowercase(Locale.ROOT) }
            "favorites" -> plugins.sortedWith(compareByDescending<Plugin> { favorites.contains(it.id) }.thenBy { it.name.lowercase(Locale.ROOT) })
            else -> plugins.sortedBy { it.name.lowercase(Locale.ROOT) }
        }
        findViewById<TextView>(R.id.filter_sort).text = buildString {
            append(if (showFavoritesOnly) "Favourites" else "All plugins")
            if (categoryFilter != "All categories") append(" · ").append(categoryFilter)
            append(" · ").append(when (sortMode) { "name_desc" -> "Z–A"; "favorites" -> "Favourites first"; else -> "A–Z" })
            append("  ▾")
        }
        list.removeAllViews()
        empty.visibility = if (plugins.isEmpty()) View.VISIBLE else View.GONE
        if (all.isEmpty()) {
            emptyTitle.text = "No plugins yet"
            emptyMsg.text = "Tap Import plugin and choose a plugin .zip file."
        } else {
            emptyTitle.text = "No matches"
            emptyMsg.text = "Try a different search."
        }
        count.text = if (plugins.size == all.size) "${all.size} installed" else "${plugins.size} of ${all.size} shown"
        for (p in plugins) {
            val v = layoutInflater.inflate(R.layout.item_plugin, list, false)
            v.findViewById<TextView>(R.id.icon).text = p.icon
            v.findViewById<TextView>(R.id.name).text = p.name
            val d = v.findViewById<TextView>(R.id.desc)
            d.text = p.description
            d.visibility = if (p.description.isEmpty()) View.GONE else View.VISIBLE
            v.findViewById<TextView>(R.id.meta).text = "v${p.version} · ${p.category}" + if (favorites.contains(p.id)) " · ★" else ""
            v.setOnClickListener { open(p) }
            v.findViewById<View>(R.id.more).setOnClickListener { anchor -> showMenu(anchor, p) }
            list.addView(v)
        }
    }

    private fun showMenu(anchor: View, p: Plugin) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "Open")
        menu.menu.add(0, 2, 1, "Details")
        menu.menu.add(0, 3, 2, "Export ZIP")
        menu.menu.add(0, 4, 3, "Remove")
        menu.menu.add(0, 5, 4, if (favoriteIds().contains(p.id)) "Remove from favourites" else "Add to favourites")
        if (store.hasRollback(p.id)) {
            menu.menu.add(0, 6, 5, "Rollback to v${store.rollbackVersion(p.id) ?: "previous"}")
        }
        if (Floater.supports(this, p.id)) {
            menu.menu.add(0, 7, 6, if (Floater.isEnabled(this, p.id)) "Hide from floating bubble" else "Show in floating bubble")
        }
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> open(p)
                2 -> showDetails(p)
                3 -> {
                    exportPluginId = p.id
                    exportPicker.launch("${safeFileName(p.name)}_v${safeFileName(p.version)}.zip")
                }
                4 -> confirmRemove(p)
                5 -> setFavorite(p.id, !favoriteIds().contains(p.id))
                6 -> confirmRollback(p)
                7 -> toggleFloater(p)
            }
            true
        }
        menu.show()
    }

    private fun toggleFloater(p: Plugin) {
        if (Floater.isEnabled(this, p.id)) {
            Floater.setEnabled(this, p.id, false)
            Toast.makeText(this, "Removed from the floating bubble", Toast.LENGTH_SHORT).show()
        } else {
            Floater.askEnable(this, p.id)
        }
    }

    private fun confirmRollback(p: Plugin) {
        val previousVersion = store.rollbackVersion(p.id) ?: "previous"
        MaterialAlertDialogBuilder(this)
            .setTitle("Rollback ${p.name}?")
            .setMessage("Switch from version ${p.version} to version $previousVersion? The current version will be retained as the next rollback point. Plugin-owned saved data will not be erased.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Rollback") { _, _ ->
                try {
                    val rolledBack = store.rollbackPlugin(p.id)
                    AppDiagnostics.record(this, "Plugin rollback succeeded: ${p.id} -> v${rolledBack.version}")
                    refresh()
                    Toast.makeText(this, "Rolled back to v${rolledBack.version}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    AppDiagnostics.record(this, "Plugin rollback failed: ${p.id}", e)
                    Toast.makeText(this, "Rollback failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun showDetails(p: Plugin) {
        val size = Formatter.formatShortFileSize(this, store.sizeOf(p.id))
        val savedDataSize = Formatter.formatShortFileSize(this, store.savedDataSize(p.id))
        val text = buildString {
            if (p.description.isNotEmpty()) append(p.description).append("\n\n")
            append("ID: ").append(p.id).append('\n')
            append("Version: ").append(p.version).append('\n')
            append("Category: ").append(p.category).append('\n')
            append("Minimum Jabra API: v").append(p.minJabraApiVersion).append('\n')
            if (p.requiredCapabilities.isNotEmpty()) append("Required capabilities: ").append(p.requiredCapabilities.joinToString(", ")).append('\n')
            append("Entry: ").append(p.entry).append('\n')
            append("Plugin files: ").append(size).append('\n')
            append("Saved data: ").append(savedDataSize)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(p.name)
            .setMessage(text)
            .setPositiveButton("Open") { _, _ -> open(p) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun open(p: Plugin) {
        startActivity(
            Intent(this, PluginActivity::class.java)
                .putExtra("id", p.id)
                .putExtra("name", p.name)
                .putExtra("entry", p.entry)
        )
    }

    private fun confirmRemove(p: Plugin) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove ${p.name}?")
            .setMessage("Remove the plugin files? Saved data will be kept so it can be restored if you reinstall the same plugin. Choose ‘Erase all data’ only if you want to permanently delete its saved work too.")
            .setPositiveButton("Remove plugin") { _, _ ->
                store.remove(p.id)
                refresh()
            }
            .setNeutralButton("Erase all data") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Permanently erase ${p.name} data?")
                    .setMessage("This deletes the plugin's saved local data and preferences. Export a backup first if you may need them.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Erase data") { _, _ ->
                        store.remove(p.id)
                        store.erasePluginData(p.id)
                        refresh()
                    }.show()
            }
            .setNegativeButton("Keep plugin", null)
            .show()
    }

    private fun importZip(uri: Uri) {
        Toast.makeText(this, "Importing…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val p = store.importZip(uri)
                AppDiagnostics.record(this, "Plugin import/install succeeded: ${p.id} v${p.version}")
                runOnUiThread {
                    refresh()
                    Toast.makeText(this, "Installed ${p.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                AppDiagnostics.record(this, "Plugin import/install failed", e)
                runOnUiThread {
                    Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
