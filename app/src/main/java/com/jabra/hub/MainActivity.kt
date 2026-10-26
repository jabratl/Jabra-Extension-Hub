package com.jabra.hub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var store: PluginStore
    private lateinit var list: LinearLayout
    private lateinit var empty: View
    private lateinit var count: TextView

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
        findViewById<ExtendedFloatingActionButton>(R.id.fab).setOnClickListener {
            picker.launch(arrayOf("*/*"))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val plugins = store.list()
        list.removeAllViews()
        empty.visibility = if (plugins.isEmpty()) View.VISIBLE else View.GONE
        count.text = "${plugins.size} installed"
        for (p in plugins) {
            val v = layoutInflater.inflate(R.layout.item_plugin, list, false)
            v.findViewById<TextView>(R.id.icon).text = p.icon
            v.findViewById<TextView>(R.id.name).text = p.name
            val d = v.findViewById<TextView>(R.id.desc)
            d.text = p.description
            d.visibility = if (p.description.isEmpty()) View.GONE else View.VISIBLE
            v.findViewById<TextView>(R.id.meta).text = "v${p.version}"
            v.setOnClickListener { open(p) }
            v.findViewById<View>(R.id.remove).setOnClickListener { confirmRemove(p) }
            list.addView(v)
        }
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
