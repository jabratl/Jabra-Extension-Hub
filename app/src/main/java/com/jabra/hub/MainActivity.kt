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
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }


    private fun showAppMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "Storage & data")
        menu.menu.add(0, 2, 1, "About Jabra Hub")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showStorage()
                2 -> MaterialAlertDialogBuilder(this)
                    .setTitle("Jabra Extension Hub")
                    .setMessage("Version 0.3.5\n\nA modular Android plugin hub. Import ZIP plugins, search installed tools, view plugin details, and export plugins for backup or transfer.\n\nPlugin code runs in Android WebView. Features requiring native Android support may need a Hub update.")
                    .setPositiveButton("OK", null).show()
            }
            true
        }
        menu.show()
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
                runOnUiThread { Toast.makeText(this, "Plugin exported successfully", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun safeFileName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('_')
        .ifEmpty { "plugin" }

    private fun refresh() {
        val all = store.list()
        val plugins = if (query.isEmpty()) all else all.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
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
        count.text = if (query.isEmpty()) "${all.size} installed" else "${plugins.size} of ${all.size} shown"
        for (p in plugins) {
            val v = layoutInflater.inflate(R.layout.item_plugin, list, false)
            v.findViewById<TextView>(R.id.icon).text = p.icon
            v.findViewById<TextView>(R.id.name).text = p.name
            val d = v.findViewById<TextView>(R.id.desc)
            d.text = p.description
            d.visibility = if (p.description.isEmpty()) View.GONE else View.VISIBLE
            v.findViewById<TextView>(R.id.meta).text = "v${p.version}"
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
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> open(p)
                2 -> showDetails(p)
                3 -> {
                    exportPluginId = p.id
                    exportPicker.launch("${safeFileName(p.name)}_v${safeFileName(p.version)}.zip")
                }
                else -> confirmRemove(p)
            }
            true
        }
        menu.show()
    }

    private fun showDetails(p: Plugin) {
        val size = Formatter.formatShortFileSize(this, store.sizeOf(p.id))
        val savedDataSize = Formatter.formatShortFileSize(this, store.savedDataSize(p.id))
        val text = buildString {
            if (p.description.isNotEmpty()) append(p.description).append("\n\n")
            append("ID: ").append(p.id).append('\n')
            append("Version: ").append(p.version).append('\n')
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
                runOnUiThread {
                    refresh()
                    Toast.makeText(this, "Installed ${p.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
