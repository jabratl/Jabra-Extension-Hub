package com.jabra.hub

import android.app.Activity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Media settings dialog (Hub 0.9.39): find media on pages, library names, default stream quality, wait between batch downloads.
 * Saved in MediaPrefs. Opened from the Browser menu > Media settings. Call MediaSettings.show(activity) from anywhere.
 */
object MediaSettings {
    fun show(act: Activity, onChanged: () -> Unit = {}) {
        val d = act.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)

        fun note(t: String) = TextView(act).apply {
            text = t
            textSize = 12f
            setTextColor(Q.color(act, R.color.q_text_sub))
            setPadding(dp(4), 0, dp(4), dp(10))
        }
        fun label(t: String) = TextView(act).apply {
            text = t
            textSize = 14f
            setTextColor(Q.color(act, R.color.q_text))
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        val lp = { LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) }

        val detect = SwitchMaterial(act)
        detect.text = "Find media on pages"
        detect.isChecked = MediaPrefs.detect(act)
        box.addView(detect, lp())
        box.addView(note("Off: the found list stays empty."))

        val lib = SwitchMaterial(act)
        lib.text = "Library names"
        lib.isChecked = MediaPrefs.library(act)
        box.addView(lib, lp())
        box.addView(note("Plex / Jellyfin / Emby / Kodi style, e.g. \"Show - S01E05\", when the page title says which episode. Off: names as before."))

        // quality: Best + common heights (+ the saved one if it is not in the list)
        val saved = MediaPrefs.hlsHeight(act)
        val heights = ArrayList<Int>(listOf(0, 1080, 720, 480, 360))
        if (saved !in heights) heights.add(1, saved)
        val q = Spinner(act)
        q.adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, heights.map { if (it == 0) "Best" else "${it}p" })
        q.setSelection(heights.indexOf(saved).coerceAtLeast(0))
        box.addView(label("Stream quality"), lp())
        box.addView(q, lp())
        box.addView(note("Used by Select > Download and episode lists (the best quality up to this height)."))

        val gaps = listOf(180, 1000, 3000)
        val names = listOf("Short (0.2 s)", "1 s", "3 s")
        val g = Spinner(act)
        g.adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, names)
        g.setSelection(gaps.indexOf(MediaPrefs.delayMs(act)).coerceAtLeast(0))
        box.addView(label("Wait between downloads of one batch"), lp())
        box.addView(g, lp())
        box.addView(note("A longer wait is gentler on sites that block many requests at once."))

        MaterialAlertDialogBuilder(act)
            .setTitle("Media settings")
            .setView(android.widget.ScrollView(act).also { it.addView(box) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                MediaPrefs.setDetect(act, detect.isChecked)
                MediaPrefs.setLibrary(act, lib.isChecked)
                MediaPrefs.setHlsHeight(act, heights[q.selectedItemPosition.coerceIn(0, heights.size - 1)])
                MediaPrefs.setDelayMs(act, gaps[g.selectedItemPosition.coerceIn(0, gaps.size - 1)])
                onChanged()
            }
            .show()
    }
}
