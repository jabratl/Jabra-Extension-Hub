package com.jabra.hub

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Lists the downloads of this app session with progress, cancel and play. Built in code (no layout file). */
class DownloadsActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout
    private lateinit var empty: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 800)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(getColor(R.color.br_bg))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(getColor(R.color.br_surface))
        bar.setPadding(dp(8), dp(8), dp(8), dp(8))

        val back = TextView(this)
        back.text = "‹"
        back.textSize = 28f
        back.gravity = Gravity.CENTER
        back.setTextColor(getColor(R.color.br_text))
        back.setOnClickListener { finish() }
        bar.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))

        val title = TextView(this)
        title.text = "Downloads"
        title.textSize = 18f
        title.setTextColor(getColor(R.color.br_text))
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val clear = TextView(this)
        clear.text = "Clear finished"
        clear.textSize = 13f
        clear.setTextColor(getColor(R.color.br_accent))
        clear.setPadding(dp(12), dp(10), dp(12), dp(10))
        clear.setOnClickListener { DlJobs.clearFinished(); render() }
        bar.addView(clear)
        root.addView(bar)

        val scroll = ScrollView(this)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(14), dp(14), dp(14), dp(14))
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        empty = TextView(this)
        empty.text = "No downloads yet.\nFiles are saved to Downloads/JabraHub."
        empty.setTextColor(getColor(R.color.br_sub))
        empty.gravity = Gravity.CENTER
        empty.setPadding(dp(20), dp(40), dp(20), dp(20))

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

    private fun render() {
        list.removeAllViews()
        val jobs = DlJobs.all()
        if (jobs.isEmpty()) {
            list.addView(empty)
            return
        }
        for (j in jobs) list.addView(row(j))
    }

    private fun row(j: DlJob): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.br_chip)
        card.setPadding(dp(14), dp(12), dp(14), dp(12))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(10)
        card.layoutParams = lp

        val name = TextView(this)
        name.text = "[" + j.kind.uppercase() + "]  " + j.title
        name.setTextColor(getColor(R.color.br_text))
        name.textSize = 14f
        name.maxLines = 2
        card.addView(name)

        val st = TextView(this)
        st.text = j.text
        st.textSize = 12f
        st.setTextColor(
            when (j.state) {
                "done" -> Color.parseColor("#5EEAD4")
                "failed" -> Color.parseColor("#FF6B6B")
                else -> getColor(R.color.br_sub)
            }
        )
        st.setPadding(0, dp(4), 0, dp(4))
        card.addView(st)

        if (j.active) {
            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
            pb.max = 100
            if (j.percent >= 0) pb.progress = j.percent else pb.isIndeterminate = true
            pb.progressTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.br_accent))
            card.addView(pb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)))
        }

        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.setPadding(0, dp(8), 0, 0)
        if (j.active) {
            actions.addView(button("Cancel") { j.cancelled = true; j.text = "Cancelling..." })
        }
        if (j.state == "done" && j.uri.isNotEmpty()) {
            if (j.kind == "hls" || j.kind == "video" || j.kind == "audio") {
                actions.addView(button("▶ Play") {
                    startActivity(Intent(this, PlayerActivity::class.java).putExtra("url", j.uri).putExtra("title", j.title))
                })
            }
            actions.addView(button("Open with…") { openExternal(j) })
        }
        if (actions.childCount > 0) card.addView(actions)
        return card
    }

    private fun button(label: String, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = label
        b.textSize = 13f
        b.setTextColor(Color.parseColor("#1A1020"))
        b.setBackgroundResource(R.drawable.br_chip_accent)
        b.setPadding(dp(16), dp(8), dp(16), dp(8))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.rightMargin = dp(8)
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
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
}
