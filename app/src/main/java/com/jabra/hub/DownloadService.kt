package com.jabra.hub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.util.concurrent.atomic.AtomicInteger

/** Keeps downloads alive while Jabra is in the background. Started and stopped by Downloader. */
class DownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val n = b.setSmallIcon(R.drawable.ic_add)
            .setContentTitle("Jabra is downloading")
            .setContentText("Files are saved to Downloads/JabraHub.")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIF_ID, n)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "jabra_downloads"
        private const val NOTIF_ID = 4102
        private val active = AtomicInteger(0)

        fun begin(ctx: Context) {
            if (active.incrementAndGet() == 1) {
                try {
                    val i = Intent(ctx, DownloadService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
                } catch (_: Exception) { }
            }
        }

        fun end(ctx: Context) {
            if (active.decrementAndGet() <= 0) {
                active.set(0)
                try { ctx.stopService(Intent(ctx, DownloadService::class.java)) } catch (_: Exception) { }
            }
        }
    }
}
