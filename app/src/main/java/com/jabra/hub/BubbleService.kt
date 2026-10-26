package com.jabra.hub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Floating "J" bubble drawn over other apps (needs "Display over other apps").
 * It is a shared launcher for plugins: every plugin the user enabled gets a button.
 *  - no plugin enabled: tap shows a hint
 *  - one plugin enabled: tap runs it directly
 *  - several: tap opens a small tray of buttons
 * Drag the J to move it.
 */
@Suppress("DEPRECATION")
class BubbleService : Service() {
    private var wm: WindowManager? = null
    private var root: LinearLayout? = null
    private var tray: LinearLayout? = null
    private var bubbleView: TextView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var expanded = false
    private var shiftX = 0
    private var shiftY = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, notification())
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        if (root == null) addBubble()
        return START_STICKY
    }

    private fun notification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Floating bubble", NotificationManager.IMPORTANCE_MIN))
        }
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BubbleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return b.setSmallIcon(R.drawable.ic_add)
            .setContentTitle("Jabra bubble is on")
            .setContentText("Tap the J bubble to use your plugins' buttons.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Turn off", stop).build())
            .build()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun addBubble() {
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = manager
        val size = dp(52)
        val b = TextView(this).apply {
            text = "J"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#E85D9A")); setStroke(dp(2), Color.WHITE) }
            alpha = 0.92f
            elevation = dp(6).toFloat()
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
        val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(b)
            addView(t)
        }
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(220) }

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        b.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt(); params.y = startY + dy.toInt()
                        try { manager.updateViewLayout(r, params) } catch (_: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) onBubbleTap(); true }
                else -> false
            }
        }
        try {
            manager.addView(r, params)
            root = r; tray = t; bubbleView = b; lp = params
        } catch (_: Exception) { stopSelf() }
    }

    private fun onBubbleTap() {
        if (expanded) { collapse(); return }
        val list = Floater.entries(this)
        when {
            list.isEmpty() -> Toast.makeText(
                this,
                "No plugin uses the bubble yet. Open a plugin's menu in Jabra and choose Show in floating bubble.",
                Toast.LENGTH_LONG
            ).show()
            list.size == 1 -> Floater.trigger(this, list[0])
            else -> expand(list)
        }
    }

    private fun chip(e: FloaterEntry) = TextView(this).apply {
        text = "${e.icon}  ${e.label}"
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), 0, dp(14), 0)
        background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(e.color); setStroke(dp(1), Color.WHITE) }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { setMargins(dp(6), dp(5), dp(6), dp(5)) }
        setOnClickListener { collapse(); Floater.trigger(this@BubbleService, e) }
    }

    private fun expand(list: List<FloaterEntry>) {
        val r = root ?: return; val t = tray ?: return; val b = bubbleView ?: return
        val p = lp ?: return; val m = wm ?: return
        t.removeAllViews()
        list.forEach { t.addView(chip(it)) }
        t.visibility = View.VISIBLE
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        t.measure(spec, spec)
        val tw = t.measuredWidth; val th = t.measuredHeight
        val dm = resources.displayMetrics
        val size = dp(52)
        r.removeAllViews()
        if (p.x + size / 2 > dm.widthPixels / 2) {
            r.addView(t); r.addView(b); shiftX = tw; p.x -= tw
        } else {
            r.addView(b); r.addView(t); shiftX = 0
        }
        if (p.y + th < dm.heightPixels - dp(100)) {
            r.gravity = Gravity.TOP; shiftY = 0
        } else {
            r.gravity = Gravity.BOTTOM; shiftY = maxOf(0, th - size); p.y -= shiftY
        }
        expanded = true
        try { m.updateViewLayout(r, p) } catch (_: Exception) { }
    }

    private fun collapse() {
        val r = root ?: return; val t = tray ?: return; val b = bubbleView ?: return
        val p = lp ?: return; val m = wm ?: return
        t.visibility = View.GONE
        p.x += shiftX; p.y += shiftY
        shiftX = 0; shiftY = 0
        r.removeAllViews()
        r.addView(b); r.addView(t)
        r.gravity = Gravity.TOP
        expanded = false
        try { m.updateViewLayout(r, p) } catch (_: Exception) { }
    }

    override fun onDestroy() {
        try { root?.let { wm?.removeView(it) } } catch (_: Exception) { }
        root = null; tray = null; bubbleView = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "jabra_bubble"
        private const val NOTIF_ID = 4101
        const val ACTION_STOP = "com.jabra.hub.BUBBLE_STOP"
        private const val PREFS = "hub_prefs"

        fun isEnabled(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("bubble", false)
        fun setEnabled(ctx: Context, on: Boolean) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("bubble", on).apply() }

        /** Turn the bubble on (asks for the overlay permission first if needed) or off. */
        fun toggle(ctx: Context, on: Boolean): Boolean {
            if (!on) {
                setEnabled(ctx, false)
                ctx.stopService(Intent(ctx, BubbleService::class.java))
                return true
            }
            if (!Settings.canDrawOverlays(ctx)) {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return false
            }
            setEnabled(ctx, true)
            val i = Intent(ctx, BubbleService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            return true
        }
    }
}
