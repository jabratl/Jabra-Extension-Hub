package com.jabra.hub

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.text.format.Formatter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var store: PluginStore
    private lateinit var list: LinearLayout
    private lateinit var empty: View
    private lateinit var count: TextView
    private lateinit var emptyTitle: TextView
    private lateinit var emptyMsg: TextView
    private lateinit var homePage: View
    private lateinit var pluginsPage: View
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var nav: BottomNavigationView
    private lateinit var homeCount: TextView
    private lateinit var backToHome: OnBackPressedCallback
    private var tab = TAB_HOME
    private var syncingNav = false
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
        homePage = findViewById(R.id.home_page)
        pluginsPage = findViewById(R.id.plugins_page)
        homeCount = findViewById(R.id.home_count)
        fab = findViewById(R.id.fab)
        nav = findViewById(R.id.bottom_navigation)
        fab.setOnClickListener { pickPluginZip() }
        findViewById<View>(R.id.app_menu).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.filter_sort).setOnClickListener { anchor -> showFilterSortMenu(anchor) }
        findViewById<View>(R.id.home_pill).setOnClickListener { showTab(TAB_PLUGINS, focusSearch = true) }
        findViewById<View>(R.id.home_import).setOnClickListener { pickPluginZip() }
        findViewById<View>(R.id.home_scan).setOnClickListener {
            startActivity(Intent(this, IntakeActivity::class.java).setAction(ChapterIntake.ACTION_SCAN_CLIPBOARD))
        }
        nav.setOnItemSelectedListener { item ->
            // Selecting an item from showTab() must not start showTab() again.
            if (syncingNav) return@setOnItemSelectedListener true
            when (item.itemId) {
                R.id.nav_home -> { showTab(TAB_HOME); true }
                R.id.nav_plugins -> { showTab(TAB_PLUGINS); true }
                // Downloads and Browser are their own screens, so the selected tab does not move.
                R.id.nav_downloads -> { startActivity(Intent(this, DownloadsActivity::class.java)); false }
                R.id.nav_browser -> { startActivity(Intent(this, BrowserActivity::class.java)); false }
                else -> false
            }
        }
        backToHome = onBackPressedDispatcher.addCallback(this, false) { showTab(TAB_HOME) }
        showTab(savedInstanceState?.getInt("tab", TAB_HOME) ?: TAB_HOME)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    private fun pickPluginZip() {
        picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
    }

    private fun showTab(t: Int, focusSearch: Boolean = false) {
        tab = t
        val plugins = t == TAB_PLUGINS
        homePage.visibility = if (plugins) View.GONE else View.VISIBLE
        pluginsPage.visibility = if (plugins) View.VISIBLE else View.GONE
        fab.visibility = if (plugins) View.VISIBLE else View.GONE
        backToHome.isEnabled = plugins
        val id = if (plugins) R.id.nav_plugins else R.id.nav_home
        if (nav.selectedItemId != id) {
            syncingNav = true
            nav.selectedItemId = id
            syncingNav = false
        }
        if (plugins && focusSearch) {
            val search = findViewById<EditText>(R.id.search)
            search.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        Updater.autoCheck(this, store) { refresh() }
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
        menu.menu.add(0, 1, 0, if (showFavoritesOnly) "Show all plugins" else "Show pinned only")
        menu.menu.add(0, 2, 1, "Sort: Name A–Z")
        menu.menu.add(0, 3, 2, "Sort: Name Z–A")
        menu.menu.add(0, 4, 3, "Sort: Pinned first")
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
            append(if (showFavoritesOnly) "Pinned" else "All plugins")
            if (categoryFilter != "All categories") append(" · ").append(categoryFilter)
            append(" · ").append(when (sortMode) { "name_desc" -> "Z–A"; "favorites" -> "Pinned first"; else -> "A–Z" })
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
        homeCount.text = when (all.size) { 0 -> "No plugins installed yet"; 1 -> "1 plugin installed"; else -> "${all.size} plugins installed" }
        count.text = if (plugins.size == all.size) "${all.size} installed" else "${plugins.size} of ${all.size} shown"
        for (p in plugins) {
            val v = layoutInflater.inflate(R.layout.item_plugin, list, false)
            bindTile(v.findViewById(R.id.icon), v.findViewById(R.id.icon_img), p)
            v.findViewById<TextView>(R.id.name).text = p.name
            val d = v.findViewById<TextView>(R.id.desc)
            d.text = p.description
            d.visibility = if (p.description.isEmpty()) View.GONE else View.VISIBLE
            v.findViewById<TextView>(R.id.meta).text = "v${p.version} · ${p.category}"
            val pinned = favorites.contains(p.id)
            val pin = v.findViewById<ImageButton>(R.id.pin)
            pin.setImageResource(if (pinned) R.drawable.q_ic_star else R.drawable.q_ic_star_outline)
            pin.imageTintList = ColorStateList.valueOf(Q.color(this, if (pinned) R.color.q_accent else R.color.q_text_sub))
            pin.contentDescription = (if (pinned) "Unpin " else "Pin ") + p.name
            pin.setOnClickListener { Q.tick(it); setFavorite(p.id, !pinned) }
            v.setOnClickListener { open(p) }
            v.setOnLongClickListener { showMenu(v, p); true }
            v.findViewById<View>(R.id.more).setOnClickListener { anchor -> showMenu(anchor, p) }
            list.addView(v)
        }
        refreshHome(all, favorites)
    }

    /** Letter tile (quiet accent colour) or the plugin's own png/webp/jpg icon. */
    private fun bindTile(letter: TextView, image: ImageView, p: Plugin) {
        val bmp = store.iconFile(p)?.let { try { BitmapFactory.decodeFile(it.path) } catch (_: Throwable) { null } }
        if (bmp != null) {
            image.setImageBitmap(bmp)
            image.visibility = View.VISIBLE
            letter.visibility = View.GONE
        } else {
            letter.text = tileLetter(p.name)
            letter.visibility = View.VISIBLE
            image.visibility = View.GONE
        }
    }

    private fun tileLetter(name: String): String {
        var i = 0
        while (i < name.length) {
            val cp = name.codePointAt(i)
            if (Character.isLetterOrDigit(cp)) return String(Character.toChars(cp)).uppercase(Locale.ROOT)
            i += Character.charCount(cp)
        }
        return "J"
    }

    private fun refreshHome(all: List<Plugin>, favorites: Set<String>) {
        val last = store.lastUsedId()?.let { id -> all.firstOrNull { it.id == id } }
        val cont = findViewById<View>(R.id.home_continue)
        cont.visibility = if (last == null) View.GONE else View.VISIBLE
        if (last != null) {
            bindTile(findViewById(R.id.continue_icon), findViewById(R.id.continue_icon_img), last)
            findViewById<TextView>(R.id.continue_name).text = last.name
            cont.contentDescription = "Continue with ${last.name}"
            cont.setOnClickListener { open(last) }
        }
        val pinned = all.filter { favorites.contains(it.id) }
        findViewById<View>(R.id.home_pins_section).visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.home_pins_hint).visibility = if (pinned.isEmpty()) View.VISIBLE else View.GONE
        val grid = findViewById<LinearLayout>(R.id.home_pins)
        grid.removeAllViews()
        val cols = ((resources.configuration.screenWidthDp - 40) / 96).coerceIn(3, 6)
        for (chunk in pinned.chunked(cols)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (p in chunk) {
                val v = layoutInflater.inflate(R.layout.item_pin, row, false)
                bindTile(v.findViewById(R.id.icon), v.findViewById(R.id.icon_img), p)
                v.findViewById<TextView>(R.id.name).text = p.name
                v.setOnClickListener { open(p) }
                v.setOnLongClickListener { showMenu(v, p); true }
                row.addView(v)
            }
            repeat(cols - chunk.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            grid.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun showMenu(anchor: View, p: Plugin) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "Open")
        menu.menu.add(0, 2, 1, "Details")
        menu.menu.add(0, 3, 2, "Export ZIP")
        menu.menu.add(0, 4, 3, "Remove")
        menu.menu.add(0, 5, 4, if (favoriteIds().contains(p.id)) "Unpin from Home" else "Pin to Home")
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

    private companion object {
        const val TAB_HOME = 0
        const val TAB_PLUGINS = 1
    }
}
