package com.jabra.hub

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/**
 * Built-in media player for links the browser (or a plugin) found.
 * Plays direct files and HLS, sends the page's Referer / cookies / User-Agent, remembers nothing.
 * Extras: url, referer, title, ua.
 */
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private var speedIndex = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url")
        if (url.isNullOrBlank() || !(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("content://") || url.startsWith("file://"))) {
            finish()
            return
        }
        val referer = intent.getStringExtra("referer") ?: ""
        val title = intent.getStringExtra("title") ?: ""
        val ua = intent.getStringExtra("ua")
            ?: android.webkit.WebSettings.getDefaultUserAgent(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        val view = PlayerView(this)
        root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Top bar: back, title, speed.
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setBackgroundColor(Color.parseColor("#99000000"))
        val pad = (8 * resources.displayMetrics.density).toInt()
        top.setPadding(pad, pad, pad, pad)

        val back = TextView(this)
        back.text = "‹"
        back.textSize = 30f
        back.setTextColor(Color.WHITE)
        back.setPadding(pad * 2, 0, pad * 2, 0)
        back.setOnClickListener { finish() }
        top.addView(back)

        val name = TextView(this)
        name.text = title.ifBlank { (android.net.Uri.parse(url).lastPathSegment ?: url) }
        name.setTextColor(Color.WHITE)
        name.textSize = 15f
        name.maxLines = 1
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        top.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val speed = TextView(this)
        speed.text = "1x"
        speed.textSize = 15f
        speed.setTextColor(Color.parseColor("#FF8A5B"))
        speed.setPadding(pad * 2, pad, pad * 2, pad)
        top.addView(speed)

        root.addView(
            top,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP)
        )
        setContentView(root)

        val headers = HashMap<String, String>()
        if (referer.isNotEmpty()) headers["Referer"] = referer
        CookieManager.getInstance().getCookie(url)?.let { headers["Cookie"] = it }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)

        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .build()
        player = p
        view.player = p

        view.setControllerVisibilityListener(object : PlayerView.ControllerVisibilityListener {
            override fun onVisibilityChanged(visibility: Int) {
                top.visibility = if (visibility == View.VISIBLE) View.VISIBLE else View.GONE
            }
        })

        speed.setOnClickListener {
            speedIndex = (speedIndex + 1) % speeds.size
            p.setPlaybackSpeed(speeds[speedIndex])
            val s = speeds[speedIndex]
            speed.text = (if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString()) + "x"
        }

        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    "Cannot play this: ${error.errorCodeName}. Encrypted streams and some formats are not supported.",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.playWhenReady = true
    }

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
