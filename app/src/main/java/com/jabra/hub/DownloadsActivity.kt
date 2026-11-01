package com.jabra.hub

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.google.android.material.appbar.MaterialToolbar

/**
 * Downloads (Quiet Pill H4). Filter chips, rows grouped by day, pause/resume, retry.
 * Built in code around item_download.xml. Rows are kept between refreshes, so a tap is never lost to a rebuild.
 */
class DownloadsActivity : AppCompatActivity() {

    private enum class Filter(val label: String) { ALL("All"), ACTIVE("Active"), DONE("Done"), FAILED("Failed") }

    private class Row(val view: View) {
        val tile: TextView = view.findViewById(R.id.dtile)
        val name: TextView = view.findViewById(R.id.dname)
        val status: TextView = view.findViewById(R.id.dstatus)
        val bar: ProgressBar = view.findViewById(R.id.dbar)
        val act1: ImageButton = view.findViewById(R.id.dact1)
        val act2: ImageButton = view.findViewById(R.id.dact2)
    }

    private lateinit var list: LinearLayout
    private lateinit var empty: TextView
    private val chips = HashMap<Filter, TextView>()
    private val rows = HashMap<Int, Row>()
    private var filter = Filter.ALL
    private var lastSig = ""
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 800)
        }
    }

    private fun dp(v: Int) = Q.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DlJobs.init(applicationContext)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(getColor(R.color.q_bg))

        val tb = MaterialToolbar(this)
        tb.setBackgroundColor(getColor(R.color.q_bg))
        tb.title = "Downloads"
        tb.setTitleTextColor(getColor(R.color.q_text))
        tb.setNavigationIcon(R.drawable.q_ic_back)
        tb.navigationContentDescription = "Back"
        tb.setNavigationOnClickListener { finish() }
        tb.overflowIcon = Q.icon(this, R.drawable.q_ic_more)
        tb.menu.add(0, MENU_CLEAR, 0, "Clear finished").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        tb.setOnMenuItemClickListener {
            if (it.itemId == MENU_CLEAR) { DlJobs.clearFinished(); lastSig = ""; render(); true } else false
        }
        root.addView(tb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        val chipScroll = HorizontalScrollView(this)
        chipScroll.isHorizontalScrollBarEnabled = false
        val chipRow = LinearLayout(this)
        chipRow.orientation = LinearLayout.HORIZONTAL
        chipRow.setPadding(dp(14), dp(4), dp(14), dp(8))
        for (f in Filter.values()) {
            val c = TextView(this)
            c.gravity = Gravity.CENTER
            c.minHeight = dp(40)
            c.setPadding(dp(16), 0, dp(16), 0)
            c.textSize = 13f
            c.setOnClickListener { filter = f; lastSig = ""; render() }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(8)
            chipRow.addView(c, lp)
            chips[f] = c
        }
        chipScroll.addView(chipRow)
        root.addView(chipScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(this)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(14), dp(4), dp(14), dp(14))
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        empty = TextView(this)
        empty.setTextColor(getColor(R.color.q_text_sub))
        empty.gravity = Gravity.CENTER
        empty.setPadding(dp(20), dp(48), dp(20), dp(20))

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    // ---------- list ----------

    private fun matches(j: DlJob, f: Filter) = when (f) {
        Filter.ALL -> true
        Filter.ACTIVE -> j.active
        Filter.DONE -> j.state == "done"
        Filter.FAILED -> j.state == "failed" || j.state == "cancelled"
    }

    private fun dayLabel(ts: Long): String = when {
        DateUtils.isToday(ts) -> "Today"
        DateUtils.isToday(ts + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        else -> DateUtils.formatDateTime(this, ts, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)
    }

    private fun render() {
        val all = DlJobs.all()
        updateChips(all)
        val shown = all.filter { matches(it, filter) }
        val sig = if (shown.isEmpty()) {
            "empty:" + filter.name
        } else {
            buildString {
                var day = ""
                for (j in shown) {
                    val d = dayLabel(j.ts)
                    if (d != day) { day = d; append("h:").append(d).append('|') }
                    append(j.id).append('|')
                }
            }
        }
        if (sig != lastSig) {
            lastSig = sig
            rebuild(all, shown)
        }
        for (j in shown) rows[j.id]?.let { bind(it, j) }
    }

    private fun updateChips(all: List<DlJob>) {
        for ((f, c) in chips) {
            val n = if (f == Filter.ACTIVE) all.count { it.active } else 0
            c.text = if (n > 0) "${f.label} ($n)" else f.label
            val on = f == filter
            c.setBackgroundResource(if (on) R.drawable.q_chip_accent else R.drawable.q_chip)
            c.setTextColor(getColor(if (on) R.color.q_accent_text else R.color.q_text_sub))
        }
    }

    private fun rebuild(all: List<DlJob>, shown: List<DlJob>) {
        list.removeAllViews()
        rows.keys.retainAll(all.map { it.id }.toSet())
        if (shown.isEmpty()) {
            empty.text = if (filter == Filter.ALL) "No downloads yet.\nFiles are saved to Downloads/JabraHub." else "Nothing here."
            list.addView(empty)
            return
        }
        var day = ""
        for (j in shown) {
            val d = dayLabel(j.ts)
            if (d != day) { day = d; list.addView(header(d)) }
            val r = rows.getOrPut(j.id) { Row(layoutInflater.inflate(R.layout.item_download, list, false)) }
            list.addView(r.view)
        }
    }

    private fun header(text: String): TextView {
        val h = TextView(this)
        h.text = text
        h.textSize = 12f
        h.setTypeface(h.typeface, android.graphics.Typeface.BOLD)
        h.setTextColor(getColor(R.color.q_text_sub))
        h.setPadding(dp(4), if (list.childCount == 0) dp(4) else dp(14), dp(4), dp(8))
        ViewCompat.setAccessibilityHeading(h, true)
        return h
    }

    // ---------- one row ----------

    private fun kindLabel(kind: String) = when (kind) {
        "hls" -> "HLS"
        "video" -> "VIDEO"
        "audio" -> "AUDIO"
        "subtitle" -> "SUB"
        else -> "FILE"
    }

    private fun pausedText(j: DlJob) = if (j.percent >= 0) "Paused · ${j.percent}%" else "Paused"

    private fun bind(r: Row, j: DlJob) {
        r.tile.text = kindLabel(j.kind)
        r.name.text = j.title
        val paused = j.paused && j.state == "running"
        r.status.text = when {
            paused -> pausedText(j)
            j.state == "cancelled" -> "Cancelled"
            else -> j.text
        }
        r.status.setTextColor(getColor(if (j.state == "failed") R.color.q_danger else R.color.q_text_sub))

        if (j.active) {
            r.bar.visibility = View.VISIBLE
            if (j.percent >= 0) { r.bar.isIndeterminate = false; r.bar.progress = j.percent } else r.bar.isIndeterminate = true
        } else {
            r.bar.visibility = View.GONE
        }

        when (j.state) {
            "queued" -> {
                setBtn(r.act1, 0, "", false, null)
                setBtn(r.act2, R.drawable.q_ic_close, "Cancel download", false) { cancel(j) }
            }
            "running" -> {
                if (j.paused) {
                    setBtn(r.act1, R.drawable.q_ic_play, "Resume download", true) { j.paused = false; render() }
                } else {
                    setBtn(r.act1, R.drawable.q_ic_pause, "Pause download", true) { j.paused = true; j.text = pausedText(j); render() }
                }
                setBtn(r.act2, R.drawable.q_ic_close, "Cancel download", false) { cancel(j) }
            }
            "done" -> {
                val playable = j.uri.isNotEmpty() && (j.kind == "hls" || j.kind == "video" || j.kind == "audio")
                if (playable) {
                    setBtn(r.act1, R.drawable.q_ic_play, "Play", true) {
                        startActivity(Intent(this, PlayerActivity::class.java).putExtra("url", j.uri).putExtra("title", j.title))
                    }
                } else setBtn(r.act1, 0, "", false, null)
                setBtn(r.act2, R.drawable.q_ic_more, "More options", false) { showMore(r.act2, j) }
            }
            else -> { // failed, cancelled
                val again = j.retry
                if (again != null) {
                    setBtn(r.act1, R.drawable.q_ic_refresh, "Try again", true) {
                        again()
                        DlJobs.remove(j)
                        Toast.makeText(this, "Download restarted", Toast.LENGTH_SHORT).show()
                        lastSig = ""
                        render()
                    }
                } else setBtn(r.act1, 0, "", false, null)
                setBtn(r.act2, R.drawable.q_ic_more, "More options", false) { showMore(r.act2, j) }
            }
        }
    }

    private fun setBtn(b: ImageButton, icon: Int, desc: String, accent: Boolean, onClick: (() -> Unit)?) {
        if (onClick == null) { b.visibility = View.GONE; return }
        b.visibility = View.VISIBLE
        b.setImageResource(icon)
        b.contentDescription = desc
        b.imageTintList = if (accent) ColorStateList.valueOf(getColor(R.color.q_accent)) else null
        b.setOnClickListener { onClick() }
    }

    private fun cancel(j: DlJob) {
        j.cancelled = true
        j.paused = false
        j.text = "Cancelling..."
        render()
    }

    private fun showMore(anchor: View, j: DlJob) {
        val pm = PopupMenu(this, anchor)
        if (j.state == "done" && j.uri.isNotEmpty()) pm.menu.add(0, MORE_OPEN, 0, "Open with…")
        pm.menu.add(0, MORE_REMOVE, 1, "Remove from list")
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                MORE_OPEN -> openExternal(j)
                MORE_REMOVE -> { DlJobs.remove(j); lastSig = ""; render() }
            }
            true
        }
        pm.show()
    }

    private fun openExternal(j: DlJob) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(j.uri), j.mime.ifEmpty { "*/*" })
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (_: Exception) {
            Toast.makeText(this, "No app can open this file. Find it in Downloads/JabraHub.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val MENU_CLEAR = 1
        private const val MORE_OPEN = 1
        private const val MORE_REMOVE = 2
    }
}
