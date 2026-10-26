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
            picker.launch(arrayOf("*/*"))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

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
        menu.menu.add(0, 3, 2, "Remove")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> open(p)
                2 -> showDetails(p)
                else -> confirmRemove(p)
            }
            true
        }
        menu.show()
    }

    private fun showDetails(p: Plugin) {
        val size = Formatter.formatShortFileSize(this, store.sizeOf(p.id))
        val text = buildString {
            if (p.description.isNotEmpty()) append(p.description).append("\n\n")
            append("ID: ").append(p.id).append('\n')
            append("Version: ").append(p.version).append('\n')
            append("Entry: ").append(p.entry).append('\n')
            append("Size: ").append(size)
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
            .setMessage("The plugin and its files will be deleted from this app.")
            .setPositiveButton("Remove") { _, _ ->
                store.remove(p.id)
                refresh()
            }
            .setNegativeButton("Cancel", null)
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
