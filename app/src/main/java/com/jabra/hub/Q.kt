package com.jabra.hub

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

/** "Quiet Pill" design helpers (v0.9.13, D0). Tokens live in res/values(-night)/q_tokens.xml. */
object Q {
    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun isDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun color(ctx: Context, @ColorRes id: Int): Int = ContextCompat.getColor(ctx, id)

    /** Returns a fresh copy so tinting one icon never changes the others. */
    fun icon(ctx: Context, @DrawableRes id: Int): Drawable? = ContextCompat.getDrawable(ctx, id)?.mutate()

    /** Light haptic tick; respects the system touch-feedback setting. */
    fun tick(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Small spring-like press: shrink, then settle back with a little overshoot. */
    fun pop(v: View) {
        v.animate().cancel()
        v.scaleX = 0.92f
        v.scaleY = 0.92f
        v.animate().scaleX(1f).scaleY(1f).setDuration(280L)
            .setInterpolator(OvershootInterpolator(2f)).start()
    }
}
