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
import android.view.WindowManager
import android.widget.TextView

/**
 * Floating "J" bubble drawn over other apps. Tap it after copying text: Jabra reads the
 * clipboard (through IntakeActivity, which has focus) and opens Novel Assistant.
 * Needs the "Display over other apps" permission, granted by the user.
 */
@Suppress("DEPRECATION")
class BubbleService : Service() {
    private var wm: WindowManager? = null
    private var bubble: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, notification())
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        if (bubble == null) addBubble()
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
            .setContentText("Copy text, then tap the J bubble to scan it.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Turn off", stop).build())
            .build()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun addBubble() {
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = manager
        val size = dp(52)
        val v = TextView(this).apply {
            text = "J"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#E85D9A")); setStroke(dp(2), Color.WHITE) }
            alpha = 0.92f
            elevation = dp(6).toFloat()
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(220) }

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true
                    if (moved) { lp.x = startX + dx.toInt(); lp.y = startY + dy.toInt(); try { manager.updateViewLayout(v, lp) } catch (_: Exception) { } }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) scanClipboard(); true }
                else -> false
            }
        }
        try { manager.addView(v, lp); bubble = v } catch (_: Exception) { stopSelf() }
    }

    private fun scanClipboard() {
        startActivity(
            Intent(this, IntakeActivity::class.java)
                .setAction(ChapterIntake.ACTION_SCAN_CLIPBOARD)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun onDestroy() {
        try { bubble?.let { wm?.removeView(it) } } catch (_: Exception) { }
        bubble = null
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
