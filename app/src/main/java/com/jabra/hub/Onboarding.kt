package com.jabra.hub

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog

/** H7b: one-time welcome sheet on a fresh install (never shown to people who already had the app). */
object Onboarding {
    private const val PREFS = "hub"
    private const val KEY = "onboarded"

    fun showOnce(a: Activity) {
        val sp = a.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        if (sp.getBoolean(KEY, false)) return
        val done = { sp.edit().putBoolean(KEY, true).apply() }

        fun dp(v: Int) = Q.dp(a, v)
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(24))
        }
        root.addView(TextView(a).apply {
            text = "Welcome to Jabra Hub"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Q.color(a, R.color.q_text))
        })
        root.addView(TextView(a).apply {
            text = "Three things to know."
            textSize = 14f
            setTextColor(Q.color(a, R.color.q_text_sub))
            setPadding(0, dp(4), 0, dp(12))
        })

        fun row(icon: Int, title: String, sub: String) {
            val r = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            r.addView(ImageView(a).apply {
                setImageDrawable(Q.icon(a, icon))
                imageTintList = android.content.res.ColorStateList.valueOf(Q.color(a, R.color.q_accent_text))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(16) })
            val t = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            t.addView(TextView(a).apply {
                text = title
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Q.color(a, R.color.q_text))
            })
            t.addView(TextView(a).apply {
                text = sub
                textSize = 14f
                setTextColor(Q.color(a, R.color.q_text_sub))
            })
            r.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            root.addView(r)
        }
        row(R.drawable.q_ic_apps, "Plugins", "Import a plugin .zip and run it natively on this phone.")
        row(R.drawable.q_ic_globe, "Browser", "Ad blocker, per-site settings and a found list that catches videos, streams and subtitles. Tune it in Settings > Media settings.")
        row(R.drawable.q_ic_copy, "Scan copied text", "Copy some text, then send it to a plugin from the home screen or the bubble.")

        val sheet = BottomSheetDialog(a)
        root.addView(Button(a).apply {
            text = "Get started"
            isAllCaps = false
            textSize = 16f
            setTextColor(Q.color(a, R.color.q_on_accent))
            setBackgroundResource(R.drawable.q_btn_accent)
            setOnClickListener { Q.tick(it); done(); sheet.dismiss() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(16) })

        sheet.setContentView(root)
        sheet.setOnShowListener {
            sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundResource(R.drawable.q_sheet_bg)
        }
        sheet.setOnDismissListener { done() }
        sheet.show()
    }
}
