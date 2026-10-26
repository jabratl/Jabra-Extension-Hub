package com.jabra.hub

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Minimal private diagnostics log. It is never uploaded automatically. */
object AppDiagnostics {
    private const val MAX_LOG_BYTES = 256 * 1024L
    private val lock = Any()

    fun record(context: Context, event: String, error: Throwable? = null) {
        synchronized(lock) {
            try {
                val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
                val file = File(dir, "jabra-diagnostics.log")
                if (file.exists() && file.length() > MAX_LOG_BYTES) {
                    val previous = File(dir, "jabra-diagnostics.previous.log")
                    previous.delete()
                    file.renameTo(previous)
                }
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date())
                val detail = error?.let { " | ${it.javaClass.simpleName}: ${it.message.orEmpty().take(500)}" }.orEmpty()
                file.appendText("[$time] ${event.take(160)}$detail\n", Charsets.UTF_8)
            } catch (_: Exception) {
                // Diagnostics must never crash or block the app's main feature.
            }
        }
    }

    fun read(context: Context): String {
        val file = File(context.filesDir, "diagnostics/jabra-diagnostics.log")
        return if (file.isFile) file.readText(Charsets.UTF_8).takeLast(24_000) else "No diagnostics recorded yet."
    }

    fun clear(context: Context) {
        File(context.filesDir, "diagnostics/jabra-diagnostics.log").delete()
        File(context.filesDir, "diagnostics/jabra-diagnostics.previous.log").delete()
    }
}
